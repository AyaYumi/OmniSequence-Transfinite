package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;

public interface MolecularBatchCraftingProvider {

    boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails);

    default long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return Long.MAX_VALUE;
    }

    /** Internal capability for the mod's molecular machines only. */
    default boolean molecularmanipulator$supportsReusableBatching(
            IPatternDetails patternDetails) {
        return false;
    }

    /** Blocking providers must re-check their target between complete recipes. */
    static boolean requiresSerialDispatch(ICraftingProvider provider) {
        return provider instanceof PatternProviderLogic patternProvider
                && patternProvider.isBlocking();
    }

    static boolean supports(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (requiresSerialDispatch(provider)) {
            return false;
        }
        if (provider instanceof MolecularBatchCraftingProvider batchProvider) {
            return batchProvider.molecularmanipulator$supportsBatching(patternDetails);
        }
        return false;
    }

    static boolean supportsOmniDispatch(ICraftingProvider provider, IPatternDetails patternDetails) {
        if (requiresSerialDispatch(provider)) {
            return false;
        }
        if (supports(provider, patternDetails)) {
            return true;
        }

        // AE2 and ExtendedAE both instantiate this exact logic class for their
        // normal pattern providers. Processing inputs that could not be inserted
        // immediately remain owned by its persistent sendList, so a bounded
        // aggregate is still a truthful accepted dispatch. Subclasses are not
        // opted in implicitly because they may override the queue semantics.
        return provider != null
                && provider.getClass() == PatternProviderLogic.class
                && patternDetails != null
                && patternDetails.supportsPushInputsToExternalInventory();
    }

    static boolean supportsReusable(ICraftingProvider provider,
            IPatternDetails patternDetails) {
        return !requiresSerialDispatch(provider)
                && provider instanceof MolecularBatchCraftingProvider batchProvider
                && batchProvider.molecularmanipulator$supportsBatching(
                        patternDetails)
                && batchProvider.molecularmanipulator$supportsReusableBatching(
                        patternDetails);
    }

    static long getBatchLimit(ICraftingProvider provider, IPatternDetails patternDetails) {
        return getBatchLimit(provider, patternDetails, null);
    }

    static long getBatchLimit(ICraftingProvider provider, IPatternDetails patternDetails,
            KeyCounter[] firstInputs) {
        if (requiresSerialDispatch(provider)) {
            return 0;
        }
        if (provider instanceof MolecularBatchCraftingProvider batchProvider
                && batchProvider.molecularmanipulator$supportsBatching(patternDetails)) {
            return Math.max(0, batchProvider.molecularmanipulator$getBatchLimit(patternDetails));
        }

        return 0;
    }
}
