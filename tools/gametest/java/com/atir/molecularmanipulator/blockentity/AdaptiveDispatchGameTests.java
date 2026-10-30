package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.registry.ModContent;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Real CPU calls into providers implementing only the ordinary ICraftingProvider contract. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class AdaptiveDispatchGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 160)
    public static void unknownMultiInputProviderFillsImmediatelyAndReprobesEveryTwentyTicks(GameTestHelper helper) {
        run(helper, 1, false, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 160)
    public static void unknownProviderFillingOnFirstCallSkipsProbing(GameTestHelper helper) {
        run(helper, 1, true, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 160)
    public static void nativeDoubledPatternKeepsIdentityAndOneParallelFirstCall(GameTestHelper helper) {
        run(helper, 16, true, false);
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 160)
    public static void capacityAndRejectionRemainIndependentAcrossProviders(GameTestHelper helper) {
        run(helper, 1, false, true);
    }

    private static void run(GameTestHelper helper, int multiplier, boolean fillsFirst, boolean multipleProviders) {
        var corePos = new BlockPos(1, 1, 1);
        helper.setBlock(corePos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(new BlockPos(1, 1, 2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.runAfterDelay(20, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
            var input = AEItemKey.of(Items.IRON_INGOT);
            var second = AEItemKey.of(Items.COAL);
            var output = AEItemKey.of(Items.EMERALD);
            var unit = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    List.of(new GenericStack(input, 1), new GenericStack(second, 2)),
                    List.of(new GenericStack(output, 1))), helper.getLevel());
            IPatternDetails pattern = multiplier == 1 ? unit : new ScaledProcessingPattern(unit, multiplier);
            var first = new OrdinaryProvider(helper, unit, pattern, multiplier, fillsFirst, multipleProviders ? 3 : 13);
            var providers = multipleProviders ? List.of(first,
                    new OrdinaryProvider(helper, unit, pattern, multiplier, false, 7)) : List.of(first);
            var nodes = new java.util.ArrayList<appeng.api.networking.IManagedGridNode>();
            for (var provider : providers) {
                var node = GridHelper.createManagedNode(provider, (owner, changed) -> {})
                        .setInWorldNode(false).setIdlePowerUsage(0).addService(ICraftingProvider.class, provider);
                node.create(helper.getLevel(), helper.absolutePos(new BlockPos(3 + nodes.size(), 1, 1)));
                GridHelper.createConnection(node.getNode(), core.getActionableNode());
                ICraftingProvider.requestUpdate(node);
                nodes.add(node);
            }
            helper.runAfterDelay(10, () -> {
                var grid = core.getMainNode().getGrid();
                var service = (CraftingService) grid.getCraftingService();
                var cpu = core.getCluster().craftingLogic;
                long requested = 100_000;
                var plan = new CraftingPlan(new GenericStack(output, requested * multiplier), 100, false, false,
                        new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, requested));
                helper.assertTrue(service.submitJob(plan, null, core.getCluster(), false, IActionSource.empty()).successful(),
                        "Ordinary custom-provider job must submit");
                cpu.getInventory().insert(input, requested * multiplier, Actionable.MODULATE);
                cpu.getInventory().insert(second, requested * multiplier * 2, Actionable.MODULATE);
                new Runnable() {
                    int ticks;
                    @Override public void run() {
                        // Drive the actual CPU every tick, independently of AE's idle scheduling interval.
                        cpu.tickCraftingLogic(grid.getEnergyService(), service);
                        long accepted = providers.stream().mapToLong(provider -> provider.accepted).sum();
                        helper.assertTrue(cpu.getWaitingFor(output) == accepted * multiplier
                                        && cpu.getInventory().list.get(input) == (requested - accepted) * multiplier
                                        && cpu.getInventory().list.get(second) == (requested - accepted) * multiplier * 2,
                                "Every accepted/rejected ordinary call must conserve both CPU inputs and exact waiting quantities");
                        if (++ticks < 45) {
                            helper.runAfterDelay(1, () -> run());
                            return;
                        }
                        helper.assertTrue(first.firstTick != Long.MIN_VALUE, "Custom provider must receive work");
                        for (var provider : providers) {
                            if (fillsFirst) {
                                helper.assertTrue(provider.successes.values().stream().allMatch(count -> count == 1)
                                                && provider.failures.isEmpty(),
                                        "A receiver filled by the first original push must skip additional probing");
                            } else if (multipleProviders) {
                                helper.assertTrue(provider.successes.getOrDefault(provider.firstTick, 0) == provider.initialCapacity,
                                        "Both independent providers must reach capacity in their first dispatch tick");
                            } else {
                                for (long offset = 0; offset <= 40; offset++) {
                                    int expected = offset < 20 ? 13 : offset < 40 ? 21 : 37;
                                    helper.assertTrue(provider.successes.getOrDefault(provider.firstTick + offset, 0) == expected,
                                            "Ordinary rate must fill immediately, hold capacity, then recover after exactly 20 ticks: offset="
                                                    + offset + ", successes=" + provider.successes + ", failures=" + provider.failures);
                                    int expectedFailures = offset == 0 || offset == 20 || offset == 40 ? 1 : 0;
                                    helper.assertTrue(provider.failures.getOrDefault(provider.firstTick + offset, 0) == expectedFailures,
                                            "Only capacity probes may reject; the established rate must not oscillate");
                                }
                            }
                        }
                        core.clearContent();
                        nodes.forEach(appeng.api.networking.IManagedGridNode::destroy);
                        helper.succeed();
                    }
                }.run();
            });
        });
    }

    private static final class OrdinaryProvider implements ICraftingProvider {
        private final GameTestHelper helper;
        private final IPatternDetails advertised;
        private final IPatternDetails pattern;
        private final int multiplier;
        private final boolean fillsFirst;
        private final int initialCapacity;
        private final Map<Long, Integer> successes = new LinkedHashMap<>();
        private final Map<Long, Integer> failures = new LinkedHashMap<>();
        private long firstTick = Long.MIN_VALUE;
        private long accepted;

        private OrdinaryProvider(GameTestHelper helper, IPatternDetails advertised, IPatternDetails pattern, int multiplier,
                boolean fillsFirst, int initialCapacity) {
            this.helper = helper;
            this.advertised = advertised;
            this.pattern = pattern;
            this.multiplier = multiplier;
            this.fillsFirst = fillsFirst;
            this.initialCapacity = initialCapacity;
        }

        @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(advertised); }

        @Override public boolean isBusy() {
            return fillsFirst && successes.getOrDefault(TickHandler.instance().getCurrentTick(), 0) > 0;
        }

        @Override public boolean pushPattern(IPatternDetails actual, KeyCounter[] inputs) {
            helper.assertTrue(actual == pattern, "A custom/native provider must receive the original dispatched task identity");
            var supplied = new KeyCounter();
            for (var holder : inputs) supplied.addAll(holder);
            helper.assertTrue(supplied.size() == 2 && supplied.get(AEItemKey.of(Items.IRON_INGOT)) == multiplier
                            && supplied.get(AEItemKey.of(Items.COAL)) == 2L * multiplier,
                    "Every ordinary push must contain exactly one complete original task, without a second multiplier");
            long tick = TickHandler.instance().getCurrentTick();
            if (firstTick == Long.MIN_VALUE) firstTick = tick;
            long offset = tick - firstTick;
            int capacity = fillsFirst ? 1 : initialCapacity != 13 ? initialCapacity : offset == 0 ? 13 : offset <= 20 ? 21 : 37;
            int count = successes.getOrDefault(tick, 0);
            if (count >= capacity) {
                failures.merge(tick, 1, Integer::sum);
                return false;
            }
            for (var holder : inputs) holder.reset();
            successes.put(tick, count + 1);
            accepted++;
            return true;
        }
    }
}
