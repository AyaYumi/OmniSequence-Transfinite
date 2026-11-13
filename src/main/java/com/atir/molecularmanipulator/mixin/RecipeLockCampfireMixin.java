package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CampfireBlockEntity.class)
public abstract class RecipeLockCampfireMixin {
    @WrapMethod(method = "getCookableRecipe")
    private Optional<CampfireCookingRecipe> recipeLock$findAsCampfire(ItemStack stack,
            Operation<Optional<CampfireCookingRecipe>> original) {
        CampfireBlockEntity campfire = (CampfireBlockEntity) (Object) this;
        try (var scope = RecipeExecutionContext.open(campfire)) {
            return original.call(stack);
        }
    }

    @WrapMethod(method = "placeFood")
    private boolean recipeLock$checkBeforePlacement(Entity entity, ItemStack food, int cookTime,
            Operation<Boolean> original) {
        CampfireBlockEntity campfire = (CampfireBlockEntity) (Object) this;
        Level level = campfire.getLevel();
        if (level == null || level.isClientSide || !RecipeLocks.hasRules(level)) {
            return original.call(entity, food, cookTime);
        }
        try (var scope = RecipeExecutionContext.open(campfire)) {
            SimpleContainer input = new SimpleContainer(food);
            Optional<CampfireCookingRecipe> selected = level.getRecipeManager()
                    .getRecipeFor(RecipeType.CAMPFIRE_COOKING, input, level)
                    .filter(recipe -> RecipeLocks.allowed(campfire, recipe))
                    .filter(recipe -> !(entity instanceof Player player) || RecipeLocks.allowedForPlayer(player, recipe));
            if (selected.isEmpty()) {
                // Both the inserter and the campfire must allow it; try other matching candidates.
                selected = level.getRecipeManager().getAllRecipesFor(RecipeType.CAMPFIRE_COOKING).stream()
                        .filter(recipe -> recipe.matches(input, level))
                        .filter(recipe -> RecipeLocks.allowed(campfire, recipe))
                        .filter(recipe -> !(entity instanceof Player player) || RecipeLocks.allowedForPlayer(player, recipe))
                        .findFirst();
            }
            // placeFood consumes the hand stack, so authorization must precede the original call.
            return selected.isPresent() && original.call(entity, food, selected.get().getCookingTime());
        }
    }

    @WrapMethod(method = "cookTick")
    private static void recipeLock$cookAsCampfire(Level level, BlockPos pos, BlockState state,
            CampfireBlockEntity campfire, Operation<Void> original) {
        try (var scope = RecipeExecutionContext.open(campfire)) {
            original.call(level, pos, state, campfire);
        }
    }

    @WrapOperation(method = "cookTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;isEmpty()Z"))
    private static boolean recipeLock$skipLockedFood(ItemStack stack, Operation<Boolean> original,
            Level level, BlockPos pos, BlockState state, CampfireBlockEntity campfire) {
        if (original.call(stack)) {
            return true;
        }
        if (level.isClientSide || !RecipeLocks.hasRules(level)) {
            return false;
        }
        // A plain Optional.empty at completion is unsafe here: vanilla orElse(rawInput) then
        // drops it and clears the slot. Skip the locked slot BEFORE its progress or items change.
        return level.getRecipeManager().getRecipeFor(RecipeType.CAMPFIRE_COOKING, new SimpleContainer(stack), level)
                .filter(recipe -> RecipeLocks.allowed(campfire, recipe))
                .isEmpty();
    }
}
