package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.config.ModConfig;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

/** External automation sees only the configured main pattern library. */
final class MolecularCenterPatternItemHandler implements IItemHandler {
    private final MolecularCenterBlockEntity controller;

    MolecularCenterPatternItemHandler(MolecularCenterBlockEntity controller) {
        this.controller = controller;
    }

    @Override
    public int getSlots() {
        return Math.min(ModConfig.activePatternSlots(), controller.getLogic().getFullPatternInventory().size());
    }

    private boolean available(int slot) {
        return slot >= 0 && slot < getSlots() && controller.canAccessExternalPatterns();
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return available(slot) ? controller.getLogic().getFullPatternInventory().getStackInSlot(slot).copy()
                : ItemStack.EMPTY;
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
