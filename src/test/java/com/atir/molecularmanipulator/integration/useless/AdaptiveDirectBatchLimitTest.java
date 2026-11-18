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
    private final UselessBigIntegerApiBridgeTest.Provider provider = new UselessBigIntegerApiBridgeTest.Provider();

    private BigInteger admitted(BigInteger requested) throws Exception {
        return UselessBigIntegerApiBridgeTest.adapter(provider).getMaximumBigIntegerCrafts(
                UselessBigIntegerApiBridgeTest.BASE, UselessBigIntegerApiBridgeTest.inputs(1), requested);
    }

    @Test void fastFullReturnsGrowPastYAndLongWithoutFixedCeiling() throws Exception {
        for (int shift = 0; shift <= 200; shift++) {
            provider.target.limit = Y.shiftLeft(shift);
            assertEquals(provider.target.limit, admitted(provider.target.limit));
        }
    }
    @Test void repeatedSameTickSuccessDoesNotExplodeWindow() throws Exception {
        provider.target.limit = Y;
        for (int repeat = 0; repeat < 100; repeat++) assertEquals(Y, admitted(Y.shiftLeft(1)));
    }
    @Test void smallDemandDoesNotInflateCapacity() throws Exception {
        provider.target.limit = Y;
        assertEquals(BigInteger.TEN, admitted(BigInteger.TEN));
        assertEquals(Y, admitted(Y.shiftLeft(1)));
    }
    @Test void slowOrDelayedReturnShrinksAndRecovers() throws Exception {
        for (var capacity : new BigInteger[]{Y, Y.shiftRight(1), Y.shiftRight(2), Y}) {
            provider.target.limit = capacity;
            assertEquals(capacity, admitted(Y));
        }
    }
    @Test void backlogBoundsNewAdmissionsAndRejectsStalledQueues() throws Exception {
        provider.target.limit = BigInteger.valueOf(50);
        assertEquals(provider.target.limit, admitted(Y));
        provider.target.limit = BigInteger.ZERO;
        assertEquals(BigInteger.ZERO, admitted(Y));
    }
    @Test void shrinkingCannotStrandAnIndivisibleScaledTask() throws Exception {
        var adapter = UselessBigIntegerApiBridgeTest.adapter(provider);
        var scaled = new UselessBigIntegerApiBridgeTest.Wrapped(10);
        var inputs = UselessBigIntegerApiBridgeTest.inputs(10);
        provider.target.limit = BigInteger.valueOf(9);
        assertEquals(BigInteger.ZERO, adapter.getMaximumBigIntegerCrafts(scaled, inputs, Y));
        provider.target.limit = BigInteger.TEN;
        assertEquals(BigInteger.ONE, adapter.getMaximumBigIntegerCrafts(scaled, inputs, Y));
    }
    @Test void idleRelearnsInsteadOfReusingStaleLargeWindow() throws Exception {
        provider.target.limit = Y.pow(2);
        assertEquals(Y.pow(2), admitted(Y.pow(2)));
        try (var scope = OmniDirectAdmission.open(null)) {
            provider.target.limit = Y;
            assertEquals(Y, admitted(Y.pow(2)));
        }
    }
}
