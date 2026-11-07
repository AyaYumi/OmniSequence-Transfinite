package com.atir.molecularmanipulator.client.gui.theme;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqButtonColorsTest {
    @Test void invertedResearchOrderLabelReversesWithTheme() {
        for (var palette : OsqPalette.values()) {
            assertEquals(palette.invT, palette.controlText("button", "danger", true));
            assertEquals(palette.invT, palette.controlText("tab", "active", true));
            assertEquals(palette.disT, palette.controlText("button", "danger", false));
            assertEquals(palette.dark ? palette.ink : 0xFFF2F2F2, palette.controlText("button", "normal", true));
            assertEquals(palette.dark ? palette.ink : 0xFF517497, palette.controlText("button", "hover", true));
            assertEquals(palette.disT, palette.controlText("button", "disabled", true));
        }
    }
}
