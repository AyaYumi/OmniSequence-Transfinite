package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.GridHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import com.atir.molecularmanipulator.registry.ModContent;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.HashSet;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MolecularAutoCraftGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void singleBlockCapacityStopAndBoundedProbes(GameTestHelper helper) {
        var machine = single(helper, new BlockPos(9320, 120, 9320));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOperational(), "Powered auto-crafter"))
                .thenExecute(() -> checkNetwork(helper, machine, machine.getAutoCrafter())).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void arrayDirectDeliveryPreservesCpuPipeline(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = new BlockPos(9440, 100, 9440);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        var chunks = new HashSet<net.minecraft.world.level.ChunkPos>();
        for (var chunk : MolecularCenterStructure.chunkFootprint(pos, Direction.NORTH,
                MolecularCenterStructure.StructureLayout.CURRENT, MolecularCenterStructure.StructureLayout.CURRENT)) {
            level.setChunkForced(chunk.x, chunk.z, true);
            level.getChunk(chunk.x, chunk.z);
        }
        for (var part : MolecularCenterStructure.parts()) {
            var world = MolecularCenterStructure.worldPos(pos, Direction.NORTH, part);
            var chunk = new net.minecraft.world.level.ChunkPos(world);
            if (chunks.add(chunk)) level.setChunkForced(chunk.x, chunk.z, true);
            if (!MolecularCenterStructure.isController(part)) {
                level.setBlock(world, MolecularCenterStructure.partState(part.partType()), 3);
            }
        }
        level.setBlock(pos, ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState(), 3);
        var occupied = new HashSet<BlockPos>();
        for (var part : MolecularCenterStructure.parts()) {
            if (part.partType() != MolecularCenterStructure.PartType.AIR) {
                occupied.add(MolecularCenterStructure.worldPos(pos, Direction.NORTH, part));
            }
        }
        var energyPos = Arrays.stream(Direction.values()).map(pos::relative)
                .filter(neighbor -> !occupied.contains(neighbor)).findFirst().orElseThrow();
        level.setBlock(energyPos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var machine = (MolecularCenterBlockEntity) level.getBlockEntity(pos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.getMainNode().getNode() != null
                        && GridHelper.getExposedNode(level, energyPos, Direction.SOUTH) != null,
                "Array and creative power nodes initialize"))
                .thenExecute(() -> GridHelper.createConnection(machine.getMainNode().getNode(),
                        GridHelper.getExposedNode(level, energyPos, Direction.SOUTH)))
                .thenWaitUntil(() -> helper.assertTrue(machine.isFormed() && machine.isOperational(),
                "Formed and powered sequence array: formed=" + machine.isFormed() + ", active=" + machine.getMainNode().isActive()
                        + ", detected=" + MolecularCenterStructure.detectLayout(level, pos, Direction.NORTH)
                        + ", energyPos=" + energyPos + ", node=" + describeNode(machine))).thenExecute(() -> {
            checkNetwork(helper, machine, machine.getAutoCrafter());
            var encoded = planks(helper);
            machine.getLogic().getFullPatternInventory().setItemDirect(0, encoded);
            machine.getLogic().updatePatterns();
            var decoded = machine.getLogic().getAvailablePatterns().getFirst();
            var input = new KeyCounter(); input.add(AEItemKey.of(Items.OAK_LOG), 1000);
            helper.assertTrue(machine.getLogic().pushPattern(decoded, new KeyCounter[]{input}) && input.isEmpty(),
                    "CPU-ordered batches still accept and own inputs");
            var saved = machine.saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(saved.getList("auto_craft_primary_escrow", Tag.TAG_COMPOUND).stream()
                    .map(tag -> GenericStack.readTag(level.registryAccess(), (net.minecraft.nbt.CompoundTag) tag))
                    .anyMatch(stack -> stack != null && stack.what().equals(AEItemKey.of(Items.OAK_PLANKS)) && stack.amount() == 4000),
                    "CPU pipeline output retains its exact pre-accounting buffer");
        }).thenSucceed();
    }

    private static void checkNetwork(GameTestHelper helper, MolecularAutoCrafterHost host, MolecularAutoCrafter crafter) {
        var level = helper.getLevel();
        var service = host.getMainNode().getGrid().getStorageService();
        var stock = new Stock();
        var logs = AEItemKey.of(Items.OAK_LOG);
        var planks = AEItemKey.of(Items.OAK_PLANKS);
        stock.stock.put(logs, 1_000_000L);
        stock.limits.put(planks, 10);
        IStorageProvider provider = mounts -> mounts.mount(stock, 0);
        service.addGlobalStorageProvider(provider);
        try {
            crafter.getPatternInventory().setItemDirect(0, planks(helper));
            crafter.setEnabled(0, true);
            long tick = level.getGameTime();
            crafter.tick(tick);
            helper.assertTrue(stock.stock.getLong(planks) == 8 && stock.stock.getLong(logs) == 999_998,
                    "Only two four-plank crafts fit, and their output arrives in the same tick");
            helper.assertTrue(!host.hasAutoCraftRecovery() && crafter.getView(0).lastBatch() == 2,
                    "Normal production creates no internal output buffer");
            crafter.setEnabled(0, false);
            crafter.tick(tick + 1);
            helper.assertTrue(stock.stock.getLong(planks) == 8, "Disabling leaves no delayed output to drain");
            crafter.setEnabled(0, true);
            int probes = stock.probes;
            for (int offset = 2; offset < 42; offset++) crafter.tick(tick + offset);
            helper.assertTrue(stock.stock.getLong(logs) == 999_998 && stock.stock.getLong(planks) == 8,
                    "Full destination consumes no ingredients and builds no backlog");
            helper.assertTrue(stock.probes - probes <= 4,
                    "Full destination probes back off instead of running each tick: " + (stock.probes - probes));
            // User control wakes a blocked slot immediately; billions remain batch operations.
            stock.limits.put(planks, Long.MAX_VALUE);
            crafter.setEnabled(0, true);
            probes = stock.probes;
            crafter.tick(tick + 42);
            helper.assertTrue(stock.stock.getLong(planks) == 4_000_000 && stock.stock.getLong(logs) == 0,
                    "Capacity-aware crafting preserves million-item throughput");
            helper.assertTrue(stock.probes - probes <= 2 && !host.hasAutoCraftRecovery(),
                    "A million-item batch needs two output probes and no buffer");
            if (host instanceof MolecularCenterBlockEntity center) {
                helper.assertTrue(center.getLastPipelineTransfer() == 3_999_992,
                        "Array overview records direct delivery throughput");
            }
            crafter.getPatternInventory().setItemDirect(0, ItemStack.EMPTY);
            crafter.tick(tick + 43);
            helper.assertTrue(stock.stock.getLong(planks) == 4_000_000 && !crafter.getView(0).enabled(),
                    "Removing the pattern cannot revive output or enabled state");
        } finally {
            crafter.setEnabled(0, false);
            service.removeGlobalStorageProvider(provider);
        }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void adjacentContainersUseTheirRealRoomAndBulkOutput(GameTestHelper helper) {
        var machine = single(helper, new BlockPos(9560, 120, 9560));
        var level = helper.getLevel();
        var targetPos = machine.getBlockPos().east();
        level.setBlock(targetPos, Blocks.CHEST.defaultBlockState(), 3);
        var chest = (ChestBlockEntity) level.getBlockEntity(targetPos);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOperational(), "Powered adjacent crafter"))
                .thenExecute(() -> {
                    var service = machine.getMainNode().getGrid().getStorageService();
                    var stock = new Stock();
                    stock.stock.put(AEItemKey.of(Items.OAK_LOG), 1000L);
                    IStorageProvider provider = mounts -> mounts.mount(stock, 0);
                    service.addGlobalStorageProvider(provider);
                    var crafter = machine.getAutoCrafter();
                    try {
                        for (int slot = 0; slot < chest.getContainerSize(); slot++) chest.setItem(slot, new ItemStack(Items.OAK_PLANKS, 64));
                        chest.setItem(0, new ItemStack(Items.OAK_PLANKS, 60));
                        machine.toggleOutputMode();
                        machine.toggleOutputSide(Direction.EAST.get3DDataValue());
                        crafter.getPatternInventory().setItemDirect(0, planks(helper));
                        crafter.setEnabled(0, true);
                        crafter.tick(level.getGameTime());
                        helper.assertTrue(chest.getItem(0).getCount() == 64 && crafter.getView(0).lastBatch() == 1,
                                "Exactly one craft fills the four free spaces");
                        crafter.tick(level.getGameTime() + 1);
                        helper.assertTrue(stock.stock.getLong(AEItemKey.of(Items.OAK_LOG)) == 999
                                && machine.getBufferedAutoCraftOutputs().isEmpty(), "Full chest creates no hidden output");
                        chest.setItem(0, ItemStack.EMPTY); chest.setItem(1, ItemStack.EMPTY);
                        crafter.setEnabled(0, true);
                        crafter.tick(level.getGameTime() + 2);
                        helper.assertTrue(crafter.getView(0).lastBatch() == 32 && chest.getItem(0).getCount() == 64
                                && chest.getItem(1).getCount() == 64 && machine.getBufferedAutoCraftOutputs().isEmpty(),
                                "Two slots receive 128 products in one tick without a 64-item cap");
                    } finally {
                        crafter.setEnabled(0, false);
                        service.removeGlobalStorageProvider(provider);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void returnedBucketsAndSharedCapacityConserveAllIngredients(GameTestHelper helper) {
        var machine = single(helper, new BlockPos(9680, 120, 9680));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOperational(), "Powered remainder crafter"))
                .thenExecute(() -> {
                    var service = machine.getMainNode().getGrid().getStorageService();
                    var stock = new Stock();
                    stock.stock.put(AEItemKey.of(Items.MILK_BUCKET), 3000L);
                    stock.stock.put(AEItemKey.of(Items.SUGAR), 2000L);
                    stock.stock.put(AEItemKey.of(Items.EGG), 1000L);
                    stock.stock.put(AEItemKey.of(Items.WHEAT), 3000L);
                    stock.limits.put(AEItemKey.of(Items.CAKE), 1000L);
                    stock.limits.put(AEItemKey.of(Items.BUCKET), 2L);
                    var initial = new Object2LongOpenHashMap<AEKey>(stock.stock);
                    IStorageProvider provider = mounts -> mounts.mount(stock, 0);
                    service.addGlobalStorageProvider(provider);
                    var crafter = machine.getAutoCrafter();
                    try {
                        crafter.getPatternInventory().setItemDirect(0, cake(helper));
                        crafter.setEnabled(0, true);
                        crafter.tick(helper.getLevel().getGameTime());
                        helper.assertTrue(initial.equals(stock.stock) && !machine.hasAutoCraftRecovery(),
                                "Two bucket spaces cannot accept three returned buckets; refund all selected materials");
                        stock.limits.put(AEItemKey.of(Items.BUCKET), 3000L);
                        stock.sharedCapacity = 3;
                        crafter.setEnabled(0, true);
                        crafter.tick(helper.getLevel().getGameTime() + 1);
                        helper.assertTrue(initial.equals(stock.stock) && !machine.hasAutoCraftRecovery(),
                                "Three shared spaces cannot hold cake plus three buckets; actual partial delivery rolls back");
                        stock.sharedCapacity = 4;
                        crafter.setEnabled(0, true);
                        crafter.tick(helper.getLevel().getGameTime() + 2);
                        helper.assertTrue(stock.stock.getLong(AEItemKey.of(Items.CAKE)) == 1
                                && stock.stock.getLong(AEItemKey.of(Items.BUCKET)) == 3
                                && stock.stock.getLong(AEItemKey.of(Items.MILK_BUCKET)) == 2997
                                && stock.stock.getLong(AEItemKey.of(Items.SUGAR)) == 1998
                                && stock.stock.getLong(AEItemKey.of(Items.EGG)) == 999
                                && stock.stock.getLong(AEItemKey.of(Items.WHEAT)) == 2997
                                && !machine.hasAutoCraftRecovery(), "One cake commits with exact inputs and returned containers");
                    } finally {
                        crafter.setEnabled(0, false);
                        service.removeGlobalStorageProvider(provider);
                    }
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 300)
    public static void legacyOutputSurvivesSavingAndBlocksNewProduction(GameTestHelper helper) {
        var machine = single(helper, new BlockPos(9800, 120, 9800));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.isOperational(), "Powered legacy crafter"))
                .thenExecute(() -> {
                    var level = helper.getLevel();
                    var service = machine.getMainNode().getGrid().getStorageService();
                    var stock = new Stock();
                    var logs = AEItemKey.of(Items.OAK_LOG);
                    var planks = AEItemKey.of(Items.OAK_PLANKS);
                    stock.stock.put(logs, 1000L); stock.limits.put(planks, 0L);
                    IStorageProvider provider = mounts -> mounts.mount(stock, 0);
                    service.addGlobalStorageProvider(provider);
                    try {
                        var crafter = machine.getAutoCrafter();
                        crafter.getPatternInventory().setItemDirect(0, planks(helper));
                        crafter.setEnabled(0, true);
                        var saved = machine.saveWithFullMetadata(level.registryAccess());
                        var legacy = new ListTag();
                        legacy.add(GenericStack.writeTag(level.registryAccess(), new GenericStack(planks, 3_000_000_000L)));
                        saved.put("outputs", legacy); saved.putLong("output_ready_tick", 0);
                        machine.loadTag(saved, level.registryAccess());
                        machine.serverTick();
                        helper.assertTrue(machine.hasAutoCraftRecovery() && stock.stock.getLong(logs) == 1000,
                                "Old three-billion-item buffer is retained without new crafting");
                        saved = machine.saveWithFullMetadata(level.registryAccess());
                        var retained = GenericStack.readTag(level.registryAccess(), saved.getList("outputs", Tag.TAG_COMPOUND).getCompound(0));
                        helper.assertTrue(retained != null && retained.amount() == 3_000_000_000L
                                && machine.hasRemovalRecovery(), "Saving and removal recovery preserve old long quantities");
                        stock.limits.put(planks, 3_000_000_000L);
                        saved.putLong("output_ready_tick", 0);
                        machine.loadTag(saved, level.registryAccess());
                        machine.serverTick();
                        helper.assertTrue(stock.stock.getLong(planks) == 3_000_000_000L && stock.stock.getLong(logs) == 1000
                                && !machine.hasAutoCraftRecovery(), "Old buffer drains exactly once and full storage still blocks new production");
                    } finally {
                        machine.getAutoCrafter().setEnabled(0, false);
                        service.removeGlobalStorageProvider(provider);
                    }
                }).thenSucceed();
    }

    private static MolecularAutoCrafterBlockEntity single(GameTestHelper helper, BlockPos pos) {
        var level = helper.getLevel();
        level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
        level.getChunkAt(pos);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(pos, ModContent.MOLECULAR_AUTO_CRAFTER.get().defaultBlockState(), 3);
        level.setBlock(pos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        return (MolecularAutoCrafterBlockEntity) level.getBlockEntity(pos);
    }

    private static String describeNode(MolecularCenterBlockEntity machine) {
        var node = machine.getMainNode().getNode();
        return node == null ? "not initialized" : "powered=" + node.isPowered() + ", booted=" + node.hasGridBooted()
                + ", channel=" + node.meetsChannelRequirements() + ", connections=" + node.getConnections().size();
    }

    private static ItemStack planks(GameTestHelper helper) {
        var grid = new ItemStack[9]; Arrays.fill(grid, ItemStack.EMPTY); grid[0] = new ItemStack(Items.OAK_LOG);
        return pattern(helper, "oak_planks", grid, new ItemStack(Items.OAK_PLANKS, 4));
    }

    private static ItemStack cake(GameTestHelper helper) {
        return pattern(helper, "cake", new ItemStack[]{new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.SUGAR), new ItemStack(Items.EGG), new ItemStack(Items.SUGAR),
                new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT)}, new ItemStack(Items.CAKE));
    }

    @SuppressWarnings("unchecked")
    private static ItemStack pattern(GameTestHelper helper, String id, ItemStack[] grid, ItemStack output) {
        var recipe = (RecipeHolder<CraftingRecipe>) (RecipeHolder<?>) helper.getLevel().getRecipeManager()
                .byKey(ResourceLocation.withDefaultNamespace(id)).orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, grid, output, false, false);
    }

    private static final class Stock implements MEStorage {
        final Object2LongOpenHashMap<AEKey> stock = new Object2LongOpenHashMap<>();
        final Object2LongOpenHashMap<AEKey> limits = new Object2LongOpenHashMap<>();
        long sharedCapacity = Long.MAX_VALUE;
        int probes;

        @Override
        public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            long accepted = amount;
            if (limits.containsKey(key)) {
                long used = 0;
                for (var output : limits.keySet()) used += stock.getLong(output);
                accepted = Math.min(amount, Math.max(0, Math.min(limits.getLong(key) - stock.getLong(key), sharedCapacity - used)));
                if (mode == Actionable.SIMULATE) probes++;
            }
            if (mode == Actionable.MODULATE && accepted > 0) stock.addTo(key, accepted);
            return accepted;
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            long extracted = Math.min(amount, stock.getLong(key));
            if (mode == Actionable.MODULATE && extracted > 0) {
                long remaining = stock.getLong(key) - extracted;
                if (remaining == 0) stock.removeLong(key); else stock.put(key, remaining);
            }
            return extracted;
        }

        @Override
        public void getAvailableStacks(KeyCounter counter) {
            for (var entry : stock.object2LongEntrySet()) if (entry.getLongValue() > 0) counter.add(entry.getKey(), entry.getLongValue());
        }

        @Override
        public Component getDescription() {
            return Component.literal("Auto crafting regression stock");
        }
    }
}
