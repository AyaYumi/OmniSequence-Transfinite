package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

public interface MolecularBatchCraftingProvider {
    boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails);

    default long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return Long.MAX_VALUE;
    }

    static boolean supports(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider instanceof MolecularBatchCraftingProvider batchProvider) {
            return batchProvider.molecularmanipulator$supportsBatching(patternDetails);
        }
        return false;
    }

    static boolean supportsOmniDispatch(ICraftingProvider provider, IPatternDetails patternDetails) {
        // Only providers with an explicit batch contract may accept an entire
        // task at once. Standard AE2/ExtendedAE providers must report work one
        // actually accepted pattern at a time so crafting status stays truthful.
        return supports(provider, patternDetails);
    }

    static long getBatchLimit(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (provider instanceof MolecularBatchCraftingProvider batchProvider
                && batchProvider.molecularmanipulator$supportsBatching(patternDetails)) {
            return Math.max(0, batchProvider.molecularmanipulator$getBatchLimit(patternDetails));
        }
        return 0;
    }
}
