package com.atir.molecularmanipulator.integration.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Verifies the actual AE2 15 hooks and all moved bridge callers without loading optional EAEP. */
class SequenceArrayPatternUploadWiringTest {
    private static final String ROOT = "com/atir/molecularmanipulator/";
    private static final String UPLOAD = ROOT + "integration/ae2/SequenceArrayPatternUpload";
    private static final String LOGIC = ROOT + "blockentity/MolecularCenterLogic";
    private static final String INVENTORY = "appeng/util/inv/AppEngInternalInventory";
    private static final String PLAYER = "Lnet/minecraft/server/level/ServerPlayer;";
    private static final String MENU = "Lappeng/menu/me/items/PatternEncodingTermMenu;";
    private static final String STACK = "Lnet/minecraft/world/item/ItemStack;";
    private static final String GRID = "Lappeng/api/networking/IGrid;";

    @Test void nativeUploadRunsAtEncodeTailNotBroadcastOrTick() throws IOException {
        var mixin = read(ROOT + "mixin/SequenceArrayPatternUploadMixin");
        var hook = method(mixin, "molecularmanipulator$uploadToEnabledSequenceArray");
        var injection = annotation(hook, "Lorg/spongepowered/asm/mixin/injection/Inject;");
        assertEquals(List.of("encode"), value(injection, "method"));
        @SuppressWarnings("unchecked")
        var at = (List<AnnotationNode>) value(injection, "at");
        assertEquals("TAIL", value(at.get(0), "value"));
        assertTrue(calls(hook).stream().anyMatch(call -> call.owner.equals(UPLOAD)
                && call.name.equals("uploadFromEncodingMenu")));
        assertTrue(calls(hook).stream().anyMatch(call -> call.name.equals("isClientSide")));
        assertNotNull(method(read("appeng/menu/me/items/PatternEncodingTermMenu"), "encode"));
    }

    @Test void menuUploadTransfersARealSourceAndEveryAdmissionUsesAutomaticControllerGate() throws IOException {
        var service = read(UPLOAD);
        var menuCalls = calls(method(service, "uploadFromEncodingMenu"));
        assertTrue(menuCalls.stream().anyMatch(call -> call.owner.equals(ROOT + "blockentity/MolecularCenterPatternTransfer")
                && call.name.equals("fromInventory")));
        for (String name : List.of("getPlayer", "isValidMenu", "stillValid", "isSpectator", "getNetworkNode")) {
            assertTrue(menuCalls.stream().anyMatch(call -> call.name.equals(name)), name);
        }
        var gate = calls(method(service, "accepts"));
        for (String name : List.of("isAutomaticPatternUploadEnabled", "canAcceptExternalPatternUpload", "getGrid")) {
            assertTrue(gate.stream().anyMatch(call -> call.name.equals(name)), name);
        }
        assertTrue(calls(method(service, "containsPatternOnSequenceArray")).stream().anyMatch(call ->
                call.owner.equals(LOGIC) && call.name.equals("containsUploadPattern")));
        assertTrue(calls(method(service, "uploadToSequenceArray")).stream().anyMatch(call ->
                call.owner.equals(LOGIC) && call.name.equals("firstUploadSlot")));
        assertTrue(service.methods.stream().flatMap(method -> calls(method).stream())
                .noneMatch(call -> List.of("hasMatrixUploadCore", "isLoaded", "getDefinition").contains(call.name)));
    }

    @Test void bothOptionalMixinsDelegateToTheNativeService() throws IOException {
        for (String mixin : List.of("ExtendedAEPlusPatternUploadMixin", "ExtendedAEPlusMatrixUploadMixin")) {
            var calls = read(ROOT + "mixin/" + mixin).methods.stream().flatMap(method -> calls(method).stream()).toList();
            assertTrue(calls.stream().anyMatch(call -> call.owner.equals(UPLOAD) && call.name.equals("uploadFromEncodingMenu")));
            assertTrue(calls.stream().anyMatch(call -> call.owner.equals(UPLOAD) && call.name.equals("uploadToSequenceArray")));
            assertTrue(calls.stream().noneMatch(call -> call.owner.endsWith("/MatrixUploadCoreIntegration")));
        }
    }

    @Test void actualForgeEaep162MatrixHooksRetainVoidMenuAndBothBooleanDescriptors() throws IOException {
        var matrix = read(ROOT + "mixin/ExtendedAEPlusMatrixUploadMixin");
        assertEquals(List.of("uploadFromEncodingMenuToMatrix(" + PLAYER + MENU + ")V"),
                value(annotation(method(matrix, "molecularmanipulator$uploadEncodedPattern"),
                        "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method"));
        assertEquals(List.of("uploadPatternToMatrix(" + PLAYER + STACK + GRID + ")Z"),
                value(annotation(method(matrix, "molecularmanipulator$uploadPattern"),
                        "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method"));
        assertEquals(List.of("uploadPatternToMatrix(" + PLAYER + STACK + GRID + "Z)Z"),
                value(annotation(method(matrix, "molecularmanipulator$uploadPatternSilently"),
                        "Lorg/spongepowered/asm/mixin/injection/Inject;"), "method"));
    }

    @Test void notificationRepairWrapsAe215MethodAndPreservesTheOldHostAbi() throws IOException {
        var wrapper = method(read(ROOT + "mixin/MolecularCenterInventoryNotificationsMixin"),
                "molecularmanipulator$releaseArrayNotificationGuard");
        assertEquals(List.of("onContentsChanged(I)V"),
                value(annotation(wrapper, "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;"), "method"));
        assertTrue(calls(wrapper).stream().anyMatch(call -> call.owner.equals(LOGIC) && call.name.equals("invalidateUploadIndex")));
        assertTrue(wrapper.tryCatchBlocks.stream().anyMatch(block -> block.type == null), "Latch reset must be in finally");
        boolean releasesLatch = false;
        for (var instruction : wrapper.instructions) {
            if (instruction instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD
                    && field.name.equals("notifyingChanges")) releasesLatch = true;
        }
        assertTrue(releasesLatch);
        var host = read("appeng/util/inv/InternalInventoryHost");
        assertEquals("(Lappeng/api/inventories/InternalInventory;I)V", method(host, "onChangeInventory").desc);
        assertNotNull(method(read(INVENTORY), "onContentsChanged"));
    }

    @Test void migrationEnumeratesEachSelectedCellOnceAndNeverUsesCraftingDefinitionsOrAggregateStorage() throws IOException {
        var session = read(ROOT + "blockentity/MolecularCenterPatternMigration$Session");
        var discovery = calls(method(session, "run"));
        assertTrue(discovery.stream().anyMatch(call -> call.name.equals("getMachineClasses")));
        assertTrue(discovery.stream().anyMatch(call -> call.name.equals("getActiveMachines")));
        var cell = calls(method(session, "moveCellHost"));
        assertEquals(1, cell.stream().filter(call -> call.name.equals("getAvailableStacks")).count());
        assertTrue(cell.stream().anyMatch(call -> call.name.equals("getOriginalCellInventory")));
        var transfer = read(ROOT + "blockentity/MolecularCenterPatternTransfer");
        assertTrue(calls(method(transfer, "fromCell")).stream().noneMatch(call -> call.name.equals("getAvailableStacks")));
        for (var type : List.of(session, transfer)) {
            assertTrue(type.methods.stream().flatMap(method -> calls(method).stream())
                    .noneMatch(call -> List.of("getDefinition", "getInventory", "encodeProcessingPattern",
                            "encodeCraftingPattern").contains(call.name)));
        }
    }

    private static ClassNode read(String owner) throws IOException {
        try (var input = SequenceArrayPatternUploadWiringTest.class.getClassLoader().getResourceAsStream(owner + ".class")) {
            assertNotNull(input, owner);
            var node = new ClassNode();
            new ClassReader(input).accept(node, 0);
            return node;
        }
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static AnnotationNode annotation(MethodNode method, String descriptor) {
        var annotations = new ArrayList<AnnotationNode>();
        if (method.visibleAnnotations != null) annotations.addAll(method.visibleAnnotations);
        if (method.invisibleAnnotations != null) annotations.addAll(method.invisibleAnnotations);
        return annotations.stream().filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElseThrow();
    }

    private static Object value(AnnotationNode annotation, String name) {
        for (int i = 0; i < annotation.values.size(); i += 2) {
            if (annotation.values.get(i).equals(name)) return annotation.values.get(i + 1);
        }
        throw new AssertionError("Missing annotation value " + name);
    }
}
