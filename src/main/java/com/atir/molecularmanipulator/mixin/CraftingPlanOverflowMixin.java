package com.atir.molecularmanipulator.mixin;

import appeng.crafting.CraftingPlan;
import com.atir.molecularmanipulator.integration.ae2.CraftingPlanOverflowBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingPlan.class, remap = false)
public abstract class CraftingPlanOverflowMixin implements CraftingPlanOverflowBridge {
    @Unique
    private boolean molecularmanipulator$unrepresentableTotal;

    @Inject(method = "simulation", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$preventUnsafeSubmission(
            CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$unrepresentableTotal) {
            callback.setReturnValue(true);
        }
    }

    @Override
    public boolean molecularmanipulator$hasUnrepresentableTotal() {
        return molecularmanipulator$unrepresentableTotal;
    }

    @Override
    public void molecularmanipulator$markUnrepresentableTotal() {
        molecularmanipulator$unrepresentableTotal = true;
    }
}
