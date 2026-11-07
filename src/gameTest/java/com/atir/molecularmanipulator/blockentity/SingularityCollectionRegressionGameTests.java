package com.atir.molecularmanipulator.blockentity;

import appeng.api.ids.AEComponents;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.network.clientbound.GuiDataSyncPacket;
import appeng.menu.guisync.DataSynchronization;
import appeng.menu.guisync.GuiSync;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.menu.SingularityMenu;
import com.atir.molecularmanipulator.network.SingularityCollectionPayload;
import com.atir.molecularmanipulator.registry.SingularityContent;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Captures actual broadcasts: authoritative lists must not depend on a large optional modpack/CSV threshold. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityCollectionRegressionGameTests {
    private static final TagKey<Item> TEST_TAG = TagKey.create(Registries.ITEM, MolecularManipulator.id("gametest/legacy_collection"));

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void realMenuSendsBoundedAuthoritativeSnapshotsIndependentlyOfScalarState(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var machine = fixture.place(fixture.origin, SingularityContent.COMPACT.get().defaultBlockState(), SingularityBlockEntity.class);
        fixture.place(fixture.origin.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        var packets = new ArrayList<Packet<?>>();
        var player = fixture.player("collection-menu", fixture.origin, packets);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.formed() && machine.networkOnline(),
                "Use a genuinely powered compact Hub and a real server player")).thenExecute(() -> {
            var oldTags = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get());
            var oldBlacklist = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            int oldBatch = ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.get();
            int oldInterval = ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get();
            var savedTags = savedTags();
            try {
                var all = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
                helper.assertTrue(all.size() > SingularityCollectionPayload.MAX_ITEMS_PER_CHUNK,
                        "The base runtime already provides a multi-packet resource list; optional mods are not a prerequisite");
                var rebound = new HashMap<>(savedTags);
                rebound.put(TEST_TAG, all.stream().<Holder<Item>>map(Item::builtInRegistryHolder).toList());
                BuiltInRegistries.ITEM.bindTags(rebound);
                serverTags(fixture);
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of("#" + TEST_TAG.location()));
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of());
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(37);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(7);
                var quantum = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                quantum.set(AEComponents.ENTANGLED_SINGULARITY_ID, 91_361_004L);
                machine.getQuantumInventory().setItemDirect(0, quantum);
                var authoritative = machine.collectionResources();
                helper.assertTrue(authoritative.equals(all), "Use actual bound server tags, not synthetic unregistered item IDs");
                var server = menu(510, player, machine);
                var receiver = menu(510, player, machine); // Receiving state only: never broadcast this instance.
                player.containerMenu = server;
                packets.clear();
                server.broadcastChanges();
                helper.assertTrue(server.isValidMenu() && server.stillValid(player), "Real current menu stays valid");
                readScalars(helper, fixture, packets, server, receiver, true);
                helper.assertTrue(receiver.formed && receiver.networkOnline && receiver.quantumFrequency == 91_361_004L
                                && receiver.quantumLinkState == server.quantumLinkState
                                && receiver.collectionBatchSize == 37 && receiver.collectionIntervalTicks == 7,
                        "The first actual AE2 GuiDataSyncPacket delivers scalar state independently of the list");
                var initial = resources(packets);
                helper.assertTrue(initial.size() > 1 && initial.getFirst().generation() == 1,
                        "The actual broadcast, not a test call to chunks(), sends one initial fragmented generation");
                for (int index = 0; index < initial.size(); index++) {
                    deliver(helper, fixture, receiver, initial.get(index));
                    if (index + 1 < initial.size()) helper.assertTrue(receiver.collectionResources().isEmpty(),
                            "An incomplete list is never exposed to the screen");
                    if (index == 0) {
                        packets.clear();
                        ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(41);
                        server.broadcastChanges();
                        readScalars(helper, fixture, packets, server, receiver, false);
                        helper.assertTrue(receiver.collectionBatchSize == 41 && receiver.networkOnline && resources(packets).isEmpty(),
                                "A scalar update can arrive between fragments without resending the list");
                    }
                }
                assertItems(helper, receiver, all);
                var displayed = receiver.collectionResources();
                packets.clear();
                for (int repeat = 0; repeat < 30; repeat++) {
                    server.broadcastChanges();
                    helper.assertTrue(machine.collectionResources() == authoritative && receiver.collectionResources() == displayed,
                            "Steady reads reuse the server/client snapshots");
                }
                helper.assertTrue(resources(packets).isEmpty(), "Steady broadcasts do not resend unchanged authoritative lists");
                helper.assertTrue(!receiver.acceptCollectionResources(new SingularityCollectionPayload(511, 2, 0, 0, List.of()))
                                && receiver.collectionResources() == displayed,
                        "A packet for a different open menu cannot clear this display");

                // Live config invalidation must replace the same authoritative snapshot used by production.
                packets.clear();
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of("minecraft:barrier"));
                server.broadcastChanges();
                var replacement = resources(packets);
                helper.assertTrue(!replacement.isEmpty() && replacement.getFirst().generation() == 2,
                        "One blacklist change sends one replacement generation");
                deliver(helper, fixture, receiver, replacement.getFirst());
                helper.assertTrue(receiver.collectionResources() == displayed, "Keep the last complete list while its replacement is fragmented");
                helper.assertTrue(!receiver.acceptCollectionResources(initial.getFirst()), "Old-generation fragments cannot overwrite an assembling replacement");
                for (int index = 1; index < replacement.size(); index++) deliver(helper, fixture, receiver, replacement.get(index));
                assertItems(helper, receiver, all.stream().filter(item -> item != Items.BARRIER).toList());

                // Real registry rebinding, followed only by our subscriber, avoids re-running NeoForge's
                // consumed DataMapLoader results through a synthetic global TagsUpdatedEvent post.
                var beforeReload = machine.collectionResources();
                rebound.put(TEST_TAG, List.<Holder<Item>>of(Items.IRON_INGOT.builtInRegistryHolder(),
                        Items.GOLD_INGOT.builtInRegistryHolder(), Items.DIAMOND.builtInRegistryHolder()));
                BuiltInRegistries.ITEM.bindTags(rebound);
                SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(fixture.level.registryAccess(), true, true));
                helper.assertTrue(machine.collectionResources() == beforeReload, "An integrated-client tag notification cannot invalidate server authority");
                serverTags(fixture);
                packets.clear();
                server.broadcastChanges();
                var reloaded = resources(packets);
                helper.assertTrue(reloaded.size() == 1 && reloaded.getFirst().generation() == 3,
                        "The server tag event invalidates the snapshot exactly once");
                deliver(helper, fixture, receiver, reloaded.getFirst());
                assertItems(helper, receiver, List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND));

                player.containerMenu = player.inventoryMenu;
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of());
                packets.clear();
                server.broadcastChanges();
                helper.assertTrue(resources(packets).isEmpty(), "Closed/stale menus must not receive resource snapshots");
                player.containerMenu = server;
                packets.clear();
                server.broadcastChanges();
                var empty = resources(packets);
                helper.assertTrue(empty.size() == 1 && empty.getFirst().generation() == 4 && empty.getFirst().total() == 0,
                        "An empty authoritative list sends an explicit clearing generation when the menu is current again");
                deliver(helper, fixture, receiver, empty.getFirst());
                helper.assertTrue(receiver.collectionResources().isEmpty(), "An empty generation clears the old screen list");
            } finally {
                player.containerMenu = player.inventoryMenu;
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(oldTags);
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(oldBlacklist);
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(oldBatch);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(oldInterval);
                // bindTags does not unbind a newly created tag omitted from the map; explicitly clear it.
                savedTags.putIfAbsent(TEST_TAG, List.of());
                BuiltInRegistries.ITEM.bindTags(savedTags);
                serverTags(fixture);
            }
        }).thenSucceed();
    }

    private static SingularityMenu menu(int id, ServerPlayer player, SingularityBlockEntity machine) {
        try {
            var constructor = SingularityMenu.class.getDeclaredConstructor(int.class, Inventory.class, SingularityBlockEntity.class);
            constructor.setAccessible(true);
            return constructor.newInstance(id, player.getInventory(), machine);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Cannot construct the actual SingularityMenu", error);
        }
    }

    private static List<SingularityCollectionPayload> resources(List<Packet<?>> packets) {
        return packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                .map(ClientboundCustomPayloadPacket.class::cast).map(ClientboundCustomPayloadPacket::payload)
                .filter(SingularityCollectionPayload.class::isInstance).map(SingularityCollectionPayload.class::cast).toList();
    }

    private static void readScalars(GameTestHelper helper, ServerRegressionFixture fixture, List<Packet<?>> packets,
            SingularityMenu server, SingularityMenu receiver, boolean initial) {
        var scalarPackets = packets.stream().filter(ClientboundCustomPayloadPacket.class::isInstance)
                .map(ClientboundCustomPayloadPacket.class::cast).map(ClientboundCustomPayloadPacket::payload)
                .filter(GuiDataSyncPacket.class::isInstance).map(GuiDataSyncPacket.class::cast).toList();
        helper.assertTrue(!scalarPackets.isEmpty(), "Capture actual AE2 scalar sync emitted by broadcastChanges");
        var updatedFields = new ShortOpenHashSet();
        var dataSync = new DataSynchronization(receiver);
        for (var packet : scalarPackets) {
            var wire = new RegistryFriendlyByteBuf(Unpooled.buffer(), fixture.level.registryAccess());
            try {
                GuiDataSyncPacket.STREAM_CODEC.encode(wire, packet);
                var decoded = GuiDataSyncPacket.STREAM_CODEC.decode(wire);
                helper.assertTrue(!wire.isReadable() && decoded.containerId() == receiver.containerId
                                && Arrays.equals(packet.syncData(), decoded.syncData()),
                        "Native AE2 packet codec consumes the full wire frame and preserves the menu and scalar bytes");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(decoded.syncData()), fixture.level.registryAccess());
                try {
                    var packetFields = new ShortOpenHashSet();
                    dataSync.readUpdate(buffer, packetFields);
                    receiver.onServerDataSync(packetFields);
                    updatedFields.addAll(packetFields);
                    // AE2 19.2.18 writes VarInt(-1) as FF FF FF FF 0F, but its native
                    // reader stops after consuming the first FF FF as a short. Validate every
                    // remaining byte of that exact terminator, not arbitrary trailing padding.
                    helper.assertTrue(buffer.readableBytes() == 3, "Only AE2's known VarInt terminator suffix remains");
                    helper.assertTrue(buffer.readUnsignedByte() == 0xFF && buffer.readUnsignedByte() == 0xFF
                                    && buffer.readUnsignedByte() == 0x0F && !buffer.isReadable(),
                            "Consume the complete native AE2 scalar update, including its exact VarInt terminator");
                } finally {
                    buffer.release();
                }
            } finally {
                wire.release();
            }
        }
        // Discover the whole schema, including effect-level additions and inherited BuildingMenu
        // field 30000, instead of maintaining a stale hand-written subset of scalar fields.
        for (var field : SingularityMenu.class.getFields()) {
            var sync = field.getAnnotation(GuiSync.class);
            if (sync == null) continue;
            if (initial) helper.assertTrue(updatedFields.contains(sync.value()), "Initial native update includes " + field.getName());
            try {
                helper.assertTrue(Objects.equals(field.get(server), field.get(receiver)),
                        "Native scalar update preserves " + field.getDeclaringClass().getSimpleName() + "." + field.getName());
            } catch (IllegalAccessException error) {
                throw new IllegalStateException("Cannot inspect public synchronized menu field " + field, error);
            }
        }
    }

    private static void deliver(GameTestHelper helper, ServerRegressionFixture fixture, SingularityMenu receiver, SingularityCollectionPayload payload) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), fixture.level.registryAccess());
        try {
            SingularityCollectionPayload.STREAM_CODEC.encode(buffer, payload);
            helper.assertTrue(buffer.readableBytes() <= 668, "The actual numeric-ID resource fragment has a hard byte bound");
            var decoded = SingularityCollectionPayload.STREAM_CODEC.decode(buffer);
            helper.assertTrue(!buffer.isReadable() && receiver.acceptCollectionResources(decoded),
                    "Native codec consumes the entire bounded fragment and the actual receiving menu accepts it");
        } finally {
            buffer.release();
        }
    }

    private static void assertItems(GameTestHelper helper, SingularityMenu menu, List<Item> expected) {
        helper.assertTrue(menu.collectionResources().stream().map(ItemStack::getItem).toList().equals(expected),
                "The screen receives the exact authoritative order, with no truncation, duplicates or lost items");
    }

    private static Map<TagKey<Item>, List<Holder<Item>>> savedTags() {
        var result = new HashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> result.put(pair.getFirst(), pair.getSecond().stream().toList()));
        return result;
    }

    private static void serverTags(ServerRegressionFixture fixture) {
        SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(fixture.level.registryAccess(), false, false));
    }
}
