package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import com.glodblock.github.extendedae.common.inventory.InfinityCellInventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = InfinityCellInventory.class, remap = false)
public abstract class ExtendedAEInfinityCellInventoryMixin {
    @Shadow
    @Final
    private AEKey record;

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$exposeLongAmount(KeyCounter output, CallbackInfo callback) {
        output.set(this.record, Long.MAX_VALUE);
        callback.cancel();
    }
}
