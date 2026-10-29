package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class OmniCpuResourcesTest {
    @Test void infiniteParallelismMapsToPMinusOneWithoutOverflow() {
        assertEquals(Integer.MAX_VALUE - 1, OmniCpuResources.coProcessors(Long.MAX_VALUE));
        for (long parallel : new long[] {1, 2, 64, 16384, 32768, 1L << 20}) {
            int coProcessors = OmniCpuResources.coProcessors(parallel);
            assertTrue(coProcessors + 1 > 0);
            assertEquals(parallel, (long) coProcessors + 1);
        }
        assertEquals(0, OmniCpuResources.coProcessors(0));
    }

    @Test void lanesShareOnePhysicalDispatchAllowanceEvenWhenThereAreMoreLanesThanSlots() {
        for (long parallel : new long[] {16, 16384, 32768, Long.MAX_VALUE}) {
            int total = OmniCpuResources.singleDispatchLimit(parallel, Integer.MAX_VALUE);
            for (int lanes : new int[] {1, 3, 17, 31}) {
                long sum = 0;
                for (int offset = 0; offset < lanes; offset++) {
                    sum += OmniCpuResources.dispatchShare(total, lanes, offset);
                }
                assertEquals(total, sum);
                assertTrue(sum <= parallel);
            }
            assertEquals(Math.min(parallel, Integer.MAX_VALUE),
                    (long) OmniCpuResources.singleDispatchLimit(parallel, Integer.MAX_VALUE));
        }
    }

    @Test void scarceWorkAndAttemptSharesAlwaysHaveRunnableOverlap() {
        for (int lanes : new int[] {1, 3, 17, 31, 65}) {
            for (long work : new long[] {2, 3, 64, 100, 2147483647L, Long.MAX_VALUE}) {
                for (int attempts : new int[] {1, 16, 64, Integer.MAX_VALUE}) {
                    for (int workStart = 0; workStart < lanes; workStart++) {
                        for (int attemptStart = 0; attemptStart < lanes; attemptStart++) {
                            long workSum = 0;
                            long attemptSum = 0;
                            int runnable = 0;
                            for (int index = 0; index < lanes; index++) {
                                var share = OmniCpuResources.dispatchLaneShare(work, attempts, lanes,
                                        index, workStart, attemptStart);
                                workSum = Math.addExact(workSum, share.workUnits());
                                attemptSum += share.attempts();
                                if (share.canProgress()) runnable++;
                            }
                            assertEquals(work, workSum);
                            assertEquals(attempts, attemptSum);
                            assertTrue(runnable > 0, "Progress selection must not consume a zero-quota lane");
                        }
                    }
                }
            }
        }
    }

    @Test void scarceSharesGiveEveryLaneATurnAcrossTicks() {
        for (int lanes : new int[] {17, 31, 64, 65, 127}) {
            for (long work : new long[] {2, 3, 64, 100, Long.MAX_VALUE}) {
                for (int attempts : new int[] {1, 16, 64, Integer.MAX_VALUE}) {
                    var served = new boolean[lanes];
                    int workStart = 0;
                    int attemptStart = lanes - 1;
                    for (int tick = 0; tick < lanes; tick++) {
                        for (int index = 0; index < lanes; index++) {
                            served[index] |= OmniCpuResources.dispatchLaneShare(work, attempts, lanes,
                                    index, workStart, attemptStart).canProgress();
                        }
                        boolean paired = work < 2L * lanes;
                        int start = paired ? workStart : attemptStart;
                        workStart = OmniCpuResources.nextDispatchWorkRotation(work, attempts, lanes, workStart);
                        attemptStart = (int) ((start + Math.max(1L, attempts % lanes)) % lanes);
                    }
                    for (boolean value : served) assertTrue(value,
                            "Scarce work/attempt rotation cannot skip lanes across ticks");
                }
            }
        }
    }

    @Test void admissionPinsSharedCapacityBeforeAnotherLaneCanExtract() {
        var storage = new OmniCpuResources.StorageBudget<Object>();
        var first = new Object();
        var second = new Object();
        var occupied = new IdentityHashMap<Object, BigInteger>();
        try (var reservation = storage.reserve(100, occupied, first, BigInteger.valueOf(60))) {
            assertNotNull(reservation);
            assertEquals(40, storage.available(100, occupied, second));
            assertEquals(100, storage.available(100, occupied, first), "AE2 compares the selected plan against its own allocation");
            assertNull(storage.reserve(100, occupied, second, BigInteger.valueOf(41)));
            assertNull(storage.reserve(100, occupied, first, BigInteger.ONE), "A reentrant submission cannot replace a pinned lane");
            occupied.put(first, BigInteger.valueOf(60));
            assertEquals(40, storage.available(100, occupied, second), "Committed stock and its reservation count once");
        }
        assertEquals(40, storage.available(100, occupied, second));
        occupied.clear();
        assertEquals(100, storage.available(100, occupied, second));
    }

    @Test void twoConcurrentLanesCannotBothReserveTheSameCapacity() throws Exception {
        var storage = new OmniCpuResources.StorageBudget<Object>();
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> {
                var reservation = storage.reserve(100, Map.of(), new Object(), BigInteger.valueOf(60));
                barrier.await(5, TimeUnit.SECONDS);
                return reservation;
            });
            var second = pool.submit(() -> {
                var reservation = storage.reserve(100, Map.of(), new Object(), BigInteger.valueOf(60));
                barrier.await(5, TimeUnit.SECONDS);
                return reservation;
            });
            var a = first.get(5, TimeUnit.SECONDS);
            var b = second.get(5, TimeUnit.SECONDS);
            assertEquals(1, (a == null ? 0 : 1) + (b == null ? 0 : 1));
            if (a != null) a.close();
            if (b != null) b.close();
        }
    }

    @Test void exactTheoreticalBytesAndActualStockCannotBypassFiniteStorage() {
        var storage = new OmniCpuResources.StorageBudget<Object>();
        var lane = new Object();
        var huge = BigInteger.ONE.shiftLeft(96);
        assertNull(storage.reserve(1L << 40, Map.of(), lane, huge));
        assertNull(storage.reserve(1L << 40, Map.of(lane, huge), lane, BigInteger.ZERO));
        try (var reservation = storage.reserve(Long.MAX_VALUE, Map.of(), lane, huge)) {
            assertNotNull(reservation);
        }
        assertEquals(BigInteger.valueOf(9), OmniCpuResources.itemBytes(BigInteger.ONE, 8));
        assertTrue(OmniCpuResources.itemBytes(huge, 8).compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0);
    }

    @Test void retainedIdleStockIsAddedToNewWorkAndPinnedForOtherLanes() {
        var storage = new OmniCpuResources.StorageBudget<Object>();
        var lane = new Object();
        var other = new Object();
        var occupied = new IdentityHashMap<Object, BigInteger>();
        occupied.put(lane, BigInteger.valueOf(60));
        assertNull(storage.reserve(100, occupied, lane, BigInteger.valueOf(41)),
                "AE2 extracts new inputs in addition to the idle lane's undeliverable stock");
        try (var reservation = storage.reserve(100, occupied, lane, BigInteger.valueOf(40))) {
            assertNotNull(reservation);
            assertEquals(BigInteger.valueOf(100), reservation.bytes(), "Persist the combined allocation for the new job");
            assertEquals(0, storage.available(100, occupied, other));
            assertNull(storage.reserve(100, occupied, other, BigInteger.ONE));
            occupied.put(lane, reservation.bytes());
        }
        assertEquals(0, storage.available(100, occupied, other), "Closing admission must not release committed capacity");
        occupied.put(lane, BigInteger.valueOf(60));
        assertEquals(40, storage.available(100, occupied, other), "Completion releases the plan but retains physical stock");
    }

    @Test void retainedStockAndNewWorkRemainExactAtUnlimitedCapacity() {
        var storage = new OmniCpuResources.StorageBudget<Object>();
        var lane = new Object();
        var huge = BigInteger.ONE.shiftLeft(96);
        try (var reservation = storage.reserve(Long.MAX_VALUE, Map.of(lane, huge), lane, huge)) {
            assertNotNull(reservation);
            assertEquals(huge.multiply(BigInteger.TWO), reservation.bytes());
        }
    }

    @Test void downgradePreservesOverCapacityJobsAndBlocksAdditionalStorage() {
        var storage = new OmniCpuResources.StorageBudget<Object>();
        var oldLane = new Object();
        var primary = new Object();
        var occupied = new IdentityHashMap<Object, BigInteger>();
        occupied.put(oldLane, BigInteger.valueOf(1000));
        assertEquals(0, storage.available(16, occupied, primary));
        assertNull(storage.reserve(16, occupied, primary, BigInteger.ONE));
        assertEquals(BigInteger.valueOf(1000), occupied.get(oldLane), "Capacity reduction cannot cancel or trim an existing lane");
        occupied.remove(oldLane);
        try (var resumed = storage.reserve(16, occupied, primary, BigInteger.valueOf(16))) {
            assertNotNull(resumed);
        }
    }
}
