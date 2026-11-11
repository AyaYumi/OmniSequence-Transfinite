package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.glodblock.github.glodium.recipe.RecipeSearchContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.function.Supplier;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Glodium 1.21-2.2: filter EAE's execution search and lastRecipe validation only inside a scope. */
@Mixin(targets = "com.glodblock.github.glodium.recipe.RecipeSearchContext", remap = false)
public abstract class RecipeLockExtendedRecipeSearchMixin {
    @Shadow @Final private Supplier<Level> levelGetter;

    @WrapOperation(method = {"findRecipe", "searchRecipe"}, at = @At(value = "INVOKE",
            target = "Lcom/glodblock/github/glodium/recipe/RecipeSearchContext;testRecipe(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private boolean recipeLock$executionCandidate(RecipeSearchContext<?, ?> context, RecipeHolder<?> recipe,
            Operation<Boolean> original) {
        var level = this.levelGetter.get();
        if (level != null && !level.isClientSide && RecipeExecutionContext.active()
                && RecipeLocks.hasRules(level) && !RecipeLocks.allowedInContext(level, recipe)) {
            return false;
        }
        return original.call(context, recipe);
    }
}
