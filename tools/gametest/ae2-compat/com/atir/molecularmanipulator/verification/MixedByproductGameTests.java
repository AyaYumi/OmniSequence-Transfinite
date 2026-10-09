package com.atir.molecularmanipulator.verification;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.me.service.CraftingService;
import appeng.menu.me.crafting.CraftingPlanSummary;
import com.appliedenhancements.Config;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class MixedByproductGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "byproduct-mixed", timeoutTicks = 300)
    public static void mixedNativeByproductPlanCanBuild(GameTestHelper helper) {
        run(helper, AEItemKey.of(Items.REDSTONE), 3, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "byproduct-other-item", timeoutTicks = 300)
    public static void differentItemByproductCanComplete(GameTestHelper helper) {
        run(helper, AEItemKey.of(Items.LAPIS_LAZULI), 5, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "byproduct-fluid", timeoutTicks = 300)
    public static void fluidByproductCanComplete(GameTestHelper helper) {
        run(helper, AEFluidKey.of(Fluids.WATER), 1000, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "byproduct-shortage", timeoutTicks = 300)
    public static void missingRawInputStillRejectsSubmission(GameTestHelper helper) {
        run(helper, AEItemKey.of(Items.REDSTONE), 3, true);
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "byproduct-unknown-rewrite", timeoutTicks = 300)
    public static void unknownRewrittenPatternRestoresCompleteJob(GameTestHelper helper) {
        run(helper, AEItemKey.of(Items.REDSTONE), 3, false, true);
    }

    private static void run(GameTestHelper helper, AEKey byproduct, long byproductAmount, boolean shortage) {
        run(helper, byproduct, byproductAmount, shortage, false);
    }

    private static void run(GameTestHelper helper, AEKey byproduct, long byproductAmount, boolean shortage,
            boolean unknownRewrite) {
        var iron = AEItemKey.of(Items.IRON_INGOT);
        var coal = AEItemKey.of(Items.COAL);
        var intermediate = AEItemKey.of(Items.EMERALD);
        var other = AEItemKey.of(Items.GOLD_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var multi = decode(helper, List.of(new GenericStack(iron, 1)),
                List.of(new GenericStack(intermediate, 2), new GenericStack(byproduct, byproductAmount)));
        var single = decode(helper, List.of(new GenericStack(coal, 1)), List.of(new GenericStack(other, 1)));
        var root = decode(helper, List.of(new GenericStack(intermediate, 1), new GenericStack(other, 1)),
                List.of(new GenericStack(output, 1)));
        boolean nativeAddons = Boolean.getBoolean("molecularmanipulator.byproductNative");
        if (nativeAddons) {
            try {
                multi.getClass().getMethod("eap$setAllowScaling", boolean.class).invoke(multi, true);
                multi.getClass().getMethod("eap$setMultiplierLimit", int.class).invoke(multi, 7);
            } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
        }
        var stock = new KeyCounter();
        stock.add(iron, 16);
        stock.add(coal, shortage ? 31 : 32);
        var inventory = new MEStorage() {
            public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
                long take = Math.min(stock.get(key), amount);
                if (mode == Actionable.MODULATE) stock.add(key, -take);
                return take;
            }
            public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
                if (mode == Actionable.MODULATE) stock.add(key, amount);
                return amount;
            }
            public void getAvailableStacks(KeyCounter stacks) { stacks.addAll(stock); }
            public Component getDescription() { return Component.literal("Mixed byproduct regression stock"); }
        };
        helper.setBlock(new BlockPos(1, 1, 1), ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(new BlockPos(2, 1, 1), AEBlocks.CREATIVE_ENERGY_CELL.block());
        boolean automatic = Config.ENABLE_AUTOMATIC_AELIS_PLANNER.get();
        Config.ENABLE_AUTOMATIC_AELIS_PLANNER.set(Boolean.getBoolean("molecularmanipulator.byproductAutomatic"));
        helper.runAfterDelay(20, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(new BlockPos(1, 1, 1));
            var nativeProvider = provider(helper, core, List.of(single, root), stock);
            if (nativeAddons) {
                try {
                    var delegate = nativeProvider;
                    var marker = Class.forName("com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider");
                    nativeProvider = (ICraftingProvider) Proxy.newProxyInstance(delegate.getClass().getClassLoader(),
                            new Class<?>[]{ICraftingProvider.class, marker}, (p, m, a) -> m.invoke(delegate, a));
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            }
            var batchNode = node(helper, core, nativeProvider, new BlockPos(3, 1, 1));
            var multiNode = node(helper, core, provider(helper, core, List.of(multi), stock), new BlockPos(4, 1, 1));
            var storageNode = GridHelper.createManagedNode(inventory, (owner, changed) -> {})
                    .setInWorldNode(false).setIdlePowerUsage(0)
                    .addService(IStorageProvider.class, mounts -> mounts.mount(inventory));
            storageNode.create(helper.getLevel(), helper.absolutePos(new BlockPos(5, 1, 1)));
            GridHelper.createConnection(storageNode.getNode(), core.getActionableNode());
            IStorageProvider.requestUpdate(storageNode);
            var requester = new ICraftingSimulationRequester() {
                public IGridNode getGridNode() { return core.getActionableNode(); }
                public IActionSource getActionSource() { return IActionSource.empty(); }
            };
            var service = core.getMainNode().getGrid().getCraftingService();
            var pending = service.beginCraftingCalculation(helper.getLevel(), requester, output, 32,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            var submitted = new AtomicBoolean();
            helper.succeedWhen(() -> {
                helper.assertTrue(pending.isDone(), "Mixed plan calculation must finish");
                if (!submitted.get()) {
                    try {
                        var plan = pending.get();
                        if (unknownRewrite) {
                            var tasks = new LinkedHashMap<>(plan.patternTimes());
                            var task = tasks.entrySet().iterator().next();
                            var original = task.getKey();
                            var wrapper = (IPatternDetails) Proxy.newProxyInstance(original.getClass().getClassLoader(),
                                    new Class<?>[]{IPatternDetails.class}, (p, m, a) -> switch (m.getName()) {
                                        case "hashCode" -> System.identityHashCode(p);
                                        case "equals" -> p == a[0];
                                        default -> m.invoke(original, a);
                                    });
                            tasks.remove(original);
                            tasks.put(wrapper, task.getValue());
                            plan = AelisCycleExecutionApi.copyMetadata(plan, new CraftingPlan(plan.finalOutput(), plan.bytes(),
                                    plan.simulation(), plan.multiplePaths(), plan.usedItems(), plan.emittedItems(),
                                    plan.missingItems(), tasks));
                            helper.assertTrue(!plan.patternTimes().containsKey(wrapper),
                                    "Unknown rewrite must restore all authoritative tasks before submission");
                        }
                        if (shortage) {
                            helper.assertTrue(plan.simulation() && !plan.missingItems().isEmpty(),
                                    "Real shortages must still be reported");
                            helper.assertTrue(CraftingPlanSummary.fromJob(core.getMainNode().getGrid(), IActionSource.empty(), plan)
                                    .isSimulation(), "Terminal must mark a genuine shortage");
                            helper.assertTrue(!service.submitJob(plan, null, core.getCluster(), false, IActionSource.empty()).successful(),
                                    "Missing raw materials must reject submission");
                            helper.assertTrue(stock.get(iron) == 16 && stock.get(coal) == 31 && !core.getCluster().isBusy(),
                                    "Rejected simulation must leave stock and CPU untouched");
                            batchNode.destroy();
                            multiNode.destroy();
                            storageNode.destroy();
                            Config.ENABLE_AUTOMATIC_AELIS_PLANNER.set(automatic);
                            System.out.println("MIXED_BYPRODUCT_SHORTAGE_PASS nativeAddons=" + nativeAddons);
                            return;
                        }
                        helper.assertTrue(!plan.simulation() && plan.missingItems().isEmpty(), "Mixed plan must be executable");
                        if (nativeAddons && !unknownRewrite) {
                            helper.assertTrue(plan.patternTimes().keySet().stream().anyMatch(p ->
                                            p.getClass().getName().startsWith("com.extendedae_plus.api.crafting.ScaledProcessingPattern")),
                                    "Recognized native byproduct batches must keep their wrapper identity");
                        }
                        helper.assertTrue(!CraftingPlanSummary.fromJob(core.getMainNode().getGrid(), IActionSource.empty(), plan)
                                .isSimulation(), "Terminal must show an executable summary");
                        var result = service.submitJob(plan, null, core.getCluster(), false, IActionSource.empty());
                        helper.assertTrue(result.successful(), "Mixed plan must submit: " + result);
                        submitted.set(true);
                    } catch (Exception failure) { throw new IllegalStateException("Mixed byproduct planning failed", failure); }
                }
                helper.assertTrue(!core.getCluster().isBusy(), "Mixed job must finish");
                helper.assertTrue(stock.get(output) == 32 && stock.get(byproduct) == 16 * byproductAmount
                                && stock.get(iron) == 0 && stock.get(coal) == 0,
                        "Mixed native work must conserve all main outputs, byproducts and raw inputs");
                batchNode.destroy();
                multiNode.destroy();
                storageNode.destroy();
                Config.ENABLE_AUTOMATIC_AELIS_PLANNER.set(automatic);
                System.out.println("MIXED_BYPRODUCT_PASS nativeAddons=" + nativeAddons + " output=32 byproduct="
                        + stock.get(byproduct));
            });
        });
    }

    private static IManagedGridNode node(GameTestHelper helper, OmniComputationCoreBlockEntity core,
            ICraftingProvider provider, BlockPos pos) {
        var node = GridHelper.createManagedNode(provider, (owner, changed) -> {})
                .setInWorldNode(false).setIdlePowerUsage(0).addService(ICraftingProvider.class, provider);
        node.create(helper.getLevel(), helper.absolutePos(pos));
        GridHelper.createConnection(node.getNode(), core.getActionableNode());
        ICraftingProvider.requestUpdate(node);
        return node;
    }

    private static ICraftingProvider provider(GameTestHelper helper, OmniComputationCoreBlockEntity core,
            List<IPatternDetails> patterns, KeyCounter stock) {
        return new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return patterns; }
            public boolean isBusy() { return false; }
            public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
                var supplied = new KeyCounter();
                for (var holder : inputs) supplied.addAll(holder);
                for (var input : pattern.getInputs()) {
                    var required = input.getPossibleInputs()[0];
                    helper.assertTrue(supplied.get(required.what()) == required.amount() * input.getMultiplier(),
                            "Native scaled push must preserve its complete input vector");
                }
                helper.runAfterDelay(1, () -> {
                    for (var produced : pattern.getOutputs()) {
                        long accepted = ((CraftingService) core.getMainNode().getGrid().getCraftingService())
                                .insertIntoCpus(produced.what(), produced.amount(), Actionable.MODULATE);
                        stock.add(produced.what(), produced.amount() - accepted);
                    }
                });
                return true;
            }
        };
    }

    private static IPatternDetails decode(GameTestHelper helper, List<GenericStack> inputs, List<GenericStack> outputs) {
        return PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(inputs, outputs), helper.getLevel());
    }
}
