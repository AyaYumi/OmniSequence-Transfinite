package com.atir.molecularmanipulator.mixin;

import appeng.crafting.execution.CraftingCpuLogic;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import com.atir.molecularmanipulator.integration.thunderbolt.OmniThunderboltCapacity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

/** Optional Thunderbolt provider capacity for the shared Omni dispatcher. */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class ThunderboltOmniCpuDispatchMixin implements OmniCapacityTarget.Thunderbolt {
    @Override
    public @Nullable OmniCapacityAdmission omniDispatch$prepareThunderbolt(OmniCapacityRequest request) {
        return OmniThunderboltCapacity.prepare(request);
    }
}
