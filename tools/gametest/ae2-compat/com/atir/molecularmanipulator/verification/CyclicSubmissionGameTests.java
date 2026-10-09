package com.atir.molecularmanipulator.verification;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.appliedenhancements.Config;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.appliedenhancements.api.AelisCycleSeedPolicy;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class CyclicSubmissionGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "cyclic-seeded", timeoutTicks = 400)
    public static void ignoredOutputSeedCanStartOnOmniCpu(GameTestHelper helper) {
        verifyScenario(helper, true, 1, 224);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "cyclic-missing-seed", timeoutTicks = 200)
    public static void missingSeedStillRejectsSubmission(GameTestHelper helper) {
        verifyScenario(helper, true, 0, 224);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "cyclic-missing-raw", timeoutTicks = 200)
    public static void missingRawMaterialRollsBackSeedForSimulation(GameTestHelper helper) {
        verifyScenario(helper, true, 1, 217);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "cyclic-ordinary", timeoutTicks = 400)
    public static void ordinaryOrderStillIgnoresExistingOutput(GameTestHelper helper) {
        verifyScenario(helper, false, 33, 224);
    }

    private static void verifyScenario(GameTestHelper helper, boolean cyclic, long seeds, long diamonds) {
        var seed = AEItemKey.of(Items.EMERALD);
        var diamond = AEItemKey.of(Items.DIAMOND);
        var netherrack = AEItemKey.of(Items.NETHERRACK);
        var patternInputs = new ArrayList<GenericStack>();
        if (cyclic) patternInputs.add(new GenericStack(seed, 1));
        patternInputs.add(new GenericStack(diamond, 7));
        patternInputs.add(new GenericStack(netherrack, 1));
        var encoded = PatternDetailsHelper.encodeProcessingPattern(patternInputs, List.of(new GenericStack(seed, 2)));
        var pattern = PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        var stock = new KeyCounter();
        stock.add(seed, seeds);
        stock.add(diamond, diamonds);
        stock.add(netherrack, 32);
        var inventory = new MEStorage() {
            public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                long taken = Math.min(stock.get(key), amount);
                if (mode == Actionable.MODULATE) stock.add(key, -taken);
                return taken;
            }
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) stock.add(key, amount);
                return amount;
            }
            public void getAvailableStacks(KeyCounter stacks) { stacks.addAll(stock); }
            public Component getDescription() { return Component.literal("Cyclic regression stock"); }
        };
        var coreRef = new OmniComputationCoreBlockEntity[1];
        var pushes = new AtomicInteger();
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
            public boolean pushPattern(IPatternDetails p, KeyCounter[] inputs) {
                var consumed = new KeyCounter();
                for (var input : inputs) consumed.addAll(input);
                helper.assertTrue(p == pattern && consumed.get(seed) == (cyclic ? 1 : 0)
                                && consumed.get(diamond) == 7 && consumed.get(netherrack) == 1,
                        "Each actual provider push must consume exactly one round");
                pushes.incrementAndGet();
                helper.runAfterDelay(1, () -> {
                    long accepted = ((CraftingService) coreRef[0].getMainNode().getGrid().getCraftingService())
                            .insertIntoCpus(seed, 2, Actionable.MODULATE);
                    stock.add(seed, 2 - accepted);
                });
                return true;
            }
            public boolean isBusy() { return false; }
        };
        helper.setBlock(new BlockPos(1, 1, 1), ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        boolean automatic = Config.ENABLE_AUTOMATIC_AELIS_PLANNER.get();
        var seedPolicy = Config.CYCLE_SEED_POLICY.get();
        boolean testAutomatic = Boolean.getBoolean("molecularmanipulator.cyclicAutomatic");
        Config.ENABLE_AUTOMATIC_AELIS_PLANNER.set(testAutomatic);
        Config.CYCLE_SEED_POLICY.set(AelisCycleSeedPolicy.PRESERVE_MINIMUM);
        helper.runAfterDelay(20, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            coreRef[0] = core;
            helper.assertTrue(core.isMaterialCalculationEnabled(), "Nexus must be formed and powered");
            var node = GridHelper.createManagedNode(provider, (owner, changed) -> {})
                    .setInWorldNode(false).setIdlePowerUsage(0)
                    .addService(ICraftingProvider.class, provider)
                    .addService(IStorageProvider.class, mounts -> mounts.mount(inventory));
            node.create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 1)));
            GridHelper.createConnection(node.getNode(), core.getActionableNode());
            ICraftingProvider.requestUpdate(node);
            IStorageProvider.requestUpdate(node);
            var requester = new ICraftingSimulationRequester() {
                public IGridNode getGridNode() { return node.getNode(); }
                public IActionSource getActionSource() { return IActionSource.empty(); }
            };
            var pending = node.getGrid().getCraftingService().beginCraftingCalculation(
                    helper.getLevel(), requester, seed, 32, CalculationStrategy.REPORT_MISSING_ITEMS);
            boolean shortage = cyclic && (seeds == 0 || diamonds < 224);
            var sequence = helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(pending.isDone(), "Calculation must finish"))
                    .thenExecute(() -> {
                        appeng.api.networking.crafting.ICraftingPlan plan;
                        try { plan = pending.get(); }
                        catch (Exception failure) { throw new IllegalStateException("Calculation failed", failure); }
                        var summary = CraftingPlanSummary.fromJob(node.getGrid(), IActionSource.empty(), plan);
                        var cpu = core.getCluster();
                        if (shortage) {
                            helper.assertTrue(plan.simulation() && summary.isSimulation(), "Real shortages must disable starting");
                            helper.assertTrue(plan.missingItems().get(seed) == (seeds == 0 ? 1 : 0)
                                            && plan.missingItems().get(diamond) == 224 - diamonds,
                                    "Simulation retry must report real shortages and preserve the existing seed");
                            var submitted = node.getGrid().getCraftingService().submitJob(
                                    plan, null, cpu, false, IActionSource.empty());
                            helper.assertTrue(submitted == CraftingSubmitResult.INCOMPLETE_PLAN,
                                    "CPU must refuse genuinely incomplete plans");
                            helper.assertTrue(stock.get(seed) == seeds && stock.get(diamond) == diamonds
                                            && stock.get(netherrack) == 32 && pushes.get() == 0,
                                    "Incomplete plans must not consume stored materials");
                            return;
                        }
                        long rounds = cyclic ? 32 : 16;
                        helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty() && !summary.isSimulation(),
                                "Sufficient stock must produce an executable terminal plan");
                        helper.assertTrue(plan.usedItems().get(seed) == (cyclic ? 1 : 0)
                                        && plan.usedItems().get(diamond) == rounds * 7
                                        && plan.usedItems().get(netherrack) == rounds,
                                "Plan must reserve exactly the seed and raw inputs");
                        helper.assertTrue(AelisCycleExecutionApi.supports(cpu), "Omni CPU must support cyclic execution");
                        var submitted = node.getGrid().getCraftingService().submitJob(
                                plan, null, cpu, false, IActionSource.empty());
                        helper.assertTrue(submitted.successful(), "Omni CPU must accept the calculated plan: " + submitted);
                    });
            if (!shortage) {
                sequence.thenWaitUntil(() -> helper.assertTrue(!core.getCluster().isBusy(), "CPU must complete the order"))
                        .thenExecute(() -> {
                            int rounds = cyclic ? 32 : 16;
                            helper.assertTrue(stock.get(seed) == seeds + 32 && pushes.get() == rounds,
                                    "Completed output and retained seed must be conserved: stock="
                                            + stock.get(seed) + ", pushes=" + pushes.get());
                            helper.assertTrue(stock.get(diamond) == diamonds - rounds * 7
                                            && stock.get(netherrack) == 32 - rounds,
                                    "CPU must consume exactly the planned raw materials");
                        });
            }
            sequence.thenExecute(() -> {
                node.destroy();
                Config.ENABLE_AUTOMATIC_AELIS_PLANNER.set(automatic);
                Config.CYCLE_SEED_POLICY.set(seedPolicy);
                System.out.println("CYCLIC_SCENARIO_PASS automatic=" + testAutomatic + " cyclic=" + cyclic
                        + " initialSeeds=" + seeds + " initialDiamonds=" + diamonds + " shortage=" + shortage);
            }).thenSucceed();
        });
    }
}
