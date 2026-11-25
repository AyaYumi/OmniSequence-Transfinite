package com.atir.molecularmanipulator.integration.thunderbolt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongUnaryOperator;
import org.junit.jupiter.api.Test;

class ThunderboltBatchAdmissionTest {
    @Test void onlyAcceptedCopiesAreChargedAfterOneSubmission() {
        var submits = new AtomicInteger();
        try (var admission = admission(9, count -> {
            assertEquals(7, count);
            submits.incrementAndGet();
            return 3;
        })) {
            assertEquals(BigInteger.valueOf(9), admission.maximumCrafts());
            assertEquals(BigInteger.valueOf(4), admission.submit(BigInteger.valueOf(7)));
            assertThrows(IllegalStateException.class, () -> admission.submit(BigInteger.ONE));
            assertEquals(1, submits.get());
        }
    }

    @Test void aFullRemainderMeansNoAcceptance() {
        try (var admission = admission(7, count -> count)) {
            assertEquals(BigInteger.ZERO, admission.submit(BigInteger.valueOf(7)));
        }
    }

    @Test void invalidRemaindersAreUnknownOwnershipAndCannotBeReplayed() {
        for (long remainder : new long[] { -1, 8 }) {
            var submits = new AtomicInteger();
            try (var admission = admission(7, count -> {
                submits.incrementAndGet();
                return remainder;
            })) {
                assertThrows(IllegalStateException.class, () -> admission.submit(BigInteger.valueOf(7)));
                assertThrows(IllegalStateException.class, () -> admission.submit(BigInteger.ONE));
                assertEquals(1, submits.get());
            }
        }
    }

    @Test void anExceptionFromTheProviderCannotBeReplayed() {
        var failure = new IllegalArgumentException("ownership unknown");
        try (var admission = admission(7, count -> { throw failure; })) {
            assertSame(failure, assertThrows(IllegalArgumentException.class,
                    () -> admission.submit(BigInteger.ONE)));
            assertThrows(IllegalStateException.class, () -> admission.submit(BigInteger.ONE));
        }
    }

    @Test void aClosedOrOutOfBoundsAdmissionCannotSubmit() {
        var closed = admission(7, count -> { throw new AssertionError("unexpected submit"); });
        closed.close();
        assertThrows(IllegalStateException.class, () -> closed.submit(BigInteger.ONE));
        try (var admission = admission(7, count -> { throw new AssertionError("unexpected submit"); })) {
            assertThrows(IllegalArgumentException.class, () -> admission.submit(BigInteger.valueOf(8)));
        }
    }

    private static ThunderboltBatchAdmission admission(long maximum, LongUnaryOperator push) {
        return new ThunderboltBatchAdmission(maximum) {
            @Override long push(long count) { return push.applyAsLong(count); }
        };
    }
}
