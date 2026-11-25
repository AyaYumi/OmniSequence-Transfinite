package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerBatchCallbacks;
import java.math.BigInteger;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.level.Level;

/** Shared CPU boundary: descriptors and default methods do not depend on optional integrations. */
public interface OmniCpuDispatchHost {
    /** Enable only after all transaction hooks below are implemented, and only for an owned Omni CPU. */
    default boolean omniDispatch$enabled() { return false; }
    default UUID omniDispatch$jobId() { return null; }
    default boolean omniDispatch$exactOrder() { return false; }
    default boolean omniDispatch$stopped() { return false; }
    default ListCraftingInventory omniDispatch$inventory() { throw new UnsupportedOperationException(); }
    /** Physical inventory plus Omni overflow; must never include projected/infinite planning stock. */
    default ICraftingInventory omniDispatch$physicalView() { return omniDispatch$inventory(); }
    /** Physical final-key stock needed by unfinished execution, including startup seeds. */
    default BigInteger omniDispatch$reservedInput(AEKey key) { return BigInteger.ZERO; }
    default BigInteger omniDispatch$remaining(IPatternDetails pattern) { return BigInteger.ZERO; }

    default OmniCpuDispatchLedger.Energy omniDispatch$dispatchEnergy() { throw new UnsupportedOperationException(); }
    default ExactBatchToken omniDispatch$bigIntegerToken(Map<AEKey, BigInteger> outputsPerCraft) { return null; }

    /** Run the selected native recipe after its single slot has been charged. Return actual accepted copies. */
    default int omniDispatch$executeNativeSingle(IPatternDetails pattern, ICraftingProvider provider,
            CraftingService crafting, IEnergyService energy, Level level) { return 0; }

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

    interface ExactBatchToken extends OmniBigIntegerBatchCallbacks {
        /** The provider push returned. Reconcile any synchronous production with the accepted prefix. */
        void confirmAccepted(BigInteger crafts);
    }

    interface Lease {
        /** Transfer the full reservation. Subsequent refunds have no effect. */
        void commit();
        /** Transfer the accepted finite inputs and return the unused reservation to the CPU. */
        void commit(Map<AEKey, BigInteger> accepted);
        void refund();
    }
    interface Accounting {
        void commit();
        void commit(OmniCpuDispatchReceipt actual);
        void rollback();
        void retain();
    }
}
