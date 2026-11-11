package com.atir.molecularmanipulator.mixin;

import appeng.menu.me.items.CraftingTermMenu;
import appeng.menu.slot.CraftingTermSlot;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Also inherited by AE2's wireless crafting terminal; server preview is not an execution grant. */
@Mixin(value = CraftingTermMenu.class, remap = false)
public abstract class RecipeLockAECraftingTermMenuMixin {
    @Shadow @Final private CraftingTermSlot outputSlot;
    @Shadow private RecipeHolder<CraftingRecipe> currentRecipe;

    @WrapMethod(method = "updateCurrentRecipeAndOutput")
    private void recipeLock$playerPreview(boolean forceUpdate, Operation<Void> original) {
        var player = ((CraftingTermMenu) (Object) this).getPlayer();
        if (player.level().isClientSide) {
            original.call(forceUpdate);
            return;
        }
        try (var scope = RecipeExecutionContext.open(player)) {
            // lastTestedInput equality must not reuse a result after authorization changes.
            original.call(forceUpdate || RecipeLocks.hasRules(player.level()));
            if (this.currentRecipe != null && !RecipeLocks.allowedForPlayer(player, this.currentRecipe)) {
                this.currentRecipe = null;
                this.outputSlot.set(ItemStack.EMPTY);
            }
        }
    }
}
