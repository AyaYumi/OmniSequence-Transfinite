package com.atir.molecularmanipulator.mixin;

import java.util.Set;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "appeng.me.cells.CreativeCellInventory", remap = false)
public abstract class CreativeCellInventoryMixin {
    @Shadow
    @Final
    private Set<AEKey> configured;

    @Inject(method = "getAvailableStacks", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$exposeLongAmount(KeyCounter output, CallbackInfo callback) {
        for (AEKey key : this.configured) {
            output.add(key, Long.MAX_VALUE);
        }
        callback.cancel();
    }
}
