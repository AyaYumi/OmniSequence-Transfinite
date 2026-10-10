package com.atir.molecularmanipulator.verification;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety;
import com.atir.molecularmanipulator.crafting.MolecularExternalScaledPattern;
import com.atir.molecularmanipulator.crafting.OmniSmartDoublingPlanner;
import com.atir.molecularmanipulator.integration.ae2.OmniSmartDoublingProvider;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Omni behavior with baseline Applied APIs, without asserting later Applied fixes. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class Applied110GameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40)
    public static void nativeBypassAndLocalRewritePreserveMixedExactWork(GameTestHelper helper) {
        var ordinary = pattern(false);
        var nativePattern = pattern(true);
        var nativeBatch = new ScaledProcessingPattern(nativePattern, 7);
        helper.assertTrue(!MolecularBatchDispatchSafety.isBatchablePattern(nativePattern)
                        && !MolecularBatchDispatchSafety.isBatchablePattern(nativeBatch),
                "Native patterns must bypass a second Omni batch without newer Applied APIs");
        var unwrapped = MolecularExternalScaledPattern.unwrapMultiInput(nativeBatch);
        helper.assertTrue(unwrapped.patternDetails() == nativeBatch && unwrapped.multiplier() == 1,
                "External wrapper identity must be preserved");
        var nativePlan = plan(Map.of(nativePattern, 15L));
        helper.assertTrue(OmniSmartDoublingPlanner.rewriteForSubmission(nativePlan,
                ignored -> { throw new AssertionError("Native task reached Omni provider lookup"); }) == nativePlan,
                "Native enabled plan must retain identity");

        var provider = (ICraftingProvider) Proxy.newProxyInstance(Applied110GameTests.class.getClassLoader(),
                new Class<?>[]{ICraftingProvider.class, OmniSmartDoublingProvider.class},
                (p, m, a) -> { throw new AssertionError(m.getName()); });
        var source = AelisExactCraftingPlanApi.attachExecutionMetadata(
                plan(Map.of(ordinary, 9L, nativeBatch, 2L, nativePattern, 1L)), BigInteger.valueOf(24),
                Map.of(ordinary, BigInteger.valueOf(9), nativeBatch, BigInteger.TWO, nativePattern, BigInteger.ONE),
                Map.of());
        var rewritten = OmniSmartDoublingPlanner.rewriteForSubmission(source, ignored -> List.of(provider));
        helper.assertTrue(rewritten != source, "Ordinary task must still use local batching");
        var exact = AelisExactCraftingPlanApi.getPatternTimes(rewritten);
        BigInteger ordinaryWork = BigInteger.ZERO;
        for (var entry : exact.entrySet()) {
            if (entry.getKey() == nativeBatch || entry.getKey() == nativePattern) continue;
            helper.assertTrue(entry.getKey() instanceof com.github.appliedenhancements.integration.ae2.AelisScaledPattern,
                    "Ordinary task must carry its checked local multiplier");
            var scaled = (com.github.appliedenhancements.integration.ae2.AelisScaledPattern) entry.getKey();
            helper.assertTrue(scaled.appliedenhancements$originalPattern() == ordinary,
                    "Local wrapper must retain the original pattern");
            ordinaryWork = ordinaryWork.add(entry.getValue().multiply(
                    BigInteger.valueOf(scaled.appliedenhancements$operationsPerPush())));
        }
        helper.assertTrue(ordinaryWork.equals(BigInteger.valueOf(9))
                        && BigInteger.TWO.equals(exact.get(nativeBatch))
                        && BigInteger.ONE.equals(exact.get(nativePattern))
                        && AelisExactCraftingPlanApi.getFinalOutputAmount(rewritten).equals(BigInteger.valueOf(24)),
                "Mixed native/local rewrite must preserve exact work, native identity and final output");
        System.out.println("APPLIED_110_COMPAT_PASS nativeBypass=true mixedExact=true localBatch=true");
        helper.succeed();
    }

    private static CraftingPlan plan(Map<IPatternDetails, Long> tasks) {
        return new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 24), 64, false, false,
                new KeyCounter(), new KeyCounter(), new KeyCounter(), tasks);
    }

    private static IPatternDetails pattern(boolean enabled) {
        return (IPatternDetails) Proxy.newProxyInstance(Applied110GameTests.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class, ISmartDoublingAwarePattern.class},
                (p, m, a) -> switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "eap$allowScaling" -> enabled;
                    case "getDefinition" -> AEItemKey.of(Items.PAPER);
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getOutputs" -> new GenericStack[]{new GenericStack(AEItemKey.of(Items.DIAMOND), 1)};
                    default -> throw new AssertionError(m.getName());
                });
    }
}
