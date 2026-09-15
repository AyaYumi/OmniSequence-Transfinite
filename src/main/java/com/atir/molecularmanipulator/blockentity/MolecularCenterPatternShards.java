package com.atir.molecularmanipulator.blockentity;

import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/** Splits the Sequence Array pattern library across the 14 quantum crystals. */
public final class MolecularCenterPatternShards {
    public static final int CRYSTAL_COUNT = 14;
    public static final String PATTERNS_TAG = PatternProviderLogic.NBT_MEMORY_CARD_PATTERNS;

    private MolecularCenterPatternShards() {
    }

    public static int shardSize() {
        return (MolecularCenterBlockEntity.MAX_PATTERN_SLOTS + CRYSTAL_COUNT - 1) / CRYSTAL_COUNT;
    }

    public static List<MolecularCenterStructure.Part> crystals() {
        return CrystalLayout.PARTS;
    }

    private static final class CrystalLayout {
        private static final List<MolecularCenterStructure.Part> PARTS = findCrystals();
    }

    private static List<MolecularCenterStructure.Part> findCrystals() {
        var crystals = new ArrayList<MolecularCenterStructure.Part>(CRYSTAL_COUNT);
        for (var part : MolecularCenterStructure.parts()) {
            if (part.partType() == MolecularCenterStructure.PartType.COIL) crystals.add(part);
        }
        crystals.sort((left, right) -> {
            int compare = Integer.compare(left.y(), right.y());
            if (compare != 0) return compare;
            compare = Integer.compare(left.x(), right.x());
            if (compare != 0) return compare;
            return Integer.compare(left.z(), right.z());
        });
        return List.copyOf(crystals);
    }

    public static List<ListTag> split(ListTag patterns) {
        var shards = new ArrayList<ListTag>(CRYSTAL_COUNT);
        for (int shard = 0; shard < CRYSTAL_COUNT; shard++) shards.add(new ListTag());
        int size = shardSize();
        for (int index = 0; index < patterns.size(); index++) {
            var item = patterns.getCompound(index).copy();
            int slot = item.getInt("Slot");
            if (slot < 0 || slot >= MolecularCenterBlockEntity.MAX_PATTERN_SLOTS) continue;
            int shard = Math.min(slot / size, CRYSTAL_COUNT - 1);
            item.putInt("Slot", slot - shard * size);
            shards.get(shard).add(item);
        }
        return List.copyOf(shards);
    }

    public static ListTag merge(List<ListTag> shards) {
        var merged = new ListTag();
        int size = shardSize();
        for (int shard = 0; shard < shards.size() && shard < CRYSTAL_COUNT; shard++) {
            var items = shards.get(shard);
            for (int index = 0; index < items.size(); index++) {
                var item = items.getCompound(index).copy();
                int slot = item.getInt("Slot");
                if (slot < 0 || slot >= size) continue;
                item.putInt("Slot", slot + shard * size);
                merged.add(item);
            }
        }
        return merged;
    }

    /** Preserve a prefilled controller and recovered crystals when first forming a new array. */
    @org.jetbrains.annotations.Nullable
    public static ListTag combine(ListTag controller, ListTag recovered) {
        var result = new ListTag();
        var occupied = new java.util.BitSet(MolecularCenterBlockEntity.MAX_PATTERN_SLOTS);
        var conflicts = new ListTag();
        for (var source : List.of(controller, recovered)) {
            for (var entry : source) {
                var item = ((CompoundTag) entry).copy();
                int slot = item.getInt("Slot");
                if (slot < 0 || slot >= MolecularCenterBlockEntity.MAX_PATTERN_SLOTS || occupied.get(slot)) {
                    conflicts.add(item);
                } else {
                    result.add(item);
                    occupied.set(slot);
                }
            }
        }
        for (var entry : conflicts) {
            int slot = occupied.nextClearBit(0);
            if (slot >= MolecularCenterBlockEntity.MAX_PATTERN_SLOTS) return null;
            var item = (CompoundTag) entry;
            item.putInt("Slot", slot);
            result.add(item);
            occupied.set(slot);
        }
        return result;
    }

    public static ListTag inventoryList(AppEngInternalInventory inventory) {
        var tag = new CompoundTag();
        inventory.writeToNBT(tag, PATTERNS_TAG);
        return tag.contains(PATTERNS_TAG, Tag.TAG_LIST) ? tag.getList(PATTERNS_TAG, Tag.TAG_COMPOUND) : new ListTag();
    }

    public static void applyList(AppEngInternalInventory inventory, ListTag patterns) {
        var tag = new CompoundTag();
        if (!patterns.isEmpty()) tag.put(PATTERNS_TAG, patterns);
        for (int slot = 0; slot < inventory.size(); slot++) inventory.setItemDirect(slot, ItemStack.EMPTY);
        inventory.readFromNBT(tag, PATTERNS_TAG);
    }

    public static void takeFromController(CompoundTag tag) {
        tag.remove(PATTERNS_TAG);
    }
}
