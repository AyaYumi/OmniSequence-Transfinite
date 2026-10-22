package com.atir.molecularmanipulator.integration.eco;

import java.math.BigInteger;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Makes task-ID progress available while ECO constructs/rebuilds a runtime, before attachment. */
public final class EcoOmniPlanRuntimeScope {
    private static final ThreadLocal<IntFunction<BigInteger>> REMAINING = new ThreadLocal<>();
    private EcoOmniPlanRuntimeScope() {}
    public static IntFunction<BigInteger> remaining() { return REMAINING.get(); }
    public static <T> T withRemaining(IntFunction<BigInteger> remaining, Supplier<T> action) {
        var previous = REMAINING.get();
        REMAINING.set(remaining);
        try { return action.get(); }
        finally { if (previous == null) REMAINING.remove(); else REMAINING.set(previous); }
    }
}
