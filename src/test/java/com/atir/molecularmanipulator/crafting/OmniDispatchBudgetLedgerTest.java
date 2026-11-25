package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atir.molecularmanipulator.crafting.dispatch.OmniDispatchAllowance;
import com.atir.molecularmanipulator.starcomputation.StarComputationBenefits;
import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OmniDispatchBudgetLedgerTest {
    private static final StarComputationBenefits L7 = new StarComputationBenefits(7, false);

    @ParameterizedTest
    @CsvSource({"0, false", "1, false", "1, true", "2, false", "2, true", "3, false", "3, true",
            "4, false", "4, true", "5, false", "5, true", "6, false", "6, true"})
    void nativeLanesShareOneFourTickWindow(int level, boolean overclocked) {
        var benefits = new StarComputationBenefits(level, overclocked);
        long parallel = benefits.cpuParallel();
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var a = new Object();
        var b = new Object();
        var lanes = List.of(a, b);
        ledger.beginTick(100, benefits, true, 64, lanes);

        try (var first = ledger.claim(a, 100, null)) {
            assertFalse(first.capacityMode());
            assertEquals(parallel / 2, first.remainingCopies());
            assertFalse(first.tryWork());
            assertFalse(first.trySingle());
            first.recordAccepted(BigInteger.valueOf(5));
        }
        try (var second = ledger.claim(b, 100, null)) {
            assertEquals(parallel - 5, second.remainingCopies());
            acceptAll(second);
        }

        for (long tick = 101; tick <= 103; tick++) {
            ledger.beginTick(tick, benefits, true, 64, lanes);
            assertEquals(0, ledger.claim(a, tick, null).remainingCopies());
            assertEquals(0, ledger.claim(b, tick, null).remainingCopies());
        }
        ledger.beginTick(104, benefits, true, 64, lanes);
        try (var first = ledger.claim(a, 104, null); var second = ledger.claim(b, 104, null)) {
            assertEquals(parallel, first.remainingCopies() + second.remainingCopies());
        }
    }

    @Test
    void nativeHistoryAgesSkippedTicksAndCountsEachAcceptedCopy() {
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var lane = new Object();
        var lanes = List.of(lane);
        ledger.beginTick(20, StarComputationBenefits.NONE, true, 64, lanes);
        try (var allowance = ledger.claim(lane, 20, null)) {
            allowance.recordAccepted(BigInteger.valueOf(5));
            allowance.recordAccepted(BigInteger.valueOf(6));
            assertThrows(IllegalArgumentException.class, () -> allowance.recordAccepted(BigInteger.valueOf(6)));
            assertThrows(IllegalArgumentException.class, () -> allowance.recordAccepted(BigInteger.ONE.shiftLeft(80)));
        }

        ledger.beginTick(23, StarComputationBenefits.NONE, true, 64, lanes);
        try (var allowance = ledger.claim(lane, 23, null)) {
            assertEquals(5, allowance.remainingCopies());
            allowance.recordAccepted(BigInteger.valueOf(2));
        }
        ledger.beginTick(24, StarComputationBenefits.NONE, true, 64, lanes);
        try (var allowance = ledger.claim(lane, 24, null)) {
            assertEquals(14, allowance.remainingCopies());
        }
        ledger.beginTick(28, StarComputationBenefits.NONE, true, 64, lanes);
        try (var allowance = ledger.claim(lane, 28, null)) {
            assertEquals(16, allowance.remainingCopies());
        }
    }

    @Test
    void tierChangesApplyNextTickAndDowngradeUsesActualCapacityAdmissions() {
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var a = new Object();
        var b = new Object();
        var lanes = List.of(a, b);
        var overclockedL6 = new StarComputationBenefits(6, true);
        var overclockedL7 = new StarComputationBenefits(7, true);
        var server = new OmniDispatchBudgetLedger.ServerBudget(() -> 0L);
        ledger.beginTick(1, overclockedL6, true, 64, lanes);
        try (var first = ledger.claim(a, 1, null)) {
            assertEquals(16_384, first.remainingCopies());
            acceptAll(first);
        }
        ledger.beginTick(1, overclockedL7, true, 64, lanes);
        try (var second = ledger.claim(b, 1, null)) {
            assertFalse(second.capacityMode());
            assertEquals(16_384, second.remainingCopies());
            acceptAll(second);
        }

        ledger.beginTick(2, overclockedL6, true, 64, lanes);
        assertEquals(0, ledger.claim(a, 2, null).remainingCopies());
        ledger.beginTick(2, overclockedL7, true, 64, lanes);
        try (var second = ledger.claim(b, 2, null)) {
            assertFalse(second.capacityMode());
            assertEquals(0, second.remainingCopies());
        }

        ledger.beginTick(3, overclockedL7, true, 64, lanes);
        startServerTick(server, 3, 16, 2_000_000, ledger);
        try (var allowance = ledger.claim(a, 3, server)) {
            assertTrue(allowance.capacityMode());
            assertEquals(Long.MAX_VALUE, allowance.remainingCopies());
            assertTrue(allowance.tryWork());
            allowance.recordAccepted(BigInteger.valueOf(29));
        }

        ledger.beginTick(5, overclockedL6, true, 64, lanes);
        try (var first = ledger.claim(a, 5, null); var second = ledger.claim(b, 5, null)) {
            assertEquals(overclockedL6.cpuParallel() - 29, first.remainingCopies() + second.remainingCopies());
        }
    }

    @Test
    void unusedCapacityWorkCarriesToAnUnfundedLaneOnlyOnce() {
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var a = new Object();
        var b = new Object();
        var server = new OmniDispatchBudgetLedger.ServerBudget(() -> 0L);
        ledger.beginTick(7, L7, true, 8, List.of(a, b));
        startServerTick(server, 7, 16, 2_000_000, ledger);

        var first = ledger.claim(a, 7, server);
        assertEquals(Long.MAX_VALUE, first.remainingCopies());
        assertEquals(0, ledger.claim(a, 7, server).remainingCopies());
        assertTrue(first.tryWork());
        first.recordAccepted(BigInteger.valueOf(1_000_000));
        first.close();
        first.close();
        assertEquals(0, first.remainingCopies());
        assertFalse(first.tryWork());
        assertFalse(first.trySingle());
        assertEquals(0, ledger.claim(a, 7, server).remainingCopies());
        assertEquals(0, ledger.claim(new Object(), 7, server).remainingCopies());

        try (var second = ledger.claim(b, 7, server)) {
            assertEquals(Long.MAX_VALUE, second.remainingCopies());
            assertEquals(7, drainWork(second));
        }
    }

    @ParameterizedTest
    @CsvSource({"7, false", "7, true", "8, false", "8, true", "9, false", "9, true"})
    void capacityLanesCanAcceptWholeLongAndExactOrdersWithinTheirWorkReservations(int level, boolean overclocked) {
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var a = new Object();
        var b = new Object();
        var lanes = List.of(a, b);
        var server = new OmniDispatchBudgetLedger.ServerBudget(() -> 0L);
        ledger.beginTick(1, new StarComputationBenefits(level, overclocked), true, Long.MAX_VALUE, lanes);
        startServerTick(server, 1, 16, 2_000_000, ledger);
        try (var ordinary = ledger.claim(a, 1, server); var exact = ledger.claim(b, 1, server)) {
            assertEquals(Long.MAX_VALUE, ordinary.remainingCopies());
            assertEquals(Long.MAX_VALUE, exact.remainingCopies());
            assertTrue(ordinary.tryWork());
            assertTrue(exact.tryWork());
            ordinary.recordAccepted(BigInteger.valueOf(Long.MAX_VALUE));
            exact.recordAccepted(BigInteger.ONE.shiftLeft(100));
            assertEquals(Long.MAX_VALUE, ordinary.remainingCopies());
            assertEquals(Long.MAX_VALUE, exact.remainingCopies());
            assertEquals(31, drainWork(ordinary));
            assertEquals(31, drainWork(exact));
        }

        var nativeTier = new StarComputationBenefits(6, false);
        for (long tick = 2; tick <= 4; tick++) {
            ledger.beginTick(tick, nativeTier, true, 64, lanes);
            assertEquals(0, ledger.claim(a, tick, null).remainingCopies());
            assertEquals(0, ledger.claim(b, tick, null).remainingCopies());
        }
        ledger.beginTick(5, nativeTier, true, 64, lanes);
        try (var first = ledger.claim(a, 5, null); var second = ledger.claim(b, 5, null)) {
            assertEquals(nativeTier.cpuParallel(), first.remainingCopies() + second.remainingCopies());
        }
    }

    @Test
    void capacityRequiresAnEnabledControllerAndAnActiveLaneInTheCurrentTick() {
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var lane = new Object();
        var lanes = List.of(lane);
        var server = new OmniDispatchBudgetLedger.ServerBudget(() -> 0L);
        ledger.beginTick(1, L7, true, 64, lanes);
        startServerTick(server, 1, 16, 2_000_000, ledger);
        try (var previous = ledger.claim(lane, 1, server)) {
            assertTrue(previous.tryWork());
            ledger.beginTick(2, L7, false, 64, lanes);
            startServerTick(server, 2, 16, 2_000_000, ledger);
            assertEmptyCapacity(previous);
            assertEmptyCapacity(ledger.claim(lane, 2, server));
        }

        ledger.beginTick(3, L7, true, 64, List.of());
        startServerTick(server, 3, 16, 2_000_000, ledger);
        assertEmptyCapacity(ledger.claim(lane, 3, server));

        ledger.beginTick(4, L7, true, 64, lanes);
        startServerTick(server, 4, 16, 2_000_000, ledger);
        assertEmptyCapacity(ledger.claim(new Object(), 4, server));
        try (var restored = ledger.claim(lane, 4, server)) {
            assertEquals(Long.MAX_VALUE, restored.remainingCopies());
            assertEquals(64, drainWork(restored));
        }
    }

    @Test
    void minimumControllerWorkRotatesAFullShareAcrossSaturatedLanes() {
        var ledger = new OmniDispatchBudgetLedger<Object>();
        var a = new Object();
        var b = new Object();
        var lanes = List.of(a, b);
        var server = new OmniDispatchBudgetLedger.ServerBudget(() -> 0L);
        for (long tick = 1; tick <= 2; tick++) {
            ledger.beginTick(tick, L7, true, 8, lanes);
            startServerTick(server, tick, 16, 250_000, ledger);
            var funded = tick == 1 ? a : b;
            var waiting = tick == 1 ? b : a;
            try (var allowance = ledger.claim(funded, tick, server)) {
                assertEquals(Long.MAX_VALUE, allowance.remainingCopies());
                assertEquals(8, drainWork(allowance));
            }
            assertEmptyCapacity(ledger.claim(waiting, tick, server));
        }
    }

    @Test
    void expiredServerWindowRotatesBoundedWorkAndProtectsOneGlobalSingleSlot() {
        var earlyController = new OmniDispatchBudgetLedger<Object>();
        var lateController = new OmniDispatchBudgetLedger<Object>();
        var early = new Object();
        var late = new Object();
        var now = new AtomicLong();
        var server = new OmniDispatchBudgetLedger.ServerBudget(now::get);

        for (long tick = 1; tick <= 3; tick++) {
            earlyController.beginTick(tick, L7, true, 64, List.of(early));
            lateController.beginTick(tick, L7, true, 64, List.of(late));
            long window = tick == 1 ? Long.MAX_VALUE : 250_000;
            int singles = tick == 3 ? 1 : Integer.MAX_VALUE;
            startServerTick(server, tick, singles, window, earlyController, lateController);
            try (var first = earlyController.claim(early, tick, server)) {
                assertTrue(first.tryWork());
                now.addAndGet(Math.min(window, 2_000_000));
                assertEquals(tick == 2 ? 0 : 11, drainWork(first));
                if (tick < 3) assertEquals(tick == 2 ? 15 : 16, drainSingles(first));
            }
            try (var second = lateController.claim(late, tick, server)) {
                assertEquals(tick == 2 ? 12 : 0, drainWork(second));
                assertEquals(tick == 1 ? 0 : 1, drainSingles(second));
            }
        }
    }

    private static void assertEmptyCapacity(OmniDispatchAllowance allowance) {
        assertTrue(allowance.capacityMode());
        assertEquals(0, allowance.remainingCopies());
        assertFalse(allowance.tryWork());
        assertFalse(allowance.trySingle());
    }

    private static void acceptAll(OmniDispatchAllowance allowance) {
        allowance.recordAccepted(BigInteger.valueOf(allowance.remainingCopies()));
        assertEquals(0, allowance.remainingCopies());
    }

    @SafeVarargs
    private static void startServerTick(OmniDispatchBudgetLedger.ServerBudget server, long tick, int singles,
            long nanos, OmniDispatchBudgetLedger<Object>... controllers) {
        assertTrue(server.beginTick(tick, singles, nanos));
        for (var controller : controllers) controller.registerServerLanes(tick, server);
        server.selectPriority();
        assertFalse(server.beginTick(tick, singles, nanos));
    }

    private static int drainWork(OmniDispatchAllowance allowance) {
        int work = 0;
        while (work <= 64 && allowance.tryWork()) work++;
        return work;
    }

    private static int drainSingles(OmniDispatchAllowance allowance) {
        int singles = 0;
        while (singles <= 16 && allowance.trySingle()) singles++;
        return singles;
    }
}
