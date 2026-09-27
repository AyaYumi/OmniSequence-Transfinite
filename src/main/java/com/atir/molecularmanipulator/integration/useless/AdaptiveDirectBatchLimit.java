package com.atir.molecularmanipulator.integration.useless;

import java.math.BigInteger;

/** Feedback window for one physical sender/grid. The amount has no fixed numeric ceiling. */
public final class AdaptiveDirectBatchLimit {
    private static final long GROWTH_INTERVAL = 5;
    private static final long IDLE_RESET = 200;
    private static final long RETURN_TARGET_TICKS = 2;
    private final BigInteger initial;
    private final long targetNanos;
    private BigInteger window;
    private BigInteger minimum = BigInteger.ONE;
    private long lastChange = Long.MIN_VALUE;
    private long lastActivity = Long.MIN_VALUE;

    public AdaptiveDirectBatchLimit(BigInteger initial, long targetNanos) {
        if (initial.signum() <= 0 || targetNanos <= 0) throw new IllegalArgumentException("Invalid adaptive return settings");
        this.initial = initial; this.window = initial; this.targetNanos = targetNanos;
    }

    public BigInteger window() { return window; }

    public BigInteger admission(long tick, BigInteger pending, long oldestAge, BigInteger minimumBatch) {
        if (pending.signum() < 0 || minimumBatch.signum() <= 0) throw new IllegalArgumentException("Invalid admission state");
        if (pending.signum() == 0 && lastActivity != Long.MIN_VALUE && tick - lastActivity > IDLE_RESET) {
            window = initial.max(minimum); lastChange = Long.MIN_VALUE;
        }
        // Smart-doubling tasks are indivisible. Never shrink below one real task
        // and then wait forever for a batch that cannot fit the window.
        minimum = minimum.max(minimumBatch);
        window = window.max(minimum);
        if (pending.signum() > 0 && (oldestAge > RETURN_TARGET_TICKS || pending.compareTo(window.shiftLeft(1)) >= 0)) {
            backoff(tick); return BigInteger.ZERO;
        }
        return window.min(window.shiftLeft(1).subtract(pending).max(BigInteger.ZERO));
    }

    public void returned(long tick, BigInteger delivered, long nanos, BigInteger pending, long oldestAge) {
        if (delivered.signum() < 0 || pending.signum() < 0 || nanos < 0) throw new IllegalArgumentException("Invalid return feedback");
        lastActivity = tick;
        if (nanos > targetNanos || oldestAge > RETURN_TARGET_TICKS || pending.compareTo(window) > 0) {
            backoff(tick); return;
        }
        if (delivered.signum() > 0 && pending.signum() == 0 && delivered.shiftLeft(1).compareTo(window) >= 0
                && (lastChange == Long.MIN_VALUE || tick - lastChange >= GROWTH_INTERVAL)) {
            window = window.shiftLeft(1); lastChange = tick;
        }
    }

    public void backoff(long tick) {
        if (lastChange == tick) return;
        window = window.shiftRight(1).max(minimum); lastChange = tick;
    }
}
