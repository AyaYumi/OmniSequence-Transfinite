package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.stream.StreamSupport;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Optional execution-only recipe enumeration, verified against AdvancedAE 1.6.12-1.21.1. */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.recipes.ReactionChamberRecipes", remap = false)
public abstract class RecipeLockAdvancedReactionRecipesMixin {
    @WrapOperation(method = "findRecipe", at = @At(value = "INVOKE",
            target = "Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipes;getRecipes(Lnet/minecraft/world/level/Level;)Ljava/lang/Iterable;"))
    private static Iterable<RecipeHolder<?>> recipeLock$executionCandidates(Level level,
            Operation<Iterable<RecipeHolder<?>>> original) {
        var recipes = original.call(level);
        if (level.isClientSide || !RecipeExecutionContext.active() || !RecipeLocks.hasRules(level)) {
            return recipes;
        }
        return StreamSupport.stream(recipes.spliterator(), false)
                .filter(recipe -> RecipeLocks.allowedInContext(level, recipe)).toList();
    }
}
