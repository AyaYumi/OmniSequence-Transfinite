package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.crafting.CraftingLink;
import appeng.crafting.CraftingLinkNexus;
import appeng.crafting.execution.CraftingCpuHelper;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import com.atir.molecularmanipulator.mixin.EcoOmniParentOrderMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Real AE2/ECO descriptors plus direct execution of the parent link guard against a real AE2 link nexus. */
class EcoOmniParentOrderWiringTest {
    private static final String OPTIONAL = "com/atir/molecularmanipulator/mixin/EcoOmniParentOrderMixin";
    private static final String CPU = "appeng/crafting/execution/CraftingCpuLogic";

    @Test void everyShadowWrapAndInjectionMatchesTheActualAe2Binary() throws IOException {
        var base = type(CPU);
        var optional = type(OPTIONAL);
        var mixin = annotation(optional.invisibleAnnotations, "/Mixin;");
        assertEquals(800, value(mixin, "priority"));
        assertEquals(false, value(mixin, "remap"));
        int shadows = 0, wraps = 0, injections = 0;
        for (var field : optional.fields) if (has(field.visibleAnnotations, "/Shadow;")) {
            assertTrue(base.fields.stream().anyMatch(target -> target.name.equals(field.name) && target.desc.equals(field.desc)), field.name);
            shadows++;
        }
        for (var method : optional.methods) {
            if (has(method.visibleAnnotations, "/Shadow;")) {
                assertTrue(base.methods.stream().anyMatch(target -> target.name.equals(method.name) && target.desc.equals(method.desc)), method.name);
                shadows++;
            }
            for (var suffix : List.of("/WrapMethod;", "/WrapOperation;", "/Inject;")) {
                if (!has(method.visibleAnnotations, suffix)) continue;
                var annotation = annotation(method.visibleAnnotations, suffix);
                @SuppressWarnings("unchecked") var selectors = (List<String>) value(annotation, "method");
                for (var selector : selectors) assertTrue(base.methods.stream().anyMatch(target -> target.name.equals(selector)
                        || (target.name + target.desc).equals(selector)), selector);
                if (suffix.equals("/WrapOperation;")) {
                    @SuppressWarnings("unchecked") var points = (List<AnnotationNode>) value(annotation, "at");
                    var point = points.getFirst();
                    String target = (String) value(point, "target");
                    long matches = base.methods.stream().filter(targetMethod -> selectors.contains(targetMethod.name))
                            .flatMap(targetMethod -> java.util.stream.StreamSupport.stream(targetMethod.instructions.spliterator(), false))
                            .filter(instruction -> instruction instanceof MethodInsnNode call && (
                                    ("L" + call.owner + ";" + call.name + call.desc).equals(target)
                                    || (call.name.equals("<init>") && (call.desc.substring(0, call.desc.length() - 1)
                                            + "L" + call.owner + ";").equals(target))))
                            .count();
                    assertEquals(((Number) value(annotation, "require")).longValue(), matches, target);
                    wraps++;
                }
                if (suffix.equals("/Inject;")) injections++;
            }
        }
        assertEquals(3, shadows);
        assertEquals(3, wraps);
        assertEquals(3, injections);
        assertTrue(Files.readString(Path.of("src/main/resources/molecularmanipulator.mixins.json")).contains("\"EcoOmniParentOrderMixin\""));
        assertTrue(Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/mixin/MolecularManipulatorMixinPlugin.java"))
                .contains("startsWith(\"Eco\")"));
    }

    @Test void intermediateChildCannotCompleteOriginalRequesterNexusButLastChildCan() throws Exception {
        // Initialize the same real Minecraft key codecs used by the state-machine tests.
        Class.forName(EcoOmniParentOrderTest.class.getName());
        var mixin = new EcoOmniParentOrderMixin() {};
        var field = EcoOmniParentOrderMixin.class.getDeclaredField("molecularmanipulator$parent");
        field.setAccessible(true);
        var handler = EcoOmniParentOrderMixin.class.getDeclaredMethod("molecularmanipulator$deferParentDone", CraftingLink.class, Operation.class);
        handler.setAccessible(true);
        var id = UUID.randomUUID();
        var requester = (ICraftingRequester) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ICraftingRequester.class},
                (object, method, args) -> null);
        var cpuLink = new CraftingLink(CraftingCpuHelper.generateLinkData(id, false, false), (ICraftingCPU) null);
        var requestLink = new CraftingLink(CraftingCpuHelper.generateLinkData(id, false, true), requester);
        var nexus = new CraftingLinkNexus(id);
        cpuLink.setNexus(nexus); requestLink.setNexus(nexus);
        Operation<Void> markDone = args -> { ((CraftingLink) args[0]).markDone(); return null; };
        field.set(mixin, running(id, BigInteger.TEN, 6));
        handler.invoke(mixin, cpuLink, markDone);
        assertFalse(cpuLink.isDone());
        assertFalse(requestLink.isDone());
        assertEquals(id, requestLink.getCraftingID());
        field.set(mixin, running(id, BigInteger.valueOf(6), 6));
        handler.invoke(mixin, cpuLink, markDone);
        assertTrue(cpuLink.isDone());
        assertTrue(requestLink.isDone());
    }

    private static EcoOmniParentOrder running(UUID id, BigInteger requested, long child) {
        var options = new ECOPlannerOptions(true, true, Set.of());
        var parent = new EcoOmniParentOrder(id, new ECOBigOrderRequest(AEItemKey.of(Items.DIAMOND), requested, true, options), IActionSource.empty());
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var tag = parent.write(registries);
        tag.putString("state", "RUNNING_CHILD"); tag.putLong("child", child);
        return EcoOmniParentOrder.read(tag, registries);
    }
    private static boolean has(List<AnnotationNode> annotations, String suffix) {
        return annotations != null && annotations.stream().anyMatch(a -> a.desc.endsWith(suffix));
    }
    private static AnnotationNode annotation(List<AnnotationNode> annotations, String suffix) {
        assertNotNull(annotations);
        return annotations.stream().filter(a -> a.desc.endsWith(suffix)).findFirst().orElseThrow();
    }
    private static Object value(AnnotationNode annotation, String key) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(key)) return annotation.values.get(i + 1);
        return null;
    }
    private static ClassNode type(String name) throws IOException {
        try (var in = EcoOmniParentOrderWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(in, name);
            var node = new ClassNode(); new ClassReader(in).accept(node, 0); return node;
        }
    }
}
