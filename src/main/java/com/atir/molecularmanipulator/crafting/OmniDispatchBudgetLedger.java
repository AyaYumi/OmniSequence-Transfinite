package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.crafting.dispatch.OmniDispatchAllowance;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/** Server-thread accounting for one controller's physical and virtual CPU lanes. */
public final class OmniDispatchBudgetLedger<L> {
    private static final int MIN_WORK_PER_CONTROLLER = 8;
    private static final int MAX_WORK_PER_CONTROLLER = 64;
    // Leave room for one prototype/provider/submission path on each selected lane.
    private static final int WORK_PER_TRANSACTION = 12;
    private static final int NATIVE_TICKS = 4;

    private final long[] acceptedHistory = new long[NATIVE_TICKS];
    private final ArrayDeque<L> rotation = new ArrayDeque<>();
    private final Map<L, Lane> lanes = new IdentityHashMap<>();
    private boolean initialized;
    private long tick;
    private boolean capacityMode;
    private boolean unlimited;
    private long returnedCopies;
    private int returnedWork;

    public boolean needsTick(long tick) {
        return !initialized || tick > this.tick;
    }

    public boolean capacityMode() {
        return capacityMode;
    }

    /** Snapshots the tier, limits and active lane set exactly once per server tick. */
    public void beginTick(long tick, StarComputationBenefits benefits, boolean enabled,
            long configuredWork, List<L> activeLanes) {
        if (!needsTick(tick)) return;
        long elapsed = tick - this.tick;
        if (!initialized || elapsed < 0L || elapsed >= NATIVE_TICKS) {
            Arrays.fill(acceptedHistory, 0L);
        } else {
            for (int age = 1; age <= elapsed; age++) {
                acceptedHistory[historyIndex(this.tick + age)] = 0L;
            }
        }
        initialized = true;
        this.tick = tick;
        capacityMode = benefits.level() >= 7;
        unlimited = enabled && benefits.infinite();
        returnedCopies = 0L;
        returnedWork = 0;

        for (var cpu : activeLanes) {
            var lane = lanes.get(cpu);
            if (lane == null) {
                lane = new Lane();
                lanes.put(cpu, lane);
                rotation.addLast(cpu);
            }
            lane.activeTick = tick;
            lane.claimed = false;
            lane.copies = 0L;
            lane.work = 0;
        }
        rotation.removeIf(cpu -> {
            if (lanes.get(cpu).activeTick == tick) return false;
            lanes.remove(cpu);
            return true;
        });

        long copies = enabled ? benefits.cpuParallel() : 0L;
        if (!capacityMode) {
            // AE2 applies its own per-lane history. The caller intersects that
            // allowance with this controller limit, which already includes all lanes.
            for (long accepted : acceptedHistory) copies -= Math.min(copies, accepted);
        }
        if (copies == 0L || rotation.isEmpty()) return;

        int work = capacityMode ? Math.clamp(configuredWork, MIN_WORK_PER_CONTROLLER, MAX_WORK_PER_CONTROLLER) : 0;
        int count = rotation.size();
        if (capacityMode) {
            // Reserve a whole transaction's worth when possible. The minimum
            // configured budget still funds one lane and rotates its full share.
            count = Math.min(count, Math.max(1, work / WORK_PER_TRANSACTION));
        }
        long copiesPerLane = unlimited ? Long.MAX_VALUE : copies / count;
        long extraCopies = unlimited ? 0L : copies % count;
        int workPerLane = work / count;
        int extraWork = work % count;
        int offset = 0;
        for (var cpu : rotation) {
            var lane = lanes.get(cpu);
            lane.copies = copiesPerLane + (offset < extraCopies ? 1L : 0L);
            lane.work = workPerLane + (offset < extraWork ? 1 : 0);
            if (++offset == count) break;
        }
        // Unfunded native ticks keep the next lane at the head. Crowded capacity
        // ticks advance past all funded lanes so every lane gets a full turn.
        int step = capacityMode && count < rotation.size() ? count : 1;
        if (!capacityMode && copies < count) step = (int) copies;
        for (int i = 0; i < step; i++) rotation.addLast(rotation.removeFirst());
    }

    /** Adds this tick's active capacity lanes to the shared server rotation. */
    public void registerServerLanes(long tick, ServerBudget server) {
        if (!initialized || this.tick != tick || !capacityMode) return;
        for (var cpu : rotation) server.register(lanes.get(cpu));
    }

    /**
     * Takes the lane's reserved share plus any shares returned by earlier closes.
     * AE2 controls tick iteration order. Only the reserved shares rotate. An
     * earlier lane cannot use a later lane's share or rerun after a late return.
     */
    public OmniDispatchAllowance claim(L cpu, long tick, ServerBudget server) {
        var lane = lanes.get(cpu);
        if (!initialized || this.tick != tick || lane == null || lane.claimed) return noAllowance();
        lane.claimed = true;
        long copies = unlimited ? Long.MAX_VALUE : lane.copies + returnedCopies;
        int work = lane.work + returnedWork;
        if (copies == 0 || (capacityMode && (work == 0 || server == null))) return noAllowance();
        returnedCopies = 0L;
        returnedWork = 0;
        return new Allowance(tick, lane, server, copies, work);
    }

    public OmniDispatchAllowance noAllowance() {
        return capacityMode ? EmptyAllowance.CAPACITY : EmptyAllowance.NATIVE;
    }

    private static int historyIndex(long tick) {
        return (int) (tick & (NATIVE_TICKS - 1L));
    }

    // Contains no CPU or controller reference, so the server budget can retain
    // lane identities without preventing a weakly keyed server from unloading.
    private static final class Lane {
        private long activeTick;
        private long copies;
        private int work;
        private boolean claimed;
    }

    private final class Allowance implements OmniDispatchAllowance {
        private final long claimedTick;
        private final Lane lane;
        private final ServerBudget server;
        private final boolean capacity = capacityMode;
        private final boolean unbounded = unlimited;
        private long copies;
        private int work;
        private boolean closed;

        private Allowance(long tick, Lane lane, ServerBudget server, long copies, int work) {
            claimedTick = tick;
            this.lane = lane;
            this.server = server;
            this.copies = copies;
            this.work = work;
        }

        private boolean open() {
            return !closed && claimedTick == tick;
        }

        @Override
        public boolean capacityMode() { return capacity; }

        @Override
        public long remainingCopies() { return open() ? copies : 0L; }

        @Override
        public boolean tryWork() {
            if (!open() || !capacity || copies == 0L || work == 0 || !server.tryWork(claimedTick, lane)) return false;
            work--;
            return true;
        }

        @Override
        public boolean trySingle() {
            return open() && capacity && copies > 0L && server.trySingle(claimedTick, lane);
        }

        @Override
        public void recordAccepted(BigInteger accepted) {
            if (!open() || accepted.signum() == 0) return;
            if (accepted.signum() < 0 || (!unbounded && (accepted.bitLength() > 63 || accepted.longValue() > copies))) {
                throw new IllegalArgumentException("Accepted copies exceed dispatch allowance");
            }
            long count = accepted.bitLength() > 63 ? Long.MAX_VALUE : accepted.longValue();
            if (!unbounded) copies -= count;
            // A transition back to a native tier still sees recent admissions.
            // Total history above Long.MAX_VALUE exhausts every finite native tier.
            int index = historyIndex(claimedTick);
            acceptedHistory[index] += Math.min(count, Long.MAX_VALUE - acceptedHistory[index]);
        }

        @Override
        public void close() {
            if (open()) {
                if (!unbounded) returnedCopies += copies;
                returnedWork += work;
                if (capacity) server.close(claimedTick, lane);
            }
            closed = true;
        }
    }

    private enum EmptyAllowance implements OmniDispatchAllowance {
        NATIVE, CAPACITY;

        @Override
        public boolean capacityMode() { return this == CAPACITY; }
        @Override
        public long remainingCopies() { return 0L; }
        @Override
        public boolean tryWork() { return false; }
        @Override
        public boolean trySingle() { return false; }
        @Override
        public void recordAccepted(BigInteger accepted) {}
        @Override
        public void close() {}
    }

    /** One soft wall-clock window and one hard single-push limit for the whole server. */
    public static final class ServerBudget {
        private static final long MAX_TIME_NANOS = 2_000_000L;
        private static final int MAX_SINGLE_PUSHES = 16;

        private final LongSupplier nanoTime;
        private final ArrayDeque<Lane> rotation = new ArrayDeque<>();
        private final Map<Lane, Boolean> registered = new IdentityHashMap<>();
        private boolean initialized;
        private long tick;
        private long timeNanos;
        private long startedAt;
        private int singlesRemaining;
        private Lane priority;
        private int priorityWork;
        private boolean prioritySingle;

        public ServerBudget() {
            this(System::nanoTime);
        }

        ServerBudget(LongSupplier nanoTime) {
            this.nanoTime = nanoTime;
        }

        public boolean needsTick(long tick) {
            return !initialized || tick > this.tick;
        }

        public boolean beginTick(long tick, int configuredSingles, long configuredTimeNanos) {
            if (!needsTick(tick)) return false;
            initialized = true;
            this.tick = tick;
            timeNanos = Math.clamp(configuredTimeNanos, 0L, MAX_TIME_NANOS);
            singlesRemaining = Math.clamp(configuredSingles, 0, MAX_SINGLE_PUSHES);
            startedAt = nanoTime.getAsLong();
            priority = null;
            priorityWork = 0;
            prioritySingle = false;
            registered.replaceAll((lane, present) -> false);
            return true;
        }

        private void register(Lane lane) {
            if (registered.put(lane, true) == null) rotation.addLast(lane);
        }

        /**
         * Selects the longest-waiting locally funded lane. For other lanes the
         * window and the unreserved single slots are first-served. The selected
         * lane can spend at most one transaction's worth of work even after the
         * deadline. Its one protected single slot counts toward the hard cap and
         * becomes available to later lanes if it is still unused at close.
         */
        public void selectPriority() {
            rotation.removeIf(lane -> {
                if (registered.get(lane)) return false;
                registered.remove(lane);
                return true;
            });
            for (var it = rotation.iterator(); it.hasNext();) {
                var lane = it.next();
                if (lane.work == 0) continue;
                it.remove();
                rotation.addLast(lane);
                priority = lane;
                priorityWork = Math.min(WORK_PER_TRANSACTION, lane.work);
                prioritySingle = singlesRemaining > 0;
                break;
            }
        }

        private boolean tryWork(long tick, Lane lane) {
            if (!initialized || this.tick != tick) return false;
            if (lane == priority && priorityWork > 0) {
                priorityWork--;
                return true;
            }
            return Long.compareUnsigned(nanoTime.getAsLong() - startedAt, timeNanos) < 0;
        }

        private boolean trySingle(long tick, Lane lane) {
            if (!initialized || this.tick != tick) return false;
            int protectedSlots = prioritySingle && lane != priority ? 1 : 0;
            if (singlesRemaining <= protectedSlots) return false;
            singlesRemaining--;
            if (lane == priority) prioritySingle = false;
            return true;
        }

        private void close(long tick, Lane lane) {
            if (this.tick == tick && lane == priority) {
                priorityWork = 0;
                prioritySingle = false;
            }
        }
    }
}
