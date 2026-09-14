package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.ResponsiveContainerScreen;
import java.util.Optional;
import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.runtime.IClickableIngredient;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scaled machine screens supply JEI ingredients themselves; vanilla slot bounds overlap the overlay. */
@Mixin(targets = "mezz.jei.library.gui.helpers.ScreenHelper", remap = false)
public abstract class JeiResponsiveSlotMixin {
    @Inject(method = "getSlotIngredientUnderMouse", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$skipVanillaSlot(IClickableIngredientFactory factory, Screen screen,
            CallbackInfoReturnable<Optional<?>> callback) {
        if (screen instanceof ResponsiveContainerScreen<?>) callback.setReturnValue(Optional.empty());
    }

    @Inject(method = "getClickedIngredient", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$skipVanillaSlotArea(IClickableIngredientFactory factory, Slot slot,
            AbstractContainerScreen<?> screen,
            CallbackInfoReturnable<Optional<IClickableIngredient<ItemStack>>> callback) {
        if (screen instanceof ResponsiveContainerScreen<?>) callback.setReturnValue(Optional.empty());
    }
}
