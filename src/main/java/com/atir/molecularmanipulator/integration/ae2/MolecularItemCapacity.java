package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;

/** Simulates each physical slot once, without repeatedly counting the same free space. */
public final class MolecularItemCapacity {
    private MolecularItemCapacity() {}

    public static long simulate(InternalInventory inventory, AEItemKey key, long amount) {
        long remaining = amount;
        for (int slot = 0; slot < inventory.size() && remaining > 0; slot++) {
            int requested = (int) Math.min(remaining, Integer.MAX_VALUE);
            var remainder = inventory.insertItem(slot, key.toStack(requested), true);
            remaining -= requested - remainder.getCount();
        }
        return amount - remaining;
    }
}
