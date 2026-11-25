package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.Map;

/** CPU capability arithmetic and atomic, controller-wide storage admission. No game dependencies. */
public final class OmniCpuResources {
    private OmniCpuResources() {}

    /** AE2 adds one; even the unlimited sentinel must leave room for that addition. */
    public static int coProcessors(long parallelism) {
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE - 1L, parallelism - 1L));
    }

    public static boolean acceptsNewJob(StarComputationBenefits benefits, boolean primary) {
        return primary || benefits.virtualCpu();
    }

    /** Inventory bytes include each distinct key and all physical overflow, using exact arithmetic. */
    public static BigInteger itemBytes(BigInteger amount, long amountPerByte) {
        if (amount.signum() <= 0) return BigInteger.ZERO;
        var unit = BigInteger.valueOf(Math.max(1L, amountPerByte));
        return amount.add(unit).subtract(BigInteger.ONE).divide(unit).add(BigInteger.valueOf(8));
    }

    public static final class StorageBudget<L> {
        private final Map<L, Reservation> pending = new IdentityHashMap<>();

        /** The selected lane's own reservation is excluded from AE2's plan-size comparison. */
        public synchronized long available(long capacity, Map<L, BigInteger> occupied, L lane) {
            if (capacity == Long.MAX_VALUE) return Long.MAX_VALUE;
            return BigInteger.valueOf(Math.max(0, capacity)).subtract(used(occupied, lane))
                    .max(BigInteger.ZERO).longValueExact();
        }

        /** Reservations pin new work plus retained stock before extraction, including reentrant submissions. */
        public synchronized Reservation reserve(long capacity, Map<L, BigInteger> occupied, L lane,
                BigInteger requested) {
            if (requested == null || requested.signum() < 0 || pending.containsKey(lane)) return null;
            var allocation = requested.add(occupied.getOrDefault(lane, BigInteger.ZERO).max(BigInteger.ZERO));
            if (capacity != Long.MAX_VALUE && used(occupied, lane).add(allocation)
                    .compareTo(BigInteger.valueOf(Math.max(0, capacity))) > 0) return null;
            var reservation = new Reservation(lane, allocation);
            pending.put(lane, reservation);
            return reservation;
        }

        private BigInteger used(Map<L, BigInteger> occupied, L excluded) {
            var amounts = new IdentityHashMap<L, BigInteger>(occupied);
            pending.forEach((lane, reservation) -> amounts.merge(lane, reservation.bytes, BigInteger::max));
            var total = BigInteger.ZERO;
            for (var entry : amounts.entrySet()) {
                if (entry.getKey() != excluded) total = total.add(entry.getValue().max(BigInteger.ZERO));
            }
            return total;
        }

        public final class Reservation implements AutoCloseable {
            private final L lane;
            private final BigInteger bytes;
            private Reservation(L lane, BigInteger bytes) { this.lane = lane; this.bytes = bytes; }
            public BigInteger bytes() { return bytes; }
            @Override public void close() {
                synchronized (StorageBudget.this) { pending.remove(lane, this); }
            }
        }
    }
}
