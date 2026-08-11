package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CraftingPlanAmountLimitsTest {
    @Test
    void acceptsExactLongMaximum() {
        assertFalse(CraftingPlanAmountLimits.additionOverflows(
                Long.MAX_VALUE - 1, 1));
    }

    @Test
    void rejectsOneBeyondLongMaximum() {
        assertTrue(CraftingPlanAmountLimits.additionOverflows(
                Long.MAX_VALUE, 1));
        assertTrue(CraftingPlanAmountLimits.additionOverflows(
                Long.MAX_VALUE - 1, 2));
    }

    @Test
    void rejectsCraftingBytesThatNarrowToLongMaximum() {
        assertFalse(CraftingPlanAmountLimits.bytesExceedLongCapacity(
                Math.nextDown(0x1.0p63), 0));
        assertTrue(CraftingPlanAmountLimits.bytesExceedLongCapacity(
                Math.nextDown(0x1.0p63), 1024));
    }
}
