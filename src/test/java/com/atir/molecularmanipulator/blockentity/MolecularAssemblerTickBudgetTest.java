package com.atir.molecularmanipulator.blockentity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MolecularAssemblerTickBudgetTest {
    @Test
    void repeatedDispatchesShareOneTickAndRejectWholeOversizedBatches() {
        var budget = new MolecularAssemblerTickBudget();
        assertTrue(budget.tryConsume(10, 4096, 4000));
        assertFalse(budget.tryConsume(10, 4096, 97));
        assertEquals(96, budget.remaining(10, 4096));
        assertTrue(budget.tryConsume(10, 4096, 96));
        assertFalse(budget.tryConsume(10, 4096, 1));
        assertEquals(4096, budget.remaining(11, 4096));
    }

    @Test
    void baselineAllowsOnlyOneRecipeAcrossEveryDispatchInTheTick() {
        var budget = new MolecularAssemblerTickBudget();
        assertFalse(budget.tryConsume(20, 1, 2));
        assertTrue(budget.tryConsume(20, 1, 1));
        assertFalse(budget.tryConsume(20, 1, 1));
        assertTrue(budget.tryConsume(21, 1, 1));
    }

    @Test
    void changingEngineLevelDoesNotResetAlreadySpentRecipes() {
        var budget = new MolecularAssemblerTickBudget();
        assertTrue(budget.tryConsume(30, 4096, 3000));
        assertEquals(0, budget.remaining(30, 1));
        assertFalse(budget.tryConsume(30, 1, 1));
        assertEquals(13384, budget.remaining(30, 16384));
        assertTrue(budget.tryConsume(30, 16384, 13384));
        assertEquals(0, budget.remaining(30, 16384));
        assertEquals(1, budget.remaining(31, 1));
    }

    @Test
    void maximumParallelismCannotOverflowOrRefreshWithinTheTick() {
        var budget = new MolecularAssemblerTickBudget();
        assertTrue(budget.tryConsume(40, Long.MAX_VALUE, Long.MAX_VALUE - 1));
        assertFalse(budget.tryConsume(40, Long.MAX_VALUE, 2));
        assertTrue(budget.tryConsume(40, Long.MAX_VALUE, 1));
        assertEquals(0, budget.remaining(40, Long.MAX_VALUE));
        assertFalse(budget.tryConsume(40, Long.MAX_VALUE, 1));
        assertTrue(budget.tryConsume(41, Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void onlyTheCurrentTickCanReceiveARollbackRefund() {
        var budget = new MolecularAssemblerTickBudget();
        assertTrue(budget.tryConsume(50, 10, 7));
        budget.refund(50, 7);
        assertEquals(10, budget.remaining(50, 10));
        assertTrue(budget.tryConsume(51, 10, 10));
        budget.refund(50, 7);
        assertEquals(0, budget.remaining(51, 10));
        assertFalse(budget.tryConsume(51, 10, 0));
        assertFalse(budget.tryConsume(51, 10, -1));
        budget.refund(51, 11);
        assertEquals(0, budget.remaining(51, 10));
    }
}
