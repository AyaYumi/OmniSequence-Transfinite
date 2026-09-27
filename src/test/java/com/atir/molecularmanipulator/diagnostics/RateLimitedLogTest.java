package com.atir.molecularmanipulator.diagnostics;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimitedLogTest {
    @Test
    void persistentFailuresLogImmediatelyAndAtMostOncePerMinute() {
        var clock = new AtomicLong(-123);
        long interval = TimeUnit.MINUTES.toNanos(1);
        var gate = new RateLimitedLog.Gate(interval, clock::get);
        assertTrue(gate.allow("auto-crafting event"));
        for (int attempt = 0; attempt < 10_000; attempt++) {
            clock.incrementAndGet();
            assertFalse(gate.allow("auto-crafting event"));
        }
        clock.set(-123 + interval - 1);
        assertFalse(gate.allow("auto-crafting event"));
        clock.incrementAndGet();
        assertTrue(gate.allow("auto-crafting event"));
        assertFalse(gate.allow("auto-crafting event"));
    }

    @Test
    void unrelatedFailureCategoriesKeepTheirFirstReport() {
        var gate = new RateLimitedLog.Gate(TimeUnit.MINUTES.toNanos(1), () -> 0);
        assertTrue(gate.allow("batch admission"));
        assertTrue(gate.allow("batch cleanup"));
        assertFalse(gate.allow("batch admission"));
        assertFalse(gate.allow("batch cleanup"));
    }

    @Test
    void concurrentMachinesShareOneWindow() {
        var gate = new RateLimitedLog.Gate(TimeUnit.MINUTES.toNanos(1), () -> 0);
        var emitted = new AtomicInteger();
        IntStream.range(0, 1_000).parallel().forEach(machine -> {
            if (gate.allow("output return")) emitted.incrementAndGet();
        });
        assertEquals(1, emitted.get());
    }

    @Test
    void monotonicClockWrapDoesNotBreakTheWindow() {
        var clock = new AtomicLong(Long.MAX_VALUE - 5);
        var gate = new RateLimitedLog.Gate(10, clock::get);
        assertTrue(gate.allow("failure"));
        clock.set(Long.MIN_VALUE + 3);
        assertFalse(gate.allow("failure"));
        clock.incrementAndGet();
        assertTrue(gate.allow("failure"));
    }
}
