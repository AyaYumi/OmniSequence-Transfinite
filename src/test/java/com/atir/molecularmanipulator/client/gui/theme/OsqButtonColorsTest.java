package com.atir.molecularmanipulator.client.gui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqButtonColorsTest {
    @Test void invertedResearchOrderLabelReversesWithTheme() {
        for (var palette : OsqPalette.values()) {
            assertEquals(palette.invT, palette.controlText("button", "danger", true));
            assertEquals(palette.invT, palette.controlText("tab", "active", true));
            assertEquals(palette.disT, palette.controlText("button", "danger", false));
            assertEquals(palette.ink, palette.controlText("button", "normal", true));
        }
    }
}
