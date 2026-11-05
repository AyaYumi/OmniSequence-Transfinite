package com.atir.molecularmanipulator.client.render;

/** Layout and clock for the manually sampled AE star-signal-flow GUI strip. */
public final class StarSignalAnimation {
    public static final int FRAME_SIZE = 16;
    public static final int FRAME_COUNT = 64;
    public static final int TEXTURE_HEIGHT = FRAME_SIZE * FRAME_COUNT;
    public static final int CYCLE_TICKS = 32;

    private StarSignalAnimation() {
    }

    /** Keep fractional ticks: four subpixel frames per pixel, at the original scroll speed. */
    public static int frameAt(double gameTicks) {
        if (!Double.isFinite(gameTicks)) {
            throw new IllegalArgumentException("Animation time must be finite");
        }
        double phase = gameTicks % CYCLE_TICKS;
        if (phase < 0) {
            phase += CYCLE_TICKS;
        }
        return Math.min(FRAME_COUNT - 1, (int) (phase * FRAME_COUNT / CYCLE_TICKS));
    }
}
