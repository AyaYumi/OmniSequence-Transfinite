package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.hooks.ticking.TickHandler;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider.PushResult;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * Learns one safe, complete-recipe batch size for each job/provider/pattern.
 *
 * <p>The probe grows as {@code 1, 2, 4, ...} across server ticks while complete
 * batches are accepted. Growth and stable dispatch are separate: at most one
 * growth step is admitted per provider/pattern pair per tick, while a verified
 * batch may be repeated in the same tick only when the provider explicitly
 * supports repeated scaled delivery ({@link MolecularScaledBatchProvider} and
 * its {@code supportsScaledBatch} contract). Ordinary providers still receive
 * at most one adaptive push per tick. Queued, unverified and rejected pushes
 * keep their same-tick backpressure.</p>
 *
 * <p>A rejection records a transient upper bound. Repeated successful batches
 * below that bound recover with small, spaced probes instead of causing a
 * success/failure doubling oscillation. A failed growth probe retains the
 * last verified size; rejection at that size backs off by 1/8, then 1/4 and
 * finally 1/2 on consecutive failed ticks. Because rejection is not proof of
 * a capacity limit, the bound expires after
 * {@link #TRANSIENT_FAILURE_COOLDOWN_TICKS}, without resuming doubling.
 * A separately forced single-only decision, such as a status-accounting
 * mismatch, stays permanent until the active job changes.</p>
 */
public final class MolecularAdaptiveBatchController {
    /**
     * Number of server ticks a rejection-derived upper bound remains in force.
     * After expiry the next verified batch may resume the growth probe.
     */
    static final long TRANSIENT_FAILURE_COOLDOWN_TICKS = 200L;
    static final long RECOVERY_PROBE_INTERVAL_TICKS = 20L;

    private final Map<ICraftingProvider, Map<IPatternDetails, State>> states =
            new IdentityHashMap<>();
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
        if (activeJob == job) {
            return;
        }
        states.clear();
        attemptSequence = 0;
        activeJob = job;
    }

    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails,
            long maxWindow) {
        if (provider == null || patternDetails == null || maxWindow <= 0) {
            return 0;
        }

        var state = getState(provider, patternDetails);
        var tick = currentTick();
        state.applyLimit(maxWindow);
        state.expireTransientFailure(tick);
        if (state.isBlocked(tick)) {
            return 0;
        }
        if (state.lastAcceptedTick == tick
                && (!state.repeatVerified || !supportsStableRepeat(provider, patternDetails))) {
            // The target path can change after a successful push in this tick.
            // An earlier capability result cannot authorize another delivery.
            state.repeatVerified = false;
            state.block(tick);
            return 0;
        }
        if (state.singleOnly) {
            return 1;
        }

        long candidate = state.effectiveNextBatch(tick);
        if (candidate > state.stableBatch && state.lastGrowthTick == tick) {
            // This tick already consumed its one growth step for this pair.
            // Only a previously verified full batch may be repeated now.
            if (!state.repeatVerified || state.stableBatch <= 0) {
                return 0;
            }
            return Math.max(1, Math.min(state.stableBatch, state.maxWindow));
        }
        return Math.max(1, candidate);
    }

    public long getLastAttemptOrder(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) {
            return Long.MIN_VALUE;
        }
        return getState(provider, patternDetails).lastAttemptOrder;
    }

    public boolean isSingleOnly(ICraftingProvider provider, IPatternDetails patternDetails) {
        return provider != null && patternDetails != null
                && getState(provider, patternDetails).singleOnly;
    }

    public void forceSingle(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) {
            return;
        }
        var state = getState(provider, patternDetails);
        state.enterSingleOnly();
        state.unblock();
    }

    public void onAccepted(ICraftingProvider provider, IPatternDetails patternDetails,
            long acceptedCrafts, long requestedCrafts, PushResult result) {
        if (provider == null || patternDetails == null
                || acceptedCrafts <= 0 || requestedCrafts <= 0 || result == null) {
            return;
        }

        var tick = currentTick();
        var state = getState(provider, patternDetails);
        state.lastAttemptOrder = nextAttemptOrder();
        state.lastAcceptedTick = tick;
        if (result != PushResult.REJECTED) {
            // Accepted ownership ends a run of failed deliveries even when it
            // cannot establish a verified stable size or authorize repeats.
            state.consecutiveRejections = 0;
        }

        if (result == PushResult.ACCEPTED_QUEUED) {
            // The provider still owns a remainder queue. Do not repeat within
            // this tick and do not advertise the queued batch as a stable one.
            state.repeatVerified = false;
            state.nextBatch = state.capByTransientFailure(
                    Math.max(1, acceptedCrafts));
            state.block(tick);
            return;
        }
        if (result == PushResult.ACCEPTED_UNVERIFIED) {
            // Ownership/accounting could not be verified. Forget the learned
            // stable size and regrow conservatively from one.
            state.stableBatch = 0;
            state.nextBatch = 1;
            state.repeatVerified = false;
            state.block(tick);
            return;
        }
        if (result == PushResult.REJECTED) {
            onRejected(provider, patternDetails, requestedCrafts);
            return;
        }

        if (state.singleOnly) {
            state.nextBatch = 1;
            state.repeatVerified = supportsStableRepeat(provider, patternDetails);
            if (state.repeatVerified) {
                state.unblock();
            } else {
                state.block(tick);
            }
            return;
        }

        long previousStable = state.stableBatch;
        if (acceptedCrafts > previousStable) {
            state.stableBatch = acceptedCrafts;
            state.lastGrowthTick = tick;
            if (state.recovering) {
                state.nextRecoveryProbeTick = saturatingAdd(tick, RECOVERY_PROBE_INTERVAL_TICKS);
            }
        } else {
            state.stableBatch = Math.max(previousStable, acceptedCrafts);
        }

        // A success at or above the remembered failure bound disproves the
        // transient limit immediately; no cooldown is needed in that case.
        if (acceptedCrafts >= state.transientFailureLimit) {
            state.clearTransientFailure();
        }

        if (state.recovering) {
            // Congestion recovery must not return to exponential probing just
            // because a timer expired or a smaller batch worked.
            state.nextBatch = state.recoveryTarget(state.stableBatch);
        } else {
            // Keep a learned probe target even when a transient maxWindow was
            // smaller than this accepted batch. getAvailableCrafts applies the
            // current window without destroying the learned target.
            state.nextBatch = max(requestedCrafts,
                    saturatingDouble(acceptedCrafts),
                    saturatingDouble(state.stableBatch));
        }

        state.repeatVerified = supportsStableRepeat(provider, patternDetails);
        if (state.repeatVerified) {
            state.unblock();
        } else {
            state.block(tick);
        }
    }

    /**
     * AE's waiting-for counter is a secondary bookkeeping signal. It is not a
     * machine-capacity probe, but a mismatch means this provider/pattern pair can
     * no longer be trusted for scaled accounting.
     */
    public void onStatusVerification(ICraftingProvider provider, IPatternDetails patternDetails,
            boolean verified) {
        if (verified || provider == null || patternDetails == null) {
            return;
        }
        var state = getState(provider, patternDetails);
        state.enterSingleOnly();
        state.block(currentTick());
    }

    public void onRejected(ICraftingProvider provider, IPatternDetails patternDetails,
            long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) {
            return;
        }

        var tick = currentTick();
        var state = getState(provider, patternDetails);
        state.lastAttemptOrder = nextAttemptOrder();
        state.repeatVerified = false;
        if (state.lastRejectionTick == tick && state.isBlocked(tick)) {
            return;
        }
        state.recovering = true;
        state.consecutiveRejections = Math.min(3, state.consecutiveRejections + 1);
        state.lastRejectionTick = tick;
        state.nextRecoveryProbeTick = saturatingAdd(tick, RECOVERY_PROBE_INTERVAL_TICKS);
        state.rememberTransientFailure(attemptedCrafts, tick);

        long fallback;
        if (state.stableBatch > 0 && attemptedCrafts > state.stableBatch) {
            fallback = state.stableBatch;
        } else {
            fallback = rejectionBackoff(attemptedCrafts, state.consecutiveRejections);
            if (state.stableBatch > fallback) {
                // A failure at or below the historical stable size means that
                // size is not currently trusted. Recover from the reduced
                // fallback instead of jumping straight back to the old probe.
                state.stableBatch = fallback;
            }
        }
        state.nextBatch = state.capByTransientFailure(fallback);
        state.block(tick);
    }

    /** Busy is backpressure, not evidence that the batch is too large. */
    public void onBusy(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider != null && patternDetails != null) {
            var state = getState(provider, patternDetails);
            state.lastAttemptOrder = nextAttemptOrder();
            state.block(currentTick());
        }
    }

    /** Recheck busy after a rejected push; availability can change since selection. */
    public void onProviderRejected(ICraftingProvider provider, IPatternDetails patternDetails,
            long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) {
            return;
        }
        try {
            if (provider.isBusy()) {
                onBusy(provider, patternDetails);
                return;
            }
        } catch (RuntimeException | LinkageError exception) {
            // An unavailable busy signal cannot authorize a retry this tick.
            // Retain the ordinary conservative rejection feedback instead.
        }
        onRejected(provider, patternDetails, attemptedCrafts);
    }

    private long nextAttemptOrder() {
        if (attemptSequence == Long.MAX_VALUE) {
            attemptSequence = 0;
            for (var providerStates : states.values()) {
                for (var state : providerStates.values()) {
                    state.lastAttemptOrder = Long.MIN_VALUE;
                }
            }
        }
        return ++attemptSequence;
    }

    private State getState(ICraftingProvider provider, IPatternDetails patternDetails) {
        var providerStates = states.computeIfAbsent(provider, ignored -> new IdentityHashMap<>());
        return providerStates.computeIfAbsent(patternDetails, ignored -> new State());
    }

    private long currentTick() {
        return tickSupplier.getAsLong();
    }

    private static boolean supportsStableRepeat(ICraftingProvider provider,
            IPatternDetails patternDetails) {
        if (!(provider instanceof MolecularScaledBatchProvider scaledProvider)) {
            // Unknown providers do not explicitly promise that two scaled
            // pushes in one tick are independent and safe.
            return false;
        }
        try {
            return scaledProvider.molecularmanipulator$supportsScaledBatch(patternDetails);
        } catch (RuntimeException | LinkageError exception) {
            // Unknown capability must not authorize repeated delivery.
            return false;
        }
    }

    private static long saturatingDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : Math.max(1, value * 2);
    }

    private static long recoveryGrowth(long value) {
        return saturatingAdd(value, Math.max(1, value / 16));
    }

    private static long rejectionBackoff(long value, int failures) {
        int divisor = failures <= 1 ? 8 : failures == 2 ? 4 : 2;
        long reduction = value / divisor + (value % divisor == 0 ? 0 : 1);
        return Math.max(1, value - reduction);
    }

    private static long saturatingAdd(long value, long increment) {
        return value > Long.MAX_VALUE - increment ? Long.MAX_VALUE : value + increment;
    }

    private static long max(long first, long second, long third) {
        return Math.max(first, Math.max(second, third));
    }

    /**
     * Largest reusable (tool-pool) batch this provider/pattern pair is currently
     * trusted with. Starts unbounded and only shrinks after a real rejection, so
     * a single rejected aggregate no longer disables aggregation for the whole
     * crafting job.
     */
    public long getReusableWindowCap(ICraftingProvider provider,
            IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) {
            return Long.MAX_VALUE;
        }
        var state = getState(provider, patternDetails);
        return state.isBlocked(currentTick()) ? 0 : state.reusableWindowCap;
    }

    /**
     * The provider already owns a batch, so this refusal says nothing about the
     * requested size. Only suspend the pair for the rest of the tick instead of
     * shrinking a window that was never actually tested.
     */
    public void onReusableBusy(ICraftingProvider provider,
            IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) {
            return;
        }
        onBusy(provider, patternDetails);
    }

    public void onReusableProviderRejected(ICraftingProvider provider,
            IPatternDetails patternDetails, long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) {
            return;
        }
        try {
            if (provider.isBusy()) {
                onReusableBusy(provider, patternDetails);
                return;
            }
        } catch (RuntimeException | LinkageError exception) {
            // Unknown availability retains conservative rejection feedback.
        }
        onReusableRejected(provider, patternDetails, attemptedCrafts);
    }

    public void onReusableRejected(ICraftingProvider provider,
            IPatternDetails patternDetails, long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) {
            return;
        }
        var state = getState(provider, patternDetails);
        var tick = currentTick();
        if (state.lastReusableRejectionTick == tick && state.isBlocked(tick)) {
            return;
        }
        state.reusableRejections = Math.min(3, state.reusableRejections + 1);
        state.lastReusableRejectionTick = tick;
        long reference = Math.min(state.reusableWindowCap, attemptedCrafts);
        state.reusableWindowCap = rejectionBackoff(reference, state.reusableRejections);
        state.block(tick);
    }

    public void onReusableAccepted(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider != null && patternDetails != null) {
            getState(provider, patternDetails).reusableRejections = 0;
        }
    }

    private static final class State {
        /** Next probe target; may exceed the transient maxWindow. */
        private long nextBatch = 1;
        /** Last fully accepted batch size that may be safely repeated. */
        private long stableBatch;
        private long maxWindow = Long.MAX_VALUE;
        private long reusableWindowCap = Long.MAX_VALUE;
        private long lastReusableRejectionTick = Long.MIN_VALUE;
        private int reusableRejections;
        private long blockedTick = Long.MIN_VALUE;
        private long lastAttemptOrder = Long.MIN_VALUE;
        private long lastGrowthTick = Long.MIN_VALUE;
        private long lastAcceptedTick = Long.MIN_VALUE;
        private long transientFailureLimit = Long.MAX_VALUE;
        private long transientFailureExpiresTick = Long.MIN_VALUE;
        private long nextRecoveryProbeTick = Long.MIN_VALUE;
        private long lastRejectionTick = Long.MIN_VALUE;
        private int consecutiveRejections;
        private boolean recovering;
        private boolean transientFailureActive;
        private boolean repeatVerified;
        private boolean singleOnly;

        private void applyLimit(long limit) {
            // A temporary lack of materials must not erase the learned target.
            // nextBatch is clamped only while reading it in getAvailableCrafts.
            maxWindow = Math.max(1, limit);
        }

        private long effectiveNextBatch(long tick) {
            long target = nextBatch;
            if (recovering && tick < nextRecoveryProbeTick && stableBatch > 0) {
                target = Math.min(target, stableBatch);
            }
            return Math.max(1, Math.min(capByTransientFailure(target), maxWindow));
        }

        private long recoveryTarget(long base) {
            long target = recoveryGrowth(base);
            if (!transientFailureActive) {
                // A cooldown grants one recheck of the observed boundary, not
                // permission to forget it and binary-search the same gap again.
                return Math.min(target, transientFailureLimit);
            }
            long ceiling = Math.max(1, transientFailureLimit - 1);
            if (ceiling <= base) {
                return ceiling;
            }
            // Narrow the remaining safe/failed interval rather than testing
            // a near-identical oversized batch on every recovery probe.
            long midpoint = base + Math.max(1, (ceiling - base) / 2);
            return Math.min(target, midpoint);
        }

        private void rememberTransientFailure(long attemptedCrafts, long tick) {
            long limit = transientFailureActive
                    ? Math.min(transientFailureLimit, attemptedCrafts)
                    : attemptedCrafts;
            transientFailureLimit = Math.max(1, limit);
            transientFailureActive = true;
            transientFailureExpiresTick = saturatingAdd(
                    tick, TRANSIENT_FAILURE_COOLDOWN_TICKS);
        }

        private long capByTransientFailure(long value) {
            long positive = Math.max(1, value);
            if (!transientFailureActive) {
                return positive;
            }
            return Math.min(positive, Math.max(1, transientFailureLimit - 1));
        }

        private void expireTransientFailure(long tick) {
            if (!transientFailureActive || tick < transientFailureExpiresTick) {
                return;
            }
            transientFailureActive = false;
            transientFailureExpiresTick = Long.MIN_VALUE;
            if (singleOnly || stableBatch <= 0) {
                // An unverified delivery must still restart from one and earn
                // a full acceptance before any growth, even at cooldown expiry.
                return;
            }
            // Keep the observed boundary until a full acceptance disproves it.
            // Near capacity expiry rechecks just that boundary, rather than
            // repeatedly opening a fresh 6.25% overshoot and searching it again.
            long base = stableBatch > 0 ? stableBatch : Math.max(1, nextBatch);
            nextBatch = recoveryTarget(base);
        }

        private void clearTransientFailure() {
            transientFailureActive = false;
            transientFailureLimit = Long.MAX_VALUE;
            transientFailureExpiresTick = Long.MIN_VALUE;
        }

        private void enterSingleOnly() {
            singleOnly = true;
            nextBatch = 1;
            repeatVerified = false;
        }

        private void block(long tick) {
            blockedTick = tick;
        }

        private boolean isBlocked(long tick) {
            return blockedTick == tick;
        }

        private void unblock() {
            blockedTick = Long.MIN_VALUE;
        }
    }
}
