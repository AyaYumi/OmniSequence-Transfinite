package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Revalidates cached output before pickup, hotbar swapping, throwing, or safeTake removes it. */
@Mixin(Slot.class)
public abstract class RecipeLockSlotMixin {
    @Shadow @Final public Container container;

    @WrapMethod(method = "mayPickup")
    private boolean recipeLock$checkStoredRecipe(Player player, Operation<Boolean> original) {
        if (!player.level().isClientSide && RecipeLocks.hasRules(player.level())
                && this.container instanceof ResultContainer result) {
            Recipe<?> recipe = result.getRecipeUsed();
            if (recipe != null && !RecipeLocks.allowedForPlayer(player, recipe)) {
                return false;
            }
        }
        return original.call(player);
    }
}
