package com.atir.molecularmanipulator.integration.useless;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AdaptiveDirectBatchLimitTest {
    static final BigInteger Y = BigInteger.TEN.pow(24);
    @Test void nativeLongProjectionDoesNotOverflowForUnboundedWindows() {
        var max = BigInteger.valueOf(Long.MAX_VALUE);
        assertEquals(1, OmniDirectAdmission.projectSegments(max));
        assertEquals(2, OmniDirectAdmission.projectSegments(max.add(BigInteger.ONE)));
        assertEquals(Long.MAX_VALUE, OmniDirectAdmission.projectSegments(max.pow(4)));
    }
    @Test void fastFullReturnsGrowPastYAndLongWithoutFixedCeiling() {
        var control = new AdaptiveDirectBatchLimit(Y, 500_000);
        for (int i = 0; i < 200; i++) {
            var window = control.window();
            assertEquals(window, control.admission(i * 5L, BigInteger.ZERO, 0, BigInteger.ONE));
            control.returned(i * 5L, window, 1000, BigInteger.ZERO, 0);
        }
        assertEquals(Y.shiftLeft(200), control.window());
        assertTrue(control.window().compareTo(BigInteger.valueOf(Long.MAX_VALUE).pow(2)) > 0);
    }
    @Test void repeatedSameTickSuccessDoesNotExplodeWindow() {
        var control = new AdaptiveDirectBatchLimit(Y, 500_000);
        for (int i = 0; i < 100; i++) control.returned(10, control.window(), 1000, BigInteger.ZERO, 0);
        assertEquals(Y.multiply(BigInteger.TWO), control.window());
    }
    @Test void smallDemandDoesNotInflateCapacity() {
        var control = new AdaptiveDirectBatchLimit(Y, 500_000);
        for (int i = 0; i < 100; i++) control.returned(i * 5L, BigInteger.TEN, 1000, BigInteger.ZERO, 0);
        assertEquals(Y, control.window());
    }
    @Test void slowOrDelayedReturnShrinksAndRecovers() {
        var control = new AdaptiveDirectBatchLimit(Y, 500_000);
        control.returned(1, Y, 600_000, BigInteger.ZERO, 0);
        assertEquals(Y.divide(BigInteger.TWO), control.window());
        control.returned(2, control.window(), 1000, BigInteger.ZERO, 3);
        assertEquals(Y.divide(BigInteger.valueOf(4)), control.window());
        control.returned(7, control.window(), 1000, BigInteger.ZERO, 0);
        assertEquals(Y.divide(BigInteger.TWO), control.window());
    }
    @Test void backlogBoundsNewAdmissionsAndRejectsStalledQueues() {
        var control = new AdaptiveDirectBatchLimit(BigInteger.valueOf(100), 500_000);
        assertEquals(BigInteger.valueOf(50), control.admission(1, BigInteger.valueOf(150), 0, BigInteger.ONE));
        assertEquals(BigInteger.ZERO, control.admission(2, BigInteger.valueOf(200), 0, BigInteger.ONE));
        assertEquals(BigInteger.valueOf(50), control.window());
        assertEquals(BigInteger.ZERO, control.admission(3, BigInteger.ONE, 3, BigInteger.ONE));
    }
    @Test void shrinkingCannotStrandAnIndivisibleScaledTask() {
        var unit = BigInteger.valueOf(Long.MAX_VALUE);
        var control = new AdaptiveDirectBatchLimit(Y, 500_000);
        control.admission(0, BigInteger.ZERO, 0, unit);
        for (int i = 1; i < 100; i++) control.backoff(i);
        assertEquals(unit, control.window());
        assertEquals(unit, control.admission(100, BigInteger.ZERO, 0, unit));
    }
    @Test void idleRelearnsInsteadOfReusingStaleLargeWindow() {
        var control = new AdaptiveDirectBatchLimit(Y, 500_000);
        control.returned(0, Y, 1000, BigInteger.ZERO, 0);
        assertEquals(Y, control.admission(201, BigInteger.ZERO, 0, BigInteger.ONE));
    }
}
