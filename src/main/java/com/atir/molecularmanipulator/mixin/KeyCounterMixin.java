package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyCounter.class, remap = false)
public abstract class KeyCounterMixin {
    @Inject(method = "addAll", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$saturateBulkOverflow(
            KeyCounter other, CallbackInfo callback) {
        var counter = (KeyCounter) (Object) this;
        for (var entry : other) {
            counter.add(entry.getKey(), entry.getLongValue());
        }
        callback.cancel();
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$saturateOverflow(
            AEKey key, long amount, CallbackInfo callback) {
        var counter = (KeyCounter) (Object) this;
        long currentAmount = counter.get(key);
        if (amount > 0 && currentAmount > Long.MAX_VALUE - amount) {
            counter.set(key, Long.MAX_VALUE);
            callback.cancel();
        } else if (amount < 0 && currentAmount < Long.MIN_VALUE - amount) {
            counter.set(key, Long.MIN_VALUE);
            callback.cancel();
        }
    }
}
