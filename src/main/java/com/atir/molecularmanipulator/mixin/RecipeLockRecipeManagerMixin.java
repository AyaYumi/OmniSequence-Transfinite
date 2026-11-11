package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.Optional;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Filters execution lookups, never the shared recipe catalogue used by JEI and other teams. */
@Mixin(RecipeManager.class)
public abstract class RecipeLockRecipeManagerMixin {
    // All three overloads (including CachedCheck's recipe-id lookup) reach this overload.
    @WrapMethod(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;")
    private <I extends RecipeInput, R extends Recipe<I>> Optional<RecipeHolder<R>> recipeLock$selectAllowed(
            RecipeType<R> type, I input, Level level, RecipeHolder<R> lastRecipe,
            Operation<Optional<RecipeHolder<R>>> original) {
        Optional<RecipeHolder<R>> selected = original.call(type, input, level, lastRecipe);
        if (level.isClientSide || !RecipeExecutionContext.active() || !RecipeLocks.hasRules(level)
                || selected.isEmpty() || RecipeLocks.allowedInContext(level, selected.get())) {
            return selected;
        }

        // A rejected cached/first match must not hide a later, allowed matching recipe.
        return ((RecipeManager) (Object) this).getAllRecipesFor(type).stream()
                .filter(candidate -> candidate.value().matches(input, level))
                .filter(candidate -> RecipeLocks.allowedInContext(level, candidate))
                .findFirst();
    }
}
