package com.atir.molecularmanipulator.verification;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.appliedenhancements.Config;
import com.appliedenhancements.api.AelisCraftingRequest;
import com.appliedenhancements.api.AelisExactCraftingService;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SelectedInterceptionGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 80)
    public static void unknownAndMismatchedRewritesContinueWithExactWork(GameTestHelper helper) {
        var original = pattern(Items.DIAMOND);
        var batch = new ScaledProcessingPattern(original, 7);
        var source = AelisExactCraftingPlanApi.attachExecutionMetadata(plan(Map.of(original, 15L)),
                BigInteger.valueOf(15), Map.of(original, BigInteger.valueOf(15)), Map.of());
        var result = AelisCycleExecutionApi.copyMetadata(source, plan(Map.of(batch, 2L)));
        helper.assertTrue(AelisExactCraftingPlanApi.getPatternTimes(result).equals(
                Map.of(batch, BigInteger.TWO, original, BigInteger.ONE)), "Mismatch must repair the exact remainder");
        var unknown = pattern(Items.EMERALD);
        result = AelisCycleExecutionApi.copyMetadata(source, plan(Map.of(batch, 2L, unknown, 1L)));
        helper.assertTrue(result.patternTimes().equals(Map.of(original, 15L)), "Unknown rewrite must restore original executable tasks");
        var huge = BigInteger.TEN.pow(1024);
        helper.assertTrue(new AelisCraftingRequest(AEItemKey.of(Items.DIAMOND), huge,
                CalculationStrategy.CRAFT_LESS).amount().equals(huge), "Large CRAFT_LESS request must be accepted");
        var cyclic = plan(Map.of(original, 1L));
        var seed = AEItemKey.of(Items.GOLD_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        var cycle = new com.appliedenhancements.api.AelisCycleExecutionPlan(
                List.of(new com.appliedenhancements.api.AelisCycleExecutionPlan.Step(
                        original.getDefinition(), 1, Map.of(seed, 1L), java.util.Set.of())),
                Map.of(seed, 1L), java.util.Set.of(seed), com.appliedenhancements.api.AelisCycleSeedPolicy.PRESERVE_MINIMUM,
                new com.appliedenhancements.api.AelisCycleExecutionPlan.Phase(java.util.Set.of(),
                        Map.of(original.getDefinition(), Map.of(output, 1L))));
        ((com.github.appliedenhancements.integration.ae2.AelisCycleExecutionPlanCarrier) (Object) cyclic)
                .appliedenhancements$setCycleExecutionPlan(cycle);
        var target = (appeng.api.networking.crafting.ICraftingCPU) java.lang.reflect.Proxy.newProxyInstance(
                SelectedInterceptionGameTests.class.getClassLoader(),
                new Class<?>[]{appeng.api.networking.crafting.ICraftingCPU.class},
                (p,m,a) -> { throw new AssertionError("Capability refusal must not query the unsupported target"); });
        IManagedGridNode probeNode = GridHelper.createManagedNode(new Object(), (owner, changed) -> {})
                .setInWorldNode(false).setIdlePowerUsage(0);
        probeNode.create(helper.getLevel(), helper.absolutePos(new BlockPos(6, 0, 0)));
        try {
            var submitted = probeNode.getGrid().getCraftingService().submitJob(cyclic, null, target, false, IActionSource.empty());
            helper.assertTrue(submitted == appeng.crafting.execution.CraftingSubmitResult.NO_CPU_FOUND,
                    "Unsupported cycle capability must reach native submission, which has no CPU in this fixture");
        } finally { probeNode.destroy(); }
        System.out.println("SELECTED_REWRITE_CONTINUATION_PASS repaired=true unknownRestored=true largeStrategy=true cycleTargetDelegated=true");
        helper.succeed();
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void exactEntryWithDisabledPreferenceContinuesNativeFallback(GameTestHelper helper) {
        var key = AEItemKey.of(Items.DIAMOND);
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
            public java.util.Set<appeng.api.stacks.AEKey> getEmitableItems() { return java.util.Set.of(key); }
            public boolean pushPattern(IPatternDetails pattern, appeng.api.stacks.KeyCounter[] inputs) { return false; }
            public boolean isBusy() { return false; }
        };
        IManagedGridNode node = GridHelper.createManagedNode(provider, (owner, changed) -> {})
                .setInWorldNode(false).setIdlePowerUsage(0).addService(ICraftingProvider.class, provider);
        node.create(helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
        ICraftingProvider.requestUpdate(node);
        boolean preference = Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.get();
        Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.set(false);
        try {
            var requester = new ICraftingSimulationRequester() {
                public IGridNode getGridNode() { return node.getNode(); }
                public IActionSource getActionSource() { return IActionSource.empty(); }
            };
            var pending = AelisExactCraftingService.begin(helper.getLevel(), requester,
                    new AelisCraftingRequest(key, BigInteger.valueOf(2_000_001), CalculationStrategy.CRAFT_LESS));
            helper.succeedWhen(() -> {
                helper.assertTrue(pending.isDone(), "Calculation must complete");
                try {
                    var plan = pending.get();
                    helper.assertTrue(plan != null && plan.finalOutput().amount() == 2_000_001,
                            "Large request must continue to a native emittable plan");
                    System.out.println("SELECTED_FALLBACK_CONTINUATION_PASS preferenceOff=true nativeFallback=true amount=2000001");
                } catch (Exception failure) { throw new AssertionError("Selected rejection still blocked the calculation", failure); }
                finally { if (pending.isDone()) { node.destroy(); Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.set(preference); } }
            });
        } catch (RuntimeException | Error failure) {
            node.destroy(); Config.ENABLE_AELIS_BIG_INTEGER_PLANNING.set(preference); throw failure;
        }
    }

    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 200)
    public static void craftLessUsesReducedExactAmount(GameTestHelper helper) {
        var input = AEItemKey.of(Items.GOLD_INGOT);
        var output = AEItemKey.of(Items.DIAMOND);
        net.minecraft.world.item.ItemStack encoded;
        try {
            var encoder = appeng.api.crafting.PatternDetailsHelper.class.getMethod("encodeProcessingPattern", List.class, List.class);
            encoded = (net.minecraft.world.item.ItemStack) encoder.invoke(null,
                    List.of(new GenericStack(input, 1)), List.of(new GenericStack(output, 1)));
        } catch (NoSuchMethodException uelm) {
            try {
                var methods = java.util.Arrays.stream(appeng.api.crafting.PatternDetailsHelper.class.getMethods())
                        .filter(m -> m.getName().equals("encodeProcessingPattern") && m.getParameterTypes()[0] == GenericStack[].class).toList();
                var encoder = methods.stream().filter(m -> m.getParameterCount() == 2).findFirst().orElse(null);
                if (encoder != null) {
                    encoded = (net.minecraft.world.item.ItemStack) encoder.invoke(null,
                            new GenericStack[]{new GenericStack(input, 1)}, new GenericStack[]{new GenericStack(output, 1)});
                } else {
                    encoder = methods.stream().filter(m -> m.getParameterCount() == 3
                            && m.getParameterTypes()[2] == String.class).findFirst().orElseThrow();
                    encoded = (net.minecraft.world.item.ItemStack) encoder.invoke(null,
                            new GenericStack[]{new GenericStack(input, 1)}, new GenericStack[]{new GenericStack(output, 1)}, "GameTest");
                }
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        var pattern = appeng.api.crafting.PatternDetailsHelper.decodePattern(encoded, helper.getLevel());
        var provider = new ICraftingProvider() {
            public List<IPatternDetails> getAvailablePatterns() { return List.of(pattern); }
            public boolean pushPattern(IPatternDetails p, appeng.api.stacks.KeyCounter[] inputs) { return false; }
            public boolean isBusy() { return false; }
        };
        var inventory = new appeng.api.storage.MEStorage() {
            public long extract(appeng.api.stacks.AEKey key, long amount, Actionable mode, IActionSource source) {
                return input.equals(key) ? Math.min(23, amount) : 0;
            }
            public void getAvailableStacks(appeng.api.stacks.KeyCounter stacks) { stacks.add(input, 23); }
            public net.minecraft.network.chat.Component getDescription() { return net.minecraft.network.chat.Component.literal("Finite exact test inventory"); }
        };
        IManagedGridNode node = GridHelper.createManagedNode(provider, (owner, changed) -> {})
                .setInWorldNode(false).setIdlePowerUsage(0).addService(ICraftingProvider.class, provider)
                .addService(appeng.api.storage.IStorageProvider.class, mounts -> mounts.mount(inventory));
        node.create(helper.getLevel(), helper.absolutePos(new BlockPos(3, 0, 0)));
        ICraftingProvider.requestUpdate(node);
        appeng.api.storage.IStorageProvider.requestUpdate(node);
        var requester = new ICraftingSimulationRequester() {
            public IGridNode getGridNode() { return node.getNode(); }
            public IActionSource getActionSource() { return IActionSource.empty(); }
        };
        var pending = AelisExactCraftingService.begin(helper.getLevel(), requester,
                new AelisCraftingRequest(output, BigInteger.valueOf(100), CalculationStrategy.CRAFT_LESS));
        helper.succeedWhen(() -> {
            helper.assertTrue(pending.isDone(), "Finite calculation must complete");
            try {
                var plan = pending.get();
                helper.assertTrue(!plan.simulation() && plan.finalOutput().amount() == 23,
                        "CRAFT_LESS must find the 23 actually craftable outputs");
                helper.assertTrue(AelisExactCraftingPlanApi.getFinalOutputAmount(plan).equals(BigInteger.valueOf(23)),
                        "Reduced result must not retain the requested 100 as its final exact amount");
                System.out.println("SELECTED_CRAFT_LESS_PASS requested=100 actual=23 exactFinal=23");
            } catch (Exception failure) { throw new AssertionError("Reduced exact attempt failed", failure); }
            finally { if (pending.isDone()) node.destroy(); }
        });
    }

    private static appeng.crafting.CraftingPlan plan(Map<IPatternDetails, Long> tasks) {
        return new appeng.crafting.CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 15), 64,
                false, false, new appeng.api.stacks.KeyCounter(), new appeng.api.stacks.KeyCounter(),
                new appeng.api.stacks.KeyCounter(), tasks);
    }
    private static IPatternDetails pattern(net.minecraft.world.item.Item item) {
        return (IPatternDetails) java.lang.reflect.Proxy.newProxyInstance(SelectedInterceptionGameTests.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class}, (p,m,a) -> switch(m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "getDefinition" -> AEItemKey.of(item);
                    case "getOutputs" -> new GenericStack[]{new GenericStack(AEItemKey.of(item), 1)};
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    default -> throw new AssertionError(m.getName());
                });
    }
}
