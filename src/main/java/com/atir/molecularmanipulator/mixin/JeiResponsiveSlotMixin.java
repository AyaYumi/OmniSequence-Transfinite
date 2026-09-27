package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.Optional;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scaled machine screens supply JEI ingredients themselves; vanilla slot bounds overlap the overlay.
 *  The factory argument changed package between JEI 19.27 and 19.56, so it is captured by type
 *  through MixinExtras instead of being part of the injected method descriptor. */
@Mixin(targets = "mezz.jei.library.gui.helpers.ScreenHelper", remap = false)
public abstract class JeiResponsiveSlotMixin {
    @Inject(method = "getSlotIngredientUnderMouse", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$skipVanillaSlot(
            CallbackInfoReturnable<Optional<?>> callback,
            @Local(argsOnly = true) Screen screen) {
        if (screen instanceof ResponsiveContainerScreen<?>) callback.setReturnValue(Optional.empty());
    }

    @Inject(method = "getClickedIngredient", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$skipVanillaSlotArea(
            CallbackInfoReturnable<Optional<IClickableIngredient<ItemStack>>> callback,
            @Local(argsOnly = true) Slot slot,
            @Local(argsOnly = true) AbstractContainerScreen<?> screen) {
        if (screen instanceof ResponsiveContainerScreen<?>) callback.setReturnValue(Optional.empty());
    }
}
