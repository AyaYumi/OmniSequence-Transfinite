package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MatterFabricationTimingTest {
    @Test
    void preservesProgressWhenSpeedIncreasesOrDecreases() {
        assertEquals(50, MatterFabricationTiming.rescaleProgress(100, 400, 200));
        assertEquals(100, MatterFabricationTiming.rescaleProgress(50, 200, 400));
        assertEquals(25, MatterFabricationTiming.rescaleProgress(100, 400, 100));
        assertEquals(200, MatterFabricationTiming.rescaleProgress(100, 400, 800));
    }

    @Test
    void roundsDownWithoutCompletingWorkDuringRetiming() {
        assertEquals(3, MatterFabricationTiming.rescaleProgress(199, 400, 7));
        assertEquals(0, MatterFabricationTiming.rescaleProgress(399, 400, 1));
        assertEquals(399, MatterFabricationTiming.rescaleProgress(400, 400, 400));
    }

    @Test
    void oldOneTickBatchesReturnToTheCurrentRecipeDuration() {
        int progress = MatterFabricationTiming.rescaleProgress(0, 1, 400);
        assertEquals(0, progress);
        assertTrue(progress + 1 < 400, "The next processing tick must not finish an old research-accelerated batch");
    }

    @Test
    void legacyManualSavesKeepElapsedTicksWithinTheNewDuration() {
        assertEquals(120, MatterFabricationTiming.rescaleProgress(120, 0, 400));
        assertEquals(6, MatterFabricationTiming.rescaleProgress(120, 0, 7));
        assertEquals(0, MatterFabricationTiming.rescaleProgress(-1, 400, 200));
    }

    @Test
    void handlesIntegerMaximumWithoutOverflow() {
        assertEquals(Integer.MAX_VALUE - 1,
                MatterFabricationTiming.rescaleProgress(Integer.MAX_VALUE - 1, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(1_073_741_823,
                MatterFabricationTiming.rescaleProgress(200, 400, Integer.MAX_VALUE));
    }
}
