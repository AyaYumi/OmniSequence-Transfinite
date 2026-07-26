package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;

public interface MolecularBalancedBatchProvider {
    void molecularmanipulator$beginBalancedBatch(KeyCounter[] firstInputs);

    default void molecularmanipulator$beginAdaptiveBatch(KeyCounter[] firstInputs) {
        molecularmanipulator$beginBalancedBatch(firstInputs);
    }

    default long molecularmanipulator$estimateFreeCrafts(IPatternDetails patternDetails,
            KeyCounter[] firstInputs, long maxCrafts) {
        return -1;
    }

    void molecularmanipulator$endBalancedBatch();
}