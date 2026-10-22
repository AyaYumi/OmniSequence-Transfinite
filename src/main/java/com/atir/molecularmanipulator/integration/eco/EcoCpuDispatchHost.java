package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

/** Shared CPU boundary: no optional ECO classes occur in descriptors or default method bodies. */
public interface EcoCpuDispatchHost {
    /** Enable only after all transaction hooks below are implemented, and only for an owned Omni CPU. */
    default boolean ecoDispatch$enabled() { return false; }
    default UUID ecoDispatch$jobId() { return null; }
    default boolean ecoDispatch$exactOrder() { return false; }
    default boolean ecoDispatch$stopped() { return false; }
    default ListCraftingInventory ecoDispatch$inventory() { throw new UnsupportedOperationException(); }
    /** Physical inventory plus Omni overflow; must never include projected/infinite planning stock. */
    default ICraftingInventory ecoDispatch$physicalView() { return ecoDispatch$inventory(); }
    /** Physical final-key stock needed by unfinished execution, including startup seeds. */
    default BigInteger ecoDispatch$reservedInput(AEKey key) { return BigInteger.ZERO; }
    default BigInteger ecoDispatch$remaining(IPatternDetails pattern) { return BigInteger.ZERO; }

    /** Reserve ALL exact inputs, including the preview copy, atomically. Null means nothing moved. */
    default Lease ecoDispatch$reserveExact(Map<AEKey, BigInteger> inputs) { throw new UnsupportedOperationException(); }

    /**
     * Stage waiting outputs/returns before submit. Null means no ledger changed. On commit, advance
     * exact task/UI progress ONCE and remove finished AE rows; do not perform AE's extra decrement.
     * Do not call ecoOmni$accepted here: the dispatcher owns its independent ECO phase token.
     * retain() seals staging without task advancement, for later manual reconciliation.
     */
    default Accounting ecoDispatch$stage(EcoCpuDispatchReceipt receipt) { throw new UnsupportedOperationException(); }
    default void ecoDispatch$markDirty() { throw new UnsupportedOperationException(); }
    default void ecoDispatch$pause(EcoCpuDispatchReceipt receipt, String reason) { }

    /**
     * Job-directed exact reception, including SIMULATE. Return accepted ownership, never an estimate.
     * Final products must honor requester/storage acceptance and retain any undeliverable owned tail.
     * The CPU may own all expected products as physical stock, including feedback and surplus;
     * only the amount actually accepted by the final destination advances the goal.
     * This is a separate protocol from nativeBigInteger provider callbacks.
     */
    default BigInteger ecoDispatch$insertExact(UUID jobId, AEKey key, BigInteger amount, Actionable mode) {
        return BigInteger.ZERO;
    }

    interface Lease {
        /** Must not throw, transfer the entire reservation, and make subsequent refund a no-op. */
        void commit();
        void refund();
    }
    interface Accounting {
        void commit();
        void rollback();
        void retain();
    }
}
