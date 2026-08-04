package com.atir.molecularmanipulator.mixin;

import appeng.util.ReadableNumberConverter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ReadableNumberConverter.class, remap = false)
public abstract class ReadableNumberConverterMixin {
    @Inject(method = "format(JI)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$formatNegativeSentinel(long amount, int width,
            CallbackInfoReturnable<String> callback) {
        if (amount < 0) {
            callback.setReturnValue("\u221e");
        }
    }
}
