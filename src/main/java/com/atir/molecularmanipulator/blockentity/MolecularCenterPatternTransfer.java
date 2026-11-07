package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.StorageCell;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.ItemStack;

/** Synchronous transfers between explicitly selected pattern inventories, not arbitrary capabilities. */
public final class MolecularCenterPatternTransfer {
    private MolecularCenterPatternTransfer() {}

    public static boolean fromInventory(InternalInventory source, int sourceSlot,
            InternalInventory destination, int destinationSlot, BooleanSupplier writable) {
        if (source == destination || sourceSlot < 0 || sourceSlot >= source.size()) return false;
        var original = source.getStackInSlot(sourceSlot).copy();
        if (original.isEmpty()) return false;
        var single = original.copyWithCount(1);
        if (!ItemStack.matches(single, source.extractItem(sourceSlot, 1, true))) return false;
        var remaining = original.getCount() == 1 ? ItemStack.EMPTY : original.copyWithCount(original.getCount() - 1);
        boolean committed = false;
        boolean extracting = false;
        boolean staged = false;
        try {
            if (!canStage(destination, destinationSlot, single, writable)) return false;
            // Write the target FIRST. A rejected/throwing target cannot spend a source pattern.
            staged = true;
            if (!destination.insertItem(destinationSlot, single.copy(), false).isEmpty()
                    || !ItemStack.matches(single, destination.getStackInSlot(destinationSlot))) return false;
            if (!writable.getAsBoolean() || !ItemStack.matches(original, source.getStackInSlot(sourceSlot))) return false;
            extracting = true;
            var extracted = source.extractItem(sourceSlot, 1, false);
            committed = ItemStack.matches(single, extracted)
                    && ItemStack.matches(remaining, source.getStackInSlot(sourceSlot));
            return committed;
        } catch (RuntimeException failure) {
            // Public physical terminal slots support direct restoration of their snapshot,
            // including a normal notification if extraction threw after writing.
            return false;
        } finally {
            if (!committed) {
                try {
                    if (extracting) restoreSlot(source, sourceSlot, original);
                } finally {
                    if (staged) undoStage(destination, destinationSlot, single);
                }
            }
        }
    }

    static boolean fromCell(StorageCell backing, MEStorage mounted, AEItemKey key,
            IActionSource actionSource, InternalInventory destination, int destinationSlot,
            BooleanSupplier writable) {
        // Query only this key in the selected physical cell, never an aggregate network
        // count or a new full KeyCounter for every pattern. Addon cells use the same API.
        long before = backing.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
        if (before <= 0 || mounted.extract(key, 1, Actionable.SIMULATE, actionSource) != 1) return false;
        var single = key.toStack(1);
        boolean committed = false;
        boolean extracting = false;
        boolean staged = false;
        try {
            if (!canStage(destination, destinationSlot, single, writable)) return false;
            staged = true;
            if (!destination.insertItem(destinationSlot, single.copy(), false).isEmpty()
                    || !ItemStack.matches(single, destination.getStackInSlot(destinationSlot))) return false;
            if (!writable.getAsBoolean()
                    || backing.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource) != before) return false;
            extracting = true;
            mounted.extract(key, 1, Actionable.MODULATE, actionSource);
        } catch (RuntimeException failure) {
            // Inspect the selected backing cell, not an aggregate network count:
            // a save/LED callback can throw AFTER the removal.
        } finally {
            if (extracting) committed = backing.extract(key, Long.MAX_VALUE,
                    Actionable.SIMULATE, actionSource) == before - 1;
            if (!committed && staged) undoStage(destination, destinationSlot, single);
        }
        // A creative/display-only cell cannot commit: it has no real decrement.
        // No refund insertion is needed (or trusted: a cell can have a void card or new filter).
        return committed;
    }

    private static boolean canStage(InternalInventory inventory, int slot, ItemStack single,
            BooleanSupplier writable) {
        return writable.getAsBoolean() && slot >= 0 && slot < inventory.size()
                && inventory.getStackInSlot(slot).isEmpty()
                && inventory.insertItem(slot, single.copy(), true).isEmpty()
                && writable.getAsBoolean() && inventory.getStackInSlot(slot).isEmpty();
    }

    private static void undoStage(InternalInventory inventory, int slot, ItemStack single) {
        // Never overwrite someone else's contents. All production target writes use the
        // matrix's known main inventory; callbacks only persist/re-publish that same slot.
        if (ItemStack.matches(single, inventory.getStackInSlot(slot))) {
            restoreSlot(inventory, slot, ItemStack.EMPTY);
        }
    }

    private static void restoreSlot(InternalInventory inventory, int slot, ItemStack snapshot) {
        try {
            inventory.setItemDirect(slot, snapshot.copy());
        } catch (RuntimeException failure) {
            if (!ItemStack.matches(snapshot, inventory.getStackInSlot(slot))) throw failure;
        }
    }
}
