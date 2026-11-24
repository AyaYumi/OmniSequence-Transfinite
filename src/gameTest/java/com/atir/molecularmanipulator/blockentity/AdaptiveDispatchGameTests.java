package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.crafting.PushDirection;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingPlan;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.verification.RegressionConfigFixture;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real CPU calls into ordinary crafting providers. */
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

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 240)
    public static void vanillaProviderPushesOneRecipeAtATimeIntoCapacityEligibleBarrel(GameTestHelper helper) {
        var corePos = new BlockPos(1, 1, 1);
        var providerPos = corePos.east();
        var targetPos = providerPos.east();
        helper.setBlock(corePos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(corePos.south(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState()
                .setValue(PatternProviderBlock.PUSH_DIRECTION, PushDirection.EAST));
        var barrelState = Blocks.BARREL.defaultBlockState();
        helper.setBlock(targetPos, barrelState);
        var target = new RecordingBarrel(helper.absolutePos(targetPos), barrelState);
        helper.getLevel().setBlockEntity(target);

        int capacity = 13;
        for (int slot = 1; slot < target.getContainerSize(); slot++) {
            target.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }
        target.setItem(0, new ItemStack(Items.IRON_INGOT, 64 - capacity));
        target.insertions.clear();

        var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
        var host = (PatternProviderBlockEntity) helper.getBlockEntity(providerPos);
        var provider = host.getLogic();
        var input = AEItemKey.of(Items.IRON_INGOT);
        var output = AEItemKey.of(Items.EMERALD);
        provider.getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[]{new GenericStack(input, 1)}, new GenericStack[]{new GenericStack(output, 2)}));

        // Measure after the other fixtures finish sharing this server's dispatch deadline.
        helper.runAfterDelay(140, () -> {
            var grid = core.getMainNode().getGrid();
            helper.assertTrue(core.isNetworkOnline() && core.getCluster() != null && grid != null
                            && host.getMainNode().getGrid() == grid && host.getMainNode().isActive(),
                    "The Nexus CPU and vanilla provider must share a powered grid");
            helper.assertTrue(provider.getClass() == PatternProviderLogic.class && !provider.isBlocking()
                            && provider.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE) == LockCraftingMode.NONE,
                    "Use an ordinary unlocked AE2 provider with a single external inventory");
            var patterns = provider.getAvailablePatterns();
            helper.assertTrue(patterns.size() == 1 && patterns.get(0).getClass() == AEProcessingPattern.class,
                    "Use one unmodified single-input AE2 processing pattern");
            var service = (CraftingService) grid.getCraftingService();
            var cpu = core.getCluster().craftingLogic;
            long requested = 23;
            var plan = new CraftingPlan(new GenericStack(output, requested * 2), 100, false, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(patterns.get(0), requested));
            helper.assertTrue(service.submitJob(plan, null, core.getCluster(), false, IActionSource.empty())
                            .successful(), "Vanilla-provider job must submit");
            cpu.getInventory().insert(input, requested, Actionable.MODULATE);

            helper.startSequence()
                    .thenWaitUntil(() -> helper.assertTrue(target.getItem(0).getCount() == 64,
                            "The ordinary provider must fill the available barrel space"))
                    .thenExecuteAfter(2, () -> {
                        try {
                            helper.assertTrue(target.insertions.size() == capacity
                                            && target.insertions.stream().allMatch(amount -> amount == 1),
                                    "The barrel must receive thirteen separate full-recipe insertions, got " + target.insertions);
                            helper.assertTrue(!provider.isBusy(), "The rejected probe must not leave queued inputs");
                            helper.assertTrue(cpu.getInventory().list.get(input) == requested - capacity
                                            && cpu.getWaitingFor(output) == capacity * 2
                                            && cpu.getPendingOutputs(output) == (requested - capacity) * 2,
                                    "The CPU must consume, await and decrement exactly the accepted recipes");
                        } finally {
                            core.clearContent();
                        }
                    })
                    .thenSucceed();
        });
    }

    // Forge's real barrel item handler writes the slot only on a non-simulated insert.
    private static final class RecordingBarrel extends BarrelBlockEntity {
        private final List<Integer> insertions = new ArrayList<>();

        private RecordingBarrel(BlockPos position, BlockState state) {
            super(position, state);
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            int before = getItem(slot).getCount();
            super.setItem(slot, stack);
            if (slot == 0 && stack.is(Items.IRON_INGOT) && stack.getCount() > before) {
                insertions.add(stack.getCount() - before);
            }
        }
    }

    private static void run(GameTestHelper helper, int multiplier, boolean fillsFirst, boolean multipleProviders) {
        var corePos = new BlockPos(1, 1, 1);
        helper.setBlock(corePos, ModContent.TRANSFINITE_COMPUTE_NEXUS.get());
        helper.setBlock(new BlockPos(1, 1, 2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        // Give the other fixtures time to finish before measuring two providers;
        // their startup work shares the same server-wide dispatch deadline.
        helper.runAfterDelay(multipleProviders ? 80 : 20, () -> {
            var core = (OmniComputationCoreBlockEntity) helper.getBlockEntity(corePos);
            helper.assertTrue(core != null && core.isNetworkOnline() && core.getCluster() != null,
                    "The Nexus fixture must be online with a real crafting CPU");
            var input = AEItemKey.of(Items.IRON_INGOT);
            var second = AEItemKey.of(Items.COAL);
            var output = AEItemKey.of(Items.EMERALD);
            var unit = PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(
                    new GenericStack[]{new GenericStack(input, 1), new GenericStack(second, 2)},
                    new GenericStack[]{new GenericStack(output, 1)}), helper.getLevel());
            helper.assertTrue(unit instanceof AEProcessingPattern,
                    "The ordinary two-input recipe must decode to a real AE processing pattern");
            IPatternDetails pattern = multiplier == 1 ? unit
                    : new ScaledProcessingPattern((AEProcessingPattern) unit, multiplier);
            var first = new OrdinaryProvider(helper, unit, pattern, multiplier, fillsFirst,
                    multipleProviders ? 3 : 13);
            var providers = multipleProviders ? List.of(first,
                    new OrdinaryProvider(helper, unit, pattern, multiplier, false, 7)) : List.of(first);
            var nodes = new ArrayList<IManagedGridNode>();
            for (var provider : providers) {
                var node = GridHelper.createManagedNode(provider, (owner, changed) -> {})
                        .setInWorldNode(false).setIdlePowerUsage(0).addService(ICraftingProvider.class, provider);
                node.create(helper.getLevel(), helper.absolutePos(new BlockPos(3 + nodes.size(), 1, 1)));
                nodes.add(node);
                GridHelper.createConnection(node.getNode(), core.getActionableNode());
                ICraftingProvider.requestUpdate(node);
            }
            helper.runAfterDelay(10, () -> {
                RegressionConfigFixture.isolate();
                var cleaned = new boolean[1];
                Runnable cleanup = () -> {
                    if (cleaned[0]) return;
                    cleaned[0] = true;
                    try {
                        core.clearContent();
                    } finally {
                        nodes.forEach(IManagedGridNode::destroy);
                    }
                };
                try {
                    var grid = core.getMainNode().getGrid();
                    helper.assertTrue(grid != null && grid.getCraftingService() != null,
                            "The Nexus must expose its real AE grid and crafting service");
                    var service = (CraftingService) grid.getCraftingService();
                    var cpu = core.getCluster().craftingLogic;
                    long requested = 100_000;
                    var plan = new CraftingPlan(new GenericStack(output, requested * multiplier), 100, false, false,
                            new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of(pattern, requested));
                    helper.assertTrue(service.submitJob(plan, null, core.getCluster(), false, IActionSource.empty())
                                    .successful(),
                            "Ordinary custom-provider job must submit");
                    cpu.getInventory().insert(input, requested * multiplier, Actionable.MODULATE);
                    cpu.getInventory().insert(second, requested * multiplier * 2, Actionable.MODULATE);
                    new Runnable() {
                        int ticks;

                        @Override
                        public void run() {
                            boolean cleanupNow = true;
                            try {
                                // Probe-round assertions must not depend on the server's wall-clock budget.
                                var limits = ModConfig.OMNI_DISPATCH_LIMITS_ENABLED;
                                boolean previousLimits = limits.get();
                                limits.set(false);
                                try {
                                    cpu.tickCraftingLogic(grid.getEnergyService(), service);
                                } finally {
                                    limits.set(previousLimits);
                                }
                                long accepted = providers.stream().mapToLong(provider -> provider.accepted).sum();
                                helper.assertTrue(cpu.getWaitingFor(output) == accepted * multiplier
                                                && cpu.getInventory().list.get(input)
                                                        == (requested - accepted) * multiplier
                                                && cpu.getInventory().list.get(second)
                                                        == (requested - accepted) * multiplier * 2,
                                        "Every accepted/rejected ordinary call must conserve both CPU inputs "
                                                + "and exact waiting quantities");
                                if (++ticks < 45) {
                                    helper.runAfterDelay(1, () -> run());
                                    cleanupNow = false;
                                    return;
                                }
                                helper.assertTrue(first.firstTick != Long.MIN_VALUE,
                                        "Custom provider must receive work");
                                for (var provider : providers) {
                                    if (fillsFirst) {
                                        helper.assertTrue(provider.successes.values().stream()
                                                        .allMatch(count -> count == 1)
                                                        && provider.failures.isEmpty(),
                                                "A receiver filled by the first original push must skip "
                                                        + "additional probing");
                                    } else if (multipleProviders) {
                                        helper.assertTrue(provider.successes.getOrDefault(provider.firstTick, 0)
                                                        == provider.initialCapacity,
                                                "Both independent providers must reach capacity in their "
                                                        + "first dispatch tick; successes=" + provider.successes
                                                        + ", failures=" + provider.failures);
                                    } else {
                                        for (long offset = 0; offset <= 40; offset++) {
                                            int expected = offset < 20 ? 13 : offset < 40 ? 21 : 37;
                                            helper.assertTrue(provider.successes.getOrDefault(
                                                            provider.firstTick + offset, 0) == expected,
                                                    "Ordinary rate must fill immediately, hold capacity, then recover "
                                                            + "after exactly 20 ticks: offset=" + offset
                                                            + ", successes=" + provider.successes
                                                            + ", failures=" + provider.failures);
                                            int expectedFailures =
                                                    offset == 0 || offset == 20 || offset == 40 ? 1 : 0;
                                            helper.assertTrue(provider.failures.getOrDefault(
                                                            provider.firstTick + offset, 0) == expectedFailures,
                                                    "Only capacity probes may reject; the established rate "
                                                            + "must not oscillate");
                                        }
                                    }
                                }
                            } finally {
                                if (cleanupNow) cleanup.run();
                            }
                            helper.succeed();
                        }
                    }.run();
                } catch (RuntimeException | Error failure) {
                    cleanup.run();
                    throw failure;
                }
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

        private OrdinaryProvider(GameTestHelper helper, IPatternDetails advertised, IPatternDetails pattern,
                int multiplier, boolean fillsFirst, int initialCapacity) {
            this.helper = helper;
            this.advertised = advertised;
            this.pattern = pattern;
            this.multiplier = multiplier;
            this.fillsFirst = fillsFirst;
            this.initialCapacity = initialCapacity;
        }

        @Override
        public List<IPatternDetails> getAvailablePatterns() {
            return List.of(advertised);
        }

        @Override
        public boolean isBusy() {
            return fillsFirst && successes.getOrDefault(TickHandler.instance().getCurrentTick(), 0) > 0;
        }

        @Override
        public boolean pushPattern(IPatternDetails actual, KeyCounter[] inputs) {
            helper.assertTrue(actual == pattern,
                    "A custom/native provider must receive the original dispatched task identity");
            var supplied = new KeyCounter();
            for (var holder : inputs) supplied.addAll(holder);
            helper.assertTrue(supplied.size() == 2 && supplied.get(AEItemKey.of(Items.IRON_INGOT)) == multiplier
                            && supplied.get(AEItemKey.of(Items.COAL)) == 2L * multiplier,
                    "Every ordinary push must contain exactly one complete original task, "
                            + "without a second multiplier");
            long tick = TickHandler.instance().getCurrentTick();
            if (firstTick == Long.MIN_VALUE) firstTick = tick;
            long offset = tick - firstTick;
            int capacity = fillsFirst ? 1
                    : initialCapacity != 13 ? initialCapacity
                    : offset == 0 ? 13 : offset <= 20 ? 21 : 37;
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
