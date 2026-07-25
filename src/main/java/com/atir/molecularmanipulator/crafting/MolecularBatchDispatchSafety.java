package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class MolecularBatchDispatchSafety {
    private static final int MAX_LOGGED_DIAGNOSTICS = 1024;
    private static final Set<String> LOGGED_DIAGNOSTICS = ConcurrentHashMap.newKeySet();

    private MolecularBatchDispatchSafety() {
    }

    public static long getAvailableBatchLimit(CraftingService craftingService,
            IPatternDetails patternDetails, Predicate<ICraftingProvider> supportsProvider) {
        if (!ModConfig.OMNI_BATCH_DISPATCH_ENABLED.get()) {
            return 0;
        }

        try {
            String unsafeReason = getUnsafePatternReason(patternDetails);
            if (unsafeReason != null) {
                logFallbackOnce(patternDetails, unsafeReason, null);
                return 0;
            }

            var providers = craftingService.getProviders(patternDetails);
            if (providers == null) {
                logFallbackOnce(patternDetails, "provider_iterable_missing", null);
                return 0;
            }

            var iterator = providers.iterator();
            long batchLimit = 0;
            while (iterator.hasNext()) {
                var provider = iterator.next();
                if (provider != null && !provider.isBusy() && supportsProvider.test(provider)) {
                    batchLimit = Math.max(batchLimit,
                            MolecularBatchCraftingProvider.getBatchLimit(provider, patternDetails));
                }
            }
            return batchLimit;
        } catch (RuntimeException exception) {
            logFallbackOnce(patternDetails, "provider_iteration_unstable", exception);
            return 0;
        }
    }

    private static String getUnsafePatternReason(IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return "missing_pattern_details";
        }

        boolean allowSubstitution = ModConfig.OMNI_BATCH_ALLOW_SUBSTITUTION_PATTERNS.get();
        if (!allowSubstitution && patternDetails instanceof AECraftingPattern craftingPattern
                && craftingPattern.canSubstitute()) {
            return "crafting_item_substitution_enabled";
        }

        var inputs = patternDetails.getInputs();
        if (inputs == null) {
            return "missing_pattern_inputs";
        }
        for (var input : inputs) {
            if (input == null) {
                return "invalid_pattern_input";
            }
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs == null || possibleInputs.length == 0) {
                return "missing_input_templates";
            }
            if (!allowSubstitution && possibleInputs.length != 1) {
                return "multiple_input_templates";
            }
            for (var possibleInput : possibleInputs) {
                if (possibleInput == null || possibleInput.what() == null || possibleInput.amount() <= 0) {
                    return "invalid_input_template";
                }
                if (input.getRemainingKey(possibleInput.what()) != null) {
                    return "container_or_reusable_input";
                }
            }
        }
        return null;
    }

    private static void logFallbackOnce(IPatternDetails patternDetails, String reason,
            RuntimeException exception) {
        if (!ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
            return;
        }

        String pattern = describePattern(patternDetails);
        String diagnosticKey = pattern + ':' + reason;
        if (LOGGED_DIAGNOSTICS.size() >= MAX_LOGGED_DIAGNOSTICS) {
            LOGGED_DIAGNOSTICS.clear();
        }
        if (!LOGGED_DIAGNOSTICS.add(diagnosticKey)) {
            return;
        }

        if (exception == null) {
            MolecularManipulator.LOGGER.info("Molecular batch dispatch fallback: pattern={}, reason={}",
                    pattern, reason);
            return;
        }
        MolecularManipulator.LOGGER.warn(
                "Molecular batch dispatch provider scan failed: pattern={}, reason={}",
                pattern, reason, exception);
    }

    private static String describePattern(IPatternDetails patternDetails) {
        if (patternDetails == null) {
            return "null";
        }
        try {
            return String.valueOf(patternDetails.getDefinition());
        } catch (RuntimeException exception) {
            return patternDetails.getClass().getName();
        }
    }
}
