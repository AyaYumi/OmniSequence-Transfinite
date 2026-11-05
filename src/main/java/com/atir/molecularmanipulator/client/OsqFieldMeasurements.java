package com.atir.molecularmanipulator.client;

import java.util.Arrays;

/** One edit field's literal text geometry, independent of Minecraft and of live hint translations. */
final class OsqFieldMeasurements {
    @FunctionalInterface
    interface Measure { float width(String text); }

    private String value;
    private Object font, splitter;
    private long generation;
    private int scaleKey, display, cursor, width, end;
    private float[] widths = new float[0];
    private boolean[] measured = new boolean[0];
    private boolean laidOut;
    private String visibleText;
    private float cursorWidth;

    void layout(String value, int cursor, int display, int width, Object font, Object splitter,
            long generation, int scaleKey, Measure measure) {
        prepare(value, Math.clamp(display, 0, Math.min(cursor, value.length())), font, splitter, generation, scaleKey);
        if (laidOut && this.cursor == cursor && this.width == width) return;
        while (this.display < cursor && widthTo(cursor, measure) > width - 2)
            resetDisplay(value.offsetByCodePoints(this.display, 1));
        int end = this.display;
        while (end < value.length()) {
            int next = value.offsetByCodePoints(end, 1);
            if (widthTo(next, measure) > width) break;
            end = next;
        }
        this.end = end;
        this.cursor = cursor;
        this.width = width;
        visibleText = value.substring(this.display, end);
        cursorWidth = widthTo(Math.min(cursor, end), measure);
        laidOut = true;
    }

    /** Clicks use the last drawn display offset, without first scrolling/clipping to a new cursor. */
    int positionAt(String value, int display, double x, Object font, Object splitter,
            long generation, int scaleKey, Measure measure) {
        prepare(value, display, font, splitter, generation, scaleKey);
        int position = Math.min(display, value.length());
        while (position < value.length()) {
            int next = value.offsetByCodePoints(position, 1);
            if (widthTo(next, measure) > x) break;
            position = next;
        }
        return position;
    }

    int display() { return display; }
    int end() { return end; }
    String visibleText() { return visibleText; }
    float cursorWidth() { return cursorWidth; }

    /** Measure whole prefixes, not sums of glyph widths: shaping/kerning need not be additive. */
    float widthTo(int position, Measure measure) {
        if (!measured[position]) {
            widths[position] = measure.width(value.substring(display, position));
            measured[position] = true;
        }
        return widths[position];
    }

    private void prepare(String value, int display, Object font, Object splitter, long generation, int scaleKey) {
        if (!value.equals(this.value) || this.font != font || this.splitter != splitter
                || this.generation != generation || this.scaleKey != scaleKey) {
            this.value = value;
            this.font = font;
            this.splitter = splitter;
            this.generation = generation;
            this.scaleKey = scaleKey;
            if (widths.length < value.length() + 1) {
                widths = new float[value.length() + 1];
                measured = new boolean[widths.length];
            }
            resetDisplay(display);
        } else if (this.display != display) resetDisplay(display);
    }

    private void resetDisplay(int display) {
        this.display = display;
        Arrays.fill(measured, false);
        laidOut = false;
    }
}
