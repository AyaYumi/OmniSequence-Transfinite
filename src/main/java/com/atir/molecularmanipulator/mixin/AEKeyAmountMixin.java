package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.atir.molecularmanipulator.storage.InfiniteStorageAmounts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Long.MAX_VALUE compact in terminal slots without rendering it as infinity. */
@Mixin(value = AEKey.class, remap = false)
public abstract class AEKeyAmountMixin {
    @Inject(method = "formatAmount", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$formatMaximumAmount(
            long amount,
            AmountFormat format,
            CallbackInfoReturnable<String> callback) {
        if (amount != InfiniteStorageAmounts.DISPLAY_AMOUNT) {
            return;
        }
        if (format == AmountFormat.SLOT) {
            callback.setReturnValue(InfiniteStorageAmounts.DISPLAY_TEXT);
        } else if (format == AmountFormat.SLOT_LARGE_FONT) {
            callback.setReturnValue(InfiniteStorageAmounts.LARGE_FONT_DISPLAY_TEXT);
        }
    }
}
