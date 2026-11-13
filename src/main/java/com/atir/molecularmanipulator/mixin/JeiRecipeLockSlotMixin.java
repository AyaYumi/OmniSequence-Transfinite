package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.RecipeLockTooltips;
import com.llamalad7.mixinextras.sugar.Local;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Include rich/custom item, fluid and AE-key output tooltips, which can skip ItemTooltipEvent. */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public abstract class JeiRecipeLockSlotMixin {
    // JEI 15.74 moved the shared rich-tooltip path to addTooltip; 15.49 uses the typed overload.
    // Both drawing and API tooltip queries reach these methods, after the slot's callbacks.
    @Inject(method = {
            "addTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;)V",
            "getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lmezz/jei/api/ingredients/ITypedIngredient;)V"
    }, at = @At("TAIL"))
    private void molecularmanipulator$researchRequirements(CallbackInfo callback,
            @Local(argsOnly = true) ITooltipBuilder tooltip) {
        if (((IRecipeSlotView) this).getRole() == RecipeIngredientRole.OUTPUT) {
            RecipeLockTooltips.appendCurrentRecipe(tooltip::add);
        }
    }
}
