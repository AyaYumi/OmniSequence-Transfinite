package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.atir.molecularmanipulator.storage.InfiniteStorageAmounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps the saturated storage sentinel compact in terminal slots. */
@Mixin(value = AEKey.class, remap = false)
public abstract class AEKeyAmountMixin {
    @Inject(method = "formatAmount", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$formatInfiniteAmount(
            long amount,
            AmountFormat format,
            CallbackInfoReturnable<String> callback) {
        if (amount == InfiniteStorageAmounts.DISPLAY_AMOUNT && format != AmountFormat.FULL) {
            callback.setReturnValue(InfiniteStorageAmounts.DISPLAY_TEXT);
        }
    }
}
