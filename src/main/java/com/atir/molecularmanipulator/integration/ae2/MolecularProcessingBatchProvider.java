package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import org.jetbrains.annotations.Nullable;

/** Internal capability for counted delivery to an AE2 pattern provider's external target. */
public interface MolecularProcessingBatchProvider {
    static boolean supports(ICraftingProvider provider, IPatternDetails pattern) {
        // ExtendedAE's regular block and part instantiate this exact logic class too.
        return provider instanceof MolecularProcessingBatchProvider
                && provider.getClass() == PatternProviderLogic.class
                && !MolecularBatchCraftingProvider.requiresSerialDispatch(provider)
                && pattern != null && pattern.getClass() == AEProcessingPattern.class
                && pattern.supportsPushInputsToExternalInventory();
    }

    @Nullable
    Target molecularmanipulator$prepareProcessingBatch(IPatternDetails pattern, KeyCounter[] oneCraft);

    interface Target {
        /** Returns the number of recipes started. The provider retains any remainder of the last recipe. */
        long push(long recipes);
    }
}
