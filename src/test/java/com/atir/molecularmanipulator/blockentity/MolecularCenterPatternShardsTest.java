package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.PartType;
import java.util.HashSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class MolecularCenterPatternShardsTest {
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
