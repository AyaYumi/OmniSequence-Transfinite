package com.atir.molecularmanipulator.blockentity;

/** Per-machine recipe accounting, shared by every dispatch path on that machine. */
public final class MolecularAssemblerTickBudget {
    private long tick = Long.MIN_VALUE;
    private long consumed;

    public long remaining(long gameTime, long limit) {
        if (tick != gameTime) {
            tick = gameTime;
            consumed = 0;
        }
        return limit <= consumed ? 0 : limit - consumed;
    }

    /** Reserves a complete batch; a rejected batch changes no accounting. */
    public boolean tryConsume(long gameTime, long limit, long crafts) {
        if (crafts <= 0 || crafts > remaining(gameTime, limit)) {
            return false;
        }
        consumed += crafts; // Bounded by limit, including Long.MAX_VALUE.
        return true;
    }

    /** Only a fully rolled-back passive delivery may return its reservation. */
    public void refund(long gameTime, long crafts) {
        if (tick == gameTime && crafts > 0 && crafts <= consumed) {
            consumed -= crafts;
        }
    }
}
