package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AssemblerMatrixLoadedOutputAmountsTest {
    @Test
    void mergesDuplicatePositiveAmounts() {
        assertEquals(96,
                AssemblerMatrixMolecularCoreBlockEntity.LoadedOutputAmounts
                        .merge(64, 32));
        assertEquals(Long.MAX_VALUE,
                AssemblerMatrixMolecularCoreBlockEntity.LoadedOutputAmounts
                        .merge(Long.MAX_VALUE - 32, 32));
    }

    @Test
    void saturatesDuplicateAmountsThatOverflow() {
        assertEquals(Long.MAX_VALUE,
                AssemblerMatrixMolecularCoreBlockEntity.LoadedOutputAmounts
                        .merge(Long.MAX_VALUE - 1, 2));
        assertEquals(Long.MAX_VALUE,
                AssemblerMatrixMolecularCoreBlockEntity.LoadedOutputAmounts
                        .merge(Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test
    void rejectsNegativePersistedAmounts() {
        assertThrows(IllegalArgumentException.class,
                () -> AssemblerMatrixMolecularCoreBlockEntity.LoadedOutputAmounts
                        .merge(0, -1));
        assertThrows(IllegalArgumentException.class,
                () -> AssemblerMatrixMolecularCoreBlockEntity.LoadedOutputAmounts
                        .merge(-1, 1));
    }
}
