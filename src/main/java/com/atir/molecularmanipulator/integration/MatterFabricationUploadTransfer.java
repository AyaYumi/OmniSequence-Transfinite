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

        // Preserve the existing terminal-slot behavior when a local blank is present.
        int localSlot = localBlankSlot();
        if (localSlot >= 0) {
            var original = localBlanks.getStackInSlot(localSlot).copy();
            boolean inserted = false;
            try {
                var consumed = localBlanks.extractItem(localSlot, 1, false);
                if (consumed.getCount() != 1 || !consumed.is(blankItem)) return false;
                inserted = insertPattern(destination, slot, pattern);
                return inserted;
            } finally {
                if (!inserted) localBlanks.setItemDirect(localSlot, original);
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
            inserted = destination.insertItem(slot, pattern.copy(), false).isEmpty();
            return inserted;
        } finally {
            if (!inserted && ItemStack.matches(destination.getStackInSlot(slot), pattern)) {
                destination.setItemDirect(slot, ItemStack.EMPTY);
            }
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
        // This transfer object lives for one upload action. Revalidate its selected key
        // instead of listing a component-bearing blank twice (availability + insertion).
        if (selectedNetworkBlank != null
                && network.extract(selectedNetworkBlank, 1, Actionable.SIMULATE, actionSource) == 1) {
            return selectedNetworkBlank;
        }
        var plain = AEItemKey.of(blankItem);
        if (network.extract(plain, 1, Actionable.SIMULATE, actionSource) == 1) return selectedNetworkBlank = plain;
        // Renamed/component-bearing blanks are still blank patterns. Compare item identity,
        // not display names, and validate live extraction rather than trusting a cached count.
        for (var entry : network.getAvailableStacks()) {
            if (entry.getLongValue() > 0 && entry.getKey() instanceof AEItemKey key
                    && key.getItem() == blankItem && !key.equals(plain)
                    && network.extract(key, 1, Actionable.SIMULATE, actionSource) == 1) return selectedNetworkBlank = key;
        }
        return null;
    }
}
