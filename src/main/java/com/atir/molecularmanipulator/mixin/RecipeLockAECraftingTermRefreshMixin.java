package com.atir.molecularmanipulator.mixin;

import appeng.menu.AEBaseMenu;
import appeng.menu.me.items.CraftingTermMenu;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** Refresh an open terminal when team/online-engine authorization changes without changing its grid. */
@Mixin(value = AEBaseMenu.class, remap = false)
public abstract class RecipeLockAECraftingTermRefreshMixin {
    @WrapMethod(method = "broadcastChanges")
    private void recipeLock$refreshBoundTerminal(Operation<Void> original) {
        if ((Object) this instanceof CraftingTermMenu menu && menu.isValidMenu()) {
            var player = menu.getPlayer();
            if (!player.level().isClientSide && player.containerMenu == menu && RecipeLocks.hasRules(player.level())) {
                // The constructor's first preview runs before player.containerMenu is bound, so it
                // cannot reliably borrow that menu's real ME network authorization. Retry once bound,
                // before the normal slot synchronization; updateCurrentRecipeAndOutput rechecks it.
                menu.slotsChanged(menu.getCraftingMatrix().toContainer());
            }
        }
        original.call();
    }
}
