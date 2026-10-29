package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

/** Enforces original-AE2-only shadows and selectors against the installed ECO 21.2.9 binaries. */
class EcoOmniPlanMenuWiringTest {
    private static final String MENU = "appeng/menu/me/crafting/CraftConfirmMenu";
    private static final String ECO = "cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin";
    private static final String OPTIONAL = "com/atir/molecularmanipulator/mixin/EcoOmniPlanMenuMixin";

    @Test void onlyOriginalAe2MembersMayBeShadowedAndEcoFieldsMustUseRuntimeAccess() throws IOException {
        var optional = annotation(type(OPTIONAL).invisibleAnnotations, "/Mixin;");
        assertEquals(900, value(optional, "priority"));
        assertEquals(false, value(optional, "remap"));
        var base = type(MENU);
        var merged = type(ECO);
        int shadows = 0;
        for (var field : type(OPTIONAL).fields) {
            assertFalse(field.name.startsWith("neoecoae$"), "Never declare another mixin's added field: " + field.name);
            if (!has(field.visibleAnnotations, "/Shadow;")) continue;
            shadows++;
            assertTrue(base.fields.stream().anyMatch(target -> target.name.equals(field.name) && target.desc.equals(field.desc)),
                    "Only fields present in original AE2 can be shadowed: " + field.name + field.desc);
        }
        for (var method : type(OPTIONAL).methods) {
            if (!has(method.visibleAnnotations, "/Shadow;")) continue;
            shadows++;
            assertTrue(base.methods.stream().anyMatch(target -> target.name.equals(method.name) && target.desc.equals(method.desc)),
                    "Only original AE2 methods can be shadowed: " + method.name);
        }
        assertEquals(4, shadows, "Only two original AE2 fields and two original AE2 methods may be shadowed");
        for (var method : type(OPTIONAL).methods) for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) {
                assertFalse(field.name.startsWith("neoecoae$"), "No direct bytecode references to ECO fields: " + field.name);
            }
        }
        var original = merged.methods.stream().filter(method -> method.name.equals("neoecoae$startBigOrder")).findFirst().orElseThrow();
        assertEquals("(Z)V", original.desc);
        assertTrue((original.access & Opcodes.ACC_PUBLIC) != 0);
        assertTrue(base.methods.stream().noneMatch(method -> method.name.equals(original.name)),
                "This selector deliberately targets ECO's merged public method");
    }

    @Test void everyInjectionSelectorAndRequiredInvocationExistsInTheRealBinaries() throws IOException {
        var base = type(MENU);
        var merged = type(ECO);
        var methods = new ArrayList<>(base.methods); methods.addAll(merged.methods);
        int checked = 0;
        for (var handler : type(OPTIONAL).methods) {
            if (!has(handler.visibleAnnotations, "/Inject;")) continue;
            var inject = annotation(handler.visibleAnnotations, "/Inject;");
            assertEquals(1, value(inject, "require"));
            @SuppressWarnings("unchecked") var selectors = (List<String>) value(inject, "method");
            for (var selector : selectors) {
                assertTrue(methods.stream().anyMatch(method -> selector.equals(method.name) || selector.equals(method.name + method.desc)),
                        "Unresolved injection selector: " + selector);
            }
            checked++;
        }
        assertEquals(4, checked);
    }

    @Test void availabilityIsWrittenAfterCpuCyclingAndBeforeGuiSync() throws IOException {
        var broadcast = type(MENU).methods.stream().filter(method -> method.name.equals("broadcastChanges")).findFirst().orElseThrow();
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : broadcast.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        var cycle = calls.stream().filter(call -> call.name.equals("detectAndSendChanges")).findFirst().orElseThrow();
        var sync = calls.stream().filter(call -> call.owner.equals("appeng/menu/AEBaseMenu")
                && call.name.equals("broadcastChanges")).findFirst().orElseThrow();
        assertEquals("()V", sync.desc);
        assertTrue(calls.indexOf(cycle) < calls.indexOf(sync));
        var handler = type(OPTIONAL).methods.stream().filter(method -> method.name.equals("molecularmanipulator$syncOmniBigOrderCpu"))
                .findFirst().orElseThrow();
        var injection = annotation(handler.visibleAnnotations, "/Inject;");
        @SuppressWarnings("unchecked") var points = (List<AnnotationNode>) value(injection, "at");
        assertEquals("Lappeng/menu/AEBaseMenu;broadcastChanges()V", value(points.getFirst(), "target"));
        assertNull(value(points.getFirst(), "shift"), "Default BEFORE runs before GuiSync sends the field");
        var available = type(ECO).fields.stream().filter(field -> field.name.equals("neoecoae$bigOrderCpu")).findFirst().orElseThrow();
        assertEquals("Z", available.desc);
        assertEquals(108, ((Number) value(annotation(available.visibleAnnotations, "/GuiSync;"), "value")).intValue());
    }

    @Test void accessFailureHasAnIndependentSynchronizedButtonGuardAndPublicSubmitError() throws IOException {
        var menu = type(OPTIONAL);
        var guard = menu.fields.stream().filter(field -> field.name.equals("molecularmanipulator$omniMenuAccessFailed"))
                .findFirst().orElseThrow();
        assertTrue(has(guard.visibleAnnotations, "/Unique;"));
        int id = ((Number) value(annotation(guard.visibleAnnotations, "/GuiSync;"), "value")).intValue();
        for (var target : List.of(type(MENU), type(ECO))) for (var field : target.fields) {
            if (has(field.visibleAnnotations, "/GuiSync;")) {
                assertNotEquals(id, ((Number) value(annotation(field.visibleAnnotations, "/GuiSync;"), "value")).intValue(),
                        "Own failure guard must not replace AE2/ECO synchronization: " + field.name);
            }
        }
        var close = menu.methods.stream().filter(method -> method.name.equals("molecularmanipulator$closeUnavailableOmniButton"))
                .findFirst().orElseThrow();
        @SuppressWarnings("unchecked") var selectors = (List<String>) value(annotation(close.visibleAnnotations, "/Inject;"), "method");
        assertEquals(List.of("neoecoae$bigOrderCpuAvailable()Z"), selectors);
        var fail = menu.methods.stream().filter(method -> method.name.equals("molecularmanipulator$failOmniMenuAccess"))
                .findFirst().orElseThrow();
        var calls = new ArrayList<String>();
        for (var instruction : fail.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
        assertTrue(calls.containsAll(List.of("setAutoStart", "failClosed")));
    }

    @Test void ecoSelectionsReturnBeforeRuntimeFieldAccessInEveryOmniHandler() throws IOException {
        for (var name : List.of("molecularmanipulator$syncOmniBigOrderCpu", "molecularmanipulator$includeOmniBigOrderCpu",
                "molecularmanipulator$startOmniBigOrder")) {
            var handler = type(OPTIONAL).methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
            var calls = new ArrayList<String>();
            for (var instruction : handler.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call.name);
            assertTrue(calls.indexOf("isOmni") < calls.indexOf("of"), "Guard ECO CPU behavior before access: " + name);
            var omni = java.util.Arrays.stream(handler.instructions.toArray())
                    .filter(instruction -> instruction instanceof MethodInsnNode call && call.name.equals("isOmni"))
                    .findFirst().orElseThrow();
            var branch = (org.objectweb.asm.tree.JumpInsnNode) omni.getNext();
            assertEquals(Opcodes.IFNE, branch.getOpcode());
            assertEquals(Opcodes.RETURN, branch.getNext().getOpcode(), "Non-Omni path must return directly");
        }
    }

    @Test void menuUsesPublicOwnershipApiAndNeverCallsEcoPrivateUniqueMethods() throws IOException {
        var menu = type(OPTIONAL);
        var privateEcoMethods = type(ECO).methods.stream()
                .filter(method -> (method.access & Opcodes.ACC_PRIVATE) != 0 && has(method.visibleAnnotations, "/Unique;"))
                .map(method -> method.name).toList();
        assertTrue(privateEcoMethods.containsAll(List.of("neoecoae$resolveBigOrderCpu",
                "neoecoae$resolveOwnedPlanningResult", "neoecoae$rejectBigOrder")));
        boolean resolver = false, runtimeAccess = false, publicError = false;
        for (var method : menu.methods) for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.FieldInsnNode field) {
                publicError |= field.owner.equals(MENU) && field.name.equals("submitError") && field.getOpcode() == Opcodes.PUTFIELD;
            }
            if (instruction instanceof MethodInsnNode call) {
                resolver |= call.name.equals("resolveOwnedResult");
                runtimeAccess |= call.owner.endsWith("/EcoOmniPlanMenuAccess") && call.name.equals("of");
                assertFalse(privateEcoMethods.contains(call.name), "Unsafe cross-mixin call: " + call.name);
                assertFalse(call.name.contains("beginCraftingCalculation"));
                assertFalse(call.owner.endsWith("ECOPlanningService"));
            }
        }
        assertTrue(resolver);
        assertTrue(runtimeAccess);
        assertTrue(publicError);
        for (var method : type("com/atir/molecularmanipulator/integration/eco/EcoOmniPlanMenu").methods) {
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.endsWith("ECOPlanningService"), "Submission must not replan");
                assertFalse(call.owner.endsWith("ECOBigOrderPlanner"), "No child/segment replanning");
            }
        }
    }

    private static boolean has(List<AnnotationNode> annotations, String suffix) {
        return annotations != null && annotations.stream().anyMatch(annotation -> annotation.desc.endsWith(suffix));
    }
    private static AnnotationNode annotation(List<AnnotationNode> annotations, String suffix) {
        assertNotNull(annotations, suffix);
        return annotations.stream().filter(annotation -> annotation.desc.endsWith(suffix)).findFirst().orElseThrow();
    }
    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        return null;
    }
    private static ClassNode type(String name) throws IOException {
        try (var input = EcoOmniPlanMenuWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var node = new ClassNode(); new ClassReader(input).accept(node, 0); return node;
        }
    }
}
