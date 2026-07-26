package com.atir.molecularmanipulator.mixin;

import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "de.mari_023.ae2wtlib.wct.CraftingTerminalHandler", remap = false)
public abstract class AE2WTLibCraftingTerminalHandlerMixin {
    @Inject(method = "getAccessibleAmount", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$saturateAccessibleAmount(ItemStack stack,
            CallbackInfoReturnable<Long> callback) {
        if (callback.getReturnValue() < 0) {
            callback.setReturnValue(Long.MAX_VALUE);
        }
    }
}