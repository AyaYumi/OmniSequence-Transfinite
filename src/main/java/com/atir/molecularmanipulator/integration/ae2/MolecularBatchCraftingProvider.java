package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.config.ModConfig;

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

    static long getBatchLimit(ICraftingProvider provider, IPatternDetails patternDetails) {
        return getBatchLimit(provider, patternDetails, null);
    }

    static long getBatchLimit(ICraftingProvider provider, IPatternDetails patternDetails,
            KeyCounter[] firstInputs) {
        if (provider instanceof MolecularBatchCraftingProvider batchProvider
                && batchProvider.molecularmanipulator$supportsBatching(patternDetails)) {
            return Math.max(0, batchProvider.molecularmanipulator$getBatchLimit(patternDetails));
        }

        if (!supportsOmniDispatch(provider, patternDetails) || firstInputs == null) {
            return 0;
        }

        long itemsPerCraft = 0;
        for (var input : firstInputs) {
            if (input == null || input.isEmpty()) {
                return 0;
            }
            for (var entry : input) {
                if (!(entry.getKey() instanceof AEItemKey) || entry.getLongValue() <= 0) {
                    return 0;
                }
                if (itemsPerCraft > Long.MAX_VALUE - entry.getLongValue()) {
                    return 0;
                }
                itemsPerCraft += entry.getLongValue();
            }
        }
        if (itemsPerCraft <= 0) {
            return 0;
        }

        long queuedItemLimit = ModConfig.OMNI_PROVIDER_MAX_QUEUED_ITEMS.get() / itemsPerCraft;
        return Math.max(0, queuedItemLimit);
    }
}