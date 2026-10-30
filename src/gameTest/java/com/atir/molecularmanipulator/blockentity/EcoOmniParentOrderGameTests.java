package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.AEBaseBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import com.atir.molecularmanipulator.integration.eco.EcoOmniParentOrder;
import com.atir.molecularmanipulator.integration.eco.EcoOmniParentOrderCpu;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanMenu;
import com.atir.molecularmanipulator.registry.ModContent;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real transformed CPU carrier, busy UI, requester identity, save/load and cancel contracts. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class EcoOmniParentOrderGameTests {
    private static final BigInteger HUGE = BigInteger.TEN.pow(60).add(BigInteger.valueOf(23));
    private static final ECOPlannerOptions OPTIONS = new ECOPlannerOptions(true, true, Set.of());

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void directCarrierCreatesRealPersistentOmniParentWithoutExtractingStock(GameTestHelper helper) {
        powered(helper, 0, cpu -> {
            var source = cpu.getSrc();
            var request = new ECOBigOrderRequest(AEItemKey.of(Items.DIAMOND), HUGE, true, OPTIONS);
            var submitted = request.submit(carrier -> cpu.getGrid().getCraftingService().submitJob(carrier, null, cpu, false, source));
            helper.assertTrue(submitted.successful(), "Direct API carrier must be accepted by selected real Omni CPU: " + submitted);
            var logic = cpu.craftingLogic;
            var bridge = (EcoOmniParentOrderCpu) logic;
            var link = logic.getLastLink();
            helper.assertTrue(logic.hasJob() && cpu.isBusy() && cpu.getJobStatus() != null
                            && logic.getFinalJobOutput() != null && link != null && !link.isDone(),
                    "Waiting parent must own an actual native job/link and render a nonempty busy CPU status");
            helper.assertTrue(bridge.ecoOmni$parentProgress().requested().equals(HUGE)
                            && bridge.ecoOmni$parentProgress().completed().signum() == 0
                            && logic.getInventory().list.isEmpty() && logic.getWaitingFor(request.goal()) == 0,
                    "Accepting a missing parent must not extract stock, create waiting output credits or count products");
            var before = new CompoundTag();
            logic.writeToNBT(before);
            helper.assertTrue(before.contains("job") && before.contains(EcoOmniParentOrder.NBT_KEY),
                    "Native job and exact parent checkpoint must be persisted together");
            logic.readFromNBT(before);
            helper.assertTrue(cpu.isBusy() && cpu.getJobStatus() != null && bridge.ecoOmni$parentProblem().isEmpty()
                            && logic.getLastLink().getCraftingID().equals(link.getCraftingID())
                            && bridge.ecoOmni$parentProgress().requested().equals(HUGE),
                    "Reload must retain the actual job, UUID, exact goal and original source identity");
            var replacement = request.submit(carrier -> cpu.submitJob(cpu.getGrid(), carrier, source, null));
            helper.assertTrue(!replacement.successful(), "A waiting parent must prevent replacement by another direct API request");
            var restoredLink = logic.getLastLink();
            logic.cancel();
            helper.assertTrue(!cpu.isBusy() && restoredLink.isCanceled() && bridge.ecoOmni$parentProgress() == null
                            && logic.getInventory().list.isEmpty(),
                    "Cancel must clear actual parent/job and preserve zero material ownership");
            helper.succeed();
        });
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void forcedMissingMenuUsesTheSameActualOmniParentAdmission(GameTestHelper helper) {
        powered(helper, 1, cpu -> {
            ICraftingPlan preview = new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 123), 0,
                    true, false, new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
            var result = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, (CraftingPlan) preview,
                    new ECOPlanTrace(), List.of(), List.of(), List.of(), 0);
            var outcome = EcoOmniPlanMenu.submit(cpu.getGrid(), cpu, preview, result, result, OPTIONS, true, cpu.getSrc());
            helper.assertTrue(outcome.submitted().successful(), "Forced missing menu must acknowledge its persistent Omni parent: " + outcome.reason());
            var progress = ((EcoOmniParentOrderCpu) cpu.craftingLogic).ecoOmni$parentProgress();
            helper.assertTrue(progress != null && progress.requested().equals(BigInteger.valueOf(123))
                            && progress.completed().signum() == 0 && cpu.craftingLogic.hasJob()
                            && cpu.craftingLogic.getInventory().list.isEmpty(),
                    "Menu preview must become a real waiting goal without executing its simulated material ledger");
            cpu.cancelJob();
            helper.succeed();
        });
    }

    private static void powered(GameTestHelper helper, int lane, Consumer<CraftingCPUCluster> action) {
        com.atir.molecularmanipulator.verification.RegressionConfigFixture.isolate();
        var level = helper.getLevel();
        var pos = new BlockPos(985_000 + lane * 96, 100, 960);
        for (var at : List.of(pos, pos.east())) {
            level.setChunkForced(at.getX() >> 4, at.getZ() >> 4, true);
            level.getChunkAt(at);
            if (level.getBlockEntity(at) instanceof AEBaseBlockEntity old) old.clearContent();
            level.setBlock(at, Blocks.AIR.defaultBlockState(), 3);
        }
        level.setBlock(pos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get().defaultBlockState(), 3);
        level.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 3);
        helper.startSequence().thenWaitUntil(() -> {
            var core = (OmniComputationCoreBlockEntity) level.getBlockEntity(pos);
            helper.assertTrue(core != null && core.isNetworkOnline() && core.getCluster() != null
                            && core.getCluster().isActive() && core.getCluster().getGrid().getCraftingService().getCpus().contains(core.getCluster()),
                    "Real powered Omni nexus must form and register its selected CPU");
        }).thenExecute(() -> action.accept(((OmniComputationCoreBlockEntity) level.getBlockEntity(pos)).getCluster()));
    }
}
