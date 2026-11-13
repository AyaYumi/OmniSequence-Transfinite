package com.atir.molecularmanipulator.mixin;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.menu.slot.CraftingTermSlot;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Covers every doClick mode, including craft-all/shift and the direct carried-item shortcut. */
@Mixin(value = CraftingTermSlot.class, remap = false)
public abstract class RecipeLockAECraftingTermSlotMixin {
    // Invocation-local identity, not an authorization cache. The base slot clears recipeUsed
    // while awarding recipes, before it asks this slot for crafting remainders.
    @Unique private Recipe<CraftingContainer> recipeLock$executingRecipe;

    @WrapMethod(method = "craftItem")
    private ItemStack recipeLock$manualCraft(Player player, MEStorage inventory, KeyCounter available,
            Operation<ItemStack> original) {
        if (player.level().isClientSide) {
            return ItemStack.EMPTY;
        }
        var previous = this.recipeLock$executingRecipe;
        this.recipeLock$executingRecipe = null;
        try (var scope = RecipeExecutionContext.open(player)) {
            return original.call(player, inventory, available);
        } finally {
            this.recipeLock$executingRecipe = previous;
        }
    }

    @WrapMethod(method = "findRecipe")
    private Recipe<CraftingContainer> recipeLock$beforeExtraction(CraftingContainer input, Level level,
            Operation<Recipe<CraftingContainer>> original) {
        var recipe = original.call(input, level);
        if (level.isClientSide || !RecipeExecutionContext.active() || !RecipeLocks.hasRules(level)) {
            return recipe;
        }
        // findRecipe may return terminalMenu.currentRecipe without consulting RecipeManager at all.
        // This check happens before extractItemsByRecipe, makeItem, and input/remainder consumption.
        if (recipe != null && !RecipeLocks.allowedInContext(level, recipe)) {
            recipe = level.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING).stream()
                    .filter(candidate -> candidate.matches(input, level))
                    .filter(candidate -> RecipeLocks.allowedInContext(level, candidate)).findFirst().orElse(null);
        }
        this.recipeLock$executingRecipe = recipe;
        if (recipe == null) {
            // End a craft-all loop promptly instead of retrying the same denied, stale output.
            ((CraftingTermSlot) (Object) this).setDisplayedCraftingOutput(ItemStack.EMPTY);
        }
        return recipe;
    }

    @WrapMethod(method = "getRemainingItems")
    private NonNullList<ItemStack> recipeLock$remaindersOfExecutedRecipe(CraftingContainer input, Level level,
            Operation<NonNullList<ItemStack>> original) {
        if (!level.isClientSide && this.recipeLock$executingRecipe != null) {
            // A permitted alternative must not use a denied terminalMenu.currentRecipe's remainders.
            return this.recipeLock$executingRecipe.getRemainingItems(input);
        }
        return original.call(input, level);
    }
}
