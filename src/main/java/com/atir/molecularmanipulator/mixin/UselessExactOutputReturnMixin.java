package com.atir.molecularmanipulator.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.AdvancedAlloyFurnaceAeManager", remap = false)
public abstract class UselessExactOutputReturnMixin {
    @Inject(method = "outputSegmentBudget", at = @At("RETURN"), cancellable = true, require = 0)
    private void omnisequence$directAdmissionBudget(CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(com.atir.molecularmanipulator.integration.useless.OmniDirectAdmission.segmentBudget(this, callback.getReturnValue()));
    }

    @Inject(method = "hardOutputChunkBudget", at = @At("RETURN"), cancellable = true, require = 0)
    private void omnisequence$directHardChunkBudget(CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(com.atir.molecularmanipulator.integration.useless.UselessExactOutputReturn
                .directChunkBudget(this, callback.getReturnValue()));
    }
    // The 2.3.8 API moved output budgeting inside the flush pass and removed
    // updateOutputSegmentBudget().  Running at the pass head works for both
    // layouts: the exact receiver removes/debits what it owns, then UselessMod
    // continues with ordinary network insertion for any remainder.
    @Inject(method = "flushQueuedCraftingOutputs", at = @At("HEAD"), require = 0)
    private void omnisequence$directReturn(boolean force, CallbackInfo callback) {
        if (!force) com.atir.molecularmanipulator.integration.useless.UselessExactOutputReturn.flush(this);
    }
}
