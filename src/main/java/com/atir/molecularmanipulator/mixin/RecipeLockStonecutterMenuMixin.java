package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.util.List;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keep vanilla recipe indices intact on both sides; guard the selected recipe, not its list. */
@Mixin(StonecutterMenu.class)
public abstract class RecipeLockStonecutterMenuMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private DataSlot selectedRecipeIndex;
    @Shadow private List<StonecutterRecipe> recipes;
    @Shadow @Final private ResultContainer resultContainer;
    @Shadow @Final private Slot resultSlot;
    @Unique private Player recipeLock$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("RETURN"))
    private void recipeLock$rememberPlayer(int containerId, Inventory inventory, ContainerLevelAccess access,
            CallbackInfo callback) {
        this.recipeLock$player = inventory.player;
    }

    @WrapMethod(method = "setupResultSlot")
    private void recipeLock$checkSelectedRecipe(Operation<Void> original) {
        Player player = this.recipeLock$player;
        if (player == null) {
            original.call();
            return;
        }
        try (var scope = RecipeExecutionContext.open(player)) {
            int index = this.selectedRecipeIndex.get();
            if (!this.level.isClientSide && RecipeLocks.hasRules(this.level) && index >= 0 && index < this.recipes.size()
                    && !RecipeLocks.allowedForPlayer(player, this.recipes.get(index))) {
                this.resultContainer.setRecipeUsed(null);
                this.resultSlot.set(ItemStack.EMPTY);
                ((StonecutterMenu) (Object) this).broadcastChanges();
                return;
            }
            original.call();
        }
    }
}
