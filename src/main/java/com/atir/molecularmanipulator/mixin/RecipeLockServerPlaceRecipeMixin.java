package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;

/** Reject recipe-book autofill before clearing the grid or moving anything out of inventory. */
@Mixin(ServerPlaceRecipe.class)
public abstract class RecipeLockServerPlaceRecipeMixin {
    @WrapMethod(method = "recipeClicked")
    private void recipeLock$checkAutofill(ServerPlayer player, Recipe<?> recipe, boolean placeAll,
            Operation<Void> original) {
        if (recipe != null && RecipeLocks.hasRules(player.level()) && !RecipeLocks.allowedForPlayer(player, recipe)) {
            return;
        }
        try (var scope = RecipeExecutionContext.open(player)) {
            original.call(player, recipe, placeAll);
        }
    }
}
