package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;

/** quickMoveStack transfers output BEFORE onTake; guard every shift-click iteration here. */
@Mixin({CraftingMenu.class, InventoryMenu.class, StonecutterMenu.class, ItemCombinerMenu.class})
public abstract class RecipeLockQuickMoveMixin {
    @WrapMethod(method = "quickMoveStack")
    private ItemStack recipeLock$checkBeforeTransfer(Player player, int index, Operation<ItemStack> original) {
        AbstractContainerMenu menu = (AbstractContainerMenu) (Object) this;
        int resultIndex;
        if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) {
            resultIndex = 0;
        } else if (menu instanceof StonecutterMenu) {
            resultIndex = 1;
        } else if (menu instanceof SmithingMenu smithing) {
            resultIndex = smithing.getResultSlot();
        } else {
            // ItemCombinerMenu also serves anvils, which have no data-driven recipe id.
            return original.call(player, index);
        }

        try (var scope = RecipeExecutionContext.open(player)) {
            if (!player.level().isClientSide && RecipeLocks.hasRules(player.level()) && index == resultIndex) {
                Slot slot = menu.getSlot(index);
                if (slot.container instanceof ResultContainer result) {
                    Recipe<?> recipe = result.getRecipeUsed();
                    if (recipe == null || !RecipeLocks.allowedForPlayer(player, recipe) || !slot.mayPickup(player)) {
                        return ItemStack.EMPTY;
                    }
                }
            }
            return original.call(player, index);
        }
    }
}
