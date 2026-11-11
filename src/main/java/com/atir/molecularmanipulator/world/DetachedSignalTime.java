package com.atir.molecularmanipulator.world;

import java.util.random.RandomGenerator;

/** One shared time rate: alternate half speed and double speed after random global-tick intervals. */
public final class DetachedSignalTime {
    public static final int MIN_PHASE_TICKS = 100;
    public static final int MAX_PHASE_TICKS = 300;
    private final RandomGenerator random;
    private boolean accelerated;
    private int phaseTicksRemaining;
    private int halfTickCredit;

    public DetachedSignalTime(RandomGenerator random) {
        this.random = random;
        accelerated = random.nextBoolean();
        phaseTicksRemaining = nextDuration();
    }

    /** Fractional credit survives phase changes; simulation can advance zero, one or two ticks. */
    public int advance() {
        if (phaseTicksRemaining == 0) {
            accelerated = !accelerated;
            phaseTicksRemaining = nextDuration();
        }
        phaseTicksRemaining--;
        halfTickCredit += accelerated ? 4 : 1;
        int ticks = halfTickCredit / 2;
        halfTickCredit %= 2;
        return ticks;
    }

    public boolean accelerated() {
        return accelerated;
    }

    public float scale() {
        return accelerated ? 2F : .5F;
    }

    private int nextDuration() {
        return random.nextInt(MIN_PHASE_TICKS, MAX_PHASE_TICKS + 1);
    }
}
