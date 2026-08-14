package com.atir.molecularmanipulator.runtime;

/** Keeps malformed crafting pattern outputs out of AE2 division operations. */
public final class CraftingTreeOutputCountGuard {
    private CraftingTreeOutputCountGuard() {
    }

    public static long sanitize(long outputCount, Runnable rejectProcess) {
        if (outputCount > 0) {
            return outputCount;
        }
        rejectProcess.run();
        // AE2 checks process.possible immediately after this call. Returning a
        // non-zero sentinel additionally guarantees the later division is safe
        // if another transformer changes that branch ordering.
        return 1;
    }
}
