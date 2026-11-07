package com.atir.molecularmanipulator.crafting;

import appeng.api.inventories.InternalInventory;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.BitSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Controller-owned, lazy index. Inventory callbacks maintain it; neither ticks nor queries rescan it. */
public final class PatternUploadIndex {
    private InternalInventory inventory;
    private AEItemKey[] slots = new AEItemKey[0];
    private final Object2IntOpenHashMap<AEItemKey> counts = new Object2IntOpenHashMap<>();
    private final BitSet occupied = new BitSet();

    public boolean contains(InternalInventory source, int activeSlots, ItemStack pattern) {
        ensure(source, activeSlots);
        var key = keyOf(pattern);
        return key != null && counts.containsKey(key);
    }

    public int firstEmptySlot(InternalInventory source, int activeSlots) {
        ensure(source, activeSlots);
        int slot = occupied.nextClearBit(0);
        return slot < slots.length ? slot : -1;
    }

    /** Called after a real inventory write, including removal, rollback and crystal restoration. */
    public void slotChanged(InternalInventory source, int slot) {
        if (inventory != source || slot < 0 || slot >= slots.length) return;
        var old = slots[slot];
        if (old != null) {
            int remaining = counts.getInt(old) - 1;
            if (remaining == 0) counts.removeInt(old);
            else counts.put(old, remaining);
        }
        var stack = source.getStackInSlot(slot);
        var key = keyOf(stack);
        slots[slot] = key;
        occupied.set(slot, !stack.isEmpty());
        if (key != null) counts.addTo(key, 1);
    }

    /** NBT loading can bypass inventory notifications. Rebuild only on the next actual upload query. */
    public void invalidate() {
        inventory = null;
        slots = new AEItemKey[0];
        counts.clear();
        occupied.clear();
    }

    private void ensure(InternalInventory source, int activeSlots) {
        int size = Math.clamp(activeSlots, 0, source.size());
        if (inventory == source && slots.length == size) return;
        invalidate();
        inventory = source;
        slots = new AEItemKey[size];
        for (int slot = 0; slot < size; slot++) slotChanged(source, slot);
    }

    public static AEItemKey keyOf(ItemStack stack) {
        return stack.isEmpty() ? null : AEItemKey.of(withoutEncoder(stack));
    }

    public static ItemStack withoutEncoder(ItemStack stack) {
        var copy = stack.copyWithCount(1);
        var customData = copy.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            tag.remove("encodePlayer");
            if (tag.isEmpty()) copy.remove(DataComponents.CUSTOM_DATA);
            else copy.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
        return copy;
    }
}
