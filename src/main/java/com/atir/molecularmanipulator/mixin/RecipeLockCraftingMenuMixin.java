package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/** Both the 2x2 inventory grid and the 3x3 table use this result calculation. */
@Mixin(CraftingMenu.class)
public abstract class RecipeLockCraftingMenuMixin {
    @WrapMethod(method = "slotChangedCraftingGrid")
    private static void recipeLock$calculateForPlayer(AbstractContainerMenu menu, Level level, Player player,
            CraftingContainer inputs, ResultContainer result, RecipeHolder<CraftingRecipe> recipe,
            Operation<Void> original) {
        try (var scope = RecipeExecutionContext.open(player)) {
            original.call(menu, level, player, inputs, result, recipe);
        }
    }
}
