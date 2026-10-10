package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.integration.jei.JeiAeStackAmounts;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** AE2Utility's encode button resolves quantities separately from AE2's JEI converter. */
@Pseudo
@Mixin(targets = "com.lhy.ae2utility.network.RecipeTransferPacketHelper", remap = false)
public abstract class Ae2UtilityJeiAmountMixin {
    @ModifyReturnValue(method = "resolveEncodeSlotDisplayedCount(Lmezz/jei/api/gui/ingredient/IRecipeSlotView;Ljava/util/List;)J",
            at = @At("RETURN"), require = 0)
    private static long molecularmanipulator$restoreAeStackAmount(long original, IRecipeSlotView slot) {
        return JeiAeStackAmounts.amount(slot, original);
    }
}
