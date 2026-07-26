package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.hooks.ticking.TickHandler;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Learns a safe in-flight craft window from real primary-output completion.
 *
 * <p>The controller starts with one bounded provider chunk, then grows from
 * real primary-output timing. Outputs returning in a tight burst produce an
 * aggressive growth step, while slow trickle completion holds the current
 * window. A provider that queues inputs, rejects a push, or loses an active job
 * causes the window to contract.</p>
 */
public final class MolecularAdaptiveBatchController {
    private final Map<ICraftingProvider, Map<IPatternDetails, State>> states =
            new IdentityHashMap<>();
    private final List<Flight> flights = new ArrayList<>();
    private Object activeJob;

    public void setActiveJob(Object job) {
        if (activeJob == job) {
            return;
        }
        abandonFlights();
        activeJob = job;
    }

    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails) {
        return getAvailableCrafts(provider, patternDetails, 1);
    }

    public long getAvailableCrafts(ICraftingProvider provider, IPatternDetails patternDetails,
            long initialWindow) {
        if (getPrimaryOutput(patternDetails) == null) {
            return 1;
        }

        var state = getState(provider, patternDetails, initialWindow);
        return Math.max(0, state.window - state.inFlight);
    }

    public void onAccepted(ICraftingProvider provider, IPatternDetails patternDetails,
            long craftCount, boolean providerBusy) {
        if (craftCount <= 0) {
            return;
        }

        var primaryOutput = getPrimaryOutput(patternDetails);
        if (primaryOutput == null) {
            return;
        }

        long outputAmount;
        try {
            outputAmount = Math.multiplyExact(primaryOutput.amount(), craftCount);
        } catch (ArithmeticException exception) {
            return;
        }
        if (outputAmount <= 0) {
            return;
        }

        var state = getState(provider, patternDetails);
        if (state.inFlight == 0) {
            state.beginCycle(TickHandler.instance().getCurrentTick());
        }
        state.inFlight = saturatingAdd(state.inFlight, craftCount);
        appendFlight(state, primaryOutput.what(), outputAmount, craftCount);

        if (providerBusy) {
            state.window = Math.max(1, craftCount / 2);
            state.congested = true;
        }
    }

    public void onRejected(ICraftingProvider provider, IPatternDetails patternDetails) {
        var state = getState(provider, patternDetails);
        state.window = Math.max(1, state.window / 2);
    }

    public void onOutput(AEKey output, long amount) {
        if (output == null || amount <= 0 || flights.isEmpty()) {
            return;
        }

        long tick = TickHandler.instance().getCurrentTick();
        long remaining = amount;
        for (int index = 0; index < flights.size() && remaining > 0;) {
            var flight = flights.get(index);
            if (!flight.output.equals(output)) {
                index++;
                continue;
            }

            long consumed = Math.min(remaining, flight.remainingOutput);
            flight.remainingOutput -= consumed;
            remaining -= consumed;
            flight.state.recordOutput(tick);
            if (flight.remainingOutput > 0) {
                index++;
                continue;
            }

            var state = flight.state;
            state.inFlight = Math.max(0, state.inFlight - flight.craftCount);
            flights.remove(index);
            if (state.inFlight == 0) {
                if (state.congested) {
                    state.congested = false;
                } else {
                    state.window = saturatingMultiply(
                            state.window, calculateGrowthMultiplier(state));
                }
                state.resetCycle();
            }
        }
    }

    private State getState(ICraftingProvider provider, IPatternDetails patternDetails) {
        return getState(provider, patternDetails, 1);
    }

    private State getState(ICraftingProvider provider, IPatternDetails patternDetails,
            long initialWindow) {
        var providerStates = states.computeIfAbsent(provider, ignored -> new IdentityHashMap<>());
        return providerStates.computeIfAbsent(
                patternDetails, ignored -> new State(initialWindow));
    }

    private void appendFlight(State state, AEKey output, long outputAmount, long craftCount) {
        if (!flights.isEmpty()) {
            var last = flights.get(flights.size() - 1);
            if (last.state == state && last.output.equals(output)) {
                try {
                    long mergedOutput = Math.addExact(last.remainingOutput, outputAmount);
                    long mergedCrafts = Math.addExact(last.craftCount, craftCount);
                    last.remainingOutput = mergedOutput;
                    last.craftCount = mergedCrafts;
                    return;
                } catch (ArithmeticException ignored) {
                    // Keep separate flight records when a merged counter would overflow.
                }
            }
        }
        flights.add(new Flight(state, output, outputAmount, craftCount));
    }

    private void abandonFlights() {
        if (flights.isEmpty()) {
            return;
        }

        var touched = new IdentityHashMap<State, Boolean>();
        for (var flight : flights) {
            var state = flight.state;
            if (touched.put(state, Boolean.TRUE) == null) {
                state.window = Math.max(1, state.window / 2);
                state.inFlight = 0;
                state.congested = false;
                state.resetCycle();
            }
        }
        flights.clear();
    }

    private static appeng.api.stacks.GenericStack getPrimaryOutput(
            IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return null;
        }
        try {
            var primaryOutput = patternDetails.getPrimaryOutput();
            if (primaryOutput == null || primaryOutput.what() == null
                    || primaryOutput.amount() <= 0) {
                return null;
            }
            return primaryOutput;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static int calculateGrowthMultiplier(State state) {
        if (state.cycleStartedTick == Long.MIN_VALUE
                || state.firstOutputTick == Long.MIN_VALUE
                || state.lastOutputTick == Long.MIN_VALUE) {
            return 1;
        }

        long latency = state.firstOutputTick >= state.cycleStartedTick
                ? Math.max(1, state.firstOutputTick - state.cycleStartedTick)
                : 1;
        long spread = state.lastOutputTick >= state.firstOutputTick
                ? state.lastOutputTick - state.firstOutputTick
                : 0;
        if (spread <= 2) {
            return 16;
        }
        if (spread <= latency / 8) {
            return 4;
        }
        if (spread <= latency / 2) {
            return 2;
        }
        return 1;
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatingMultiply(long value, int multiplier) {
        if (multiplier <= 1) {
            return Math.max(1, value);
        }
        return value > Long.MAX_VALUE / multiplier
                ? Long.MAX_VALUE
                : Math.max(1, value * multiplier);
    }

    private static final class State {
        private long window;
        private long inFlight;
        private boolean congested;
        private long cycleStartedTick = Long.MIN_VALUE;
        private long firstOutputTick = Long.MIN_VALUE;
        private long lastOutputTick = Long.MIN_VALUE;

        private State(long initialWindow) {
            window = Math.max(1, initialWindow);
        }

        private void beginCycle(long tick) {
            cycleStartedTick = tick;
            firstOutputTick = Long.MIN_VALUE;
            lastOutputTick = Long.MIN_VALUE;
        }

        private void recordOutput(long tick) {
            if (firstOutputTick == Long.MIN_VALUE) {
                firstOutputTick = tick;
            }
            lastOutputTick = tick;
        }

        private void resetCycle() {
            cycleStartedTick = Long.MIN_VALUE;
            firstOutputTick = Long.MIN_VALUE;
            lastOutputTick = Long.MIN_VALUE;
        }
    }

    private static final class Flight {
        private final State state;
        private final AEKey output;
        private long remainingOutput;
        private long craftCount;

        private Flight(State state, AEKey output, long remainingOutput, long craftCount) {
            this.state = state;
            this.output = output;
            this.remainingOutput = remainingOutput;
            this.craftCount = craftCount;
        }
    }
}