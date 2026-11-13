package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.integration.jei.JeiAeStackAmounts;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import java.util.List;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Restores native AE ingredient quantities before JEI fills a processing pattern. */
@Pseudo
@Mixin(targets = "appeng.integration.modules.jei.GenericEntryStackHelper", remap = false)
public abstract class JeiAeStackAmountMixin {
    // AE2 15 ships this integration itself and materializes each slot as a List, not a Stream.
    @ModifyReturnValue(method = "ofSlot(Lmezz/jei/api/gui/ingredient/IRecipeSlotView;)Ljava/util/List;",
            at = @At("RETURN"))
    private static List<GenericStack> molecularmanipulator$restoreAeStackAmounts(
            List<GenericStack> original, IRecipeSlotView slot) {
        return original.stream().map(stack -> JeiAeStackAmounts.restore(slot, stack)).toList();
    }
}
