package com.atir.molecularmanipulator.crafting.maxfast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OmniMaxFastPlannerArithmeticTest {
    @Test
    void acceptsExactLongMaximumCraftingTotal() {
        assertEquals(Long.MAX_VALUE,
                OmniMaxFastPlanner.checkedCraftingTotal(Long.MAX_VALUE - 2, 2));
    }

    @Test
    void rejectsCraftingTotalOverflow() {
        assertThrows(ArithmeticException.class,
                () -> OmniMaxFastPlanner.checkedCraftingTotal(Long.MAX_VALUE, 1));
    }

    @Test
    void rejectsNonPositiveCraftingIncrements() {
        assertThrows(ArithmeticException.class,
                () -> OmniMaxFastPlanner.checkedCraftingTotal(0, 0));
        assertThrows(ArithmeticException.class,
                () -> OmniMaxFastPlanner.checkedCraftingTotal(0, -1));
        assertThrows(ArithmeticException.class,
                () -> OmniMaxFastPlanner.checkedCraftingTotal(-1, 1));
    }
}
