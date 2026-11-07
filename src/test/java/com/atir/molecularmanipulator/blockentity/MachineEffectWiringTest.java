package com.atir.molecularmanipulator.blockentity;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Checks production hooks without constructing a Minecraft world, screen or AE2 grid. */
class MachineEffectWiringTest {
    private static final String ROOT = "com/atir/molecularmanipulator/";
    private static final String SETTINGS = ROOT + "blockentity/MachineEffectSettings";
    private static final String LEVELS = ROOT + "blockentity/MachineEffectLevels";
    private static final String ACCESS = ROOT + "menu/MachineEffectMenuAccess";
    private static final List<String> MACHINES = List.of("MolecularCenter", "MatterFabrication", "OmniComputation", "Singularity");

    @Test void allControllersPersistAndStreamTheOverrideInBothDirections() throws IOException {
        for (var prefix : MACHINES) {
            String name = prefix.equals("OmniComputation") ? prefix + "Core" : prefix;
            var type = read(ROOT + "blockentity/" + name + "BlockEntity");
            assertTrue(type.interfaces.contains(ROOT + "blockentity/MachineEffectHost"), name);
            assertCall(method(type, prefix.equals("MolecularCenter") ? "saveStoredContents" : "saveAdditional"),
                    SETTINGS, "save");
            assertCall(method(type, "loadTag"), SETTINGS, "load");
            assertCall(method(type, "writeToStream"), SETTINGS, "write");
            assertCall(method(type, "readFromStream"), SETTINGS, "read");
            assertTrue(method(type, "getMachineEffects").instructions.size() > 0);
        }
    }

    @Test void menusUseTypedAe2ActionsAndSyncMachineStateRatherThanAClientConfigValue() throws IOException {
        for (var prefix : MACHINES) {
            var type = read(ROOT + "menu/" + prefix + "Menu");
            var field = type.fields.stream().filter(f -> f.name.equals("dynamicEffectOverride")).findFirst().orElseThrow();
            assertTrue(field.visibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lappeng/menu/guisync/GuiSync;")));
            assertCall(method(type, "<init>"), type.name, "registerClientAction");
            assertCall(method(type, "requestDynamicEffectLevel"), type.name, "sendClientAction");
            assertCall(method(type, "requestDynamicEffectLevel"), SETTINGS, "isValidOverride");
            assertCall(method(type, "broadcastChanges"), SETTINGS, "getOverride");
            assertTrue(type.methods.stream().flatMap(m -> calls(m).stream())
                    .anyMatch(call -> call.owner.equals(ACCESS) && call.name.equals("apply")), prefix);
            assertFalse(fields(type).stream().anyMatch(f -> f.name.equals("DYNAMIC_EFFECT_LEVEL")), prefix);
        }
    }

    @Test void actionValidationPrecedesMutationAndBroadcastsToAllChunkWatchers() throws IOException {
        var method = method(read(ACCESS), "apply");
        var calls = calls(method);
        for (var check : List.of("isValidOverride", "isClientSide", "getPlayer", "isValidMenu", "stillValid",
                "isSpectator", "mayBuild", "getLevel", "isRemoved", "hasChunkAt",
                "getBlockEntity", "distanceToSqr", "mayInteract", "getLocator", "target", "dimension", "pos", "isValid")) {
            assertTrue(index(calls, check) < index(calls, "setOverride"), check);
        }
        assertTrue(fields(read(ACCESS)).stream().anyMatch(f -> f.name.equals("containerMenu")));
        assertTrue(index(calls, "hasChunkAt") < index(calls, "getBlockEntity"));
        assertTrue(index(calls, "hasChunkAt") < index(calls, "stillValid"));
        assertCall(method, ROOT + "terminal/BuildingTerminalLocator", "isValid");
        assertTrue(index(calls, "setOverride") < index(calls, "saveChanges"));
        assertTrue(index(calls, "saveChanges") < index(calls, "markForClientUpdate"));
        assertTrue(calls.stream().noneMatch(c -> c.owner.contains("ModConfig")));
        // The singularity path must additionally retain its existing canManage/withPlayer gate.
        assertCall(method(read(ROOT + "menu/SingularityMenu"), "withPlayer"),
                ROOT + "blockentity/SingularityBlockEntity", "canManage");
    }

    @Test void everyExistingVisualEntryUsesTheMachineResolver() throws IOException {
        for (var renderer : List.of("MolecularCenter", "OmniComputation", "MatterFabrication", "Singularity")) {
            var type = read(ROOT + "client/" + renderer + "Renderer");
            // The early-frame pipeline samples the effective level in capture, not during geometry submission.
            boolean earlyFrame = type.interfaces.contains(ROOT + "client/render/EarlyFrameGeometry$Source");
            assertCall(method(type, earlyFrame ? "capture" : "render"), LEVELS, "forMachine");
        }
        assertCall(method(read(ROOT + "client/SingularityCoreRenderer"), "render"), LEVELS, "forSingularityPart");
        assertCall(method(read(ROOT + "block/SingularityPartBlock"), "animateTick"), LEVELS, "forSingularityPart");
        assertCall(method(read(ROOT + "block/MolecularCenterCoreBlock"), "animateTick"), LEVELS, "forMolecularCore");
        for (var name : List.of("client/MolecularCenterRenderer", "client/OmniComputationRenderer",
                "client/MatterFabricationRenderer", "client/SingularityRenderer", "client/SingularityCoreRenderer",
                "block/SingularityPartBlock", "block/MolecularCenterCoreBlock")) {
            assertFalse(fields(read(ROOT + name)).stream().anyMatch(f -> f.name.equals("DYNAMIC_EFFECT_LEVEL")), name);
        }
        var ownerCalls = calls(method(read(LEVELS), "liveOwner"));
        assertTrue(index(ownerCalls, "hasChunkAt") < index(ownerCalls, "getBlockEntity"));
        assertTrue(ownerCalls.stream().noneMatch(c -> c.name.equals("getChunk")));
        for (var lookup : List.of("forMolecularCore", "forSingularityPart")) {
            assertCall(method(read(LEVELS), lookup), LEVELS, "resolve");
        }
        var cachedLookup = calls(method(read(LEVELS), "resolve"));
        assertTrue(index(cachedLookup, "get") < index(cachedLookup, "iterator"),
                "Read the cached owner before considering blueprint candidates");
    }

    @Test void singularityOffGatePrecedesEveryDecorativePassIncludingTheSceneLens() throws IOException {
        var render = method(read(ROOT + "client/SingularityRenderer"), "render");
        var instructions = render.instructions;
        int resolver = -1;
        for (int i = 0; i < instructions.size(); i++) {
            if (instructions.get(i) instanceof MethodInsnNode call && call.owner.equals(LEVELS)) resolver = i;
        }
        assertTrue(resolver >= 0);
        boolean offGate = false;
        for (int i = resolver + 1; i < instructions.size(); i++) {
            if (instructions.get(i).getOpcode() == Opcodes.RETURN) break;
            if (instructions.get(i) instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IFLE) offGate = true;
        }
        assertTrue(offGate, "0=off must not retain the old suspended-layout exemption");
        assertCall(render, ROOT + "client/render/SingularityWhiteHoleRenderer", "enqueue");
    }

    @Test void smallAe2ButtonsRemainKeyboardActivatableAndReadServerConfirmedState() throws IOException {
        assertAuthoritativeEffectButton();
        for (var prefix : MACHINES) {
            assertCall(method(read(ROOT + "client/" + prefix + "Screen"), "init"), ROOT + "client/MachineEffectButton", "<init>");
        }
    }

    private static void assertAuthoritativeEffectButton() throws IOException {
        var button = read(ROOT + "client/MachineEffectButton");
        assertEquals(ROOT + "client/ResponsiveAE2Button", button.superName);
        assertCall(method(button, "onPress"), "net/minecraft/client/gui/screens/Screen", "hasShiftDown");
        assertCall(method(button, "onPress"), SETTINGS, "nextLevel");
        assertCall(method(button, "refresh"), "java/util/function/IntSupplier", "getAsInt");
        assertCall(method(button, "refresh"), button.name, "setMessage");
        assertCall(method(button, "refresh"), button.name, "setTooltip");
        assertTrue(calls(method(button, "onPress")).stream().noneMatch(c -> c.name.equals("setOverride")));
    }

    @Test void fabricationAndOmniKeepDistinctMenuSyncIdsAndRemoteBuildingMenus() throws IOException {
        for (var prefix : List.of("MatterFabrication", "OmniComputation")) {
            var type = read(ROOT + "menu/" + prefix + "Menu");
            assertEquals(ROOT + "menu/BuildingMenu", type.superName);
            var ids = new java.util.HashSet<Object>();
            for (var field : type.fields) {
                if (field.visibleAnnotations == null) continue;
                for (var annotation : field.visibleAnnotations) {
                    if (annotation.desc.equals("Lappeng/menu/guisync/GuiSync;")) {
                        assertTrue(ids.add(annotation.values.get(1)), prefix + "." + field.name);
                    }
                }
            }
            assertCall(method(type, "<init>"), type.name, "registerClientAction");
            assertCall(method(type, "requestDynamicEffectLevel"), SETTINGS, "isValidOverride");
            assertCall(method(type, "broadcastChanges"), SETTINGS, "getOverride");
            var override = type.fields.stream().filter(f -> f.name.equals("dynamicEffectOverride")).findFirst().orElseThrow();
            var sync = override.visibleAnnotations.stream().filter(a -> a.desc.equals("Lappeng/menu/guisync/GuiSync;"))
                    .findFirst().orElseThrow();
            assertEquals(prefix.equals("OmniComputation") ? 23 : 29, ((Number) sync.values.get(1)).intValue());
        }
        var omni = read(ROOT + "menu/OmniComputationMenu");
        for (var fieldName : List.of("storageBytes", "cpuParallel")) {
            var field = omni.fields.stream().filter(f -> f.name.equals(fieldName)).findFirst().orElseThrow();
            assertEquals("J", field.desc);
            var sync = field.visibleAnnotations.stream().filter(a -> a.desc.equals("Lappeng/menu/guisync/GuiSync;"))
                    .findFirst().orElseThrow();
            assertEquals(fieldName.equals("storageBytes") ? 21 : 22, ((Number) sync.values.get(1)).intValue());
        }
    }

    @Test void fabricationAndOmniPersistAndStreamEffectsWithoutReplacingArchiveOrLegacyRecovery() throws IOException {
        for (var name : List.of("MatterFabrication", "OmniComputationCore")) {
            var type = read(ROOT + "blockentity/" + name + "BlockEntity");
            assertTrue(type.interfaces.contains(ROOT + "blockentity/MachineEffectHost"));
            assertCall(method(type, "saveAdditional"), SETTINGS, "save");
            assertCall(method(type, "loadTag"), SETTINGS, "load");
            assertCall(method(type, "writeToStream"), SETTINGS, "write");
            assertCall(method(type, "readFromStream"), SETTINGS, "read");
            assertCall(method(type, "startDismantle"), ROOT + "building/BuildingTransfer", "seal");
            assertCall(method(type, "loadTag"), ROOT + "blockentity/DismantlePlan", "load");
            assertCall(method(type, "serverTick"), ROOT + "building/BuildingTransfer", "restoring");
            var load = calls(method(type, "loadTag"));
            assertTrue(index(load, "unpack") < index(load, "load"), "Native retained contents precede effect loading");
            // The effect byte is appended, so all old animation/structure fields retain their existing order.
            for (var hook : List.of("writeToStream", "readFromStream")) {
                var stream = calls(method(type, hook));
                assertEquals(SETTINGS, stream.getLast().owner);
            }
        }
    }

    @Test void singularityNewReclaimUsesTheArchiveAndPersistsTheRequesterBeforeDocking() throws IOException {
        var type = read(ROOT + "blockentity/SingularityBlockEntity");
        assertCall(method(type, "startDismantle"), type.name, "requestPackageReclaim");
        var request = method(type, "requestPackageReclaim");
        var requestCalls = calls(request);
        assertTrue(index(requestCalls, "canManage") < index(requestCalls, "hasBodies"));
        assertTrue(index(requestCalls, "hasBodies") < index(requestCalls, "seal"));
        assertCall(request, ROOT + "building/BuildingTransfer", "seal");
        assertTrue(calls(method(type, "canManage")).stream().anyMatch(call -> call.name.equals("isSpectator")));
        var queued = fields(request).stream().filter(field -> field.getOpcode() == Opcodes.PUTFIELD
                && field.name.equals("packageReclaimRequester")).findFirst().orElseThrow();
        var dock = requestCalls.get(index(requestCalls, "dock"));
        assertTrue(request.instructions.indexOf(queued)
                < request.instructions.indexOf(requestCalls.get(index(requestCalls, "saveChanges"))));
        assertTrue(index(requestCalls, "saveChanges") < index(requestCalls, "dock"));
        assertEquals(Opcodes.ICONST_0, dock.getPrevious().getOpcode(), "New docking must not request native reclaim");
        assertTrue(requestCalls.stream().noneMatch(call -> call.name.equals("beginDismantle")));
        for (var hook : List.of("requestPackageReclaim", "tickPackageReclaim", "canSealPackage")) {
            assertTrue(calls(method(type, hook)).stream().noneMatch(call -> List.of(
                    "flushRecovery", "nextPortableStack", "clearContent").contains(call.name)), hook);
        }
    }

    @Test void singularityPendingReclaimPersistsBlocksConflictingWorkAndAllowsNativeBuffers() throws IOException {
        var type = read(ROOT + "blockentity/SingularityBlockEntity");
        var tag = type.fields.stream().filter(field -> field.name.equals("PACKAGE_RECLAIM_REQUESTER_TAG"))
                .findFirst().orElseThrow().value;
        for (var hook : List.of("saveAdditional", "loadTag")) {
            assertTrue(java.util.Arrays.stream(method(type, hook).instructions.toArray())
                    .anyMatch(instruction -> instruction instanceof LdcInsnNode literal && tag.equals(literal.cst)), hook);
        }
        assertCall(method(type, "saveAdditional"), "net/minecraft/nbt/CompoundTag", "putUUID");
        assertCall(method(type, "loadTag"), "net/minecraft/nbt/CompoundTag", "hasUUID");
        assertCall(method(type, "loadTag"), "net/minecraft/nbt/CompoundTag", "getUUID");
        assertCall(method(type, "saveAdditional"), ROOT + "blockentity/SingularityDuplicationProcessor", "save");
        assertCall(method(type, "loadTag"), ROOT + "blockentity/SingularityDuplicationProcessor", "load");
        for (var hook : List.of("startBuild", "toggleCollection", "requestEmbedding", "requestSuspendedUpgrade")) {
            assertCall(method(type, hook), type.name, "packageReclaimRequested");
        }
        var motion = read(ROOT + "blockentity/SingularityMotionState");
        for (var hook : List.of("startForCollection", "toggleInternal")) {
            assertCall(method(motion, hook), type.name, "packageReclaimRequested");
        }
        for (var hook : List.of("cancel", "clearContent")) {
            var clear = fields(method(type, hook)).stream().filter(field -> field.getOpcode() == Opcodes.PUTFIELD
                    && field.name.equals("packageReclaimRequester")).findFirst().orElseThrow();
            assertEquals(Opcodes.ACONST_NULL, clear.getPrevious().getOpcode(), hook);
        }
        var guard = method(type, "canSealPackage");
        assertCall(guard, type.name, "isSingleBlock");
        assertCall(guard, type.name, "packageReclaimRequested");
        assertCall(guard, motion.name, "hasBodies");
        for (var field : List.of("operation", "IDLE", "collectionActive", "embedRequested")) {
            assertTrue(fields(guard).stream().anyMatch(access -> access.name.equals(field)), field);
        }
        assertTrue(calls(guard).stream().noneMatch(call -> List.of("hasPortable", "isEmpty", "hasContents").contains(call.name)),
                "Portable materials, recovery, quantum and duplication contents must not make an idle controller busy");
    }

    @Test void singularityDeferredReclaimClearsBeforeSnapshotAndKeepsLegacyDismantleExecution() throws IOException {
        var type = read(ROOT + "blockentity/SingularityBlockEntity");
        var pending = method(type, "tickPackageReclaim");
        var pendingCalls = calls(pending);
        assertTrue(index(pendingCalls, "canManage") < index(pendingCalls, "hasBodies"));
        assertTrue(index(pendingCalls, "hasBodies") < index(pendingCalls, "seal"));
        assertTrue(fields(pending).stream().anyMatch(field -> field.name.equals("packageReclaimRequester")));
        assertFalse(fields(pending).stream().anyMatch(field -> field.name.equals("owner")),
                "Retry authorization must use the recorded requester, not a later recovery owner");
        var clear = fields(pending).stream().filter(field -> field.getOpcode() == Opcodes.PUTFIELD
                && field.name.equals("packageReclaimRequester")).findFirst().orElseThrow();
        assertEquals(Opcodes.ACONST_NULL, clear.getPrevious().getOpcode());
        assertTrue(pending.instructions.indexOf(clear)
                < pending.instructions.indexOf(pendingCalls.get(index(pendingCalls, "saveChanges"))));
        assertTrue(index(pendingCalls, "saveChanges") < index(pendingCalls, "seal"));
        assertEquals(1L, fields(pending).stream().filter(field -> field.getOpcode() == Opcodes.PUTFIELD
                && field.name.equals("packageReclaimRequester")).count(), "A refused seal must not requeue itself");
        var tick = method(type, "serverTick");
        assertCall(tick, ROOT + "building/BuildingTransfer", "restoring");
        var tickCalls = calls(tick);
        var afterMotion = tickCalls.subList(index(tickCalls, "serverTick") + 1, tickCalls.size());
        assertTrue(index(afterMotion, "isRemoved") < index(afterMotion, "tickPackageReclaim"));
        assertTrue(index(afterMotion, "tickPackageReclaim") < index(afterMotion, "tickDuplication"));
        assertTrue(index(afterMotion, "tickPackageReclaim") < index(afterMotion, "flushRecovery"));
        assertTrue(index(afterMotion, "tickPackageReclaim") < index(afterMotion, "nextPortableStack"));
        var following = afterMotion.get(index(afterMotion, "tickPackageReclaim")).getNext();
        while (following.getOpcode() < 0) following = following.getNext();
        assertEquals(Opcodes.RETURN, following.getOpcode(), "No controller work may follow a mid-tick seal");
        assertCall(method(type, "loadTag"), ROOT + "blockentity/DismantlePlan", "load");
        assertCall(method(type, "saveAdditional"), ROOT + "blockentity/DismantlePlan", "save");
        assertCall(tick, type.name, "dismantle");
        assertCall(method(type, "beginDismantle"), ROOT + "blockentity/DismantlePlan", "create");
        var motion = read(ROOT + "blockentity/SingularityMotionState");
        assertCall(method(motion, "restore"), type.name, "beginDismantle");
        assertTrue(fields(method(motion, "load")).stream().anyMatch(field -> field.name.equals("reclaimAfterDock")));
        assertTrue(fields(method(motion, "save")).stream().anyMatch(field -> field.name.equals("reclaimAfterDock")));
    }

    @Test void omniRetainsStarGradedPhysicalVirtualCpusAndSharedBudgetsWithoutAutomergeDuplicates() throws IOException {
        var type = read(ROOT + "blockentity/OmniComputationCoreBlockEntity");
        var signatures = new java.util.HashSet<String>();
        for (var method : type.methods) assertTrue(signatures.add(method.name + method.desc), method.name);
        assertTrue(type.fields.stream().noneMatch(f -> f.name.equals("VIRTUAL_PARALLEL_LIMIT")));
        assertCall(method(type, "getStorageBytes"), ROOT + "starcomputation/StarComputationBenefits", "storageBytes");
        assertCall(method(type, "getParallelismLimit"), ROOT + "starcomputation/StarComputationBenefits", "cpuParallel");
        assertCall(method(type, "entropyBenefits"), ROOT + "starcomputation/StarComputationNetwork", "forGrid");
        assertCall(method(type, "canAcceptNewJob"), ROOT + "crafting/OmniCpuResources", "acceptsNewJob");
        var create = type.methods.stream().filter(m -> m.name.equals("createVirtualCpu") && m.desc.startsWith("()"))
                .findFirst().orElseThrow();
        assertCall(create, ROOT + "starcomputation/StarComputationBenefits", "virtualCpu");
        assertCall(create, type.name, "hasUnrestoredCpuStates");
        assertCall(method(type, "reserveCpuStorage"), ROOT + "crafting/OmniCpuResources$StorageBudget", "reserve");
        assertCall(method(type, "claimDispatchAllowance"), ROOT + "crafting/OmniDispatchBudgetLedger", "claim");
        assertCall(method(type, "recordDispatchWork"), ROOT + "crafting/OmniDispatchBudgetLedger", "record");
        assertCall(method(type, "prepareDispatchAllowances"), ROOT + "crafting/OmniCpuResources", "singleDispatchLimit");
    }

    @Test void fabricationAndOmniOnlyReplaceTheCapturedLevelAndKeepEarlyParallelGeometry() throws IOException {
        assertAuthoritativeEffectButton();
        for (var name : List.of("MatterFabrication", "OmniComputation")) {
            var type = read(ROOT + "client/" + name + "Renderer");
            assertTrue(type.interfaces.contains(ROOT + "client/render/EarlyFrameGeometry$Source"));
            assertCall(method(type, "render"), ROOT + "client/render/EarlyFrameGeometry", "take");
            assertCall(method(type, "render"), type.name, "capture");
            assertCall(method(type, "capture"), LEVELS, "forMachine");
            assertFalse(fields(type).stream().anyMatch(f -> f.name.equals("DYNAMIC_EFFECT_LEVEL")));
            assertTrue(type.fields.stream().anyMatch(f -> f.desc.equals("L" + ROOT + "client/render/ParallelEffectGeometry;")));
            assertCall(method(read(ROOT + "client/" + name + "Screen"), "init"), ROOT + "client/MachineEffectButton", "<init>");
        }
    }

    private static void assertCall(MethodNode method, String owner, String name) {
        assertTrue(calls(method).stream().anyMatch(c -> c.owner.equals(owner) && c.name.equals(name)),
                () -> method.name + " must call " + owner + "." + name);
    }

    private static int index(List<MethodInsnNode> calls, String name) {
        for (int i = 0; i < calls.size(); i++) if (calls.get(i).name.equals(name)) return i;
        fail("Missing call: " + name);
        return -1;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        var result = new ArrayList<MethodInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof MethodInsnNode call) result.add(call);
        return result;
    }

    private static List<FieldInsnNode> fields(MethodNode method) {
        var result = new ArrayList<FieldInsnNode>();
        for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field) result.add(field);
        return result;
    }

    private static List<FieldInsnNode> fields(ClassNode type) {
        var result = new ArrayList<FieldInsnNode>();
        for (var method : type.methods) {
            for (var instruction : method.instructions) if (instruction instanceof FieldInsnNode field) result.add(field);
        }
        return result;
    }

    private static MethodNode method(ClassNode type, String name) {
        return type.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode read(String name) throws IOException {
        try (var input = MachineEffectWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var type = new ClassNode();
            new ClassReader(input).accept(type, 0);
            return type;
        }
    }
}
