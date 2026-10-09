package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.api.crafting.ScaledProcessingPattern;
import com.github.appliedenhancements.integration.ae2.AelisScaledPattern;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LegacyAelisTaskReconciliationTest {
    @Test void mixedNativeAndLocalWorkRetainsCompleteCountsAndRemainders() {
        var a = pattern();
        var b = pattern();
        var nativeBatch = new ScaledProcessingPattern(a, 7);
        var localBatch = scaled(b, 4);
        var result = LegacyAelisTaskReconciliation.reconcile(
                Map.of(nativeBatch, 2L, a, 1L, localBatch, 1L),
                Map.of(a, BigInteger.valueOf(15), b, BigInteger.valueOf(9)));
        assertEquals(Map.of(nativeBatch, BigInteger.TWO, a, BigInteger.ONE,
                localBatch, BigInteger.TWO, b, BigInteger.ONE), result.exact());
        assertFalse(nativeBatch instanceof AelisScaledPattern);
    }

    @Test void unevenNativeSplitsKeepTheirIdentities() {
        var original = pattern();
        var large = new ScaledProcessingPattern(original, 4);
        var small = new ScaledProcessingPattern(original, 3);
        var projected = Map.<IPatternDetails, Long>of(large, 2L, small, 1L, original, 1L);
        var result = LegacyAelisTaskReconciliation.reconcile(projected, Map.of(original, BigInteger.valueOf(12)));
        assertEquals(projected, result.projected());
        assertEquals(Map.of(large, BigInteger.TWO, small, BigInteger.ONE, original, BigInteger.ONE), result.exact());
    }

    @Test void mismatchedNativeProjectionIsRepairedWithoutRoundingUp() {
        var original = pattern();
        var batch = new ScaledProcessingPattern(original, 7);
        var result = LegacyAelisTaskReconciliation.reconcile(Map.of(batch, 2L), Map.of(original, BigInteger.valueOf(15)));
        assertEquals(Map.of(batch, 2L, original, 1L), result.projected());
        assertEquals(Map.of(batch, BigInteger.TWO, original, BigInteger.ONE), result.exact());
    }

    @Test void beyondLongCountsPreserveAllWorkAndBoundOnlyTheProjection() {
        var original = pattern();
        var batch = scaled(original, 7);
        var amount = new BigInteger("99999999999999999999999");
        var result = LegacyAelisTaskReconciliation.reconcile(Map.of(batch, 1L), Map.of(original, amount));
        assertEquals(amount, result.exact().get(batch).multiply(BigInteger.valueOf(7))
                .add(result.exact().getOrDefault(original, BigInteger.ZERO)));
        assertEquals(Long.MAX_VALUE - 1, result.projected().get(batch));
    }

    @Test void unknownWrapperRestoresEveryOriginalTaskWithoutThrowing() {
        var original = pattern();
        var other = pattern();
        var batch = scaled(original, 4);
        var exact = Map.of(original, BigInteger.valueOf(9), other, BigInteger.valueOf(5));
        var result = assertDoesNotThrow(() -> LegacyAelisTaskReconciliation.reconcile(
                Map.of(batch, 1L, pattern(), 1L), exact));
        assertEquals(exact, result.exact());
        assertEquals(Map.of(original, 9L, other, 5L), result.projected());
    }

    @Test void nestedNativeAndLocalMultipliersAreAppliedOnce() {
        var original = pattern();
        var batch = scaled(new ScaledProcessingPattern(original, 3), 4);
        var result = LegacyAelisTaskReconciliation.reconcile(Map.of(batch, 1L), Map.of(original, BigInteger.valueOf(25)));
        assertEquals(Map.of(batch, BigInteger.TWO, original, BigInteger.ONE), result.exact());
        var copied = LegacyAelisTaskReconciliation.reconcile(result.projected(), result.exact());
        assertSame(result.exact(), copied.exact());
        assertSame(result.projected(), copied.projected());
    }

    @Test void invalidOrOverflowingScaleUsesCompleteOriginalWork() {
        var original = pattern();
        var exact = Map.of(original, BigInteger.TWO);
        for (var batch : List.of(scaled(original, 0), scaled(pattern(), 4),
                scaled(scaled(original, Long.MAX_VALUE), 2),
                scaled(new ScaledProcessingPattern(original, 0), 4))) {
            assertEquals(exact, assertDoesNotThrow(() -> LegacyAelisTaskReconciliation.reconcile(
                    Map.of(batch, 1L), exact)).exact());
        }
    }

    @Test void ordinaryPlansAndAbsentLedgersKeepTheirMaps() {
        var original = pattern();
        var projected = Map.of(original, 9L);
        assertSame(projected, LegacyAelisTaskReconciliation.reconcile(projected, Map.of()).projected());
        assertSame(projected, LegacyAelisTaskReconciliation.reconcile(projected,
                Map.of(original, BigInteger.valueOf(9))).projected());
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) Proxy.newProxyInstance(LegacyAelisTaskReconciliationTest.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class}, (p, m, a) -> switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "toString" -> "Original@" + System.identityHashCode(p);
                    case "getDefinition" -> null;
                    case "getInputs" -> new IPatternDetails.IInput[0];
                    case "getOutputs" -> List.of();
                    default -> throw new AssertionError(m.getName());
                });
    }

    private static IPatternDetails scaled(IPatternDetails original, long multiplier) {
        return (IPatternDetails) Proxy.newProxyInstance(LegacyAelisTaskReconciliationTest.class.getClassLoader(),
                new Class<?>[]{IPatternDetails.class, AelisScaledPattern.class}, (p, m, a) -> switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "toString" -> "Batch@" + System.identityHashCode(p);
                    case "appliedenhancements$originalPattern" -> original;
                    case "appliedenhancements$operationsPerPush" -> multiplier;
                    default -> throw new AssertionError(m.getName());
                });
    }
}
