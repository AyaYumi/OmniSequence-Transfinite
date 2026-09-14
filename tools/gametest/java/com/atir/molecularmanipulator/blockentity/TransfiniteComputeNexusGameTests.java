package com.atir.molecularmanipulator.blockentity;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.AEColor;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.MachineSource;
import com.atir.molecularmanipulator.api.crafting.OmniBatchCraftingApi;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class TransfiniteComputeNexusGameTests {
    private static final BlockPos ORIGIN = new BlockPos(2408, 80, 328);
    private static final long MATERIALS = 3_000_000_000L;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 420)
    public static void cablesIndependentCpusAndPortableJobs(GameTestHelper helper) {
        var level = helper.getLevel();
        level.setChunkForced(ORIGIN.getX() >> 4, ORIGIN.getZ() >> 4, true);
        level.getChunkAt(ORIGIN);
        for (var pos : BlockPos.betweenClosed(ORIGIN.offset(-4, -3, -4), ORIGIN.offset(4, 3, 4))) {
            if (level.getBlockEntity(pos) instanceof appeng.blockentity.AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(5)).forEach(ItemEntity::discard);
        level.setBlock(ORIGIN, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        for (var side : Direction.values()) {
            var cablePos = ORIGIN.relative(side);
            level.setBlock(cablePos, AEBlocks.CABLE_BUS.block().defaultBlockState(), 3);
            var cable = (CableBusBlockEntity) level.getBlockEntity(cablePos);
            helper.assertTrue(cable.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, null) != null,
                    "Real AE smart cable must be installed: " + side);
            level.setBlock(ORIGIN.relative(side, 2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        }
        helper.runAfterDelay(60, () -> {
            var core = core(helper);
            helper.assertTrue(core.isSingleBlock() && core.isFormed() && core.isNetworkOnline(),
                    "A standalone nexus must form and come online through real cables");
            helper.assertTrue(core.isMaterialCalculationEnabled(), "Cable network must enable the existing planning path");
            helper.assertTrue(core.getMainNode().getNode().getIdlePowerUsage() == ModConfig.NEXUS_IDLE_POWER.get(),
                    "Nexus must use its configured power cost");
            for (var side : Direction.values()) {
                helper.assertTrue(level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, ORIGIN) == core,
                        "Nexus must expose the AE grid host capability");
                var cable = (CableBusBlockEntity) level.getBlockEntity(ORIGIN.relative(side));
                helper.assertTrue(cable.getGridNode(side.getOpposite()).getGrid() == core.getMainNode().getGrid(),
                        "Every face must physically connect to the cable network: " + side);
            }
            helper.assertTrue(core.getMainNode().getNode().getInWorldConnections().size() == 6,
                    "All six cable connections must be present");
            helper.assertTrue(core.getQuantumInventory().size() == 0 && core.getQuantumFrequency() == 0,
                    "Nexus must have no quantum inventory or frequency");
            core.startBuild(null); core.startDismantle(null); core.startStructureUpdate(null); core.openMenu(null);
            helper.assertTrue(!core.isBuilding() && !core.isDismantling() && core.getChunkLoadingChunks().isEmpty()
                            && MultiblockChunkLoading.ownedChunks(level, ORIGIN).isEmpty(),
                    "No controller operation or forced chunk footprint may be enabled");
            helper.assertTrue(!core.getVisualLayout().isFormed(), "Nexus must not render a multiblock effect");
            level.setBlock(ORIGIN.east(), ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
            level.setBlock(ORIGIN.west(), AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState(), 3);
        });
        helper.runAfterDelay(110, () -> {
            var core = core(helper);
            var adjacent = (OmniComputationCoreBlockEntity) level.getBlockEntity(ORIGIN.east());
            var ordinary = (CraftingBlockEntity) level.getBlockEntity(ORIGIN.west());
            helper.assertTrue(core.getCluster() != adjacent.getCluster() && core.getCluster() != ordinary.getCluster(),
                    "Touching nexus and AE CPUs must remain separate");
            helper.assertTrue(adjacent.isNetworkOnline() && ordinary.getCluster() != null,
                    "Cluster isolation must retain wired ME connectivity");
            helper.assertTrue(ordinary.getCluster().getAvailableStorage() == 1024,
                    "The ordinary AE CPU must not gain nexus storage");
            helper.assertTrue(core.getCluster().getAvailableStorage() == Long.MAX_VALUE
                            && core.getCluster().getCoProcessors() == Integer.MAX_VALUE,
                    "Nexus must keep Omni storage and parallelism");
            helper.assertTrue(OmniBatchCraftingApi.isOmniManagedCpu(core.getCluster().craftingLogic),
                    "Nexus must use the existing material allocation integration");
            helper.assertTrue(core.getCluster().getName().getContents() instanceof TranslatableContents name
                            && name.getKey().equals("gui.molecularmanipulator.nexus.cpu_name"),
                    "AE CPU selection must identify the nexus separately");
            var service = core.getMainNode().getGrid().getCraftingService();
            var source = new MachineSource(core);
            var plan = plan(helper);
            var primary = core.getCluster();
            helper.assertTrue(service.submitJob(plan, null, primary, false, source).successful(), "First job must submit");
            helper.assertTrue(service.submitJob(plan, null, primary, false, source).successful(),
                    "Submitting to a busy nexus must allocate a separate virtual CPU");
            var busy = core.allCpus().stream().filter(cpu -> cpu.isBusy()).toList();
            helper.assertTrue(busy.size() == 2 && core.getCpuLaneCount() == 3, "Two jobs must leave a spare CPU");
            for (var cpu : busy) cpu.craftingLogic.getInventory().insert(AEItemKey.of(Items.DIAMOND), MATERIALS, Actionable.MODULATE);
            // Capture a normal world save, then simulate the unload lifecycle before restoring the entity.
            var saved = core.saveWithFullMetadata(level.registryAccess());
            core.onChunkUnloaded();
            level.removeBlockEntity(ORIGIN);
            var loaded = BlockEntity.loadStatic(ORIGIN, level.getBlockState(ORIGIN), saved, level.registryAccess());
            helper.assertTrue(loaded instanceof OmniComputationCoreBlockEntity, "Shared entity type must restore nexus state");
            level.setBlockEntity(loaded);
        });
        helper.runAfterDelay(165, () -> {
            verifyJobs(helper);
            level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(2)).forEach(ItemEntity::discard);
            level.destroyBlock(ORIGIN, true);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(2));
            helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().is(ModContent.TRANSFINITE_COMPUTE_NEXUS_ITEM.get()),
                    "Breaking a busy nexus must produce exactly one retained nexus and no loose material drops");
            var portable = drops.getFirst().getItem().get(DataComponents.BLOCK_ENTITY_DATA);
            helper.assertTrue(portable != null, "Portable nexus must carry CPU recovery data");
            drops.getFirst().discard();
            level.setBlock(ORIGIN, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
            core(helper).loadTag(portable.copyTag(), level.registryAccess());
        });
        helper.runAfterDelay(220, () -> {
            verifyJobs(helper);
            for (var side : Direction.values()) level.setBlock(ORIGIN.relative(side, 2), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(275, () -> {
            var core = core(helper);
            helper.assertTrue(!core.isNetworkOnline() && !core.isMaterialCalculationEnabled()
                            && !core.getCluster().isActive(), "Losing cable power must stop computation");
            helper.assertTrue(!level.getBlockState(ORIGIN).getValue(BlockStateProperties.POWERED),
                    "Offline nexus must use the inactive model");
            verifyStoredJobs(helper);
            level.setBlock(ORIGIN.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        });
        helper.runAfterDelay(335, () -> {
            verifyJobs(helper);
            core(helper).clearContent();
            System.out.println("TRANSFINITE_NEXUS_PASS: six real cables, independent CPU clusters, virtual jobs, long materials, world reload, portable drop and power recovery");
            helper.succeed();
        });
    }

    private static OmniComputationCoreBlockEntity core(GameTestHelper helper) {
        return (OmniComputationCoreBlockEntity) helper.getLevel().getBlockEntity(ORIGIN);
    }

    private static void verifyJobs(GameTestHelper helper) {
        helper.assertTrue(core(helper).isNetworkOnline(), "Restored nexus must reconnect to its cable grid");
        helper.assertTrue(helper.getLevel().getBlockState(ORIGIN).getValue(BlockStateProperties.POWERED),
                "Online nexus must use the powered model");
        verifyStoredJobs(helper);
    }

    private static void verifyStoredJobs(GameTestHelper helper) {
        var core = core(helper);
        var busy = core.allCpus().stream().filter(cpu -> cpu.isBusy()).toList();
        helper.assertTrue(busy.size() == 2, "Both running CPU jobs must survive the lifecycle transition");
        for (var cpu : busy) {
            helper.assertTrue(cpu.craftingLogic.getInventory().list.get(AEItemKey.of(Items.DIAMOND)) == MATERIALS,
                    "More than int-range materials must remain exact on each CPU");
        }
        helper.assertTrue(core.getQuantumInventory().size() == 0 && core.getChunkLoadingChunks().isEmpty(),
                "Restoring contents must not enable quantum slots or multiblock loading");
    }

    private static ICraftingPlan plan(GameTestHelper helper) {
        var output = new GenericStack(AEItemKey.of(Items.EMERALD), 1);
        var encoded = PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)), List.of(output));
        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        return new ICraftingPlan() {
            @Override public GenericStack finalOutput() { return output; }
            @Override public long bytes() { return 4096; }
            @Override public boolean simulation() { return false; }
            @Override public boolean multiplePaths() { return false; }
            @Override public KeyCounter usedItems() { return new KeyCounter(); }
            @Override public KeyCounter emittedItems() { return new KeyCounter(); }
            @Override public KeyCounter missingItems() { return new KeyCounter(); }
            @Override public Map<IPatternDetails, Long> patternTimes() { return Map.of(pattern, 1L); }
        };
    }
}
