package com.atir.molecularmanipulator.network;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import static org.junit.jupiter.api.Assertions.*;

/** Forge transport and menu admission checks without constructing a client, AE grid or world. */
class SingularityCollectionWiringTest {
    private static final String ROOT = "com/atir/molecularmanipulator/";
    private static final String PAYLOAD = ROOT + "network/SingularityCollectionPayload";
    private static final String STATE = ROOT + "network/SingularityCollectionState";
    private static final String MENU = ROOT + "menu/SingularityMenu";
    private static final String MACHINE = ROOT + "blockentity/SingularityBlockEntity";
    private static final String RESOURCES = ROOT + "blockentity/SingularityCollectionResources";

    @Test void forgeChannelIsClientBoundAndDispatchesOnTheClientRatherThanUsingServerSender() throws IOException {
        var payload = read(PAYLOAD);
        assertTrue(payload.fields.stream().anyMatch(field -> field.desc.equals("Lnet/minecraftforge/network/simple/SimpleChannel;")));
        assertCall(method(payload, "<clinit>"), "net/minecraftforge/network/NetworkRegistry", "newSimpleChannel");
        assertTrue(fields(payload).stream().anyMatch(field -> field.owner.equals("net/minecraftforge/network/NetworkDirection")
                && field.name.equals("PLAY_TO_CLIENT")));
        assertTrue(calls(method(payload, "register")).stream().anyMatch(call -> call.name.equals("consumerMainThread")));
        assertTrue(payload.methods.stream().flatMap(method -> calls(method).stream()).anyMatch(call ->
                call.owner.equals("net/minecraftforge/fml/DistExecutor") && call.name.equals("unsafeRunWhenOn")));
        assertTrue(payload.methods.stream().flatMap(method -> calls(method).stream()).noneMatch(call -> call.name.equals("getSender")));
        var client = read(PAYLOAD + "$ClientHandler");
        assertCall(method(client, "accept"), "net/minecraft/client/Minecraft", "getInstance");
        assertCall(method(client, "accept"), MENU, "acceptCollectionResources");
        assertTrue(fields(client).stream().anyMatch(field -> field.name.equals("player")));
        assertCall(method(read(ROOT + "MolecularManipulator"), "<init>"), PAYLOAD, "register");
    }

    @Test void menuPublishesAfterAe2SyncAndDoesNotResendTheSameSnapshotReference() throws IOException {
        var menu = read(MENU);
        assertFalse(menu.fields.stream().anyMatch(field -> field.name.equals("collectionItems")));
        var broadcast = calls(method(menu, "broadcastChanges"));
        assertTrue(index(broadcast, "broadcastChanges") < index(broadcast, "syncCollectionResources"));
        var sync = method(menu, "syncCollectionResources");
        var syncCalls = calls(sync);
        for (var gate : List.of("isServerSide", "isValidMenu", "getPlayer")) {
            assertTrue(index(syncCalls, gate) < index(syncCalls, "collectionResources"));
        }
        assertCall(sync, MACHINE, "collectionResources");
        assertCall(sync, PAYLOAD, "chunks");
        assertCall(sync, PAYLOAD, "sendToPlayer");
        assertTrue(fields(menu).stream().anyMatch(field -> field.name.equals("containerMenu")));
        boolean identityComparison = false;
        for (var instruction : sync.instructions) {
            if (instruction instanceof JumpInsnNode jump && jump.getOpcode() == Opcodes.IF_ACMPNE) identityComparison = true;
        }
        assertTrue(identityComparison, "Use stable snapshot identity, not per-tick serialization or item comparisons");
        assertTrue(syncCalls.stream().noneMatch(call -> call.name.equals("equals") || call.name.equals("collectionItemIds")));
        var accept = calls(method(menu, "acceptCollectionResources"));
        assertTrue(index(accept, "containerId") < index(accept, "accept"));
        assertCall(method(menu, "acceptCollectionResources"), STATE, "accept");
        assertCall(method(menu, "collectionResources"), STATE, "resources");
    }

    @Test void productionAndOpenMenusShareOneCachedTagSnapshotAndOnlyServerReloadInvalidatesIt() throws IOException {
        var machine = read(MACHINE);
        assertCall(method(machine, "collectionResources"), RESOURCES, "get");
        assertCall(method(machine, "collectResource"), MACHINE, "collectionResources");
        assertCall(method(machine, "collectionItemIds"), MACHINE, "collectionResources");
        var cache = read(RESOURCES);
        assertTrue(cache.visibleAnnotations.stream().anyMatch(annotation ->
                annotation.desc.equals("Lnet/minecraftforge/fml/common/Mod$EventBusSubscriber;")));
        assertCall(method(cache, "onTagsUpdated"), "net/minecraftforge/event/TagsUpdatedEvent", "getUpdateCause");
        var updateFields = new ArrayList<FieldInsnNode>();
        for (var instruction : method(cache, "onTagsUpdated").instructions) {
            if (instruction instanceof FieldInsnNode field) updateFields.add(field);
        }
        assertTrue(updateFields.stream().anyMatch(field -> field.name.equals("SERVER_DATA_LOAD")));
        assertTrue(updateFields.stream().noneMatch(field -> field.name.equals("CLIENT_PACKET_RECEIVED")));
    }

    @Test void screenUsesMenuOwnedStacksWithoutParsingOrAllocatingResourcesEachFrame() throws IOException {
        var resources = method(read(ROOT + "client/SingularityScreen"), "collectionResources");
        assertCall(resources, MENU, "collectionResources");
        assertTrue(calls(resources).stream().noneMatch(call -> call.name.equals("split") || call.name.equals("<init>")));
    }

    private static void assertCall(MethodNode method, String owner, String name) {
        assertTrue(calls(method).stream().anyMatch(call -> call.owner.equals(owner) && call.name.equals(name)),
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
        return type.methods.stream().filter(method -> method.name.equals(name)).findFirst().orElseThrow();
    }

    private static ClassNode read(String name) throws IOException {
        try (var input = SingularityCollectionWiringTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input, name);
            var type = new ClassNode();
            new ClassReader(input).accept(type, 0);
            return type;
        }
    }
}
