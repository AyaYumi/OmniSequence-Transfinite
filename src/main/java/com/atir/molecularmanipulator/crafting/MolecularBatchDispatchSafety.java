package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class MolecularBatchDispatchSafety {
    private MolecularBatchDispatchSafety() {
    }

    public enum ExtractedInputShape {
        INVALID,
        ONE_KEY,
        MULTIPLE_KEYS
    }

    public static boolean isBatchablePattern(IPatternDetails patternDetails) {
        if (com.atir.molecularmanipulator.crafting.OmniNativeSmartDoubling.isExternallyManaged(patternDetails)) return false;
        if (!ModConfig.OMNI_BATCH_DISPATCH_ENABLED.get()) {
            return false;
        }
        try {
            return hasValidPatternInputs(patternDetails);
        } catch (RuntimeException exception) {
            RateLimitedLog.warn("Molecular batch pattern validation failed; using ordinary dispatch", exception);
            return false;
        }
    }

    public static long getAvailableBatchLimit(CraftingService craftingService,
            IPatternDetails patternDetails, KeyCounter[] firstInputs,
            Predicate<ICraftingProvider> supportsProvider) {
        long batchLimit = 0;
        for (var offer : getAvailableBatchOffers(
                craftingService, patternDetails, firstInputs, supportsProvider)) {
            batchLimit = Math.max(batchLimit, offer.batchLimit());
        }
        return batchLimit;
    }

    /**
     * Returns whether one extracted recipe contains multiple distinct material
     * keys. Invalid extracted input is treated conservatively as multi-key.
     */
    public static ExtractedInputShape classifyExtractedInputs(
            KeyCounter[] firstInputs) {
        if (firstInputs == null || firstInputs.length == 0) {
            return ExtractedInputShape.INVALID;
        }
        try {
            AEKey firstKey = null;
            boolean foundInput = false;
            boolean multipleKeys = false;
            for (var input : firstInputs) {
                if (input == null) {
                    return ExtractedInputShape.INVALID;
                }
                boolean holderHasInput = false;
                for (var entry : input) {
                    holderHasInput = true;
                    var key = entry.getKey();
                    long amount = entry.getLongValue();
                    if (key == null || amount <= 0) {
                        return ExtractedInputShape.INVALID;
                    }
                    if (!foundInput) {
                        firstKey = key;
                        foundInput = true;
                    } else if (!firstKey.equals(key)) {
                        multipleKeys = true;
                    }
                }
                if (!holderHasInput) {
                    return ExtractedInputShape.INVALID;
                }
            }
            if (!foundInput) {
                return ExtractedInputShape.INVALID;
            }
            return multipleKeys
                    ? ExtractedInputShape.MULTIPLE_KEYS
                    : ExtractedInputShape.ONE_KEY;
        } catch (RuntimeException exception) {
            return ExtractedInputShape.INVALID;
        }
    }

    /**
     * Invalid extracted input remains conservative for callers that only need a
     * yes/no batching guard.
     */
    public static boolean hasMultipleDistinctInputKeys(
            KeyCounter[] firstInputs) {
        return classifyExtractedInputs(firstInputs)
                != ExtractedInputShape.ONE_KEY;
    }

    public static List<BatchOffer> getAvailableBatchOffers(CraftingService craftingService,
            IPatternDetails patternDetails, KeyCounter[] firstInputs,
            Predicate<ICraftingProvider> supportsProvider) {
        try {
            if (!isBatchablePattern(patternDetails)) {
                return List.of();
            }

            var providers = craftingService.getProviders(patternDetails);
            if (providers == null) {
                return List.of();
            }

            var iterator = providers.iterator();
            var offers = new ArrayList<BatchOffer>();
            while (iterator.hasNext()) {
                var provider = iterator.next();
                if (provider != null && !provider.isBusy() && supportsProvider.test(provider)) {
                    long batchLimit = MolecularBatchCraftingProvider.getBatchLimit(
                            provider, patternDetails, firstInputs);
                    if (batchLimit > 0) {
                        offers.add(new BatchOffer(provider, batchLimit));
                    }
                }
            }
            return offers;
        } catch (RuntimeException exception) {
            RateLimitedLog.warn("Molecular batch provider scan failed; using ordinary dispatch", exception);
            return List.of();
        }
    }

    public record BatchOffer(ICraftingProvider provider, long batchLimit) {
    }
    private static boolean hasValidPatternInputs(IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return false;
        }

        var inputs = patternDetails.getInputs();
        if (inputs == null) {
            return false;
        }
        for (var input : inputs) {
            if (input == null) {
                return false;
            }
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs == null || possibleInputs.length == 0) {
                return false;
            }
            for (var possibleInput : possibleInputs) {
                if (possibleInput == null || possibleInput.what() == null || possibleInput.amount() <= 0) {
                    return false;
                }
                // Remainders are classified from the actual extracted key by
                // MolecularBatchCraftingExtractor. Only explicit molecular
                // providers may opt into reusable-input expansion; buckets,
                // random damage and unknown data-component transitions still
                // fall back.
            }
        }
        return true;
    }
}
