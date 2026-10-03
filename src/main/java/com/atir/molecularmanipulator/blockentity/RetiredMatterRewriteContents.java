package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.util.inv.AppEngInternalInventory;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** One-way recovery of real item contents from the removed Matter Rewrite feature. */
final class RetiredMatterRewriteContents {
    private RetiredMatterRewriteContents() {}

    static void migrate(CompoundTag tag, HolderLookup.Provider registries,
            AppEngInternalInventory quantum, Object2LongOpenHashMap<AEKey> refunds) {
        if (tag.contains("matter_sequence_inventory")) {
            var legacy = new AppEngInternalInventory(4);
            legacy.readFromNBT(tag, "matter_sequence_inventory", registries);
            // Modern input slots were ghost filters. Older input slots held real items,
            // unless those same items had already moved to the legacy refund escrow.
            if (!tag.getBoolean("matter_deconstruct_marker_format")
                    && !tag.contains("matter_deconstruct_legacy_refund", Tag.TAG_COMPOUND)) {
                refund(legacy.getStackInSlot(0), refunds);
            }
            refund(legacy.getStackInSlot(1), refunds);
            refund(legacy.getStackInSlot(2), refunds);
            var singularity = legacy.getStackInSlot(3);
            if (!singularity.isEmpty()) {
                if (quantum.isEmpty()) quantum.setItemDirect(0, singularity.copy());
                else refund(singularity, refunds);
            }
        }
        if (tag.contains("matter_sequence_upgrades")) {
            var upgrades = new AppEngInternalInventory(4);
            upgrades.readFromNBT(tag, "matter_sequence_upgrades", registries);
            for (int i = 0; i < upgrades.size(); i++) refund(upgrades.getStackInSlot(i), refunds);
        }
        if (tag.contains("matter_deconstruct_legacy_refund", Tag.TAG_COMPOUND)) {
            refund(ItemStack.parseOptional(registries, tag.getCompound("matter_deconstruct_legacy_refund")), refunds);
        }
        // Omit retired balances, job state, ghost filters and templates on the next save.
        for (String key : java.util.List.copyOf(tag.getAllKeys())) {
            if (key.startsWith("matter_sequence_") || key.startsWith("matter_deconstruct_")
                    || key.startsWith("matter_rewrite_") || key.startsWith("matter_job_")) tag.remove(key);
        }
    }

    private static void refund(ItemStack stack, Object2LongOpenHashMap<AEKey> refunds) {
        var key = AEItemKey.of(stack);
        if (key != null) refunds.addTo(key, stack.getCount());
    }
}
