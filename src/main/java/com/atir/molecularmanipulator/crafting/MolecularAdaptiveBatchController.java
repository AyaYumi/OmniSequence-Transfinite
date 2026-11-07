package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.hooks.ticking.TickHandler;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider.PushResult;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Learns the number of ordinary, complete-recipe pushes a provider can accept per tick.
 *
 * <p>Initial probe rounds allow 1, 2, 4, ... additional calls in the same tick.
 * A busy or rejected delivery ends the round and retains the actual successful
 * call count as the rate for subsequent ticks. After 20 ticks, probes test that
 * baseline plus 1, 2, 4, ... until capacity is reached again. Neither a provider
 * interface nor a scaled pattern is required: every call retains AE2's ordinary
 * input, output and task accounting.</p>
 *
 * <p>Input availability and dispatch budgets only limit the caller's current
 * work; they do not establish machine capacity or discard a learned rate.</p>
 */
public final class MolecularAdaptiveBatchController {
    static final long RECOVERY_PROBE_INTERVAL_TICKS = 20L;

    private final Map<ICraftingProvider, Map<IPatternDetails, State>> states = new IdentityHashMap<>();
    private final LongSupplier tickSupplier;
    private Object activeJob;
    private long attemptSequence;

    public MolecularAdaptiveBatchController() {
        this(() -> TickHandler.instance().getCurrentTick());
    }

    MolecularAdaptiveBatchController(LongSupplier tickSupplier) {
        this.tickSupplier = Objects.requireNonNull(tickSupplier, "tickSupplier");
    }

    public void setActiveJob(Object job) {
        if (activeJob != job) {
            states.clear();
            attemptSequence = 0;
            activeJob = job;
        }
    }

    /** Remaining ordinary calls in the current probe round, bounded by current demand. */
    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails, long maxWindow) {
        if (provider == null || patternDetails == null || maxWindow <= 0) return 0;
        var state = getState(provider, patternDetails);
        var tick = currentTick();
        state.beginTick(tick);
        if (state.isBlocked(tick)) return 0;
        long target = state.singleOnly ? 1 : state.target;
        return Math.min(maxWindow, Math.max(0, target - state.acceptedThisTick));
    }

    public long getLastAttemptOrder(ICraftingProvider provider, IPatternDetails patternDetails) {
        return provider == null || patternDetails == null ? Long.MIN_VALUE
                : getState(provider, patternDetails).lastAttemptOrder;
    }

    public boolean isSingleOnly(ICraftingProvider provider, IPatternDetails patternDetails) {
        return provider != null && patternDetails != null && getState(provider, patternDetails).singleOnly;
    }

    public void forceSingle(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider != null && patternDetails != null) getState(provider, patternDetails).singleOnly = true;
    }

    /** acceptedCrafts counts successful ordinary calls, including calls using a native batch API. */
    public void onAccepted(ICraftingProvider provider, IPatternDetails patternDetails,
            long acceptedCrafts, long requestedCrafts, PushResult result) {
        if (provider == null || patternDetails == null || acceptedCrafts <= 0 || requestedCrafts <= 0 || result == null) return;
        if (result == PushResult.REJECTED) {
            onRejected(provider, patternDetails, requestedCrafts);
            return;
        }
        var tick = currentTick();
        var state = getState(provider, patternDetails);
        state.beginTick(tick);
        state.lastAttemptOrder = nextAttemptOrder();
        state.acceptedThisTick = saturatingAdd(state.acceptedThisTick, acceptedCrafts);
        if (result == PushResult.ACCEPTED_UNVERIFIED) state.singleOnly = true;
        if (result != PushResult.ACCEPTED_FULL) {
            state.reachCapacity(tick);
        } else if (!state.singleOnly && state.acceptedThisTick >= state.target && state.growing) {
            state.probeStep = saturatingDouble(state.probeStep);
            state.target = state.recovering ? saturatingAdd(state.probeBase, state.probeStep)
                    : saturatingAdd(state.target, state.probeStep);
        }
    }

    /** Accounting mismatches retain a single ordinary delivery per tick until the job changes. */
    public void onStatusVerification(ICraftingProvider provider, IPatternDetails patternDetails, boolean verified) {
        if (!verified && provider != null && patternDetails != null) {
            var state = getState(provider, patternDetails);
            state.singleOnly = true;
            state.blockedTick = currentTick();
        }
    }

    public void onRejected(ICraftingProvider provider, IPatternDetails patternDetails, long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) return;
        var state = getState(provider, patternDetails);
        var tick = currentTick();
        state.beginTick(tick);
        state.lastAttemptOrder = nextAttemptOrder();
        state.reachCapacity(tick);
    }

    /** Recheck after each successful original push, so native doubling can end probing immediately. */
    public void onBusy(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) return;
        var state = getState(provider, patternDetails);
        var tick = currentTick();
        state.beginTick(tick);
        state.lastAttemptOrder = nextAttemptOrder();
        if (state.acceptedThisTick > 0) state.reachCapacity(tick);
        else state.blockedTick = tick;
    }

    public void onProviderRejected(ICraftingProvider provider, IPatternDetails patternDetails, long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) return;
        try {
            if (provider.isBusy()) {
                onBusy(provider, patternDetails);
                return;
            }
        } catch (RuntimeException | LinkageError exception) {
            // The failed original push still ends this tick if availability cannot be queried.
        }
        onRejected(provider, patternDetails, attemptedCrafts);
    }

    private long nextAttemptOrder() {
        if (attemptSequence == Long.MAX_VALUE) {
            attemptSequence = 0;
            for (var providerStates : states.values()) {
                for (var state : providerStates.values()) state.lastAttemptOrder = Long.MIN_VALUE;
            }
        }
        return ++attemptSequence;
    }

    private State getState(ICraftingProvider provider, IPatternDetails patternDetails) {
        return states.computeIfAbsent(provider, ignored -> new IdentityHashMap<>())
                .computeIfAbsent(patternDetails, ignored -> new State());
    }

    private long currentTick() { return tickSupplier.getAsLong(); }

    private static long saturatingDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : value * 2;
    }

    private static long saturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static long rejectionBackoff(long value, int failures) {
        int divisor = failures <= 1 ? 8 : failures == 2 ? 4 : 2;
        long reduction = value / divisor + (value % divisor == 0 ? 0 : 1);
        return Math.max(1, value - reduction);
    }

    /** Largest reusable tool-pool aggregate supported after actual validation rejections. */
    public long getReusableWindowCap(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) return Long.MAX_VALUE;
        var state = getState(provider, patternDetails);
        return state.isBlocked(currentTick()) ? 0 : state.reusableWindowCap;
    }

    public void onReusableBusy(ICraftingProvider provider, IPatternDetails patternDetails) {
        onBusy(provider, patternDetails);
    }

    public void onReusableProviderRejected(ICraftingProvider provider, IPatternDetails patternDetails, long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) return;
        try {
            if (provider.isBusy()) {
                onReusableBusy(provider, patternDetails);
                return;
            }
        } catch (RuntimeException | LinkageError exception) {
            // A failed validation still reduces the reusable window if busy cannot be queried.
        }
        onReusableRejected(provider, patternDetails, attemptedCrafts);
    }

    public void onReusableRejected(ICraftingProvider provider, IPatternDetails patternDetails, long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) return;
        var state = getState(provider, patternDetails);
        var tick = currentTick();
        if (state.lastReusableRejectionTick == tick && state.isBlocked(tick)) return;
        state.reusableRejections = Math.min(3, state.reusableRejections + 1);
        state.lastReusableRejectionTick = tick;
        state.reusableWindowCap = rejectionBackoff(Math.min(state.reusableWindowCap, attemptedCrafts), state.reusableRejections);
        state.blockedTick = tick;
    }

    public void onReusableAccepted(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider != null && patternDetails != null) getState(provider, patternDetails).reusableRejections = 0;
    }

    private static final class State {
        private long tick = Long.MIN_VALUE;
        private long acceptedThisTick;
        private long target = 1;
        private long baseline = 1;
        private long probeBase;
        private long probeStep = 1;
        private long nextProbeTick = Long.MAX_VALUE;
        private long blockedTick = Long.MIN_VALUE;
        private long lastAttemptOrder = Long.MIN_VALUE;
        private boolean capacityKnown;
        private boolean growing = true;
        private boolean recovering;
        private boolean singleOnly;
        private long reusableWindowCap = Long.MAX_VALUE;
        private long lastReusableRejectionTick = Long.MIN_VALUE;
        private int reusableRejections;

        private void beginTick(long currentTick) {
            if (tick == currentTick) return;
            tick = currentTick;
            acceptedThisTick = 0;
            if (capacityKnown && !growing) {
                target = baseline;
                if (currentTick >= nextProbeTick) {
                    growing = true;
                    recovering = true;
                    probeBase = baseline;
                    probeStep = 1;
                    target = saturatingAdd(probeBase, probeStep);
                }
            }
        }

        private void reachCapacity(long currentTick) {
            if (isBlocked(currentTick)) return;
            if (acceptedThisTick > 0) baseline = acceptedThisTick;
            capacityKnown = true;
            growing = false;
            recovering = false;
            target = baseline;
            nextProbeTick = saturatingAdd(currentTick, RECOVERY_PROBE_INTERVAL_TICKS);
            blockedTick = currentTick;
        }

        private boolean isBlocked(long currentTick) { return blockedTick == currentTick; }
    }
}
