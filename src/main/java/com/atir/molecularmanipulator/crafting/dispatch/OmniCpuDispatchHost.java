package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;

/** Shared CPU boundary: descriptors and default methods do not depend on optional integrations. */
public interface OmniCpuDispatchHost {
    /** Enable only after all transaction hooks below are implemented, and only for an owned Omni CPU. */
    default boolean omniDispatch$enabled() { return false; }
    default UUID omniDispatch$jobId() { return null; }
    default boolean omniDispatch$exactOrder() { return false; }
    /** Physical CPUs use one real recipe per provider call; virtual CPUs may aggregate. */
    default boolean omniDispatch$virtualCpu() { return true; }
    default boolean omniDispatch$stopped() { return false; }
    default boolean omniDispatch$paused() { return false; }
    /** Ordinary task selected by the CPU's shared native/fallback rotation. */
    default IPatternDetails omniDispatch$preferredPattern() { return null; }
    default ListCraftingInventory omniDispatch$inventory() { throw new UnsupportedOperationException(); }
    /** Physical inventory plus Omni overflow; must never include projected/infinite planning stock. */
    default ICraftingInventory omniDispatch$physicalView() { return omniDispatch$inventory(); }
    /** Physical final-key stock needed by unfinished execution, including startup seeds. */
    default BigInteger omniDispatch$reservedInput(AEKey key) { return BigInteger.ZERO; }
    default BigInteger omniDispatch$remaining(IPatternDetails pattern) { return BigInteger.ZERO; }

    /** Reserve ALL exact inputs, including the preview copy, atomically. Null means nothing moved. */
    default Lease omniDispatch$reserveExact(Map<AEKey, BigInteger> inputs) { throw new UnsupportedOperationException(); }

    /**
     * Stage waiting outputs/returns before submit. Null means no ledger changed. On commit, advance
     * exact task/UI progress ONCE and remove finished AE rows; do not perform AE's extra decrement.
     * Phase advancement belongs to the dispatcher and must not be repeated by these hooks.
     * retain() seals staging without task advancement, for later manual reconciliation.
     */
    default Accounting omniDispatch$stage(OmniCpuDispatchReceipt receipt) { throw new UnsupportedOperationException(); }
    default void omniDispatch$markDirty() { throw new UnsupportedOperationException(); }
    default void omniDispatch$pause(OmniCpuDispatchReceipt receipt, String reason) { }

    /**
     * Job-directed exact reception, including SIMULATE. Return accepted ownership, never an estimate.
     * Final products must honor requester/storage acceptance and retain any undeliverable owned tail.
     * A matched live job owns the entire offer as physical stock, including unexpected byproducts,
     * feedback and surplus. Only its expected portion settles waiting, and only the amount actually
     * accepted by the final destination advances the goal. Mismatched/retired jobs accept nothing.
     * This is a separate protocol from nativeBigInteger provider callbacks.
     */
    default BigInteger omniDispatch$insertExact(UUID jobId, AEKey key, BigInteger amount, Actionable mode) {
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
