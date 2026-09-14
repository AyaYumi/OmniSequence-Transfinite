package com.atir.molecularmanipulator.blockentity;

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
import net.minecraft.world.item.BlockItem;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class TransfiniteComputeNexusGameTests {
    private static BlockPos ORIGIN = new BlockPos(2408, 80, 328);
    private static final long MATERIALS = 3_000_000_000L;

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 800)
    public static void cablesIndependentCpusAndPortableJobs(GameTestHelper helper) {
        var level = helper.getLevel();
        if (!net.minecraftforge.fml.ModList.get().isLoaded("advanced_ae")) {
            helper.assertTrue(level.getRecipeManager().byKey(new net.minecraft.resources.ResourceLocation(
                    "molecularmanipulator:transfinite_compute_nexus")).isEmpty(),
                    "Nexus recipe must be absent without AdvancedAE");
        }
        // A reused test world can still be restoring AE grid nodes at the old fixture.
        // Use a fresh chunk per run; reload and retained-drop behavior are exercised below.
        ORIGIN = new BlockPos(2408 + (int) (level.getGameTime() % 100_000) * 16, 80, 328);
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
        helper.startSequence().thenWaitUntil(() -> {
            var ready = core(helper);
            helper.assertTrue(ready.getMainNode().getNode() != null && ready.isNetworkOnline(),
                    "Wait for AE grid: node=" + (ready.getMainNode().getNode() != null)
                            + ", formed=" + ready.isFormed() + ", powered=" + ready.isPowered()
                            + ", cluster=" + (ready.getCluster() != null));
        }).thenExecute(() -> {
            var core = core(helper);
            helper.assertTrue(core.isSingleBlock() && core.isFormed() && core.isNetworkOnline(),
                    "A standalone nexus must form and come online through real cables");
            helper.assertTrue(core.isMaterialCalculationEnabled(), "Cable network must enable the existing planning path");
            helper.assertTrue(core.getMainNode().getNode().getIdlePowerUsage() == ModConfig.NEXUS_IDLE_POWER.get(),
                    "Nexus must use its configured power cost");
            for (var side : Direction.values()) {
                helper.assertTrue(core.getGridNode(side) != null,
                        "Nexus must expose an AE grid node on each side");
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
        }).thenIdle(50).thenExecute(() -> {
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
            var saved = core.saveWithFullMetadata();
            core.onChunkUnloaded();
            level.removeBlockEntity(ORIGIN);
            var loaded = BlockEntity.loadStatic(ORIGIN, level.getBlockState(ORIGIN), saved);
            helper.assertTrue(loaded instanceof OmniComputationCoreBlockEntity, "Shared entity type must restore nexus state");
            level.setBlockEntity(loaded);
        }).thenIdle(55).thenWaitUntil(() -> verifyJobs(helper, "world reload")).thenExecute(() -> {
            level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(2)).forEach(ItemEntity::discard);
            level.destroyBlock(ORIGIN, true);
            var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(ORIGIN).inflate(2));
            helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(ModContent.TRANSFINITE_COMPUTE_NEXUS_ITEM.get()),
                    "Breaking a busy nexus must produce exactly one retained nexus and no loose material drops");
            var portable = BlockItem.getBlockEntityData(drops.get(0).getItem());
            helper.assertTrue(portable != null, "Portable nexus must carry CPU recovery data");
            drops.get(0).discard();
            level.setBlock(ORIGIN, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
            core(helper).loadTag(portable.copy());
        }).thenIdle(55).thenWaitUntil(() -> verifyJobs(helper, "portable drop")).thenExecute(() -> {
            for (var side : Direction.values()) level.setBlock(ORIGIN.relative(side, 2), Blocks.AIR.defaultBlockState(), 3);
        }).thenIdle(55).thenExecute(() -> {
            var core = core(helper);
            helper.assertTrue(!core.isNetworkOnline() && !core.isMaterialCalculationEnabled()
                            && !core.getCluster().isActive(), "Losing cable power must stop computation");
            helper.assertTrue(!level.getBlockState(ORIGIN).getValue(BlockStateProperties.POWERED),
                    "Offline nexus must use the inactive model");
            verifyStoredJobs(helper, "power loss");
            level.setBlock(ORIGIN.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        }).thenIdle(60).thenWaitUntil(() -> verifyJobs(helper, "power recovery")).thenExecute(() -> {
            core(helper).clearContent();
            System.out.println("TRANSFINITE_NEXUS_PASS: six real cables, independent CPU clusters, virtual jobs, long materials, world reload, portable drop and power recovery");
        }).thenSucceed();
    }

    private static OmniComputationCoreBlockEntity core(GameTestHelper helper) {
        return (OmniComputationCoreBlockEntity) helper.getLevel().getBlockEntity(ORIGIN);
    }

    private static void verifyJobs(GameTestHelper helper, String phase) {
        helper.assertTrue(core(helper).isNetworkOnline(), "Restored nexus must reconnect to its cable grid");
        helper.assertTrue(helper.getLevel().getBlockState(ORIGIN).getValue(BlockStateProperties.POWERED),
                "Online nexus must use the powered model");
        verifyStoredJobs(helper, phase);
    }

    private static void verifyStoredJobs(GameTestHelper helper, String phase) {
        var core = core(helper);
        var busy = core.allCpus().stream().filter(cpu -> cpu.isBusy()).toList();
        helper.assertTrue(busy.size() == 2, phase + ": expected two busy lanes, got " + busy.size() + " / " + core.getCpuLaneCount());
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
                new GenericStack[] {new GenericStack(AEItemKey.of(Items.DIAMOND), 1)}, new GenericStack[] {output});
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
