package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.LanternInventoryLock;
import com.atir.molecularmanipulator.world.LanternSimulation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Register this class and each nested mixin in the mixin configuration's client list. */
@Mixin(MultiPlayerGameMode.class)
public abstract class LanternClientInventoryMixin {
    @Inject(method = "handleInventoryMouseClick", at = @At("HEAD"), cancellable = true)
    private void lantern$click(int containerId, int slotId, int button, ClickType type,
            Player player, CallbackInfo ci) {
        if (containerId == player.containerMenu.containerId
                && LanternInventoryLock.blocksClick(player, player.containerMenu, slotId, button, type)) {
            LanternInventoryLock.message(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleCreativeModeItemAdd", at = @At("HEAD"), cancellable = true)
    private void lantern$creativeSlot(ItemStack stack, int slotId, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && LanternInventoryLock.blocksCreativeChange(player, slotId, stack)) {
            LanternInventoryLock.message(player);
            ci.cancel();
        }
    }

    @Inject(method = "handleCreativeModeItemDrop", at = @At("HEAD"), cancellable = true)
    private void lantern$creativeDrop(ItemStack stack, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && LanternInventoryLock.blocksCreativeChange(player, -1, stack)) {
            LanternInventoryLock.message(player);
            ci.cancel();
        }
    }

    @Inject(method = "handlePickItem", at = @At("HEAD"), cancellable = true)
    private void lantern$pick(int inventorySlot, CallbackInfo ci) {
        Player player = Minecraft.getInstance().player;
        if (player != null && LanternInventoryLock.locksMainHand(player)) {
            LanternInventoryLock.message(player);
            ci.cancel();
        }
    }

    @Mixin(Minecraft.class)
    public abstract static class HotbarKeys {
        @Redirect(method = "handleKeybinds", at = @At(value = "FIELD",
                target = "Lnet/minecraft/world/entity/player/Inventory;selected:I", opcode = Opcodes.PUTFIELD))
        private void lantern$select(Inventory inventory, int selected) {
            if (LanternInventoryLock.blocksSelection(inventory.player, selected)) {
                LanternInventoryLock.message(inventory.player);
            } else {
                inventory.selected = selected;
            }
        }

        @Inject(method = "pickBlock", at = @At("HEAD"), cancellable = true)
        private void lantern$pickBlock(CallbackInfo ci) {
            Player player = ((Minecraft) (Object) this).player;
            if (player != null && LanternInventoryLock.locksMainHand(player)) {
                LanternInventoryLock.message(player);
                ci.cancel();
            }
        }
    }

    @Mixin(Inventory.class)
    public abstract static class Hotbar {
        @Shadow @Final public Player player;

        @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
        private void lantern$scroll(double direction, CallbackInfo ci) {
            if (direction != 0 && LanternInventoryLock.locksMainHand(player)) {
                LanternInventoryLock.message(player);
                ci.cancel();
            }
        }

        @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
        private void lantern$drop(boolean fullStack, CallbackInfoReturnable<ItemStack> cir) {
            if (LanternInventoryLock.locksMainHand(player)) {
                LanternInventoryLock.message(player);
                cir.setReturnValue(ItemStack.EMPTY);
            }
        }
    }

    @Mixin(CreativeModeInventoryScreen.class)
    public abstract static class CreativeScreen {
        @Inject(method = "slotClicked", at = @At("HEAD"), cancellable = true)
        private void lantern$creativeClick(Slot slot, int slotId, int button, ClickType type, CallbackInfo ci) {
            Player player = Minecraft.getInstance().player;
            if (player == null || !LanternSimulation.isActive(player)) {
                return;
            }
            AbstractContainerMenu menu = ((CreativeModeInventoryScreen) (Object) this).getMenu();
            // SlotWrapper delegates getSlotIndex(); its own menu index can be unset.
            if (LanternInventoryLock.blocksClick(player, menu, slot, button, type)
                    || type == ClickType.THROW && slot != null && LanternInventoryLock.isLantern(slot.getItem())) {
                LanternInventoryLock.message(player);
                ci.cancel();
            }
        }

        @Inject(method = "handleHotbarLoadOrSave", at = @At("HEAD"), cancellable = true)
        private static void lantern$loadHotbar(Minecraft client, int index, boolean load, boolean save, CallbackInfo ci) {
            if (load && client.player != null && LanternInventoryLock.locksMainHand(client.player)) {
                LanternInventoryLock.message(client.player);
                ci.cancel();
            }
        }
    }
}
