package com.atir.molecularmanipulator.client.gui.theme;

/** Whole-row scrolling keeps item names, amounts and hover areas together. */
public final class OsqScrollList {
    private OsqScrollList() {}

    public static int visibleRows(int height, int rowHeight) {
        return Math.max(1, height / rowHeight);
    }

    public static int maxScroll(int rows, int height, int rowHeight) {
        return Math.max(0, rows - visibleRows(height, rowHeight)) * rowHeight;
    }

    public static int clamp(double scroll, int rows, int height, int rowHeight) {
        return Math.clamp((int)Math.round(scroll / rowHeight) * rowHeight,
                0, maxScroll(rows, height, rowHeight));
    }

    public static int scroll(double current, double wheel, int rows, int height, int rowHeight) {
        int steps = wheel == 0 ? 0 : (int)Math.copySign(Math.max(1, Math.ceil(Math.abs(wheel))), wheel);
        return clamp(current - steps * rowHeight, rows, height, rowHeight);
    }
}
