package com.atir.molecularmanipulator.client.gui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqFontMetricsTest {
    @Test void scalesVanillaNinePixelLineHeightToExplicitDesignUnits() {
        for (float size : new float[]{7.5f, 8, 9, 13, 15, 26}) {
            assertEquals(size / 9, OsqFontMetrics.scale(size, 9));
            assertEquals(size, 9 * OsqFontMetrics.scale(size, 9), .001f);
        }
        assertEquals(1, OsqFontMetrics.scale(9, 9));
    }

    @Test void scalesModernUiUniformFontSizeToDesignUnits() {
        assertEquals(.75f, OsqFontMetrics.scale(6, 8));
        assertEquals(3.25f, OsqFontMetrics.scale(26, 8));
        assertEquals(6 / 9.5f, OsqFontMetrics.scale(6, 9.5f));
        assertEquals(6, 8 * OsqFontMetrics.scale(6, 8));
        assertEquals(26, 8 * OsqFontMetrics.scale(26, 8));
    }

    @Test void rejectsInvalidExternalMetrics() {
        assertEquals(1, OsqFontMetrics.scale(6, 0));
        assertEquals(1, OsqFontMetrics.scale(6, Float.NaN));
        assertEquals(1, OsqFontMetrics.scale(6, Float.POSITIVE_INFINITY));
        assertEquals(1, OsqFontMetrics.scale(0, 9));
        assertEquals(1, OsqFontMetrics.scale(-1, 9));
        assertEquals(1, OsqFontMetrics.scale(Float.NaN, 9));
        assertEquals(1, OsqFontMetrics.scale(Float.POSITIVE_INFINITY, 9));
        assertEquals(7, OsqFontMetrics.baseline(Float.POSITIVE_INFINITY));
        assertEquals(7, OsqFontMetrics.baseline(Float.NaN));
        assertEquals(7, OsqFontMetrics.baseline(-1));
        assertEquals(7, OsqFontMetrics.baseline(33));
        assertEquals(0, OsqFontMetrics.baseline(0));
        assertEquals(7, OsqFontMetrics.baseline(7));
        assertEquals(9, OsqFontMetrics.baseline(9));
    }
}
