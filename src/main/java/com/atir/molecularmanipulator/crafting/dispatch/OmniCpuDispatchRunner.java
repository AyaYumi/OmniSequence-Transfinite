package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.networking.crafting.ICraftingPlan;
import java.math.BigInteger;

/** Optional plan admission and lifecycle hooks with integration-independent descriptors. */
public interface OmniCpuDispatchRunner {
    boolean omniDispatch$isExactPlan(ICraftingPlan plan);
    default BigInteger omniDispatch$storageBytes(ICraftingPlan plan) {
        return BigInteger.valueOf(plan.bytes());
    }
    /** Existing parent orders may advance their next physical child after a level downgrade. */
    default boolean omniDispatch$continuingJob() { return false; }
    /** Hold optional plan lifecycle hooks when the shared ledger requires reconciliation. */
    void omniDispatch$suspend(String reason);
}
