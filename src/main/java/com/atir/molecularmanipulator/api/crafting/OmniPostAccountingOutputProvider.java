package com.atir.molecularmanipulator.api.crafting;

/**
 * Optional provider capability for outputs that are ready before AE2 records
 * the corresponding crafting-job {@code waitingFor} amounts.
 *
 * <p>The crafting CPU invokes this only after its output accounting has
 * completed. Implementations must only retry delivery of already-owned output;
 * they must not execute a recipe again. Any amount that AE2 cannot accept must
 * remain owned by the provider for its normal retry path.</p>
 */
public interface OmniPostAccountingOutputProvider {
    void flushOutputsAfterCpuAccounting();
}
