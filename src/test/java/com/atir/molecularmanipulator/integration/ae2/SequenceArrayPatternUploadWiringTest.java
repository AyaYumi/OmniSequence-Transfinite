package com.atir.molecularmanipulator.integration.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Checks the actual event/transaction wiring without a running server or addon registration. */
class SequenceArrayPatternUploadWiringTest {
    private static final String ROOT = "com/atir/molecularmanipulator/";
    private static final String UPLOAD = ROOT + "integration/ae2/SequenceArrayPatternUpload";
    private static final String CENTER = ROOT + "blockentity/MolecularCenterBlockEntity";
    private static final String LOGIC = ROOT + "blockentity/MolecularCenterLogic";

    @Test void nativeAndEaepEncodingMenusShareOneValidatedTransferPath() throws IOException {
        var nativeHook = read(ROOT + "mixin/SequenceArrayPatternUploadMixin");
        assertCall(method(nativeHook, "molecularmanipulator$uploadToEnabledSequenceArray"), UPLOAD, "uploadFromEncodingMenu");
        var optionalHook = read(ROOT + "mixin/ExtendedAEPlusPatternUploadMixin");
        assertCall(method(optionalHook, "molecularmanipulator$uploadEncodedPatternToSequenceArray"), UPLOAD, "uploadFromEncodingMenu");
        for (var type : List.of(nativeHook, optionalHook)) {
            assertTrue(type.methods.stream().flatMap(m -> calls(m).stream())
                    .noneMatch(call -> call.owner.contains("MatrixUploadCoreIntegration") || call.name.equals("extractItem")),
                    "Only the shared service may consume the encoding menu's physical output");
        }
        var encode = method(read(UPLOAD), "uploadFromEncodingMenu");
        for (var check : List.of("isSameThread", "isClientSide", "getPlayer", "isValidMenu", "stillValid", "isSpectator")) {
            assertTrue(index(calls(encode), check) < index(calls(encode), "containsPatternOnSequenceArray"), check);
        }
        assertCall(encode, UPLOAD, "consumeEncodedPattern");
        assertTrue(index(calls(encode), "consumeEncodedPattern") < index(calls(encode), "getBlankPatternInv"));
        assertTrue(calls(encode).stream().anyMatch(call -> call.name.equals("saveChanges")));
        assertTrue(calls(encode).stream().anyMatch(call -> call.name.equals("broadcastChanges")));
    }

    @Test void automatedInsertionUsesThePhysicalRollbackTransactionAndLiveExternalValidation() throws IOException {
        var upload = read(UPLOAD).methods.stream().filter(m -> m.name.equals("uploadToSequenceArray")
                && m.desc.contains("InternalInventory;")).findFirst().orElseThrow();
        assertCall(upload, CENTER, "canAcceptExternalPatternUpload");
        assertTrue(calls(upload).stream().anyMatch(call -> call.name.equals("canAcceptExternalPatternUpload")
                && call.desc.equals("(Lnet/minecraft/world/item/ItemStack;)Z")));
        assertCall(upload, LOGIC, "containsUploadPattern");
        assertCall(upload, LOGIC, "firstUploadSlot");
        assertCall(upload, ROOT + "blockentity/MolecularCenterPatternTransfer", "fromInventory");
        assertTrue(calls(upload).stream().noneMatch(call -> call.name.equals("insertItem")),
                "No unguarded copy-then-consume insertion alongside the rollback transaction");
        var center = read(CENTER);
        assertCall(method(center, "canMutateExternalPatterns"), CENTER, "canAccessExternalPatterns");
        assertCall(method(center, "canMutateExternalPatterns"), CENTER, "patternCrystalsUnchanged");
        assertCall(method(center, "isSupportedExternalPattern"), "appeng/api/crafting/PatternDetailsHelper", "decodePattern");
    }

    @Test void uploadIndexIsCallbackMaintainedAndRawRestorationInvalidatesIt() throws IOException {
        var logic = read(LOGIC);
        var notification = calls(method(logic, "onChangeInventory"));
        assertTrue(index(notification, "slotChanged") < index(notification, "onPatternInventoryChanged"));
        var center = read(CENTER);
        assertCall(method(center, "loadTag"), LOGIC, "invalidateUploadIndex");
        assertCall(method(center, "loadPatternShards"), LOGIC, "invalidateUploadIndex");
        var guard = read(ROOT + "mixin/MolecularCenterInventoryNotificationsMixin");
        assertCall(method(guard, "molecularmanipulator$releaseArrayNotificationGuard"), LOGIC, "invalidateUploadIndex");
        var index = read(ROOT + "crafting/PatternUploadIndex");
        assertTrue(index.methods.stream().noneMatch(m -> m.name.toLowerCase().contains("tick")),
                "The index is not a periodic library rescan");
    }

    @Test void starComputationBudgetAndBuildingArchivePathsAreNotReplacedByLegacyImplementations() throws IOException {
        for (var name : List.of("MolecularCenter", "MolecularManipulator", "AssemblerMatrixMolecularCore")) {
            var machine = read(ROOT + "blockentity/" + name + "BlockEntity");
            assertEquals(1, machine.methods.stream().filter(m -> m.name.equals("getRemainingAssemblerBudget")).count());
            assertCall(method(machine, "getRemainingAssemblerBudget"), ROOT + "starcomputation/StarComputationNetwork", "forGrid");
        }
        var center = read(CENTER);
        assertCall(method(center, "startDismantle"), ROOT + "building/BuildingTransfer", "seal");
        assertNotNull(method(center, "processWorkQueue"), "Old saved work queues must still have a reader/executor");
        var menu = read(ROOT + "menu/MolecularCenterMenu");
        assertEquals(ROOT + "menu/BuildingMenu", menu.superName);
        var manage = calls(method(menu, "canManagePatterns"));
        assertTrue(index(manage, "hasChunkAt") < index(manage, "getBlockEntity"));
        assertTrue(index(manage, "isValid") < index(manage, "stillValid"), "Remote access must validate its exact terminal binding");
    }

    @Test void machineEffectsAreSampledDuringEarlyFrameCaptureWhileTheScreenStaysOsq() throws IOException {
        var renderer = read(ROOT + "client/MolecularCenterRenderer");
        assertTrue(renderer.interfaces.contains(ROOT + "client/render/EarlyFrameGeometry$Source"));
        assertCall(method(renderer, "capture"), ROOT + "blockentity/MachineEffectLevels", "forMachine");
        assertCall(method(renderer, "render"), ROOT + "client/render/EarlyFrameGeometry", "take");
        assertCall(method(renderer, "prepareFeatherPasses"), ROOT + "client/render/ParallelEffectGeometry", "prepare");
        var init = method(read(ROOT + "client/MolecularCenterScreen"), "init");
        assertCall(init, ROOT + "client/OsqUi", "<init>");
        assertCall(init, ROOT + "client/MachineEffectButton", "<init>");
        assertTrue(calls(init).stream().noneMatch(call -> call.owner.contains("MolecularCenterLdUi")));
    }

    private static int index(List<MethodInsnNode> calls, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i;
        fail("Missing call: " + name);
        return -1;
    }

    private static void assertCall(MethodNode method, String owner, String name) {
        assertTrue(calls(method).stream().anyMatch(call -> call.owner.equals(owner) && call.name.equals(name)),
                () -> method.name + " must call " + owner + "." + name);
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode read(String name) throws IOException {
        try (var input = SequenceArrayPatternUploadWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var type = new ClassNode();
            new ClassReader(input).accept(type, 0);
            return type;
        }
    }
}
