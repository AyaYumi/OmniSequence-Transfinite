package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Binary contract checks against the actual AE2/ECO JARs used by this optional integration. */
class EcoOmniPlanWiringTest {
    private static final String MIXINS = "com/atir/molecularmanipulator/mixin/";
    private static final String CPU = "appeng/crafting/execution/CraftingCpuLogic";
    private static final String RUNTIME = "cn/dancingsnow/neoecoae/crafting/execution/ECOExecutionRuntime";

    @Test void bothAeExtractionsAndAllAcceptancePathsReachThePlanHooks() throws IOException {
        var base = type(CPU);
        var execute = method(base, "executeCrafting");
        assertEquals(2, calls(execute, "extractPatternInputs").size(), "AE2 extracts at task start and after acceptance");
        assertEquals(2, calls(execute, "insert").stream().filter(call -> call.owner.endsWith("ListCraftingInventory")).count());
        var shared = type(MIXINS + "CraftingCpuLogicMixin");
        var extraction = method(shared, "molecularmanipulator$extractBatch");
        assertFalse(calls(extraction, "ecoOmni$guard").isEmpty());
        assertFalse(calls(extraction, "ecoOmni$extract").isEmpty());
        assertFalse(calls(extraction, "ecoOmni$limit").isEmpty());
        assertFalse(calls(method(shared, "molecularmanipulator$pushExactBigIntegerBatch"), "ecoOmni$limit").isEmpty());
        var commits = shared.methods.stream().filter(value -> value.name.equals("molecularmanipulator$commitExactCraft")).toList();
        assertEquals(1, commits.stream().flatMap(value -> calls(value, "ecoOmni$accepted").stream()).count());
        assertFalse(calls(method(shared, "molecularmanipulator$rotateTaskEntries"), "ecoOmni$candidates").isEmpty());
    }

    @Test void sharedCpuAndHookDescriptorsHaveNoEcoImplementationClassDependency() throws IOException {
        for (var name : List.of(MIXINS + "CraftingCpuLogicMixin",
                "com/atir/molecularmanipulator/integration/eco/EcoOmniPlanCpuAccess")) {
            var reader = reader(name);
            for (int index = 1; index < reader.getItemCount(); index++) {
                if (reader.getItem(index) == 0 || reader.readByte(reader.getItem(index) - 1) != 7) continue;
                var dependency = reader.readUTF8(reader.getItem(index), new char[reader.getMaxStringLength()]);
                assertFalse(dependency.startsWith("cn/dancingsnow/neoecoae/"), name + " links " + dependency);
            }
        }
    }

    @Test void sharedCpuClassCanLoadWithAllEcoImplementationClassesUnavailable() throws Exception {
        var urls = java.util.Arrays.stream(System.getProperty("java.class.path").split(java.io.File.pathSeparator))
                .filter(value -> !value.contains("neoecoae-"))
                .map(value -> {
                    try { return java.nio.file.Path.of(value).toUri().toURL(); }
                    catch (java.net.MalformedURLException failure) { throw new AssertionError(failure); }
                }).toArray(java.net.URL[]::new);
        try (var isolated = new java.net.URLClassLoader(urls, ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("cn.dancingsnow.neoecoae.")) throw new ClassNotFoundException(name);
                return super.loadClass(name, resolve);
            }
        }) {
            var shared = Class.forName(MIXINS.replace('/', '.') + "CraftingCpuLogicMixin", false, isolated);
            assertTrue(java.util.Arrays.stream(shared.getInterfaces()).anyMatch(type -> type.getSimpleName().equals("EcoOmniPlanCpuAccess")));
            assertThrows(ClassNotFoundException.class, () -> isolated.loadClass(RUNTIME.replace('/', '.')));
        }
    }

    @Test void optionalWrapsAndRuntimeInvokersMatchTheInstalledBinarySignatures() throws IOException {
        var cpu = type(CPU);
        var optional = type(MIXINS + "EcoOmniCpuPlanMixin");
        int checkedWrappers = 0;
        for (var handler : optional.methods) {
            var annotations = new ArrayList<org.objectweb.asm.tree.AnnotationNode>();
            if (handler.invisibleAnnotations != null) annotations.addAll(handler.invisibleAnnotations);
            if (handler.visibleAnnotations != null) annotations.addAll(handler.visibleAnnotations);
            for (var annotation : annotations) {
                if (!annotation.desc.endsWith("/WrapMethod;")) continue;
                checkedWrappers++;
                for (int index = 0; index < annotation.values.size(); index += 2) {
                    if (!annotation.values.get(index).equals("method")) continue;
                    @SuppressWarnings("unchecked") var names = (List<String>) annotation.values.get(index + 1);
                    for (var name : names) assertNotNull(method(cpu, name));
                }
            }
        }
        assertTrue(checkedWrappers >= 6, "Every optional lifecycle wrapper must be checked");
        var runtime = type(RUNTIME);
        assertEquals("(ILjava/util/Map;)J", method(runtime, "taskRemaining").desc);
        assertEquals("(L" + RUNTIME + "$DispatchCandidate;Ljava/math/BigInteger;)Ljava/math/BigInteger;",
                method(runtime, "exactAllowance").desc);
        assertTrue(runtime.methods.stream().anyMatch(value -> value.name.equals("fromNBT")
                && value.desc.equals("(Lcn/dancingsnow/neoecoae/crafting/planner/result/ECOExecutionPlan;Ljava/util/Map;"
                        + "Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)L" + RUNTIME + ";")));
    }

    private static List<MethodInsnNode> calls(MethodNode method, String name) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call && call.name.equals(name)) result.add(call);
        return result;
    }
    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(value -> value.name.equals(name)).findFirst()
                .orElseThrow(() -> new AssertionError(type.name + "." + name + " is missing"));
    }
    private static ClassReader reader(String name) throws IOException {
        try (var resource = EcoOmniPlanWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(resource, name);
            return new ClassReader(resource);
        }
    }
    private static ClassNode type(String name) throws IOException {
        var result = new ClassNode();
        reader(name).accept(result, 0);
        return result;
    }
}
