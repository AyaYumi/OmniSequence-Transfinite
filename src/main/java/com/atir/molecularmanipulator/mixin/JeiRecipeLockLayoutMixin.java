package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.RecipeLockTooltips;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mezz.jei.api.gui.builder.ITooltipBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Bind hover text to the displayed recipe ID, never to a guess from its output item. */
@Mixin(targets = "mezz.jei.library.gui.recipes.RecipeLayout", remap = false)
public abstract class JeiRecipeLockLayoutMixin<R> {
    @Shadow public abstract IRecipeCategory<R> getRecipeCategory();
    @Shadow public abstract R getRecipe();

    @WrapMethod(method = "drawOverlays")
    private void molecularmanipulator$recipeTooltipContext(GuiGraphics graphics, int mouseX, int mouseY,
                                                           Operation<Void> original) {
        var recipe = getRecipe();
        try (var ignored = RecipeLockTooltips.recipeContext(getRecipeCategory().getRegistryName(recipe), recipe)) {
            original.call(graphics, mouseX, mouseY);
        }
    }

    @WrapOperation(method = "drawOverlays", at = @At(value = "INVOKE", target =
            "Lmezz/jei/api/recipe/category/IRecipeCategory;getTooltip(Lmezz/jei/api/gui/builder/ITooltipBuilder;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;DD)V"))
    private void molecularmanipulator$recipeBackgroundTooltip(IRecipeCategory<R> category, ITooltipBuilder tooltip,
            R recipe, IRecipeSlotsView slots, double mouseX, double mouseY, Operation<Void> original) {
        original.call(category, tooltip, recipe, slots, mouseX, mouseY);
        RecipeLockTooltips.appendCurrentRecipe(tooltip::add);
    }
}
