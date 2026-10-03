package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/** Copies one sample item for one bucket, retaining any interrupted output or fluid refund. */
public final class SingularityDuplicationProcessor {
    public static final int MATTER_PER_ITEM = AEFluidKey.AMOUNT_BUCKET;
    private final Runnable saveChanges;
    private ItemStack pendingOutput = ItemStack.EMPTY;
    private int pendingRefund;

    public SingularityDuplicationProcessor(Runnable saveChanges) {
        this.saveChanges = saveChanges;
    }

    /** An empty sample only flushes already owned contents; it never starts another copy. */
    public void tick(MEStorage storage, AEFluidKey matter, IActionSource source, ItemStack sample) {
        if (!flush(storage, matter, source) || sample.isEmpty()) return;
        var output = AEItemKey.of(sample);
        if (storage.insert(output, 1, Actionable.SIMULATE, source) < 1) return;
        if (storage.extract(matter, MATTER_PER_ITEM, Actionable.SIMULATE, source) < MATTER_PER_ITEM) return;

        long extracted = storage.extract(matter, MATTER_PER_ITEM, Actionable.MODULATE, source);
        if (extracted < MATTER_PER_ITEM) {
            // Storage-bus inventories can change between simulation and extraction.
            // Retain an unaccepted refund instead of spending part of a bucket.
            pendingRefund = (int) extracted;
            if (pendingRefund > 0) {
                saveChanges.run();
                flush(storage, matter, source);
            }
            return;
        }
        pendingOutput = sample.copyWithCount(1);
        saveChanges.run();
        flush(storage, matter, source);
    }

    private boolean flush(MEStorage storage, AEFluidKey matter, IActionSource source) {
        if (pendingRefund > 0) {
            int inserted = (int) storage.insert(matter, pendingRefund, Actionable.MODULATE, source);
            if (inserted > 0) {
                pendingRefund -= inserted;
                saveChanges.run();
            }
            if (pendingRefund > 0) return false;
        }
        if (!pendingOutput.isEmpty()) {
            int inserted = (int) storage.insert(AEItemKey.of(pendingOutput), pendingOutput.getCount(), Actionable.MODULATE, source);
            if (inserted > 0) {
                pendingOutput.shrink(inserted);
                saveChanges.run();
            }
            if (!pendingOutput.isEmpty()) return false;
        }
        return true;
    }

    public boolean hasContents() {
        return pendingRefund > 0 || !pendingOutput.isEmpty();
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("refund", pendingRefund);
        tag.put("output", pendingOutput.saveOptional(registries));
        return tag;
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        pendingRefund = Math.clamp(tag.getInt("refund"), 0, MATTER_PER_ITEM - 1);
        pendingOutput = ItemStack.parseOptional(registries, tag.getCompound("output"));
    }

    public void clear() {
        pendingOutput = ItemStack.EMPTY;
        pendingRefund = 0;
    }
}
