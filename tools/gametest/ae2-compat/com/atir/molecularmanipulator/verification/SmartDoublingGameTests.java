package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.appliedenhancements.api.AelisCycleExecutionApi;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.atir.molecularmanipulator.crafting.MolecularScaledPattern;
import com.atir.molecularmanipulator.crafting.OmniSmartDoublingPlanner;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SmartDoublingGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40)
    public static void nativeTasksBypassAndMixedPlanKeepsExactWork(GameTestHelper helper) {
        var sourcePattern = pattern(false);
        var nativeOriginal = pattern(true);
        var nativeBatch = new ScaledProcessingPattern(nativeOriginal, 7);
        var localBatch = new MolecularScaledPattern(sourcePattern, 4);
        helper.assertTrue(!com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety.isBatchablePattern(nativeOriginal)
                        && !com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety.isBatchablePattern(nativeBatch),
                "Native enabled tasks must bypass runtime Omni batching");
        var unchanged = com.atir.molecularmanipulator.crafting.MolecularExternalScaledPattern.unwrapMultiInput(nativeBatch);
        helper.assertTrue(unchanged.patternDetails() == nativeBatch && unchanged.multiplier() == 1,
                "Native task normalization must retain external wrapper identity");
        var source = AelisExactCraftingPlanApi.attachExecutionMetadata(plan(Map.of(sourcePattern, 9L, nativeOriginal, 15L)),
                BigInteger.valueOf(24), Map.of(sourcePattern, BigInteger.valueOf(9), nativeOriginal, BigInteger.valueOf(15)), Map.of());
        var target = plan(Map.of(localBatch, 2L, sourcePattern, 1L, nativeBatch, 2L, nativeOriginal, 1L));
        var reconciled = AelisCycleExecutionApi.copyMetadata(source, target);
        var exact = AelisExactCraftingPlanApi.getPatternTimes(reconciled);
        helper.assertTrue(exact.equals(Map.of(localBatch, BigInteger.TWO, sourcePattern, BigInteger.ONE,
                nativeBatch, BigInteger.TWO, nativeOriginal, BigInteger.ONE)), "Mixed native/local tasks must conserve exact work");
        helper.assertTrue(!(nativeBatch instanceof com.github.appliedenhancements.integration.ae2.AelisScaledPattern),
                "External batch must retain its native interface only");
        var enabledPlan = plan(Map.of(nativeOriginal, 15L));
        helper.assertTrue(OmniSmartDoublingPlanner.rewriteForSubmission(enabledPlan,
                ignored -> { throw new AssertionError("Native enabled task must bypass Omni lookup"); }) == enabledPlan,
                "Native enabled plan must retain identity");
        var localPlan = plan(Map.of(sourcePattern, 9L));
        var provider = (ICraftingProvider) Proxy.newProxyInstance(SmartDoublingGameTests.class.getClassLoader(),
                new Class<?>[]{ICraftingProvider.class, com.atir.molecularmanipulator.integration.ae2.OmniSmartDoublingProvider.class},
                (p,m,a) -> { throw new AssertionError(m.getName()); });
        var rewritten = OmniSmartDoublingPlanner.rewriteForSubmission(localPlan, ignored -> java.util.List.of(provider));
        helper.assertTrue(rewritten.patternTimes().size() == 1 && rewritten.patternTimes().values().iterator().next() == 1L,
                "Disabled ordinary task must still receive local batching");
        var mixed = plan(Map.of(sourcePattern, 9L, nativeOriginal, 15L));
        var twoArgs = com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans
                .rewriteForSubmission(mixed, ignored -> java.util.List.of());
        helper.assertTrue(com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans.observed
                        .equals(Map.of(sourcePattern, 9L)), "Two-argument native hook must omit enabled external tasks");
        helper.assertTrue(twoArgs.patternTimes().equals(mixed.patternTimes()), "Two-argument hook must restore all tasks");
        var threeArgs = com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans
                .rewriteForSubmission(mixed, ignored -> java.util.List.of(), helper.getLevel());
        helper.assertTrue(com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.SmartDoublingPlans.observed
                        .equals(Map.of(sourcePattern, 9L)), "Three-argument native hook must omit enabled external tasks");
        helper.assertTrue(threeArgs.patternTimes().equals(mixed.patternTimes()), "Three-argument hook must restore all tasks");
        System.out.println("SMART_DOUBLING_BYPASS_PASS nativeIdentity=true exactMixed=true noSecondScale=true localBatch=true");
        helper.succeed();
    }

    private static CraftingPlan plan(Map<IPatternDetails, Long> tasks) {
        return new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 24), 64, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), tasks);
    }
    private static IPatternDetails pattern(boolean nativeEnabled) {
        var output = new GenericStack(AEItemKey.of(Items.DIAMOND), 1);
        return (IPatternDetails) Proxy.newProxyInstance(SmartDoublingGameTests.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class, ISmartDoublingAwarePattern.class}, (p,m,a) -> switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "eap$allowScaling" -> nativeEnabled;
                    case "getDefinition" -> AEItemKey.of(Items.PAPER);
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getOutputs" -> java.util.List.of(output);
                    default -> throw new AssertionError(m.getName());
                });
    }
}
