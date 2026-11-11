package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** Execution checks for inventory/table results, including calls made directly to the slot. */
@Mixin(ResultSlot.class)
public abstract class RecipeLockResultSlotMixin {
    @Shadow @Final private Player player;
    @Unique private RecipeHolder<?> recipeLock$takingRecipe;

    @Unique
    private RecipeHolder<?> recipeLock$storedRecipe() {
        return ((ResultSlot) (Object) this).container instanceof ResultContainer result ? result.getRecipeUsed() : null;
    }

    @WrapMethod(method = "remove")
    private ItemStack recipeLock$checkBeforeRemove(int amount, Operation<ItemStack> original) {
        ResultSlot slot = (ResultSlot) (Object) this;
        if (!slot.mayPickup(this.player)) {
            this.recipeLock$takingRecipe = null;
            return ItemStack.EMPTY;
        }
        this.recipeLock$takingRecipe = this.recipeLock$storedRecipe();
        try (var scope = RecipeExecutionContext.open(this.player)) {
            return original.call(amount);
        }
    }

    @WrapMethod(method = "checkTakeAchievements")
    private void recipeLock$rememberBeforeAward(ItemStack stack, Operation<Void> original) {
        // onQuickCraft awards (and clears recipeUsed) before onTake in the shift-click path.
        RecipeHolder<?> recipe = this.recipeLock$storedRecipe();
        if (recipe != null) {
            this.recipeLock$takingRecipe = recipe;
        }
        try (var scope = RecipeExecutionContext.open(this.player)) {
            original.call(stack);
        }
    }

    @WrapMethod(method = "onTake")
    private void recipeLock$checkBeforeConsume(Player player, ItemStack stack, Operation<Void> original) {
        RecipeHolder<?> recipe = this.recipeLock$takingRecipe != null
                ? this.recipeLock$takingRecipe : this.recipeLock$storedRecipe();
        try (var scope = RecipeExecutionContext.open(player)) {
            if (!player.level().isClientSide && RecipeLocks.hasRules(player.level())
                    && recipe != null && !RecipeLocks.allowedForPlayer(player, recipe)) {
                return;
            }
            // Keeps the remaining-items lookup in the same player context as result selection.
            original.call(player, stack);
        } finally {
            this.recipeLock$takingRecipe = null;
        }
    }
}
