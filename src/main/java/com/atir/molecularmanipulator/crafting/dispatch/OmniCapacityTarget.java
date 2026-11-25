package com.atir.molecularmanipulator.crafting.dispatch;

import org.jetbrains.annotations.Nullable;

/** Capacity admission for a provider's own inventory-backed target. */
public interface OmniCapacityTarget {
    @Nullable OmniCapacityAdmission omniDispatch$prepareTarget(OmniCapacityRequest request);

    /** Optional Thunderbolt endpoint without a class-loading dependency on the mod. */
    interface Thunderbolt {
        @Nullable OmniCapacityAdmission omniDispatch$prepareThunderbolt(OmniCapacityRequest request);
    }
}
