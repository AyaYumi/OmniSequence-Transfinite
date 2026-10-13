package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.mojang.authlib.GameProfile;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Verifies the shared collector against a real powered ME grid and server configuration. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityCollectionGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_collection_config", timeoutTicks = 200)
    public static void configuredCyclesLiveChangesAndReload(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(1, 2, 1));
        level.setBlock(pos, SingularityContent.COMPACT.get().defaultBlockState(), 3);
        level.setBlock(pos.relative(Direction.EAST), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        helper.runAfterDelay(60, () -> {
            var machine = (SingularityBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(machine != null && machine.networkOnline(), "Hub must have a live powered ME grid");
            var previousTags = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get());
            var previousBlacklist = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            int previousBatch = ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.get();
            int previousInterval = ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get();
            var inventory = new Stock();
            var service = machine.getMainNode().getGrid().getStorageService();
            IStorageProvider provider = mounts -> mounts.mount(inventory, 0);
            var player = FakePlayerFactory.get(level, new GameProfile(
                    UUID.fromString("ac0db679-a5ab-41f6-9e03-998852dc9320"), "CollectionVerifier"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
            service.addGlobalStorageProvider(provider);
            try {
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of("minecraft:planks"));
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of());
                int resources = machine.collectionItemIds().split(",").length;
                helper.assertTrue(resources > 1, "The collection tag must contain multiple resources");
                for (int[] setting : new int[][] {{1000, 20}, {37, 7}, {1, 1}, {12345, 53}, {1000000, 1200}}) {
                    int batch = setting[0], interval = setting[1];
                    ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(batch);
                    ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(interval);
                    inventory.stock.clear();
                    machine.toggleCollection(player);
                    helper.assertTrue(machine.collectionActive(), "Collection must start");
                    helper.assertTrue(machine.collectionBatchSizeForMenu() == batch
                            && machine.collectionIntervalTicksForMenu() == interval,
                            "Menu values must reflect the server configuration");
                    for (int elapsed = 1; elapsed <= interval * 2 + 1; elapsed++) {
                        advance(level, machine, 1);
                        inventory.assertEach(helper, resources, (long) (elapsed / interval) * batch);
                    }
                    machine.toggleCollection(player);
                    long stopped = inventory.total();
                    advance(level, machine, interval * 5);
                    helper.assertTrue(inventory.total() == stopped, "Stopped collection must not produce items");
                    System.out.println("SINGULARITY_COLLECTION_CONFIG_PASS batch=" + batch + " interval=" + interval);
                }

                inventory.stock.clear();
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(37);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(50);
                machine.toggleCollection(player);
                advance(level, machine, 4);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(7);
                machine.serverTick();
                advance(level, machine, 6);
                inventory.assertEach(helper, resources, 0);
                advance(level, machine, 1);
                inventory.assertEach(helper, resources, 37);

                // Increasing the interval must also discard the previous deadline.
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(20);
                machine.serverTick();
                advance(level, machine, 7);
                inventory.assertEach(helper, resources, 37);
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(41);
                advance(level, machine, 13);
                inventory.assertEach(helper, resources, 78);

                inventory.itemsEnabled = false;
                advance(level, machine, 20);
                helper.assertTrue(machine.status() == SingularityBlockEntity.Status.STORAGE_FULL,
                        "Blocked ME storage must pause production");
                inventory.assertEach(helper, resources, 78);
                inventory.itemsEnabled = true;
                advance(level, machine, 19);
                inventory.assertEach(helper, resources, 78);
                advance(level, machine, 1);
                inventory.assertEach(helper, resources, 119);
                advance(level, machine, 60);
                inventory.assertEach(helper, resources, 160);

                var saved = machine.saveWithFullMetadata(level.registryAccess());
                machine.loadTag(saved, level.registryAccess());
                helper.assertTrue(machine.collectionActive(), "Reload must retain enabled collection");
                machine.serverTick();
                advance(level, machine, 19);
                inventory.assertEach(helper, resources, 160);
                advance(level, machine, 1);
                inventory.assertEach(helper, resources, 201);
                System.out.println("SINGULARITY_COLLECTION_LIVE_PASS shorter=true longer=true batch=true storageWait=true noCatchUp=true reload=true");
                helper.succeed();
            } finally {
                if (machine.collectionActive()) machine.toggleCollection(player);
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(previousTags);
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(previousBlacklist);
                ModConfig.SINGULARITY_COLLECTION_BATCH_SIZE.set(previousBatch);
                ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.set(previousInterval);
                service.removeGlobalStorageProvider(provider);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos.relative(Direction.EAST), Blocks.AIR.defaultBlockState(), 3);
            }
        });
    }

    private static void advance(ServerLevel level, SingularityBlockEntity machine, int ticks) {
        ((ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + ticks);
        machine.serverTick();
    }

    private static final class Stock implements MEStorage {
        final Map<AEKey, Long> stock = new HashMap<>();
        boolean itemsEnabled = true;
        long total() { return stock.values().stream().mapToLong(Long::longValue).sum(); }
        void assertEach(GameTestHelper helper, int resources, long expected) {
            helper.assertTrue(stock.size() == (expected == 0 ? 0 : resources), "Every resource must share the cycle");
            for (long actual : stock.values()) {
                helper.assertTrue(actual == expected, "Expected " + expected + " of each resource, got " + actual);
            }
        }
        @Override public Component getDescription() { return Component.literal("Collection verification storage"); }
        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            if (!(what instanceof AEItemKey) || !itemsEnabled) return 0;
            if (mode == Actionable.MODULATE) stock.merge(what, amount, Long::sum);
            return amount;
        }
    }
}
