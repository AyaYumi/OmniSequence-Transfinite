package com.atir.molecularmanipulator.mixin;

import appeng.client.gui.me.items.SetProcessingPatternAmountScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Allows the large AE inputs used by fabrication recipes to be entered in the pattern terminal. */
@Mixin(value = SetProcessingPatternAmountScreen.class, remap = false)
public abstract class ProcessingPatternAmountScreenMixin {
    @Inject(method = "getMaxAmount", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$allowFabricationAmounts(CallbackInfoReturnable<Long> callback) {
        callback.setReturnValue(Long.MAX_VALUE);
    }
}
