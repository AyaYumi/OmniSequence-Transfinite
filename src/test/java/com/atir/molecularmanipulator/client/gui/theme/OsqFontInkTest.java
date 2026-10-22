package com.atir.molecularmanipulator.client.gui.theme;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OsqFontInkTest {
    @Test void chineseButtonInkHasEqualTopAndBottomSpace() throws Exception {
        try (var stream = Files.newInputStream(Path.of("src/main/resources/assets/molecularmanipulator/font/osq_sc_semibold.ttf"))) {
            var face = Font.createFont(Font.TRUETYPE_FONT, stream);
            for (float size : new float[]{5.4f, 6.2f, 6.4f, 7, 13}) {
                for (String text : new String[]{"装配总控", "方块投影", "结构未成型", "ME 网络", "0123456789"}) {
                    float baseline = 17 / 2f + OsqFontInk.centeredBaselineOffset(List.of(face), text, size);
                    var bounds = face.deriveFont(size).createGlyphVector(new FontRenderContext(null, true, true), text).getVisualBounds();
                    assertEquals(baseline + bounds.getMinY(), 17 - baseline - bounds.getMaxY(), .03, text);
                }
            }
        }
    }
}
