package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Random;
import org.junit.jupiter.api.Test;

class DetachedSignalTimeTest {
    @Test
    void alternatesOnlyHalfAndDoubleSpeedAtRandomFiveToFifteenSecondIntervals() {
        var time = new DetachedSignalTime(new Random(42));
        var durations = new HashSet<Integer>();
        boolean previous = time.accelerated();
        int duration = 0;
        int switches = 0;
        for (int step = 0; step < 10000; step++) {
            time.advance();
            assertTrue(time.scale() == .5F || time.scale() == 2F, "No intermediate rates");
            if (previous != time.accelerated()) {
                assertTrue(duration >= 100 && duration <= 300, "Each phase lasts 5–15 global seconds");
                durations.add(duration);
                previous = time.accelerated();
                duration = 0;
                switches++;
            }
            duration++;
        }
        assertTrue(switches > 20, "Time keeps switching between slowdown and acceleration");
        assertTrue(durations.size() > 5, "Phase durations are random, rather than a fixed cycle");
    }

    @Test
    void fractionalTicksMatchBothRatesWithoutLosingCreditAcrossSwitches() {
        var time = new DetachedSignalTime(new Random(7));
        int earnedHalfTicks = 0;
        int simulatedTicks = 0;
        boolean paused = false;
        boolean accelerated = false;
        for (int step = 0; step < 10000; step++) {
            int ticks = time.advance();
            earnedHalfTicks += time.accelerated() ? 4 : 1;
            simulatedTicks += ticks;
            assertEquals(earnedHalfTicks / 2, simulatedTicks);
            assertTrue(ticks >= 0 && ticks <= 2, "Never simulate more than double speed");
            if (time.accelerated()) {
                accelerated = true;
                assertEquals(2, ticks);
            } else {
                assertTrue(ticks == 0 || ticks == 1);
                paused |= ticks == 0;
            }
        }
        assertTrue(paused && accelerated, "Both skipped steps and double steps actually occur");
    }
}
