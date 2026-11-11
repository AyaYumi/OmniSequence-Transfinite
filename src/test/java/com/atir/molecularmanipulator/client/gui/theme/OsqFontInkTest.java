package com.atir.molecularmanipulator.client.gui.theme;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.UiRenderRecorder;
import com.atir.molecularmanipulator.client.VanillaFontTestFixture;
import java.util.List;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

class OsqFontInkTest {
    @Test void chineseLatinAndMixedButtonCellsHaveEqualTopAndBottomSpace() throws Exception {
        try (var runtime = new VanillaFontTestFixture("zh_cn")) {
            var face = VanillaFontTestFixture.DEFAULT_FONT;
            for (float size : new float[]{5.4f, 6.2f, 6.4f, 7, 7.5f, 8, 9, 13}) {
                for (String text : new String[]{"装配总控", "方块投影", "结构未成型", "ME 网络", "0123456789"}) {
                    var value = Component.literal(text);
                    float offset = OsqFonts.centeredBaselineOffset(value, face, size);
                    assertEquals(3 * size / 9, offset, .001f, text);
                    float baseline = 17 / 2f + offset;
                    var graphics = new UiRenderRecorder();
                    OsqFonts.draw(graphics, runtime.font, value, face, size, 0, baseline, 0xFFFFFFFF, 0);
                    assertEquals(1, graphics.text.size());
                    var draw = graphics.text.getFirst();
                    float scale = draw.transform().m11();
                    assertEquals(size / runtime.font.lineHeight, scale, .001f, text);
                    assertEquals(baseline - 7 * scale, draw.transform().m31(), .001f, text);
                    float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
                    for (int codepoint : text.codePoints().toArray()) {
                        var glyph = runtime.findGlyph(face, codepoint);
                        assertNotNull(glyph, text + " U+" + Integer.toHexString(codepoint));
                        glyph.bake(sheet -> {
                            bounds[0] = Math.min(bounds[0], draw.transform().m31() + sheet.getTop() * scale);
                            bounds[1] = Math.max(bounds[1], draw.transform().m31() + sheet.getBottom() * scale);
                            return null;
                        });
                    }
                    assertTrue(Float.isFinite(bounds[0]) && Float.isFinite(bounds[1]), text);
                    assertEquals(bounds[0], 17 - bounds[1], .03f, text);
                    assertEquals(8 * size / 9, bounds[1] - bounds[0], .001f, text);
                }
            }
        }
    }

    @Test void widthAndWrappingUseExplicitSizeRatherThanAFontIdEncodedSizeOrSharedCacheEntry() throws Exception {
        try (var runtime = new VanillaFontTestFixture("zh_cn")) {
            var face = VanillaFontTestFixture.DEFAULT_FONT;
            String text = "ME 网络 0123456789";
            float nativeWidth = runtime.font.getSplitter().stringWidth(Component.literal(text));
            for (float size : new float[]{7.5f, 13, 8, 9, 7.5f}) {
                assertEquals(nativeWidth * size / 9, OsqFonts.widthF(runtime.font, text, face, size), .001f);
            }
            assertEquals(nativeWidth * 7.5f / 9, OsqFonts.widthF(runtime.font, text, face), .001f,
                    "The convenience width remains a 7.5px body measurement");
            assertEquals(List.of("AB AB", "AB"), OsqFonts.wrap(runtime.font, "AB AB AB", face, 7.5f, 34));
            assertEquals(List.of("AB", "AB", "AB"), OsqFonts.wrap(runtime.font, "AB AB AB", face, 13, 34));
            assertEquals(List.of("AB AB", "AB"), OsqFonts.wrap(runtime.font, "AB AB AB", face, 7.5f, 34),
                    "Wrapping the same text again must not reuse another size's cached lines");
        }
    }
}
