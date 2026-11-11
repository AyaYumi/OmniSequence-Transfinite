package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class StarSeaCalculationEnvironmentTest {
    @Test
    void realNightIncludes13000AndExcludes23000() {
        assertFalse(StarSeaCalculationEnvironment.isNight(true, false, 12_999));
        assertTrue(StarSeaCalculationEnvironment.isNight(true, false, 13_000));
        assertTrue(StarSeaCalculationEnvironment.isNight(true, false, 22_999));
        assertFalse(StarSeaCalculationEnvironment.isNight(true, false, 23_000));
        for (long day : new long[] {0, 6_000, 12_000, 23_999, 24_000}) {
            assertFalse(StarSeaCalculationEnvironment.isNight(true, false, day));
        }
    }

    @Test
    void onlyDimensionsWithSkylightAndAdvancingTimeQualify() {
        assertTrue(StarSeaCalculationEnvironment.isNight(true, false, 18_000));
        assertFalse(StarSeaCalculationEnvironment.isNight(false, false, 18_000));
        assertFalse(StarSeaCalculationEnvironment.isNight(true, true, 18_000));
        assertFalse(StarSeaCalculationEnvironment.isNight(false, true, 18_000));
    }

    @Test
    void aDarkThunderstormOrCoveredRoofCannotTurnDaytimeIntoNight() {
        assertTrue(StarSeaCalculationEnvironment.isDarkEnough(0));
        assertFalse(StarSeaCalculationEnvironment.isNight(true, false, 6_000));
        assertFalse(StarSeaCalculationEnvironment.isNight(true, false, 12_999));
        assertFalse(StarSeaCalculationEnvironment.isNight(true, false, 23_000));
    }

    @Test
    void timeWrapsAcrossDaysIncludingNegativeAndVeryLargeDayCounts() {
        for (long day : new long[] {-100_000_000_000L, -1, 0, 1, 100_000_000_000L}) {
            long offset = day * 24_000;
            assertFalse(StarSeaCalculationEnvironment.isNight(true, false, offset + 12_999));
            assertTrue(StarSeaCalculationEnvironment.isNight(true, false, offset + 13_000));
            assertTrue(StarSeaCalculationEnvironment.isNight(true, false, offset + 22_999));
            assertFalse(StarSeaCalculationEnvironment.isNight(true, false, offset + 23_000));
        }
        assertTrue(StarSeaCalculationEnvironment.isNight(true, false, -11_000));
        assertTrue(StarSeaCalculationEnvironment.isNight(true, false, -1_001));
        assertFalse(StarSeaCalculationEnvironment.isNight(true, false, -1_000));
    }

    @Test
    void rawBrightnessSevenIsAllowedAndEightStopsProduction() {
        assertTrue(StarSeaCalculationEnvironment.isDarkEnough(0));
        assertTrue(StarSeaCalculationEnvironment.isDarkEnough(7));
        assertFalse(StarSeaCalculationEnvironment.isDarkEnough(8));
        assertFalse(StarSeaCalculationEnvironment.isDarkEnough(15));
    }
}
