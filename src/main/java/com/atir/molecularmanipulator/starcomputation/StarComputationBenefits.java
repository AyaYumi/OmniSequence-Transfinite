package com.atir.molecularmanipulator.starcomputation;

/** Immutable server/client capability snapshot. Level zero is the unassisted machine baseline. */
public record StarComputationBenefits(int level, boolean overclocked) {
    public static final StarComputationBenefits NONE = new StarComputationBenefits(0, false);
    private static final int[] THRESHOLDS = {0, 90, 270, 900, 1800, 4500, 9000, 18000, 45000, 90000};
    private static final int[] SPEEDS = {1, 2, 3, 4, 8, 12, 16, 32, 48, 64};

    public StarComputationBenefits {
        if (level < 0 || level > 9) throw new IllegalArgumentException("Entropy level must be 0..9");
        overclocked &= level > 0;
    }

    public static int levelFor(long entropy) {
        int level = 0;
        while (level < 9 && entropy >= THRESHOLDS[level + 1]) level++;
        return level;
    }

    public static int threshold(int level) { return THRESHOLDS[com.atir.molecularmanipulator.util.MathCompat.clamp(level, 0, 9)]; }

    /** High tiers replace low tiers, including their entire capped contribution. */
    public static long entropy(long base, long quantum, long terminal) {
        long first = Math.min(900, Math.min(Math.max(0, base), 90) * 10);
        long second = Math.min(9000, first + Math.min(Math.max(0, quantum), 90) * 100);
        return Math.min(90000, second + Math.min(Math.max(0, terminal), 90) * 1000);
    }

    public boolean infinite() { return level == 9 && overclocked; }
    public boolean virtualCpu() { return level >= 7; }
    private int multiplier() { return overclocked ? 2 : 1; }
    public long assemblerParallel() { return infinite() ? Long.MAX_VALUE : level == 0 ? 1 : (4096L << (2 * (level - 1))) * multiplier(); }
    public long cpuParallel() { return infinite() ? Long.MAX_VALUE : level == 0 ? 16 : (512L << (level - 1)) * multiplier(); }
    public long storageBytes() { return infinite() ? Long.MAX_VALUE : level == 0 ? 1024L * 1024 : (1L << (30 + 2 * (level - 1))) * multiplier(); }
    public int speedMultiplier() { return SPEEDS[level] * multiplier(); }
    public long infoPerTick() { return overclocked ? 1L << level : 0; }
    public int processingTicks(int baseTicks) {
        return infinite() ? 1 : Math.max(1, (int) (((long) Math.max(1, baseTicks) + speedMultiplier() - 1) / speedMultiplier()));
    }
}
