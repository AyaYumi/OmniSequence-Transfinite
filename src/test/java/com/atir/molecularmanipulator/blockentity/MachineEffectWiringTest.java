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
            var save = method(type, prefix.equals("MolecularCenter") ? "saveStoredContents" : "saveAdditional");
            assertEquals("(Lnet/minecraft/nbt/CompoundTag;)V", save.desc, "Forge single-argument NBT");
            assertCall(save, SETTINGS, "save");
            assertCall(method(type, "loadTag"), SETTINGS, "load");
            assertCall(method(type, "writeToStream"), SETTINGS, "write");
            var streamRead = method(type, "readFromStream");
            assertCall(streamRead, SETTINGS, "read");
            assertEquals("(Lnet/minecraft/network/FriendlyByteBuf;)Z", streamRead.desc);
            for (var instruction : streamRead.instructions) {
                if (instruction instanceof MethodInsnNode call && call.owner.equals(SETTINGS) && call.name.equals("read")) {
                    assertEquals(Opcodes.IOR, call.getNext().getOpcode(), "Always consume the field with changed |= read");
                }
            }
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
                "getBlockEntity", "distanceToSqr", "mayInteract")) {
            assertTrue(index(calls, check) < index(calls, "setOverride"), check);
        }
        assertTrue(fields(read(ACCESS)).stream().anyMatch(f -> f.name.equals("containerMenu")));
        assertTrue(index(calls, "hasChunkAt") < index(calls, "getBlockEntity"));
        assertTrue(index(calls, "setOverride") < index(calls, "saveChanges"));
        assertTrue(index(calls, "saveChanges") < index(calls, "markForUpdate"));
        assertTrue(calls.stream().noneMatch(c -> c.owner.contains("ModConfig")));
        // The singularity path must additionally retain its existing canManage/withPlayer gate.
        assertCall(method(read(ROOT + "menu/SingularityMenu"), "withPlayer"),
                ROOT + "blockentity/SingularityBlockEntity", "canManage");
    }

    @Test void everyExistingVisualEntryUsesTheMachineResolver() throws IOException {
        for (var renderer : List.of("MolecularCenter", "OmniComputation", "MatterFabrication", "Singularity")) {
            assertCall(method(read(ROOT + "client/" + renderer + "Renderer"), "render"), LEVELS, "forMachine");
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
        var button = read(ROOT + "client/MachineEffectButton");
        assertEquals(ROOT + "client/OmniButton", button.superName);
        assertCall(method(button, "onPress"), "net/minecraft/client/gui/screens/Screen", "hasShiftDown");
        assertCall(method(button, "onPress"), SETTINGS, "nextLevel");
        assertCall(method(button, "refresh"), "java/util/function/IntSupplier", "getAsInt");
        assertCall(method(button, "refresh"), button.name, "setMessage");
        assertCall(method(button, "refresh"), button.name, "setTooltip");
        assertTrue(calls(method(button, "onPress")).stream().noneMatch(c -> c.name.equals("setOverride")));
        for (var prefix : List.of("MatterFabrication", "OmniComputation", "Singularity")) {
            assertCall(method(read(ROOT + "client/" + prefix + "Screen"), "init"), button.name, "<init>");
        }
        var controls = read(ROOT + "client/MolecularCenterControls");
        assertCall(method(controls, "<init>"), button.name, "<init>");
        assertCall(method(controls, "<init>"), ROOT + "client/MolecularCenterScreen", "addScreenWidget");
        assertCall(method(read(ROOT + "client/MolecularCenterScreen"), "init"), controls.name, "<init>");
    }

    @Test void singleBlockNexusAndHubRejectEffectActionsAndDoNotShowIneffectiveControls() throws IOException {
        for (var prefix : List.of("OmniComputation", "Singularity")) {
            var menu = read(ROOT + "menu/" + prefix + "Menu");
            var action = menu.methods.stream().filter(m -> calls(m).stream()
                    .anyMatch(c -> c.owner.equals(ACCESS) && c.name.equals("apply"))).findFirst().orElseThrow();
            var actionCalls = calls(action);
            assertTrue(index(actionCalls, "isSingleBlock") < index(actionCalls, "apply"));
        }
        assertCall(method(read(ROOT + "client/OmniComputationScreen"), "init"),
                ROOT + "blockentity/OmniComputationCoreBlockEntity", "isSingleBlock");
        assertCall(method(read(ROOT + "client/SingularityScreen"), "updateWidgets"),
                ROOT + "blockentity/SingularityBlockEntity", "isSingleBlock");
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
