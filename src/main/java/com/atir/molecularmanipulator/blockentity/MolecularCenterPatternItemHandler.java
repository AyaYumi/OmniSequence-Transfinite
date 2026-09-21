package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** External automation sees only the configured main pattern library. */
final class MolecularCenterPatternItemHandler implements IItemHandler {
    private final MolecularCenterBlockEntity controller;
    private ItemStack[] snapshot = new ItemStack[0];
    private int snapshotRevision = Integer.MIN_VALUE;

    MolecularCenterPatternItemHandler(MolecularCenterBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public int getSlots() {
        return Math.min(ModConfig.activePatternSlots(), controller.getLogic().getFullPatternInventory().size());
    }

    /**
     * Storage buses poll every exposed slot repeatedly. Keep one immutable view
     * of the pattern library and rebuild it only after the inventory revision
     * changes; this avoids copying/reading every pattern NBT on every poll.
     */
    private void refreshSnapshotIfNeeded() {
        int slots = getSlots();
        int revision = controller.getLogic().getPatternRevision();
        if (snapshot.length == slots && snapshotRevision == revision) return;
        var next = new ItemStack[slots];
        var inventory = controller.getLogic().getFullPatternInventory();
        for (int slot = 0; slot < slots; slot++) {
            var stack = inventory.getStackInSlot(slot);
            next[slot] = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        }
        snapshot = next;
        snapshotRevision = revision;
    }

    private boolean available(int slot) {
        return slot >= 0 && slot < getSlots() && controller.canAccessExternalPatterns();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        if (!available(slot)) return ItemStack.EMPTY;
        refreshSnapshotIfNeeded();
        // StorageBus only reads this value. Returning the cached immutable view
        // avoids a second deep NBT copy for every slot poll; all mutations still
        // go through insertItem/extractItem and invalidate via patternRevision.
        return snapshot[slot];
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!available(slot) || stack.isEmpty()) return stack;
        var inventory = controller.getLogic().getFullPatternInventory();
        if (!inventory.getStackInSlot(slot).isEmpty()) return stack;
        if (!controller.canMutateExternalPatterns() || !controller.isSupportedExternalPattern(stack)) return stack;
        var rejected = inventory.insertItem(slot, stack.copyWithCount(1), simulate);
        if (!rejected.isEmpty()) return stack;
        return stack.getCount() == 1 ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - 1);
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (!available(slot) || amount <= 0 || !controller.canMutateExternalPatterns()) return ItemStack.EMPTY;
        return controller.getLogic().getFullPatternInventory().extractItem(slot, amount, simulate);
    }

    @Override
    public int getSlotLimit(int slot) {
        return slot >= 0 && slot < getSlots() ? 1 : 0;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return available(slot) && controller.isSupportedExternalPattern(stack);
    }
}
