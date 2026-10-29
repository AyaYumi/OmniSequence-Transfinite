package com.atir.molecularmanipulator.blockentity;

/** Converts completed work when a fabrication recipe's effective duration changes. */
final class MatterFabricationTiming {
    private MatterFabricationTiming() {}

    static int rescaleProgress(int progress, int previousDuration, int nextDuration) {
        int next = Math.max(1, nextDuration);
        if (progress <= 0) return 0;
        // Old manual saves did not store a duration. Keep their elapsed ticks, bounded to unfinished work.
        if (previousDuration <= 0) return Math.min(progress, next - 1);
        long completed = Math.min(progress, previousDuration - 1);
        return (int) Math.min(next - 1L, completed * next / previousDuration);
    }
}
