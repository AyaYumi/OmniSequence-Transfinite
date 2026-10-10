package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.ModFluids;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class CompactSingularityGameTests {
    private static final BlockPos ORIGIN = new BlockPos(3224, 120, 3224);

    @GameTest(template = "multiblock_dismantle_empty", batch = "compact_singularity", timeoutTicks = 200)
    public static void wiredProductionCapacityAndPortableContents(GameTestHelper helper) {
        var level = helper.getLevel();
        var chunk = new net.minecraft.world.level.ChunkPos(ORIGIN);
        level.setChunkForced(chunk.x, chunk.z, true);
        level.getChunkAt(ORIGIN);
        for (var pos : BlockPos.betweenClosed(ORIGIN.offset(-3, -3, -3), ORIGIN.offset(3, 3, 3)))
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(ORIGIN, SingularityContent.COMPACT.get().defaultBlockState(), 3);
        for (var side : Direction.values()) {
            var cablePos = ORIGIN.relative(side);
            level.setBlock(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            var cable = (CableBusBlockEntity) level.getBlockEntity(cablePos);
            helper.assertTrue(cable.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, null) != null,
                    "Real ME cable must be installed on " + side);
            level.setBlock(ORIGIN.relative(side, 2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        }
        helper.runAfterDelay(60, () -> {
            var machine = (SingularityBlockEntity) level.getBlockEntity(ORIGIN);
            var tags = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.get());
            var blacklist = List.copyOf(ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.get());
            var service = machine.getMainNode().getGrid().getStorageService();
            var storage = new Stock();
            IStorageProvider provider = mounts -> mounts.mount(storage, 0);
            service.addGlobalStorageProvider(provider);
            try {
                helper.assertTrue(machine.isSingleBlock() && machine.formed() && machine.networkOnline(),
                        "Compact hub must work without any structure");
                helper.assertTrue(level.getBlockEntity(ORIGIN) == machine
                        && machine.getMainNode().getNode().getInWorldConnections().size() == 6,
                        "Grid host capability must connect to real cables on all six faces");
                helper.assertTrue(machine.getBlockState().is(BlockTags.MINEABLE_WITH_PICKAXE), "Compact hub must be mineable");
                var player = FakePlayerFactory.get(level, new GameProfile(UUID.fromString("b5ea8401-bf1c-4e90-9353-7c938b0c099b"), "CompactHubVerifier"));
                player.setGameMode(GameType.SURVIVAL);
                player.setPos(ORIGIN.getX() + .5, ORIGIN.getY() + 1, ORIGIN.getZ() + .5);
                machine.startBuild(player); machine.startDismantle(player); machine.requestInspection(player);
                machine.requestEmbedding(player); machine.requestSuspendedUpgrade(player);
                helper.assertTrue(machine.operation() == SingularityBlockEntity.Operation.IDLE
                        && machine.getChunkLoadingChunks().isEmpty()
                        && MultiblockChunkLoading.ownedChunks(level, ORIGIN).isEmpty(),
                        "Compact hub must never start construction or own chunk tickets");

                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(List.of("minecraft:planks", "#minecraft:planks"));
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(List.of("minecraft:oak_planks"));
                int count = machine.collectionItemIds().split(",").length;
                helper.assertTrue(count > 1 && !machine.collectionItemIds().contains("minecraft:oak_planks"),
                        "Collection must union/deduplicate tags and apply the blacklist");
                machine.toggleCollection(player);
                helper.assertTrue(machine.collectionActive() && !machine.collectionStarting()
                        && !machine.motion().hasBodies() && !machine.motion().startForCollection(player),
                        "Compact collection must start immediately without moving bodies");
                storage.itemsEnabled = false;
                cycle(level, machine, ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get());
                helper.assertTrue(storage.totalItems() == 0 && machine.status() == SingularityBlockEntity.Status.STORAGE_FULL,
                        "Full ME item storage must pause production");
                storage.itemsEnabled = true;
                cycle(level, machine, ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get());
                helper.assertTrue(storage.totalItems() == (long) count * machine.collectionBatchSizeForMenu(),
                        "Every configured resource must be produced once per cycle");
                machine.toggleCollection(player);
                long harvested = storage.totalItems();
                cycle(level, machine, ModConfig.SINGULARITY_COLLECTION_INTERVAL_TICKS.get());
                helper.assertTrue(storage.totalItems() == harvested, "Stop must immediately stop collection");

                var matter = AEFluidKey.of(ModFluids.SEQUENCE_MATTER.get());
                machine.getDuplicationInventory().setItemDirect(1, new ItemStack(ModContent.BLACK_HOLE.get(), 2));
                machine.serverTick();
                storage.fluidCapacity = 0;
                cycle(level, machine, machine.duplicationIntervalTicksForMenu());
                helper.assertTrue(machine.lastDuplicationAeConsumed() == 0 && storage.amount(matter) == 0,
                        "Full fluid storage must stop generation before energy is consumed");
                storage.fluidCapacity = 7;
                cycle(level, machine, machine.duplicationIntervalTicksForMenu());
                helper.assertTrue(storage.amount(matter) == 7
                        && machine.lastDuplicationAeConsumed() == 7.0 * ModConfig.SINGULARITY_DUPLICATION_AE_PER_UNIT.get(),
                        "Partial capacity must generate only what fits and bill exact AE");
                storage.fluidCapacity = Long.MAX_VALUE;
                cycle(level, machine, machine.duplicationIntervalTicksForMenu());
                helper.assertTrue(storage.amount(matter) == 7 + machine.duplicationBatchSizeForMenu(),
                        "Black holes must produce fluid independently of collection and sample");

                var sample = new ItemStack(Items.DIAMOND);
                sample.setHoverName(Component.literal("Preserved sample"));
                var output = AEItemKey.of(sample);
                machine.getDuplicationInventory().setItemDirect(0, sample);
                storage.stock.put(matter, 1000L);
                storage.itemsEnabled = false;
                machine.serverTick();
                helper.assertTrue(storage.amount(matter) == 1000 && storage.amount(output) == 0,
                        "Blocked copied-item output must not consume fluid");
                storage.itemsEnabled = true;
                machine.serverTick();
                helper.assertTrue(storage.amount(matter) == 0 && storage.amount(output) == 1
                        && ItemStack.isSameItemSameTags(sample, machine.getDuplicationInventory().getStackInSlot(0)),
                        "Copy must spend one bucket and preserve sample components");

                var quantum = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
                quantum.getOrCreateTag().putLong("freq", 913579L);
                machine.getQuantumInventory().setItemDirect(0, quantum);
                var saved = machine.saveWithFullMetadata();
                helper.assertTrue(!saved.contains("singularityMotion") && !saved.contains("singularityLayout"),
                        "Single-block world saves must omit multiblock motion and layout");
                var reloaded = (SingularityBlockEntity) BlockEntity.loadStatic(ORIGIN, machine.getBlockState(), saved);
                helper.assertTrue(reloaded != null && reloaded.isSingleBlock() && reloaded.formed()
                        && reloaded.blackHoleCount() == 2 && reloaded.getQuantumFrequency() == 913579L,
                        "World reload must restore compact identity and production slots");
                // AE2 spawns retained contents during onRemove, separately from the loot table.
                helper.assertTrue(Block.getDrops(machine.getBlockState(), level, ORIGIN, machine).isEmpty(),
                        "Stored contents must suppress the ordinary empty block loot");
                level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(2)).forEach(ItemEntity::discard);
                level.destroyBlock(ORIGIN, true);
                var entities = level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(2));
                var drops = entities.stream().map(ItemEntity::getItem).toList();
                helper.assertTrue(drops.size() == 1 && drops.get(0).is(SingularityContent.COMPACT.get().asItem()),
                        "Removal must return exactly one compact hub, never a multiblock controller");
                var packed = drops.get(0).getTagElement("BlockEntityTag");
                var placed = new SingularityBlockEntity(ORIGIN, SingularityContent.COMPACT.get().defaultBlockState());
                helper.assertTrue(packed != null, "Drop must retain its stored contents");
                placed.loadTag(packed.copy());
                helper.assertTrue(placed.formed() && placed.getQuantumFrequency() == 913579L && placed.blackHoleCount() == 2
                        && ItemStack.isSameItemSameTags(sample, placed.getDuplicationInventory().getStackInSlot(0)),
                        "Packed drop must preserve quantum input, black holes and exact sample");
                entities.forEach(ItemEntity::discard);
                System.out.println("COMPACT_SINGULARITY_PASS sixFaces=true collection=true blacklist=true noMotion=true noTickets=true fluidCapacity=true exactEnergy=true copyComponents=true worldReload=true retainedDrop=true");
                helper.succeed();
            } finally {
                ModConfig.SINGULARITY_COLLECTION_ITEM_TAGS.set(tags);
                ModConfig.SINGULARITY_COLLECTION_ITEM_BLACKLIST.set(blacklist);
                service.removeGlobalStorageProvider(provider);
                if (!machine.isRemoved()) machine.clearContent();
                for (var pos : BlockPos.betweenClosed(ORIGIN.offset(-3, -3, -3), ORIGIN.offset(3, 3, 3)))
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setChunkForced(chunk.x, chunk.z, false);
            }
        });
    }

    private static void cycle(ServerLevel level, SingularityBlockEntity machine, int ticks) {
        ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(level.getGameTime() + ticks);
        machine.serverTick();
    }

    private static final class Stock implements MEStorage {
        final Map<AEKey, Long> stock = new HashMap<>();
        boolean itemsEnabled = true;
        long fluidCapacity = Long.MAX_VALUE;
        long amount(AEKey key) { return stock.getOrDefault(key, 0L); }
        long totalItems() { return stock.entrySet().stream().filter(e -> e.getKey() instanceof AEItemKey).mapToLong(Map.Entry::getValue).sum(); }
        @Override public long insert(AEKey what, long amount, Actionable mode, IActionSource source) {
            long accepted = what instanceof AEItemKey ? itemsEnabled ? amount : 0
                    : Math.min(amount, Math.max(0, fluidCapacity - amount(what)));
            if (mode == Actionable.MODULATE && accepted > 0) stock.merge(what, accepted, Long::sum);
            return accepted;
        }
        @Override public long extract(AEKey what, long amount, Actionable mode, IActionSource source) {
            long taken = Math.min(amount, amount(what));
            if (mode == Actionable.MODULATE) stock.put(what, amount(what) - taken);
            return taken;
        }
        @Override public void getAvailableStacks(KeyCounter counter) { stock.forEach(counter::add); }
        @Override public Component getDescription() { return Component.literal("Compact hub verification storage"); }
    }
}
