package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OmniDispatchBudgetLedgerTest {
    private static OmniDispatchBudgetLedger.Claim claim(long work, int unscaled) {
        return new OmniDispatchBudgetLedger.Claim(work, unscaled);
    }

    @Test
    void duplicateClaimDoesNotStealReturnedAttemptsFromLaterLanes() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(7L, 100L, 100);
        assertTrue(ledger.reserve("first", 50L, 50));
        assertTrue(ledger.reserve("second", 50L, 50));

        assertEquals(claim(50L, 50), ledger.claim("first"));
        assertTrue(ledger.record("first", 50L, 50, 0L, 0));
        assertEquals(100L, ledger.remainingWorkUnits());
        assertEquals(100, ledger.remainingUnscaledAttempts());

        // The second claim is rejected outright; returned work stays available.
        assertEquals(OmniDispatchBudgetLedger.EMPTY_CLAIM, ledger.claim("first"));
        assertEquals(100L, ledger.remainingWorkUnits());
        assertEquals(100, ledger.remainingUnscaledAttempts());
        assertFalse(ledger.record("first", 0L, 0, 0L, 0));

        // The later lane can use its own share plus at most one borrowed share.
        assertEquals(claim(100L, 100), ledger.claim("second"));
        assertEquals(0L, ledger.remainingWorkUnits());
        assertEquals(0, ledger.remainingUnscaledAttempts());
    }

    @Test
    void repeatedRecordCannotRefundTheSameClaimTwice() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(1L, 100L, 100);
        assertTrue(ledger.reserve("lane", 100L, 100));
        assertEquals(claim(100L, 100), ledger.claim("lane"));

        assertFalse(ledger.record("lane", 99L, 100, 0L, 0),
                "A declared allowance that differs from the issued claim is stale");
        assertEquals(0L, ledger.remainingWorkUnits());

        assertTrue(ledger.record("lane", 100L, 100, 30L, 40));
        assertEquals(70L, ledger.remainingWorkUnits());
        assertEquals(60, ledger.remainingUnscaledAttempts());

        assertFalse(ledger.record("lane", 100L, 100, 0L, 0));
        assertEquals(70L, ledger.remainingWorkUnits());
        assertEquals(60, ledger.remainingUnscaledAttempts());
        assertTrue(ledger.hasClaimed("lane"));
    }

    @Test
    void unusedWorkIsReturnedButBorrowingIsCappedAtOneShare() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(2L, 100L, 100);
        assertTrue(ledger.reserve("first", 40L, 40));
        assertTrue(ledger.reserve("second", 30L, 30));
        assertTrue(ledger.reserve("third", 30L, 30));

        assertEquals(claim(40L, 40), ledger.claim("first"));
        assertTrue(ledger.record("first", 40L, 40, 0L, 0));

        // 40 returned work is available, but the second lane may borrow only
        // its own 30 reservation instead of draining all 40.
        assertEquals(claim(60L, 60), ledger.claim("second"));
        assertTrue(ledger.record("second", 60L, 60, 0L, 0));

        // The last lane still receives its untouched share plus the remaining
        // returned work, and no lane can exceed the tick total.
        assertEquals(claim(60L, 60), ledger.claim("third"));
        assertTrue(ledger.record("third", 60L, 60, 0L, 0));
        assertEquals(100L, ledger.remainingWorkUnits());
        assertEquals(100, ledger.remainingUnscaledAttempts());
    }

    @Test
    void returnedAttemptsFlowToTheNextLaneWithoutExceedingTheTickTotal() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(3L, 0L, 120);
        assertTrue(ledger.reserve("a", 0L, 40));
        assertTrue(ledger.reserve("b", 0L, 40));
        assertTrue(ledger.reserve("c", 0L, 40));

        assertEquals(claim(0L, 40), ledger.claim("a"));
        assertTrue(ledger.record("a", 0L, 40, 0L, 10));
        assertEquals(110, ledger.remainingUnscaledAttempts());

        assertEquals(claim(0L, 70), ledger.claim("b"));
        assertTrue(ledger.record("b", 0L, 70, 0L, 70));

        assertEquals(claim(0L, 40), ledger.claim("c"));
        assertTrue(ledger.record("c", 0L, 40, 0L, 40));

        // Actual charges are 10 + 70 + 40 = 120, exactly the tick total.
        assertEquals(0, ledger.remainingUnscaledAttempts());
    }

    @Test
    void laneWithNoReservationCannotBorrowReturnedWork() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(4L, 100L, 100);
        assertTrue(ledger.reserve("old", 100L, 100));
        assertEquals(claim(100L, 100), ledger.claim("old"));
        assertTrue(ledger.record("old", 100L, 100, 0L, 0));

        // A lane that became busy after allowances were prepared gets nothing
        // this tick rather than consuming another lane's returned share.
        assertEquals(OmniDispatchBudgetLedger.EMPTY_CLAIM, ledger.claim("new"));
        assertEquals(100L, ledger.remainingWorkUnits());
        assertEquals(100, ledger.remainingUnscaledAttempts());
    }

    @Test
    void overUseIsClampedToTheIssuedClaim() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(5L, 50L, 50);
        assertTrue(ledger.reserve("lane", 50L, 50));
        assertEquals(claim(50L, 50), ledger.claim("lane"));
        assertTrue(ledger.record("lane", 50L, 50, 500L, 500));
        assertEquals(0L, ledger.remainingWorkUnits());
        assertEquals(0, ledger.remainingUnscaledAttempts());
    }

    @Test
    void reservationsCannotExceedTheTickTotalAndDuplicatesAreRejected() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(6L, 100L, 100);
        assertTrue(ledger.reserve("first", 70L, 70));
        assertFalse(ledger.reserve("first", 10L, 10));
        assertTrue(ledger.reserve("second", 70L, 70));

        assertEquals(100L, ledger.reservedWorkUnits());
        assertEquals(100, ledger.reservedUnscaledAttempts());
        assertEquals(claim(70L, 70), ledger.claim("first"));
        assertTrue(ledger.record("first", 70L, 70, 0L, 0));
        assertEquals(claim(60L, 60), ledger.claim("second"),
                "The capped reservation gets no more than its 30 remaining share plus 30 borrowed");
        assertEquals(40L, ledger.remainingWorkUnits());
    }

    @Test
    void longMaxAndIntMaxBudgetsSaturateWithoutOverflow() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        long firstWork = Long.MAX_VALUE / 2L;
        long secondWork = Long.MAX_VALUE - firstWork;
        int firstUnscaled = Integer.MAX_VALUE / 2;
        int secondUnscaled = Integer.MAX_VALUE - firstUnscaled;
        ledger.beginTick(Long.MAX_VALUE - 1L, Long.MAX_VALUE, Integer.MAX_VALUE);
        assertTrue(ledger.reserve("first", firstWork, firstUnscaled));
        assertTrue(ledger.reserve("second", secondWork, secondUnscaled));

        assertEquals(claim(firstWork, firstUnscaled), ledger.claim("first"));
        assertTrue(ledger.record("first", firstWork, firstUnscaled, 0L, 0));
        assertEquals(Long.MAX_VALUE, ledger.remainingWorkUnits());
        assertEquals(Integer.MAX_VALUE, ledger.remainingUnscaledAttempts());

        assertEquals(claim(Long.MAX_VALUE, Integer.MAX_VALUE), ledger.claim("second"));
        assertEquals(0L, ledger.remainingWorkUnits());
        assertEquals(0, ledger.remainingUnscaledAttempts());
        assertTrue(ledger.record(
                "second", Long.MAX_VALUE, Integer.MAX_VALUE, 0L, 0));
        assertEquals(Long.MAX_VALUE, ledger.remainingWorkUnits());
        assertEquals(Integer.MAX_VALUE, ledger.remainingUnscaledAttempts());
    }

    @Test
    void claimingClosesReservationPhaseUntilTheNextTick() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(9L, 100L, 100);
        assertTrue(ledger.reserve("first", 50L, 50));
        assertEquals(new OmniDispatchBudgetLedger.Claim(100L, 100), ledger.claim("first"));
        assertTrue(ledger.record("first", 100L, 100, 100L, 100));
        assertFalse(ledger.reserve("late", 100L, 100), "A late reservation cannot recreate spent work");
        assertEquals(OmniDispatchBudgetLedger.EMPTY_CLAIM, ledger.claim("late"));
        ledger.beginTick(10L, 100L, 100);
        assertTrue(ledger.reserve("late", 100L, 100));
        assertEquals(new OmniDispatchBudgetLedger.Claim(100L, 100), ledger.claim("late"));
    }

    @Test
    void negativeAndDuplicateInputsAreRejected() {
        var ledger = new OmniDispatchBudgetLedger<String>();
        ledger.beginTick(8L, 10L, 10);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.reserve("lane", -1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.reserve("lane", 0L, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new OmniDispatchBudgetLedger.Claim(-1L, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new OmniDispatchBudgetLedger.Claim(0L, -1));
    }
}
