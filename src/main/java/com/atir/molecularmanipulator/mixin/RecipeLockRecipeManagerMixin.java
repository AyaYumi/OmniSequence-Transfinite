package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.datafixers.util.Pair;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Filters execution lookups, never the shared recipe catalogue used by JEI and other teams. */
@Mixin(RecipeManager.class)
public abstract class RecipeLockRecipeManagerMixin {
    @WrapMethod(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/Optional;")
    private <C extends Container, R extends Recipe<C>> Optional<R> recipeLock$selectAllowed(
            RecipeType<R> type, C input, Level level, Operation<Optional<R>> original) {
        var selected = original.call(type, input, level);
        if (level.isClientSide || !RecipeExecutionContext.active() || !RecipeLocks.hasRules(level)
                || selected.isEmpty() || RecipeLocks.allowedInContext(level, selected.get())) {
            return selected;
        }
        return this.recipeLock$allowedMatch(type, input, level);
    }

    // Forge's CachedCheck reaches this independent overload and expects the selected ID as well.
    @WrapMethod(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;")
    private <C extends Container, R extends Recipe<C>> Optional<Pair<ResourceLocation, R>> recipeLock$selectAllowedCached(
            RecipeType<R> type, C input, Level level, ResourceLocation lastRecipe,
            Operation<Optional<Pair<ResourceLocation, R>>> original) {
        var selected = original.call(type, input, level, lastRecipe);
        if (level.isClientSide || !RecipeExecutionContext.active() || !RecipeLocks.hasRules(level)
                || selected.isEmpty() || RecipeLocks.allowedInContext(level, selected.get().getSecond())) {
            return selected;
        }
        return this.recipeLock$allowedMatch(type, input, level).map(recipe -> Pair.of(recipe.getId(), recipe));
    }

    @Unique
    private <C extends Container, R extends Recipe<C>> Optional<R> recipeLock$allowedMatch(
            RecipeType<R> type, C input, Level level) {
        // A rejected cached/first match must not hide a later, allowed matching recipe.
        return ((RecipeManager) (Object) this).getAllRecipesFor(type).stream()
                .filter(candidate -> candidate.matches(input, level))
                .filter(candidate -> RecipeLocks.allowedInContext(level, candidate))
                .findFirst();
    }
}
