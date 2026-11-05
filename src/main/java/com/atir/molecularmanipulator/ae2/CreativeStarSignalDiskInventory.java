package com.atir.molecularmanipulator.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.StorageCell;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * A dedicated creative cell: it always exposes the single star-signal-flow key
 * with an effectively infinite supply and never stores anything.
 */
public final class CreativeStarSignalDiskInventory implements StorageCell {
    private static final long REPORTED_AMOUNT = Integer.MAX_VALUE;

    private final ItemStack stack;

    public CreativeStarSignalDiskInventory(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
        return key.equals(StarSignalKey.INSTANCE) ? amount : 0;
    }

    @Override
    public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        return key.equals(StarSignalKey.INSTANCE) ? amount : 0;
    }

    @Override
    public void getAvailableStacks(KeyCounter out) {
        out.add(StarSignalKey.INSTANCE, REPORTED_AMOUNT);
    }

    @Override
    public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
        return key.equals(StarSignalKey.INSTANCE);
    }

    @Override
    public CellState getStatus() {
        return CellState.TYPES_FULL;
    }

    @Override
    public double getIdleDrain() {
        return 0;
    }

    @Override
    public boolean canFitInsideCell() {
        return false;
    }

    @Override
    public Component getDescription() {
        return stack.getHoverName();
    }

    @Override
    public void persist() {
    }
}
