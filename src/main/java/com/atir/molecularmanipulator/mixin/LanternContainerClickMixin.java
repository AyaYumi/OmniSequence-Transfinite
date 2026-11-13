package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.LanternInventoryLock;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Common guard for direct client and server menu clicks. */
@Mixin(AbstractContainerMenu.class)
public abstract class LanternContainerClickMixin {
    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void lantern$click(int slotId, int button, ClickType type, Player player, CallbackInfo ci) {
        if (LanternInventoryLock.blocksClick(player, (AbstractContainerMenu) (Object) this, slotId, button, type)) {
            LanternInventoryLock.message(player);
            ci.cancel();
        }
    }
}
