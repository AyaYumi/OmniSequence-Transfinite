package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.integration.jei.JeiAeStackAmounts;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.stream.Stream;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Restores native AE ingredient quantities before JEI fills a processing pattern. */
@Pseudo
@Mixin(targets = "tamaized.ae2jeiintegration.integration.modules.jei.GenericEntryStackHelper", remap = false)
public abstract class JeiAeStackAmountMixin {
    @ModifyReturnValue(method = "ofSlot", at = @At("RETURN"))
    private static Stream<GenericStack> molecularmanipulator$restoreAeStackAmounts(
            Stream<GenericStack> original, IRecipeSlotView slot) {
        return original.map(stack -> JeiAeStackAmounts.restore(slot, stack));
    }
}
