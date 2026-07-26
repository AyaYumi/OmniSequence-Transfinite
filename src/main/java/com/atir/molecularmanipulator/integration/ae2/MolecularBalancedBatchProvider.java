package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.stacks.KeyCounter;

public interface MolecularBalancedBatchProvider {
    void molecularmanipulator$beginBalancedBatch(KeyCounter[] firstInputs);

    default void molecularmanipulator$beginAdaptiveBatch(KeyCounter[] firstInputs) {
        molecularmanipulator$beginBalancedBatch(firstInputs);
    }

    void molecularmanipulator$endBalancedBatch();
}
