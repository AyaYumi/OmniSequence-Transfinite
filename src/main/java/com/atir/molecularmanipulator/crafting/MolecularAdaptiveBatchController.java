package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.hooks.ticking.TickHandler;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Learns a safe dispatch window from complete input acceptance.
 *
 * <p>Every accepted complete recipe consumes one permit. Completing the current
 * permit window immediately opens a larger window, so slow or output-less
 * processing targets can still be filled continuously. A rejected push or a
 * provider-side remainder queue contracts the window. Server tick time and work
 * unit budgets remain the hard limits for how much probing happens at once.</p>
 */
public final class MolecularAdaptiveBatchController {
    private final Map<ICraftingProvider, Map<IPatternDetails, State>> states =
            new IdentityHashMap<>();
    private Object activeJob;

    public void setActiveJob(Object job) {
        if (activeJob == job) {
            return;
        }
        states.clear();
        activeJob = job;
    }

    public boolean hasState(ICraftingProvider provider, IPatternDetails patternDetails) {
        var providerStates = states.get(provider);
        return providerStates != null && providerStates.containsKey(patternDetails);
    }

    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) {
            return 0;
        }
        var state = getState(provider, patternDetails, 1);
        return state.isBlocked(currentTick()) ? 0 : state.availableCrafts();
    }

    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails,
            long initialWindow) {
        return getAvailableCrafts(provider, patternDetails, initialWindow, Long.MAX_VALUE);
    }

    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails,
            long initialWindow, long maxWindow) {
        if (provider == null || patternDetails == null
                || initialWindow <= 0 || maxWindow <= 0) {
            return 0;
        }

        var state = getState(provider, patternDetails, initialWindow);
        state.applyLimit(maxWindow);
        return state.isBlocked(currentTick()) ? 0 : state.availableCrafts();
    }

    public void onAccepted(ICraftingProvider provider, IPatternDetails patternDetails,
            long craftCount, boolean providerBusy) {
        if (provider == null || patternDetails == null || craftCount <= 0) {
            return;
        }

        var state = getState(provider, patternDetails, 1);
        state.remainingPermits = Math.max(0, state.remainingPermits - craftCount);

        if (providerBusy) {
            state.contractAndBlock(currentTick());
            return;
        }

        state.unblock();
        if (state.remainingPermits == 0) {
            state.grow();
        }
    }

    public void onRejected(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider == null || patternDetails == null) {
            return;
        }
        getState(provider, patternDetails, 1).contractAndBlock(currentTick());
    }

    private State getState(ICraftingProvider provider, IPatternDetails patternDetails,
            long initialWindow) {
        var providerStates = states.computeIfAbsent(provider, ignored -> new IdentityHashMap<>());
        return providerStates.computeIfAbsent(
                patternDetails, ignored -> new State(initialWindow));
    }

    private static long currentTick() {
        return TickHandler.instance().getCurrentTick();
    }

    private static long saturatingDouble(long value) {
        return value > Long.MAX_VALUE / 2 ? Long.MAX_VALUE : Math.max(1, value * 2);
    }

    private static final class State {
        private long window;
        private long remainingPermits;
        private long maxWindow = Long.MAX_VALUE;
        private long blockedTick = Long.MIN_VALUE;

        private State(long initialWindow) {
            window = Math.max(1, initialWindow);
            remainingPermits = window;
        }

        private void applyLimit(long limit) {
            maxWindow = Math.max(1, limit);
            window = Math.min(window, maxWindow);
            remainingPermits = Math.min(Math.max(1, remainingPermits), window);
        }

        private long availableCrafts() {
            return Math.max(1, Math.min(remainingPermits, maxWindow));
        }

        private void grow() {
            window = Math.min(maxWindow, saturatingDouble(window));
            remainingPermits = window;
        }

        private void contractAndBlock(long tick) {
            window = Math.max(1, window / 2);
            window = Math.min(window, maxWindow);
            remainingPermits = window;
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