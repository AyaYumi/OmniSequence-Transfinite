package com.atir.molecularmanipulator.integration.useless;

import appeng.api.networking.IGrid;
import java.math.BigInteger;

/** Server-thread scope: only a verified sender serving this CPU may use the direct-return budget. */
public final class OmniDirectAdmission implements AutoCloseable {
    private static final ThreadLocal<OmniDirectAdmission> CURRENT = new ThreadLocal<>();
    private final OmniDirectAdmission previous;
    final IGrid grid;
    private final BigInteger ceiling;
    private BigInteger outputLimit;
    private Object manager;
    private OmniDirectAdmission(IGrid grid, BigInteger outputLimit) {
        this.previous = CURRENT.get(); this.grid = grid; this.ceiling = outputLimit; CURRENT.set(this);
    }
    public static OmniDirectAdmission open(IGrid grid) { return new OmniDirectAdmission(grid, null); }
    public static OmniDirectAdmission open(IGrid grid, BigInteger outputLimit) { return new OmniDirectAdmission(grid, outputLimit); }
    static OmniDirectAdmission current() { return CURRENT.get(); }
    static BigInteger limitRequested(appeng.api.crafting.IPatternDetails pattern, BigInteger requested) {
        var scope = CURRENT.get();
        if (scope == null || scope.manager == null || scope.outputLimit == null) return requested;
        var outputs = new java.util.HashMap<appeng.api.stacks.AEKey, BigInteger>();
        for (var output : pattern.getOutputs()) {
            if (output.amount() <= 0) return BigInteger.ZERO;
            outputs.merge(output.what(), BigInteger.valueOf(output.amount()), BigInteger::add);
        }
        // Same aggregate units used by the source backlog and return feedback.
        var perCraft = outputs.values().stream().reduce(BigInteger.ZERO, BigInteger::add);
        if (perCraft.signum() > 0) requested = requested.min(scope.outputLimit.divide(perCraft));
        return requested;
    }
    public static long segmentBudget(Object sender, long original) {
        var scope = CURRENT.get();
        if (scope == null || scope.manager != sender || scope.outputLimit == null) return original;
        return projectSegments(scope.outputLimit);
    }
    static long projectSegments(BigInteger amount) {
        var max = BigInteger.valueOf(Long.MAX_VALUE);
        // The native ABI uses a long segment budget; saturate the projection,
        // not the exact feedback window, and leave native hardware limits intact.
        return amount.add(max).subtract(BigInteger.ONE).divide(max).min(max).max(BigInteger.ONE).longValueExact();
    }
    @FunctionalInterface public interface ReflectiveCall<T> { T call() throws ReflectiveOperationException; }
    static <T> T withSender(Object sender, ReflectiveCall<T> call) throws ReflectiveOperationException {
        return withSender(sender, BigInteger.ONE, call);
    }
    static <T> T withSender(Object sender, BigInteger minimumBatch, ReflectiveCall<T> call) throws ReflectiveOperationException {
        var scope = CURRENT.get();
        if (scope == null) return call.call();
        Object old = scope.manager;
        BigInteger oldLimit = scope.outputLimit;
        try {
            scope.manager = scope.grid != null && UselessExactOutputReturn.isVerified(sender, scope.grid) ? sender : null;
            // Useless capacity(admit) is the source of truth. Do not impose a
            // separate Y-sized feedback window on the requested amount.
            scope.outputLimit = null;
            return call.call();
        } finally { scope.manager = old; scope.outputLimit = oldLimit; }
    }
    @Override public void close() { if (previous == null) CURRENT.remove(); else CURRENT.set(previous); }
}
