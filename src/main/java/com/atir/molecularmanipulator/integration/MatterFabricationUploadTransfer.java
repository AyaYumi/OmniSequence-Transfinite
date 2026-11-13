package com.atir.molecularmanipulator.integration;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** One recipe upload spends one blank, not one blank per recipe input or output. */
final class MatterFabricationUploadTransfer {
    private final InternalInventory localBlanks;
    private final MEStorage network;
    private final IActionSource actionSource;
    private final Item blankItem;
    private final Consumer<ItemStack> recover;
    private AEItemKey selectedNetworkBlank;

    MatterFabricationUploadTransfer(InternalInventory localBlanks, MEStorage network,
            IActionSource actionSource, Item blankItem, Consumer<ItemStack> recover) {
        this.localBlanks = localBlanks;
        this.network = network;
        this.actionSource = actionSource;
        this.blankItem = blankItem;
        this.recover = recover;
    }

    boolean hasBlank() {
        return localBlankSlot() >= 0 || networkBlank() != null;
    }

    boolean insert(InternalInventory destination, int slot, ItemStack pattern) {
        if (pattern.isEmpty() || pattern.getCount() != 1 || slot < 0 || slot >= destination.size()
                || !destination.getStackInSlot(slot).isEmpty()
                || !destination.insertItem(slot, pattern.copy(), true).isEmpty()) return false;

        // Preserve the terminal-slot behavior and the exact NBT of any unspent blanks.
        int localSlot = localBlankSlot();
        if (localSlot >= 0) {
            var original = localBlanks.getStackInSlot(localSlot).copy();
            var single = original.copyWithCount(1);
            var remaining = original.getCount() == 1 ? ItemStack.EMPTY : original.copyWithCount(original.getCount() - 1);
            if (!ItemStack.matches(single, localBlanks.extractItem(localSlot, 1, true))) return false;
            boolean inserted = false;
            try {
                var consumed = localBlanks.extractItem(localSlot, 1, false);
                if (!ItemStack.matches(single, consumed)
                        || !ItemStack.matches(remaining, localBlanks.getStackInSlot(localSlot))) return false;
                inserted = insertPattern(destination, slot, pattern);
                return inserted;
            } finally {
                if (!inserted) restoreSlot(localBlanks, localSlot, original);
            }
        }

        var key = networkBlank();
        if (key == null || network.extract(key, 1, Actionable.MODULATE, actionSource) != 1) return false;
        boolean inserted = false;
        try {
            inserted = insertPattern(destination, slot, pattern);
            return inserted;
        } finally {
            if (!inserted && network.insert(key, 1, Actionable.MODULATE, actionSource) != 1) {
                // Storage/permissions may change during insertion. Never discard an extracted blank.
                var remainder = localBlanks.addItems(key.toStack(1));
                if (!remainder.isEmpty()) recover.accept(remainder);
            }
        }
    }

    /** The validated slot was empty; undo our own write if a destination callback fails. */
    private static boolean insertPattern(InternalInventory destination, int slot, ItemStack pattern) {
        boolean inserted = false;
        try {
            inserted = destination.insertItem(slot, pattern.copy(), false).isEmpty()
                    && ItemStack.matches(pattern, destination.getStackInSlot(slot));
            return inserted;
        } finally {
            if (!inserted && ItemStack.matches(destination.getStackInSlot(slot), pattern)) {
                restoreSlot(destination, slot, ItemStack.EMPTY);
            }
        }
    }

    private static void restoreSlot(InternalInventory inventory, int slot, ItemStack snapshot) {
        try {
            inventory.setItemDirect(slot, snapshot.copy());
        } catch (RuntimeException failure) {
            if (!ItemStack.matches(snapshot, inventory.getStackInSlot(slot))) throw failure;
        }
    }

    private int localBlankSlot() {
        for (int slot = 0; slot < localBlanks.size(); slot++) {
            var stack = localBlanks.getStackInSlot(slot);
            if (!stack.isEmpty() && stack.is(blankItem)) return slot;
        }
        return -1;
    }

    private AEItemKey networkBlank() {
        if (network == null) return null;
        // This object lives for one upload action. Revalidate its selected key instead
        // of listing an NBT-bearing blank twice (availability + insertion).
        if (selectedNetworkBlank != null
                && network.extract(selectedNetworkBlank, 1, Actionable.SIMULATE, actionSource) == 1) {
            return selectedNetworkBlank;
        }
        var plain = AEItemKey.of(blankItem);
        if (network.extract(plain, 1, Actionable.SIMULATE, actionSource) == 1) return selectedNetworkBlank = plain;
        // Renamed/NBT-bearing blanks are still blank patterns. Never compare display names.
        for (var entry : network.getAvailableStacks()) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key
                    && key.getItem() == blankItem && !key.equals(plain)
                    && network.extract(key, 1, Actionable.SIMULATE, actionSource) == 1) return selectedNetworkBlank = key;
        }
        return null;
    }
}
