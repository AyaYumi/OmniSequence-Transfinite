package com.atir.molecularmanipulator.client;

/** A short two-click confirmation, with a debounce against accidental double clicks. */
final class StructureUpdateConfirmation {
    static final int TIMEOUT_TICKS = 100;
    static final int DELAY_TICKS = 10;
    private int remaining;

    boolean click() {
        if (remaining == 0) { remaining = TIMEOUT_TICKS; return false; }
        if (remaining > TIMEOUT_TICKS - DELAY_TICKS) return false;
        remaining = 0;
        return true;
    }

    void tick(boolean eligible) {
        if (!eligible) remaining = 0;
        else if (remaining > 0) remaining--;
    }

    void cancel() { remaining = 0; }
    boolean isArmed() { return remaining > 0; }
}
