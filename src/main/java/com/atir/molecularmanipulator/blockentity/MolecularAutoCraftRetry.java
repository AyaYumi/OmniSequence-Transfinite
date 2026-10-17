package com.atir.molecularmanipulator.blockentity;

/** Successful slots run every tick; full destinations retry after 5, 10, then 20 ticks. */
final class MolecularAutoCraftRetry {
    private long nextAttempt = Long.MIN_VALUE;
    private int delay = 5;

    boolean ready(long tick) {
        return tick >= nextAttempt;
    }

    void blocked(long tick) {
        nextAttempt = tick + delay;
        delay = Math.min(20, delay * 2);
    }

    void reset() {
        nextAttempt = Long.MIN_VALUE;
        delay = 5;
    }
}
