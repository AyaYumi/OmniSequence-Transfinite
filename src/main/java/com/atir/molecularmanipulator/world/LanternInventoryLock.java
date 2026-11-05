package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.registry.GreenFireLanternContent;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Guards user inventory actions; placement may still clear a hand explicitly. */
public final class LanternInventoryLock {
    public static final String MESSAGE_KEY = "message.molecularmanipulator.lantern_locked";
    private static final long MESSAGE_INTERVAL = TimeUnit.SECONDS.toNanos(1);
    private static final Map<Player, Long> LAST_MESSAGE = new WeakHashMap<>();

    private LanternInventoryLock() {}

    public static boolean locksMainHand(Player player) {
        return LanternSimulation.isActive(player) && isLantern(player.getMainHandItem());
    }

    public static boolean blocksSelection(Player player, int selected) {
        return selected != player.getInventory().selected && locksMainHand(player);
    }

    public static boolean isLantern(ItemStack stack) {
        return !stack.isEmpty() && GreenFireLanternContent.isLantern(stack);
    }

    private static boolean isLockedHand(Player player, int inventorySlot) {
        Inventory inventory = player.getInventory();
        return (inventorySlot == inventory.selected || inventorySlot == Inventory.SLOT_OFFHAND)
                && isLantern(inventory.getItem(inventorySlot));
    }

    /** Slot indices here belong to the underlying inventory, never to the menu. */
    private static boolean isLockedSlot(Player player, Slot slot) {
        return slot != null && slot.container == player.getInventory()
                && isLockedHand(player, slot.getSlotIndex());
    }

    public static boolean blocksClick(Player player, AbstractContainerMenu menu,
            int slotId, int button, ClickType type) {
        Slot slot = slotId >= 0 && slotId < menu.slots.size() ? menu.getSlot(slotId) : null;
        return blocksClick(player, menu, slot, button, type);
    }

    /** Creative screens pass the actual wrapper; its menu index may be unset. */
    public static boolean blocksClick(Player player, AbstractContainerMenu menu,
            Slot slot, int button, ClickType type) {
        if (!LanternSimulation.isActive(player)) {
            return false;
        }

        // Only the lantern already occupying a hand is locked. A spare or freshly
        // crafted lantern on the cursor must still be returnable to its source slot.
        boolean lockedSlot = isLockedSlot(player, slot);
        if (type == ClickType.SWAP && (Inventory.isHotbarSlot(button) || button == Inventory.SLOT_OFFHAND)) {
            if (!lockedSlot && !isLockedHand(player, button)) {
                return false;
            }
            // A self-swap preserves the hand. Hand-to-hand transfers use the F packet.
            return slot == null || slot.container != player.getInventory() || slot.getSlotIndex() != button;
        }

        return lockedSlot;
    }

    /** Creative slot packets contain the proposed replacement, not a click. */
    public static boolean blocksCreativeChange(Player player, int slotId, ItemStack replacement) {
        if (!LanternSimulation.isActive(player)) {
            return false;
        }
        if (slotId < 0) {
            return isLantern(replacement);
        }
        if (slotId < 1 || slotId >= player.inventoryMenu.slots.size()) {
            return false;
        }

        Slot slot = player.inventoryMenu.getSlot(slotId);
        return isLockedSlot(player, slot) && !ItemStack.matches(slot.getItem(), replacement);
    }

    /** Shared by rejected inventory actions and invalid placement attempts. */
    public static void message(Player player) {
        long now = System.nanoTime();
        synchronized (LAST_MESSAGE) {
            Long previous = LAST_MESSAGE.get(player);
            if (previous != null && now - previous < MESSAGE_INTERVAL) {
                return;
            }
            LAST_MESSAGE.put(player, now);
        }
        player.displayClientMessage(Component.translatable(MESSAGE_KEY), true);
    }
}
