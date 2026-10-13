package com.atir.molecularmanipulator.diagnostics;

import com.atir.molecularmanipulator.MolecularManipulator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/** Shared across machines and CPUs; callers must use constant message templates. */
public final class RateLimitedLog {
    private static final Gate GATE = new Gate(TimeUnit.MINUTES.toNanos(1), System::nanoTime);

    private RateLimitedLog() {}

    public static void warn(String message, Object... arguments) {
        if (MolecularManipulator.LOGGER.isWarnEnabled() && GATE.allow(message)) {
            MolecularManipulator.LOGGER.warn(message, arguments);
        }
    }

    public static void error(String message, Object... arguments) {
        if (MolecularManipulator.LOGGER.isErrorEnabled() && GATE.allow(message)) {
            MolecularManipulator.LOGGER.error(message, arguments);
        }
    }

    public static void debug(String message, Object... arguments) {
        if (MolecularManipulator.LOGGER.isDebugEnabled() && GATE.allow(message)) {
            MolecularManipulator.LOGGER.debug(message, arguments);
        }
    }

    static final class Gate {
        private final Map<String, Long> lastEmission = new HashMap<>();
        private final long intervalNanos;
        private final LongSupplier clock;

        Gate(long intervalNanos, LongSupplier clock) {
            this.intervalNanos = intervalNanos;
            this.clock = clock;
        }

        synchronized boolean allow(String category) {
            long now = clock.getAsLong();
            var previous = lastEmission.get(category);
            if (previous != null && now - previous < intervalNanos) return false;
            lastEmission.put(category, now);
            return true;
        }
    }
}
