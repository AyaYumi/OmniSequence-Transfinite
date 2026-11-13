package com.atir.molecularmanipulator.world;

/** Shared timings for server travel, pad lighting and the travelling player's fade. */
public final class WarpAnimation {
    public static final int IDLE = 0;
    public static final int CHARGING = 1;
    public static final int ARRIVING = 2;
    public static final int CHARGE_TICKS = 28;
    public static final int ARRIVAL_TICKS = 16;
    public static final int FADE_TICKS = 6;

    private WarpAnimation() {}

    public static float ease(float progress) {
        float t = com.atir.molecularmanipulator.util.MathCompat.clamp(progress, 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static float keyLock(float age, int key) {
        return ease((age - 3 - key * 6) / 3);
    }

    public static boolean active(int phase, float age) {
        return age >= 0 && (phase == CHARGING && age < CHARGE_TICKS
                || phase == ARRIVING && age < ARRIVAL_TICKS);
    }

    public static float screenOpacity(int phase, float age) {
        return switch (phase) {
            case CHARGING -> .96F * ease((age - (CHARGE_TICKS - FADE_TICKS)) / FADE_TICKS);
            case ARRIVING -> .96F * (1 - ease(age / (FADE_TICKS + 2)));
            default -> 0;
        };
    }
}
