package com.atir.molecularmanipulator.client.gui.theme;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.List;

/** Measures actual glyph ink about the baseline; does not create an AWT window. */
final class OsqFontInk {
    private static final FontRenderContext CONTEXT = new FontRenderContext(null, true, true);
    private OsqFontInk() {}

    static float centeredBaselineOffset(List<Font> faces, String text, float size) {
        double top = Double.POSITIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < text.length();) {
            int cp = text.codePointAt(i); i += Character.charCount(cp);
            if (Character.isWhitespace(cp) || cp == 0xA7) continue;
            var face = faces.stream().filter(f -> f.canDisplay(cp)).findFirst().orElse(null);
            if (face == null) continue;
            var bounds = face.deriveFont(size).createGlyphVector(CONTEXT, new String(Character.toChars(cp))).getVisualBounds();
            if (!bounds.isEmpty()) {
                top = Math.min(top, bounds.getMinY()); bottom = Math.max(bottom, bounds.getMaxY());
            }
        }
        return Double.isFinite(top) && Double.isFinite(bottom) ? (float) -(top + bottom) / 2 : size * .375f;
    }
}
