package com.atir.molecularmanipulator.starcomputation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StarComputationBenefitsTest {
    @Test
    void assemblersStartAt4096AndQuadrupleAtEveryLevel() {
        assertEquals(1, StarComputationBenefits.NONE.assemblerParallel());
        long parallel = 4096;
        for (int level = 1; level <= 9; level++, parallel *= 4) {
            assertEquals(parallel, new StarComputationBenefits(level, false).assemblerParallel(), "L" + level);
            assertEquals(level == 9 ? Long.MAX_VALUE : parallel * 2,
                    new StarComputationBenefits(level, true).assemblerParallel(), "L" + level + "OC");
        }
    }

    @Test
    void productionUsesTheExactSpeedTableAndCeilingDivision() {
        int[] speeds = {1, 2, 3, 4, 8, 12, 16, 32, 48, 64};
        int[] ticks = {400, 200, 134, 100, 50, 34, 25, 13, 9, 7};
        for (int level = 0; level <= 9; level++) {
            var benefits = new StarComputationBenefits(level, false);
            assertEquals(speeds[level], benefits.speedMultiplier(), "L" + level);
            assertEquals(ticks[level], benefits.processingTicks(400), "L" + level);
            for (int custom : new int[]{1, 73, 200, 1200, Integer.MAX_VALUE}) {
                assertEquals((int) ((custom - 1L) / speeds[level] + 1), benefits.processingTicks(custom));
            }
        }
    }

    @Test
    void overclockDoublesFiniteSpeedAndOnlyL9OverclockForcesOneTick() {
        for (int level = 1; level <= 9; level++) {
            var normal = new StarComputationBenefits(level, false);
            var overclock = new StarComputationBenefits(level, true);
            assertEquals(normal.speedMultiplier() * 2, overclock.speedMultiplier());
            assertEquals(level == 9, overclock.infinite());
            for (int base : new int[]{0, 1, 73, 400, Integer.MAX_VALUE}) {
                int expected = level == 9 ? 1 : (int) ((Math.max(1, base) - 1L) / overclock.speedMultiplier() + 1);
                assertEquals(expected, overclock.processingTicks(base), "L" + level + "OC base=" + base);
            }
        }
        var absent = new StarComputationBenefits(0, true);
        assertFalse(absent.overclocked());
        assertEquals(400, absent.processingTicks(400));
        assertEquals(1, absent.processingTicks(Integer.MIN_VALUE));
    }
}
