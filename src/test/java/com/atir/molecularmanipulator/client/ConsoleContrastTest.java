package com.atir.molecularmanipulator.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConsoleContrastTest {
    @Test void activeTextHasReadableContrastOnAllConsoleSurfaces() {
        for (int background : new int[] {OmniUiTheme.CANVAS, OmniUiTheme.PANEL,
                OmniUiTheme.PANEL_INSET, OmniUiTheme.CONTROL, OmniUiTheme.SLOT, OmniUiTheme.SOFT_ACCENT}) {
            assertTrue(contrast(OmniUiTheme.PRIMARY_TEXT, background) >= 4.5);
            assertTrue(contrast(OmniUiTheme.MUTED_TEXT, background) >= 4.5);
        }
        assertTrue(contrast(OmniUiTheme.PRIMARY_TEXT, OmniUiTheme.PANEL) >= 7);
        assertTrue(contrast(OmniUiTheme.FIELD_TEXT, OmniUiTheme.FIELD_BACKGROUND) >= 7);
        assertTrue(contrast(OmniUiTheme.FIELD_HINT, OmniUiTheme.FIELD_BACKGROUND) >= 4.5);
        assertTrue(contrast(OmniUiTheme.HIGHLIGHT, OmniUiTheme.ACCENT) >= 4.5);
        assertTrue(contrast(OmniUiTheme.HIGHLIGHT, OmniUiTheme.QUANTITY_BACKGROUND) >= 10);
    }

    static double contrast(int foreground, int background) {
        double a = luminance(foreground), b = luminance(background);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int color) {
        return 0.2126 * channel(color >> 16) + 0.7152 * channel(color >> 8) + 0.0722 * channel(color);
    }

    private static double channel(int value) {
        double component = (value & 255) / 255.0;
        return component <= 0.04045 ? component / 12.92 : Math.pow((component + 0.055) / 1.055, 2.4);
    }
}
