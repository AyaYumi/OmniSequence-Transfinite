package com.atir.molecularmanipulator.menu;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.util.ConfigMenuInventory;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

/** A synchronized, read-only window onto the machine's long-sized output buffer. */
final class AutoCrafterOutputView extends GenericStackInv {
    static final int COLUMNS = 9;
    static final int ROWS = 2;
    static final int SLOTS = COLUMNS * ROWS;
    private final LinkedHashMap<AEKey, GenericStack> entries = new LinkedHashMap<>();

    AutoCrafterOutputView() {
        this(java.util.stream.StreamSupport.stream(AEKeyTypes.getAll().spliterator(), false)
                .collect(java.util.stream.Collectors.toSet()));
    }

    AutoCrafterOutputView(Set<AEKeyType> supportedTypes) {
        super(null, Mode.STORAGE, SLOTS);
        setFilter(key -> supportedTypes.contains(key.getType()));
    }

    int refresh(List<GenericStack> outputs, int requestedPage) {
        // Keep existing types in place while amounts change, independent of hash-map iteration order.
        var current = new LinkedHashMap<AEKey, GenericStack>();
        for (var stack : outputs) {
            if (stack != null && stack.amount() > 0) current.put(stack.what(), stack);
        }
        entries.keySet().retainAll(current.keySet());
        entries.putAll(current);
        int page = net.minecraft.util.Mth.clamp(requestedPage, 0, pageCount() - 1);
        var visible = entries.values().stream().skip((long) page * SLOTS).limit(SLOTS).toList();
        for (int slot = 0; slot < SLOTS; slot++) {
            setStack(slot, slot < visible.size() ? visible.get(slot) : null);
        }
        return page;
    }

    int pageCount() {
        return Math.max(1, (entries.size() + SLOTS - 1) / SLOTS);
    }

    @Override
    public ConfigMenuInventory createMenuWrapper() {
        return new ConfigMenuInventory(this) {
            @Override
            public void setItemDirect(int slot, ItemStack stack) {
                // The normal wrapper converts item amounts to int during client slot sync.
                // Keep wrapped long amounts intact for this read-only buffer view.
                var generic = GenericStack.unwrapItemStack(stack);
                if (generic != null) setStack(slot, generic);
                else super.setItemDirect(slot, stack);
            }
        };
    }

    @Override
    public long getMaxAmount(AEKey key) {
        return Long.MAX_VALUE;
    }

    @Override
    public boolean canInsert() {
        return false;
    }

    @Override
    public boolean canExtract() {
        return false;
    }
}
