package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.blockentity.networking.CreativeEnergyCellBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.me.helpers.MachineSource;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationThreadingCoreBlockEntity;
import cn.dancingsnow.neoecoae.multiblock.cluster.NEComputationCluster;
import com.atir.molecularmanipulator.crafting.MatterFabricationPatternEncoding;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.verification.RegressionConfigFixture;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Compiled and run against the actual 20.4.2 CPU, without the 20.5.0 provider APIs. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class LegacyEcoCpuGameTests {
    private static final AEItemKey INPUT = AEItemKey.of(Items.DIAMOND);
    private static final AEItemKey OUTPUT = AEItemKey.of(Items.EMERALD);

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void ordinaryLegacyJobsKeepOwnershipAndRefundOnlyCanceledJob(GameTestHelper helper) {
        RegressionConfigFixture.isolate();
        var level = helper.getLevel();
        var origin = new BlockPos(941_000, 100, 960);
        for (var part : MatterFabricationStructure.parts()) {
            var pos = MatterFabricationStructure.worldPos(origin, Direction.NORTH, part);
            level.setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
            level.getChunkAt(pos);
            if (level.getBlockEntity(pos) instanceof AEBaseBlockEntity old) old.clearContent();
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                            .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH)
                    : MatterFabricationStructure.partState(part.type()), 3);
        }
        var controller = (MatterFabricationBlockEntity) level.getBlockEntity(origin);
        var bay = MatterFabricationStructure.worldPos(origin, Direction.NORTH,
                MatterFabricationStructure.patternAssemblyBays().get(0));
        level.setBlock(bay, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 3);
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(bay);
        var powerPos = origin.east(30);
        level.setChunkForced(powerPos.getX() >> 4, powerPos.getZ() >> 4, true);
        level.getChunkAt(powerPos);
        level.setBlock(powerPos, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        var power = (CreativeEnergyCellBlockEntity) level.getBlockEntity(powerPos);
        helper.runAfterDelay(5, () -> {
            controller.refreshStructure();
            assembly.setControllerPos(origin);
            GridHelper.createConnection(controller.getMainNode().getNode(), power.getMainNode().getNode());
        });
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(assembly.isOperational(),
                "Real legacy-ECO well must form and receive AE power")).thenExecute(() -> {
            var recipe = new MatterFabricationRecipe(
                    List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1)),
                    List.of(new ItemStack(Items.EMERALD)), FluidStack.EMPTY, FluidStack.EMPTY, 200, 2)
                    .withId(new ResourceLocation("molecularmanipulator", "gametest/legacy_eco_job"));
            var recipes = new ArrayList<>(level.getRecipeManager().getRecipes());
            recipes.add(recipe);
            level.getRecipeManager().replaceRecipes(recipes);
            assembly.getLogic().getPatternInv().setItemDirect(0,
                    MatterFabricationPatternEncoding.encode(recipe, List.of(new ItemStack(Items.DIAMOND))));
            assembly.getLogic().updatePatterns();
            helper.runAfterDelay(2, () -> verifyJobs(helper, controller, assembly));
        });
    }

    private static void verifyJobs(GameTestHelper helper, MatterFabricationBlockEntity controller,
            MatterFabricationPatternAssemblyBlockEntity assembly) {
        var grid = assembly.getMainNode().getGrid();
        var service = (CraftingService) grid.getCraftingService();
        var pattern = assembly.getLogic().getAvailablePatterns().get(0);
        var first = cpu(helper.getLevel(), grid, assembly);
        var second = cpu(helper.getLevel(), grid, assembly);
        var cpus = List.of(first, second);
        for (int i = 0; i < cpus.size(); i++) {
            var logic = cpus.get(i).getLogic();
            long count = 3 - i;
            var plan = new CraftingPlan(new GenericStack(OUTPUT, count), 1, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, count));
            helper.assertTrue(logic.trySubmitJob(grid, plan, new MachineSource(assembly), null).successful(),
                    "The actual old ECO CPU must accept an ordinary plan");
            logic.getInventory().insert(INPUT, count, Actionable.MODULATE);
            for (int tick = 0; tick < 8; tick++) logic.tickCraftingLogic(grid.getEnergyService(), service);
            helper.assertTrue(logic.getInventory().list.isEmpty() && logic.getWaitingFor(OUTPUT) == count,
                    "Ordinary old-ECO dispatch must consume each input once and account for every output");
        }
        var firstId = first.getLogic().getLastLink().getCraftingID();
        var secondId = second.getLogic().getLastLink().getCraftingID();
        var buffer = assembly.getBuffer();
        var before = save(buffer);
        var queued = before.getCompound("fabrication_pattern_buffer").getList("queued", Tag.TAG_COMPOUND);
        helper.assertTrue(!firstId.equals(secondId) && queued.size() == 2
                        && queued.getCompound(0).hasUUID("crafting_id")
                        && queued.getCompound(0).getUUID("crafting_id").equals(firstId)
                        && queued.getCompound(1).hasUUID("crafting_id")
                        && queued.getCompound(1).getUUID("crafting_id").equals(secondId),
                "Ordinary dispatch must persist the real old-CPU UUID without merging separate jobs");
        buffer.load(before);
        helper.assertTrue(save(buffer).equals(before), "Both old-ECO job identities must survive NBT reload");
        buffer.process(controller);
        helper.assertTrue(buffer.isProcessing(), "Cancellation must also cover already-active well work");
        first.getLogic().cancel();
        buffer.serverTick();
        var canceled = save(buffer);
        var state = canceled.getCompound("fabrication_pattern_buffer");
        var remaining = state.getList("queued", Tag.TAG_COMPOUND);
        helper.assertTrue(MolecularBatchCancellationData.isCanceled(helper.getLevel(), firstId)
                        && !MolecularBatchCancellationData.isCanceled(helper.getLevel(), secondId)
                        && !buffer.isProcessing() && remaining.size() == 1
                        && remaining.getCompound(0).getUUID("crafting_id").equals(secondId)
                        && remaining.getCompound(0).getLong("crafts") == 2
                        && refundAmount(state) == 3 && buffer.contents(true).isEmpty()
                        && MatterFabricationPatternLogic.batchInputs(buffer.contents(false)).equals(Map.of(INPUT, 5L)),
                "Canceling the first old-ECO job must refund only its three inputs, preserving the other two");
        buffer.load(canceled);
        buffer.serverTick();
        helper.assertTrue(save(buffer).equals(canceled), "Reload must not replay old-ECO cancellation refunds");
        second.getLogic().cancel();
        buffer.serverTick();
        helper.assertTrue(buffer.queuedPatterns() == 0
                        && refundAmount(save(buffer).getCompound("fabrication_pattern_buffer")) == 5
                        && buffer.contents(true).isEmpty(),
                "Canceling the remaining job must preserve exactly five total inputs and no outputs");
        com.mojang.logging.LogUtils.getLogger().info("ECO_LEGACY_JOB_OWNERSHIP_PASS inputs=5 refunds=5 outputs=0");
        assembly.clearContent();
        helper.succeed();
    }

    private static ECOCraftingCPU cpu(Level level, IGrid grid, MatterFabricationPatternAssemblyBlockEntity assembly) {
        // Only the physical host is controlled; submission, scheduling and cancellation are the real old implementation.
        var cluster = new NEComputationCluster(BlockPos.ZERO, BlockPos.ZERO);
        var block = BuiltInRegistries.BLOCK.get(new ResourceLocation("neoecoae", "computation_threading_core_l4"));
        var owner = (ECOComputationThreadingCoreBlockEntity) ((EntityBlock) block)
                .newBlockEntity(assembly.getBlockPos().east(30), block.defaultBlockState());
        owner.setLevel(level);
        return new ECOCraftingCPU(cluster, null, owner) {
            @Override public boolean isActive() { return true; }
            @Override public long getAvailableStorage() { return Long.MAX_VALUE; }
            @Override public int getCoProcessors() { return 0; }
            @Override public Level getLevel() { return level; }
            @Override public IGrid getGrid() { return grid; }
            @Override public IActionSource getActionSource() { return new MachineSource(assembly); }
            @Override public void markDirty() { }
        };
    }

    private static CompoundTag save(MatterPatternBuffer buffer) {
        var parent = new CompoundTag();
        buffer.save(parent);
        return parent;
    }

    private static long refundAmount(CompoundTag state) {
        long amount = 0;
        for (var tag : state.getList("refunds", Tag.TAG_COMPOUND)) {
            var stack = GenericStack.readTag((CompoundTag) tag);
            if (stack == null || !stack.what().equals(INPUT)) throw new AssertionError("Unexpected refund material");
            amount = Math.addExact(amount, stack.amount());
        }
        return amount;
    }
}
