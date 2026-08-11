package com.atir.molecularmanipulator.crafting;

/**
 * Boundary checks for values that AE2 persists or transfers as signed longs.
 */
public final class CraftingPlanAmountLimits {
    private static final double FIRST_DOUBLE_ABOVE_LONG_MAX = 0x1.0p63;

    private CraftingPlanAmountLimits() {
    }

    public static boolean additionOverflows(long current, long addition) {
        return addition > 0 && current > Long.MAX_VALUE - addition
                || addition < 0 && current < Long.MIN_VALUE - addition;
    }

    /**
     * AE2 accumulates crafting bytes as a double and later narrows it to a long.
     * Once the sum reaches 2^63, narrowing silently clamps it to Long.MAX_VALUE,
     * making an oversized plan appear to fit exactly.
     */
    public static boolean bytesExceedLongCapacity(double current, double addition) {
        if (!Double.isFinite(current) || !Double.isFinite(addition)
                || current < 0 || addition < 0) {
            return true;
        }
        double sum = current + addition;
        return !Double.isFinite(sum) || sum >= FIRST_DOUBLE_ABOVE_LONG_MAX;
    }
}
