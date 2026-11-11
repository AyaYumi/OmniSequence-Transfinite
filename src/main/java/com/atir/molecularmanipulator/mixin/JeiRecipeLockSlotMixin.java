package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.RecipeLockTooltips;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Group;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Include rich/custom item, fluid and AE-key output tooltips, which can skip ItemTooltipEvent. */
@Mixin(targets = "mezz.jei.library.gui.ingredients.RecipeSlot", remap = false)
public abstract class JeiRecipeLockSlotMixin {
    // JEI 19.27 builds the rich tooltip here; newer JEI moved its callbacks to addTooltip below.
    // Exactly one path must match: never silently lose the hint or append it twice.
    @Group(name = "molecularmanipulator$researchTooltip", min = 1, max = 1)
    @Inject(method = "getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Lmezz/jei/api/ingredients/ITypedIngredient;)V",
            at = @At("TAIL"), require = 0)
    private void molecularmanipulator$researchRequirements(ITooltipBuilder tooltip, ITypedIngredient<?> ingredient,
                                                          CallbackInfo callback) {
        if (((IRecipeSlotView) this).getRole() == RecipeIngredientRole.OUTPUT) {
            RecipeLockTooltips.appendCurrentRecipe(tooltip::add);
        }
    }

    @Group(name = "molecularmanipulator$researchTooltip", min = 1, max = 1)
    @Inject(method = "addTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;)V",
            at = @At("TAIL"), require = 0)
    private void molecularmanipulator$researchRequirementsAfterCallbacks(ITooltipBuilder tooltip,
                                                                         CallbackInfo callback) {
        if (((IRecipeSlotView) this).getRole() == RecipeIngredientRole.OUTPUT) {
            RecipeLockTooltips.appendCurrentRecipe(tooltip::add);
        }
    }
}
