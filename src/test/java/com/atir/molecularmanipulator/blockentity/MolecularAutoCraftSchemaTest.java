package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MolecularAutoCraftSchemaTest {
    @Test
    void schemaV2DefinesExactlyNineIndependentPatternSlots() {
        assertEquals(2, MolecularAutoCraftSchema.VERSION);
        assertEquals(9, MolecularAutoCraftSchema.PATTERN_SLOTS);

        for (int slot = 0; slot < MolecularAutoCraftSchema.PATTERN_SLOTS; slot++) {
            assertTrue(MolecularAutoCraftSchema.isIndependentSlot(slot),
                    "slot " + slot + " should belong to the independent inventory");
        }
        assertFalse(MolecularAutoCraftSchema.isIndependentSlot(-1));
        assertFalse(MolecularAutoCraftSchema.isIndependentSlot(
                MolecularAutoCraftSchema.PATTERN_SLOTS));
        assertFalse(MolecularAutoCraftSchema.isIndependentSlot(Integer.MIN_VALUE));
        assertFalse(MolecularAutoCraftSchema.isIndependentSlot(Integer.MAX_VALUE));
    }

    @Test
    void onlyCurrentSchemaCanRestoreIndependentSlotConfiguration() {
        assertTrue(MolecularAutoCraftSchema.shouldLoadConfig(2));
        assertFalse(MolecularAutoCraftSchema.shouldLoadConfig(-1));
        assertFalse(MolecularAutoCraftSchema.shouldLoadConfig(0));
        assertFalse(MolecularAutoCraftSchema.shouldLoadConfig(1),
                "v1 slot ids referred to the left-side provider inventory");
        assertFalse(MolecularAutoCraftSchema.shouldLoadConfig(3));
        assertFalse(MolecularAutoCraftSchema.shouldLoadConfig(Integer.MAX_VALUE));
    }
}
