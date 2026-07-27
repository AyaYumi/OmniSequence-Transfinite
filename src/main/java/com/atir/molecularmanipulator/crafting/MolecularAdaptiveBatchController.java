package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.hooks.ticking.TickHandler;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider.PushResult;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Learns one safe, complete-recipe batch size for each job/provider/pattern.
 *
 * <p>The probe grows as {@code 1, 2, 4, ...} across server ticks while complete
 * batches are accepted. Each provider/pattern pair may advance at most one
 * growth step per tick; the doubled size only becomes available on the next
 * tick. After rejection, the last successful size remains the next tick's
 * baseline. Single-only compatibility dispatch keeps its separate per-tick
 * quota. A provider-owned remainder queue blocks further pushes until it
 * drains.</p>
 */
public final class MolecularAdaptiveBatchController {
    private final Map<ICraftingProvider, Map<IPatternDetails, State>> states =
            new IdentityHashMap<>();
    private Object activeJob;
    private long attemptSequence;

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
        state.applyLimit(maxWindow);
        return state.isBlocked(currentTick()) ? 0 : state.nextBatch();
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
        state.lastAccepted = acceptedCrafts;

        if (result == PushResult.ACCEPTED_QUEUED) {
            state.nextBatch = Math.min(state.maxWindow, Math.max(1, acceptedCrafts));
            state.probing = false;
            state.block(tick);
            return;
        }
        if (result == PushResult.ACCEPTED_UNVERIFIED) {
            state.nextBatch = 1;
            state.probing = false;
            state.block(tick);
            return;
        }
        if (result == PushResult.REJECTED) {
            onRejected(provider, patternDetails, requestedCrafts);
            return;
        }
        if (state.singleOnly) {
            state.nextBatch = 1;
            state.probing = false;
            state.unblock();
            return;
        }

        long doubled = saturatingDouble(acceptedCrafts);
        if (acceptedCrafts < requestedCrafts) {
            // Inventory or energy, rather than the target, limited this attempt.
            state.nextBatch = Math.min(state.maxWindow, Math.max(requestedCrafts, doubled));
            state.probing = false;
            state.block(tick);
        } else {
            state.nextBatch = Math.min(state.maxWindow, doubled);
            // Grow only across ticks. The next tick may try the doubled batch,
            // but this provider/pattern pair cannot immediately run 1, 2, 4, ...
            // in the same server tick.
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
        state.lastAccepted = 0;
        state.block(currentTick());
    }

    public void onRejected(ICraftingProvider provider, IPatternDetails patternDetails,
            long attemptedCrafts) {
        if (provider == null || patternDetails == null || attemptedCrafts <= 0) {
            return;
        }

        var state = getState(provider, patternDetails);
        state.lastAttemptOrder = nextAttemptOrder();
        if (attemptedCrafts > 1 && state.lastAccepted == 1) {
            state.enterSingleOnly();
            state.block(currentTick());
            return;
        }
        long next;
        if (state.lastAccepted > 0 && attemptedCrafts > state.lastAccepted) {
            next = state.lastAccepted;
        } else {
            next = Math.max(1, attemptedCrafts / 2);
        }
        state.nextBatch = Math.min(state.maxWindow, next);
        state.probing = false;
        state.block(currentTick());
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

    private static long currentTick() {
        return TickHandler.instance().getCurrentTick();
    }

    private static long saturatingDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : Math.max(1, value * 2);
    }

    private static final class State {
        private long nextBatch = 1;
        private long lastAccepted;
        private long maxWindow = Long.MAX_VALUE;
        private long blockedTick = Long.MIN_VALUE;
        private long lastAttemptOrder = Long.MIN_VALUE;
        private boolean probing = true;
        private boolean singleOnly;

        private void applyLimit(long limit) {
            maxWindow = Math.max(1, limit);
            nextBatch = Math.max(1, Math.min(nextBatch, maxWindow));
        }

        private long nextBatch() {
            return Math.max(1, Math.min(nextBatch, maxWindow));
        }

        private void enterSingleOnly() {
            singleOnly = true;
            nextBatch = 1;
            probing = false;
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
