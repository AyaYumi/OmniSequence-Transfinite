package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class MolecularCenterPatternShardsTest {
    @Test
    void firstFormationPreservesPrefilledControllerAndRecoveredCrystals() {
        var local = new ListTag();
        var recovered = new ListTag();
        for (var source : java.util.List.of(local, recovered)) {
            var item = new CompoundTag();
            item.putInt("Slot", 0);
            item.putString("source", source == local ? "controller" : "crystal");
            source.add(item);
        }
        var combined = MolecularCenterPatternShards.combine(local, recovered);
        assertEquals(2, combined.size());
        assertEquals(0, combined.getCompound(0).getInt("Slot"));
        assertEquals(1, combined.getCompound(1).getInt("Slot"));
        assertEquals("crystal", combined.getCompound(1).getString("source"));
        assertEquals(0, recovered.getCompound(0).getInt("Slot"), "Original shards must remain untouched");
        var full = new ListTag();
        for (int slot = 0; slot < MolecularCenterBlockEntity.MAX_PATTERN_SLOTS; slot++) {
            var item = new CompoundTag(); item.putInt("Slot", slot); full.add(item);
        }
        assertNull(MolecularCenterPatternShards.combine(full, recovered), "Overflow must not discard either source");
        assertEquals(MolecularCenterBlockEntity.MAX_PATTERN_SLOTS, full.size());
        assertEquals(1, recovered.size());
    }

    @Test
    void forgeInventorySerializationRetainsPatternsAndNbtAcrossCrystalShards() throws Exception {
        Class.forName(com.atir.molecularmanipulator.client.UiRenderRecorder.class.getName());
        var inventory = new appeng.util.inv.AppEngInternalInventory(null, MolecularCenterBlockEntity.MAX_PATTERN_SLOTS);
        var first = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER);
        first.getOrCreateTag().putString("pattern_test", "first");
        var last = first.copy();
        last.getOrCreateTag().putString("pattern_test", "last");
        int finalSlot = MolecularCenterBlockEntity.MAX_PATTERN_SLOTS - 1;
        inventory.setItemDirect(0, first);
        inventory.setItemDirect(finalSlot, last);
        var saved = MolecularCenterPatternShards.inventoryList(inventory);
        var merged = MolecularCenterPatternShards.merge(MolecularCenterPatternShards.split(saved));
        var restored = new appeng.util.inv.AppEngInternalInventory(null, MolecularCenterBlockEntity.MAX_PATTERN_SLOTS);
        MolecularCenterPatternShards.applyList(restored, merged);
        assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameTags(first, restored.getStackInSlot(0)));
        assertTrue(net.minecraft.world.item.ItemStack.isSameItemSameTags(last, restored.getStackInSlot(finalSlot)));
        assertTrue(restored.getStackInSlot(1).isEmpty());
        assertEquals(2, MolecularCenterPatternShards.inventoryList(restored).size());
    }

    private static int shardSize() {
        return MolecularCenterPatternShards.shardSize();
    }

    @Test
    void currentLayoutHasFourteenDistinctCrystals() {
        var crystals = MolecularCenterPatternShards.crystals();
        assertEquals(MolecularCenterPatternShards.CRYSTAL_COUNT, crystals.size());
        assertTrue(crystals.stream().allMatch(part -> part.partType() == PartType.COIL));
        var positions = new HashSet<String>();
        for (var crystal : crystals) {
            assertTrue(positions.add(crystal.x() + "," + crystal.y() + "," + crystal.z()));
        }
    }

    @Test
    void occupiedSlotsRoundTripAcrossFourteenShards() {
        var patterns = new ListTag();
        for (int slot : new int[] {0, 35, 36, shardSize() - 1, shardSize(),
                MolecularCenterBlockEntity.MAX_PATTERN_SLOTS - 1}) {
            var item = new CompoundTag();
            item.putInt("Slot", slot);
            item.putString("id", "slot_" + slot);
            patterns.add(item);
        }
        var shards = MolecularCenterPatternShards.split(patterns);
        assertEquals(MolecularCenterPatternShards.CRYSTAL_COUNT, shards.size());
        var merged = MolecularCenterPatternShards.merge(shards);
        assertEquals(6, merged.size());
        var restored = new HashSet<Integer>();
        for (int index = 0; index < merged.size(); index++) {
            restored.add(merged.getCompound(index).getInt("Slot"));
        }
        assertEquals(java.util.Set.of(0, 35, 36, shardSize() - 1, shardSize(),
                MolecularCenterBlockEntity.MAX_PATTERN_SLOTS - 1), restored);
    }
}
