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
