package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputProvider;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;

import java.util.UUID;

public interface MolecularBatchCraftingProvider extends OmniPostAccountingOutputProvider {

    boolean molecularmanipulator$supportsBatching(IPatternDetails patternDetails);

    default long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails) {
        return Long.MAX_VALUE;
    }

    /** Capacity for copies of the complete, actual output of one craft. */
    default long molecularmanipulator$getBatchLimit(IPatternDetails patternDetails, KeyCounter unitOutputs) {
        return molecularmanipulator$getBatchLimit(patternDetails);
    }

    /**
     * Internal capability used only by this mod's three molecular crafting
     * machines. The public Omni Batch Provider API intentionally remains
     * consumable-only.
     */
    default boolean molecularmanipulator$supportsReusableBatching(
            IPatternDetails patternDetails) {
        return false;
    }

    enum ReusableValidation { READY, PENDING, REJECTED }

    /** Read-only verification of the proposed ownership and wear transitions. */
    default ReusableValidation molecularmanipulator$validateReusableBatch(
            IPatternDetails pattern, MolecularReusableBatchPlan plan, UUID jobId) {
        for (var input : plan.inputs()) {
            if (input.mode() == MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE) {
                return ReusableValidation.REJECTED;
            }
        }
        return ReusableValidation.READY;
    }

    /** A cancelled reusable job must also fit in the machine's refund inventory. */
    default boolean molecularmanipulator$canBufferRefund(IPatternDetails pattern, KeyCounter refund) {
        return molecularmanipulator$getBatchLimit(pattern, refund) > 0;
    }

    /**
     * Returns outputs from an accepted aggregate after the CPU has recorded
     * them in its waiting-for inventory. Implementations may keep the normal
     * next-tick retry when the network cannot accept the complete output yet.
     */
    default void molecularmanipulator$flushOutputsAfterCpuAccounting() {
    }

    @Override
    default void flushOutputsAfterCpuAccounting() {
        molecularmanipulator$flushOutputsAfterCpuAccounting();
    }

    /**
     * AE2 blocking mode must inspect the target again between complete recipes.
     * An aggregate provider call would perform that check only once for the
     * whole batch and would therefore weaken the configured blocking semantics.
     */
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
