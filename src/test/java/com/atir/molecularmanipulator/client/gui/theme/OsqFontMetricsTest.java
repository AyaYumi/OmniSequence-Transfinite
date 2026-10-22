package com.atir.molecularmanipulator.client.gui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqFontMetricsTest {
    @Test void readsSizeAfterFamilyAndWeight() {
        assertEquals(6.4f, OsqFontMetrics.providerSize("osq_nsc_600_6_4", 8));
        assertEquals(26, OsqFontMetrics.providerSize("osq_intertight_700_26", 8));
        assertEquals(5.4f, OsqFontMetrics.providerSize("osq_inter_500_5_4", 8));
        assertEquals(8, OsqFontMetrics.providerSize("default", 8));
        assertEquals(8, OsqFontMetrics.providerSize("osq_nsc_600_NaN", 8));
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
        assertEquals(7, OsqFontMetrics.baseline(Float.POSITIVE_INFINITY));
        assertEquals(9, OsqFontMetrics.baseline(9));
    }
}
