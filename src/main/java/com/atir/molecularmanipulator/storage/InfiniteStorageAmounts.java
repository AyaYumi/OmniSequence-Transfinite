package com.atir.molecularmanipulator.storage;

/**
 * Display sentinel and arithmetic helpers for storage providers whose
 * extractable amount is not bounded by their advertised contents.
 */
public final class InfiniteStorageAmounts {
    /** The largest amount AE2 can represent without overflowing a signed long. */
    public static final long DISPLAY_AMOUNT = Long.MAX_VALUE;

    /** AE2's compact item-count representation of {@link Long#MAX_VALUE}. */
    public static final String DISPLAY_TEXT = "9.2E";

    /** AE2's large-font slot representation of {@link Long#MAX_VALUE}. */
    public static final String LARGE_FONT_DISPLAY_TEXT = "9E";

    private InfiniteStorageAmounts() {
    }

    /**
     * Selects the largest positive probe that is aligned to the key's storage
     * unit. If no aligned value is larger than the advertised amount, the long
     * maximum itself is used so the probe still exceeds that advertisement.
     */
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

    /** Adds a mounted inventory's availability without allowing signed overflow. */
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
