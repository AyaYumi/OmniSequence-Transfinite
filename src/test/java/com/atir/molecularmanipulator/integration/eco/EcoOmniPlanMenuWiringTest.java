package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class EcoOmniPlanMenuWiringTest {
    private static final String MENU = "appeng/menu/me/crafting/CraftConfirmMenu";
    private static final String ECO = "cn/dancingsnow/neoecoae/mixins/ae2/menu/CraftConfirmMenuMixin";

    @Test void availabilityIsWrittenAfterCpuCyclingAndBeforeGuiSync() throws IOException {
        var broadcast = type(MENU).methods.stream().filter(method -> method.name.equals("broadcastChanges")).findFirst().orElseThrow();
        var calls = new ArrayList<MethodInsnNode>();
        for (var instruction : broadcast.instructions) if (instruction instanceof MethodInsnNode call) calls.add(call);
        var cycle = calls.stream().filter(call -> call.name.equals("detectAndSendChanges")).findFirst().orElseThrow();
        var sync = calls.stream().filter(call -> call.owner.equals("appeng/menu/AEBaseMenu")
                && call.name.equals("broadcastChanges")).findFirst().orElseThrow();
        assertEquals("()V", sync.desc);
        assertTrue(calls.indexOf(cycle) < calls.indexOf(sync));
        var handler = type(ECO).methods.stream().filter(method -> method.name.equals("neoecoae$syncBigOrderCpu"))
                .findFirst().orElseThrow();
        var injection = annotation(handler.visibleAnnotations, "/Inject;");
        @SuppressWarnings("unchecked") var points = (List<AnnotationNode>) value(injection, "at");
        assertEquals("Lappeng/menu/AEBaseMenu;broadcastChanges()V", value(points.getFirst(), "target"));
        assertNull(value(points.getFirst(), "shift"), "Default BEFORE runs before GuiSync sends the field");
        var available = type(ECO).fields.stream().filter(field -> field.name.equals("neoecoae$bigOrderCpu")).findFirst().orElseThrow();
        assertEquals("Z", available.desc);
        var syncId = value(annotation(available.visibleAnnotations, "/GuiSync;"), "value");
        assertTrue(type(MENU).fields.stream().noneMatch(field -> field.visibleAnnotations != null
                && field.visibleAnnotations.stream().anyMatch(value -> value.desc.endsWith("/GuiSync;")
                        && syncId.equals(value(value, "value")))));
    }

    @Test void submissionUsesTheConfirmedPlanWithoutReplanning() throws IOException {
        for (var method : type("com/atir/molecularmanipulator/integration/eco/EcoOmniPlanMenu").methods) {
            for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) {
                assertFalse(call.owner.endsWith("ECOPlanningService"), "Submission must not replan");
                assertFalse(call.owner.endsWith("ECOBigOrderPlanner"), "No child/segment replanning");
            }
        }
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
