package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;

/**
 * Explicit capability for providers that can safely receive a runtime-scaled
 * processing pattern and report where the accepted inputs ended up.
 */
public interface MolecularScaledBatchProvider {
    enum PushResult {
        REJECTED,
        ACCEPTED_FULL,
        ACCEPTED_QUEUED,
        ACCEPTED_UNVERIFIED
    }

    /**
     * Returns the current whole-recipe capacity for a single-key processing
     * pattern using AE2's external-inventory path. Zero leaves normal dispatch available.
     */
    long molecularmanipulator$getScaledBatchLimit(IPatternDetails patternDetails, AEKey key,
            long amountPerCraft, long requestedCopies);

    /**
     * Begins one scaled push for a single input key. {@code completeInputs}
     * contains the full admitted amount.
     */
    void molecularmanipulator$beginScaledBatch(KeyCounter[] completeInputs);

    /**
     * Finishes the push and classifies the provider's ownership of the inputs.
     */
    PushResult molecularmanipulator$endScaledBatch(boolean accepted);

    /**
     * Clears a partially armed context when the underlying provider throws.
     */
    void molecularmanipulator$abortScaledBatch();
}
