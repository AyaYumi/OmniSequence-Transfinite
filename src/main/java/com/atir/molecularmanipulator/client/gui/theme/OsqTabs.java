package com.atir.molecularmanipulator.client.gui.theme;

import net.minecraft.client.gui.GuiGraphics;

/** AE-style text tabs. The selected glass silhouette is drawn with its parent, before slots/items. */
public final class OsqTabs {
    public static final int TEXT_INSET = 5;
    private OsqTabs() {}

    public static float textCenter(float height) { return (height - 2) / 2; }

    public static int textColor(OsqPalette palette, String state) {
        return "disabled".equals(state) ? palette.disT : palette.ink;
    }

    public static void draw(GuiGraphics graphics, int x, int y, int width, int height,
            OsqPalette palette, String state, boolean selected, boolean joined) {
        // Repainting the selected parent here would cover native slots and live items.
        if (selected && joined) return;
        int border = palette.dark ? palette.ink4 : 0xFF696D88;
        int rim = palette.dark ? palette.disT : 0xFFADB0C4;
        int fill = selected ? palette.card : "disabled".equals(state) ? palette.dis
                : "hover".equals(state) ? palette.dark ? palette.slot : palette.sunk : palette.raise;
        // Reference-style neutral grey frame and face, with a closed pale inner
        // rim. No dark heavy frame, depressed lip or exterior bottom highlight.
        graphics.fill(x, y, x + width, y + height - 2, border);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 3, rim);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 4, fill);
    }
}
