package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AmountFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = AEKeyType.class, remap = false)
public abstract class AEKeyTypeMixin {
    @Inject(method = "formatAmount", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$formatInfiniteAmount(long amount, AmountFormat format,
            CallbackInfoReturnable<String> callback) {
        if (amount == Long.MAX_VALUE) {
            callback.setReturnValue("\u221e");
        }
    }
}
