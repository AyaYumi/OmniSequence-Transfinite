package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SmithingMenu.class)
public abstract class RecipeLockSmithingMenuMixin extends ItemCombinerMenu {
    @Shadow @Final private Level level;
    @Shadow private SmithingRecipe selectedRecipe;

    protected RecipeLockSmithingMenuMixin(int containerId, Inventory inventory, ContainerLevelAccess access) {
        super(MenuType.SMITHING, containerId, inventory, access);
    }

    @WrapMethod(method = "createResult")
    private void recipeLock$calculateForPlayer(Operation<Void> original) {
        try (var scope = RecipeExecutionContext.open(this.player)) {
            if (!this.level.isClientSide && RecipeLocks.hasRules(this.level)) {
                this.selectedRecipe = null;
                this.resultSlots.setRecipeUsed(null);
                this.resultSlots.setItem(0, ItemStack.EMPTY);
            }
            original.call();
        }
    }

    @WrapOperation(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipesFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/Container;Lnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private List<SmithingRecipe> recipeLock$allowedCandidates(RecipeManager manager,
            RecipeType<SmithingRecipe> type, Container input, Level level,
            Operation<List<SmithingRecipe>> original) {
        List<SmithingRecipe> candidates = original.call(manager, type, input, level);
        if (level.isClientSide || !RecipeLocks.hasRules(level)) {
            return candidates;
        }
        // Filter only this execution call site, and try subsequent matches instead of rejecting list[0].
        return candidates.stream().filter(recipe -> RecipeLocks.allowedForPlayer(this.player, recipe)).toList();
    }

    @WrapMethod(method = "mayPickup")
    private boolean recipeLock$checkPickup(Player player, boolean hasStack, Operation<Boolean> original) {
        // The anonymous result Slot overrides mayPickup without calling Slot.mayPickup.
        if (!this.level.isClientSide && RecipeLocks.hasRules(this.level) && this.selectedRecipe != null
                && !RecipeLocks.allowedForPlayer(player, this.selectedRecipe)) {
            return false;
        }
        return original.call(player, hasStack);
    }

    @WrapMethod(method = "onTake")
    private void recipeLock$checkBeforeConsume(Player player, ItemStack stack, Operation<Void> original) {
        try (var scope = RecipeExecutionContext.open(player)) {
            if (!this.level.isClientSide && RecipeLocks.hasRules(this.level)
                    && (this.selectedRecipe == null || !RecipeLocks.allowedForPlayer(player, this.selectedRecipe))) {
                return;
            }
            original.call(player, stack);
        }
    }
}
