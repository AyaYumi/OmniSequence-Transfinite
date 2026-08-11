package com.atir.molecularmanipulator.storage;

/**
 * Sentinel and arithmetic helpers for storage providers whose extractable amount
 * is not bounded by their advertised contents.
 */
public final class InfiniteStorageAmounts {
    public static final long DISPLAY_AMOUNT = Long.MAX_VALUE;
    public static final String DISPLAY_TEXT = "9.2E";

    private InfiniteStorageAmounts() {
    }

    public static long probeAmount(long advertisedAmount, int amountPerUnit) {
        if (advertisedAmount < 0) {
            throw new IllegalArgumentException("Advertised storage amount must be non-negative");
        }
        if (amountPerUnit <= 0) {
            throw new IllegalArgumentException("Amount per unit must be positive");
        }

        long alignedMaximum = DISPLAY_AMOUNT - Math.floorMod(DISPLAY_AMOUNT, (long) amountPerUnit);
        return alignedMaximum > advertisedAmount ? alignedMaximum : DISPLAY_AMOUNT;
    }

    public static boolean isInfiniteResponse(
            long advertisedAmount, long requestedAmount, long extractedAmount) {
        return advertisedAmount == DISPLAY_AMOUNT
                || advertisedAmount > 0
                        && requestedAmount > advertisedAmount
                        && extractedAmount >= requestedAmount;
    }

    public static long mergeAvailable(
            long currentAmount, long additionalAmount, boolean infinite) {
        if (currentAmount < 0 || additionalAmount < 0) {
            throw new IllegalArgumentException("Available storage amounts must be non-negative");
        }
        if (infinite
                || currentAmount == DISPLAY_AMOUNT
                || additionalAmount == DISPLAY_AMOUNT
                || currentAmount > Long.MAX_VALUE - additionalAmount) {
            return DISPLAY_AMOUNT;
        }
        return currentAmount + additionalAmount;
    }
}
