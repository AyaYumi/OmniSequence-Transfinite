package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Shared by furnaces, blast furnaces, and smokers; denied recipes cannot ignite or consume input. */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class RecipeLockFurnaceMixin {
    @WrapMethod(method = "serverTick")
    private static void recipeLock$tickAsFurnace(Level level, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity furnace, Operation<Void> original) {
        try (var scope = RecipeExecutionContext.open(furnace)) {
            original.call(level, pos, state, furnace);
        }
    }

    @WrapMethod(method = "getTotalCookTime")
    private static int recipeLock$timeAsFurnace(Level level, AbstractFurnaceBlockEntity furnace,
            Operation<Integer> original) {
        // setItem also queries this outside serverTick; do not let its CachedCheck escape ownership.
        try (var scope = RecipeExecutionContext.open(furnace)) {
            return original.call(level, furnace);
        }
    }

    @WrapMethod(method = "canBurn")
    private boolean recipeLock$checkBeforeFuelOrInput(RegistryAccess registries, Recipe<?> recipe,
            NonNullList<ItemStack> inventory, int maxStackSize, Operation<Boolean> original) {
        var furnace = (AbstractFurnaceBlockEntity) (Object) this;
        Level level = furnace.getLevel();
        if (recipe != null && level != null && !level.isClientSide && RecipeLocks.hasRules(level)
                && !RecipeLocks.allowed(furnace, recipe)) {
            return false;
        }
        // Forge's instance burn method calls canBurn again before consuming inputs.
        return original.call(registries, recipe, inventory, maxStackSize);
    }
}
