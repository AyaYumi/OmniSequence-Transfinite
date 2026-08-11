package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.crafting.CraftingPlanAmountLimits;
import com.atir.molecularmanipulator.integration.ae2.KeyCounterOverflowBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = KeyCounter.class, remap = false)
public abstract class KeyCounterMixin implements KeyCounterOverflowBridge {
    @Unique
    private boolean molecularmanipulator$unrepresentableAmount;

    @Inject(method = "addAll", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$saturateBulkOverflow(
            KeyCounter other, CallbackInfo callback) {
        var counter = (KeyCounter) (Object) this;
        if (((KeyCounterOverflowBridge) (Object) other)
                .molecularmanipulator$hasUnrepresentableAmount()) {
            molecularmanipulator$unrepresentableAmount = true;
        }
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
        if (CraftingPlanAmountLimits.additionOverflows(currentAmount, amount)) {
            molecularmanipulator$unrepresentableAmount = true;
            counter.set(key, amount > 0 ? Long.MAX_VALUE : Long.MIN_VALUE);
            callback.cancel();
        }
    }

    @Inject(method = { "reset", "clear" }, at = @At("HEAD"))
    private void molecularmanipulator$clearOverflowMarker(CallbackInfo callback) {
        molecularmanipulator$unrepresentableAmount = false;
    }

    @Override
    public boolean molecularmanipulator$hasUnrepresentableAmount() {
        return molecularmanipulator$unrepresentableAmount;
    }

    @Override
    public void molecularmanipulator$markUnrepresentableAmount() {
        molecularmanipulator$unrepresentableAmount = true;
    }
}
