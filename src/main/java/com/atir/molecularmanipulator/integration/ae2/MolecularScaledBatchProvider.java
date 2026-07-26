package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
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
     * Returns whether this provider will use AE2's generic external-inventory
     * path for this pattern. Implementations must return {@code false} when the
     * push could be routed through an unknown dedicated crafting machine.
     */
    boolean molecularmanipulator$supportsScaledBatch(IPatternDetails patternDetails);

    /**
     * Begins one atomic scaled push. {@code completeInputs} contains the entire
     * scaled recipe, while {@code firstInputs} contains one complete craft and
     * is used to preserve fair multi-ingredient seeding.
     */
    void molecularmanipulator$beginScaledBatch(
            KeyCounter[] completeInputs, KeyCounter[] firstInputs);

    /**
     * Finishes the push and classifies the provider's ownership of the inputs.
     */
    PushResult molecularmanipulator$endScaledBatch(boolean accepted);

    /**
     * Clears a partially armed context when the underlying provider throws.
     */
    void molecularmanipulator$abortScaledBatch();
}
