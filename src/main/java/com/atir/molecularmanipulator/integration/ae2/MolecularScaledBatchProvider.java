package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.GenericStack;
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
     * Returns the whole-recipe capacity of an AE2 external inventory. The
     * ingredients represent one recipe across independent storage types.
     */
    long molecularmanipulator$getScaledBatchLimit(IPatternDetails patternDetails,
            GenericStack[] ingredients, long requestedCopies);

    /**
     * Begins one scaled push. {@code completeInputs} contains the full admitted
     * amount for every ingredient.
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
