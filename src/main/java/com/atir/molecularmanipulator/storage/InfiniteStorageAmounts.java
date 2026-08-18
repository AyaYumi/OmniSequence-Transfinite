package com.atir.molecularmanipulator.storage;

/** Display constants for explicitly supported infinite storage cells. */
public final class InfiniteStorageAmounts {
    /** The largest amount AE2 can represent without overflowing a signed long. */
    public static final long DISPLAY_AMOUNT = Long.MAX_VALUE;

    /** AE2's compact item-count representation of {@link Long#MAX_VALUE}. */
    public static final String DISPLAY_TEXT = "9.2E";

    /** AE2's large-font slot representation of {@link Long#MAX_VALUE}. */
    public static final String LARGE_FONT_DISPLAY_TEXT = "9E";

    private InfiniteStorageAmounts() {
    }
}
