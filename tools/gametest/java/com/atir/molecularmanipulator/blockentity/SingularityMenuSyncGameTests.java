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
import io.netty.handler.codec.EncoderException;
import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual AE2 menu packets, bounded resource codecs, and live tag/config cache invalidation for issue #9. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityMenuSyncGameTests {
    private static final TagKey<Item> TEST_TAG = TagKey.create(Registries.ITEM,
            MolecularManipulator.id("verification/menu_resources"));
    private static final TagKey<Item> SECOND_TAG = TagKey.create(Registries.ITEM,
            MolecularManipulator.id("verification/menu_resources_second"));

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void hugeResourceListDoesNotBlockMenuState(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, SingularityContent.COMPACT.get().defaultBlockState(), 3);
        level.setBlock(pos.relative(Direction.EAST), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        helper.runAfterDelay(50, () -> {
            var machine = (SingularityBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(machine != null && machine.networkOnline(), "Use an actually powered Hub");
            var oldTags = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get());
            var oldBlacklist = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            int oldBatch = ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.get();
            int oldInterval = ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get();
            var savedTags = savedTags();
            var player = FakePlayerFactory.getMinecraft(level);
            var oldMenu = player.containerMenu;
            try {
                var all = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).toList();
                var rebound = new HashMap<>(savedTags);
                rebound.put(TEST_TAG, all.stream().<Holder<Item>>map(Item::builtInRegistryHolder).toList());
                bindTags(rebound, level.registryAccess());
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of("#" + TEST_TAG.location()));
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of());
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(37);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(7);
                var quantum = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                quantum.set(AEComponents.ENTANGLED_SINGULARITY_ID, 9_000_009L);
                machine.getQuantumInventory().setItemDirect(0, quantum);
                var snapshot = machine.collectionResources();
                var csv = machine.collectionItemIds();
                helper.assertTrue(csv.length() > 35_822 && snapshot.equals(all),
                        "Cross the report's actual old-string threshold with distinct registered items: " + csv.length());
                var legacy = new LegacyFields();
                legacy.collectionItems = csv;
                boolean reproduced = false;
                try {
                    new GuiDataSyncPacket(31, new DataSynchronization(legacy)::writeUpdate, level.registryAccess());
                } catch (EncoderException expected) {
                    reproduced = expected.getMessage().contains("String too big");
                }
                helper.assertTrue(reproduced, "The same real AE2 packet must reproduce the old failure");

                var server = menu(31, player, machine);
                var receiverMachine = new SingularityBlockEntity(pos, SingularityContent.COMPACT.get().defaultBlockState());
                receiverMachine.setLevel(level);
                var receiver = menu(31, player, receiverMachine);
                player.containerMenu = server;
                server.broadcastChanges();
                var scalarSync = new DataSynchronization(server);
                deliverScalars(helper, scalarSync, receiver);
                helper.assertTrue(receiver.formed && receiver.networkOnline && receiver.quantumFrequency == 9_000_009L
                                && receiver.quantumLinkState == server.quantumLinkState
                                && receiver.collectionBatchSize == 37 && receiver.collectionIntervalTicks == 7,
                        "The first actual menu packet must deliver non-default state despite the huge resource list");
                long generation = generation(server);
                helper.assertTrue(generation == 1, "Actual broadcast must send the first resource snapshot");
                var chunks = SingularityCollectionPayload.chunks(server.containerId, generation, snapshot);
                int largest = 0;
                for (int index = 0; index < chunks.size(); index++) {
                    var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                    try {
                        SingularityCollectionPayload.STREAM_CODEC.encode(buffer, chunks.get(index));
                        largest = Math.max(largest, buffer.readableBytes());
                        helper.assertTrue(buffer.readableBytes() <= 668, "Each packet has a hard numeric-id byte bound");
                        var decoded = SingularityCollectionPayload.STREAM_CODEC.decode(buffer);
                        helper.assertTrue(!buffer.isReadable() && receiver.acceptCollectionResources(decoded),
                                "Every real encoded chunk is accepted once by the actual receiving menu");
                        if (index + 1 < chunks.size()) helper.assertTrue(receiver.collectionResources().isEmpty(),
                                "Do not publish an incomplete list");
                    } finally {
                        buffer.release();
                    }
                    // State updates remain independently decodable between resource fragments.
                    if (index == 0) {
                        ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(41);
                        server.broadcastChanges();
                        deliverScalars(helper, scalarSync, receiver);
                        helper.assertTrue(receiver.collectionBatchSize == 41 && receiver.networkOnline,
                                "A scalar update must arrive while the list is still being assembled");
                    }
                }
                helper.assertTrue(receiver.collectionResources().stream().map(ItemStack::getItem).toList().equals(all),
                        "No resources may be truncated, reordered, duplicated, or silently lost");
                var displayed = receiver.collectionResources();
                for (int read = 0; read < 1_000; read++) {
                    helper.assertTrue(machine.collectionResources() == snapshot && receiver.collectionResources() == displayed,
                            "Steady reads must reuse server and client snapshots");
                    server.broadcastChanges();
                }
                helper.assertTrue(generation(server) == generation, "A thousand menu updates must not resend the list");
                helper.assertTrue(!receiver.acceptCollectionResources(new SingularityCollectionPayload(32,
                                generation + 1, 0, 0, List.of())) && receiver.collectionResources() == displayed,
                        "A packet for a different open menu cannot clear this one");
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of());
                server.broadcastChanges();
                helper.assertTrue(generation(server) == generation + 1, "A live resource change sends one replacement");
                helper.assertTrue(receiver.acceptCollectionResources(SingularityCollectionPayload.chunks(31,
                                generation + 1, machine.collectionResources()).getFirst())
                                && receiver.collectionResources().isEmpty(), "Empty snapshots explicitly clear the display");
                System.out.println("SINGULARITY_MENU_LARGE_SYNC_PASS oldCsv=" + csv.length() + " items=" + all.size()
                        + " chunks=" + chunks.size() + " maxChunkBytes=" + largest
                        + " initialScalars=true interleavedScalars=true steadyUpdates=1000 noResend=true emptyClear=true");
                helper.succeed();
            } finally {
                player.containerMenu = oldMenu;
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(oldTags);
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(oldBlacklist);
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(oldBatch);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(oldInterval);
                restoreTags(savedTags, level.registryAccess());
                level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.relative(Direction.EAST), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
            }
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void resourceCacheTracksConfigAndServerTagsWithoutTickScans(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var savedTags = savedTags();
        var oldTags = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get());
        var oldBlacklist = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
        try {
            var rebound = new HashMap<>(savedTags);
            rebound.put(TEST_TAG, holders(Items.IRON_INGOT, Items.GOLD_INGOT));
            rebound.put(SECOND_TAG, holders(Items.GOLD_INGOT, Items.DIAMOND));
            bindTags(rebound, registries);
            var counter = new CountingAccess(registries);
            var cache = new SingularityCollectionResources();
            var configured = new ArrayList<String>(List.of("#" + TEST_TAG.location()));
            ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(configured);
            ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of());
            var first = cache.get(counter, ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get(),
                    ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            helper.assertTrue(first.equals(List.of(Items.IRON_INGOT, Items.GOLD_INGOT)), "Actual bound tag contents");
            int lookups = counter.lookups;
            for (int read = 0; read < 1_000; read++) helper.assertTrue(cache.get(counter,
                            ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get(),
                            ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get()) == first,
                    "Repeated reads must reuse the snapshot");
            helper.assertTrue(counter.lookups == lookups, "Repeated reads must not even query the tag registry");
            configured.add(SECOND_TAG.location().toString());
            var union = cache.get(counter, ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get(), List.of());
            helper.assertTrue(union.equals(List.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND)),
                    "An in-place config-list change unions tags, deduplicates, and preserves order");
            ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of("minecraft:gold_ingot"));
            var excluded = cache.get(counter, configured, ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            helper.assertTrue(excluded.equals(List.of(Items.IRON_INGOT, Items.DIAMOND)), "Live blacklist invalidation");
            // Rebinding alone is not the invalidation signal; the completed server tag event is.
            rebound.put(TEST_TAG, holders(Items.EMERALD));
            BuiltInRegistries.ITEM.bindTags(rebound);
            SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(registries, true, true));
            helper.assertTrue(cache.get(counter, configured, ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get()) == excluded,
                    "An integrated-client notification must not invalidate authoritative server caches");
            SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(registries, false, false));
            var reloaded = cache.get(counter, configured, ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            helper.assertTrue(reloaded.equals(List.of(Items.EMERALD, Items.DIAMOND)),
                    "The production subscriber must rebuild changed tag contents for a server tag event");
            int afterReload = counter.lookups;
            helper.assertTrue(cache.get(counter, configured, ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get()) == reloaded
                            && counter.lookups == afterReload, "Reload invalidates once, not on every subsequent read");
            var replacement = new CountingAccess(registries);
            helper.assertTrue(cache.get(replacement, configured, ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get())
                            .equals(reloaded) && replacement.lookups > 0, "A replaced registry access cannot use an old lookup cache");
            System.out.println("SINGULARITY_RESOURCE_CACHE_PASS reads=1000 repeatedTagLookups=0 inPlaceTags=true blacklist=true serverTags=true clientEventIgnored=true registryReplacement=true");
            helper.succeed();
        } finally {
            ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(oldTags);
            ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(oldBlacklist);
            restoreTags(savedTags, registries);
        }
    }

    public static final class LegacyFields {
        @GuiSync(33) public String collectionItems = "";
    }

    private static SingularityMenu menu(int id, ServerPlayer player, SingularityBlockEntity machine) {
        try {
            var constructor = SingularityMenu.class.getDeclaredConstructor(int.class, Inventory.class, SingularityBlockEntity.class);
            constructor.setAccessible(true);
            return constructor.newInstance(id, player.getInventory(), machine);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Unable to construct actual SingularityMenu", exception);
        }
    }

    private static long generation(SingularityMenu menu) {
        try {
            var field = SingularityMenu.class.getDeclaredField("collectionGeneration");
            field.setAccessible(true);
            return field.getLong(menu);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void deliverScalars(GameTestHelper helper, DataSynchronization sender, SingularityMenu receiver) {
        var packet = new GuiDataSyncPacket(receiver.containerId, sender::writeUpdate, helper.getLevel().registryAccess());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(packet.syncData()), helper.getLevel().registryAccess());
        try {
            new DataSynchronization(receiver).readUpdate(buffer, new ShortOpenHashSet());
        } finally {
            buffer.release();
        }
    }

    private static Map<TagKey<Item>, List<Holder<Item>>> savedTags() {
        var saved = new HashMap<TagKey<Item>, List<Holder<Item>>>();
        BuiltInRegistries.ITEM.getTags().forEach(pair -> saved.put(pair.getFirst(), pair.getSecond().stream().toList()));
        return saved;
    }

    private static List<Holder<Item>> holders(Item... items) {
        return java.util.Arrays.stream(items).<Holder<Item>>map(Item::builtInRegistryHolder).toList();
    }

    private static void bindTags(Map<TagKey<Item>, List<Holder<Item>>> tags, RegistryAccess registries) {
        BuiltInRegistries.ITEM.bindTags(tags);
        // A synthetic global post outside the reload lifecycle makes NeoForge's consumed
        // DataMapLoader results fail. Exercise our real event handler without re-running
        // unrelated data-map application; actual tag binding above is still used.
        SingularityCollectionResources.onTagsUpdated(new TagsUpdatedEvent(registries, false, false));
    }

    private static void restoreTags(Map<TagKey<Item>, List<Holder<Item>>> tags, RegistryAccess registries) {
        tags.putIfAbsent(TEST_TAG, List.of());
        tags.putIfAbsent(SECOND_TAG, List.of());
        bindTags(tags, registries);
    }

    private static final class CountingAccess implements RegistryAccess {
        private final RegistryAccess delegate;
        int lookups;
        CountingAccess(RegistryAccess delegate) { this.delegate = delegate; }
        @Override public <E> Optional<Registry<E>> registry(ResourceKey<? extends Registry<? extends E>> key) {
            lookups++;
            return delegate.registry(key);
        }
        @Override public Stream<RegistryEntry<?>> registries() { return delegate.registries(); }
    }
}
