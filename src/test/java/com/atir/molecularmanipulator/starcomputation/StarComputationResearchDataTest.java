package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class StarComputationResearchDataTest {
    @Test
    void unlocksSurviveReloadAndStayPartitionedByBoundTeam() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var data = new StarComputationResearchData();
        assertTrue(data.addUnlock(first, 0));
        assertTrue(data.addUnlock(first, 1));
        assertTrue(data.addUnlock(second, 3));

        var loaded = StarComputationResearchData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(3, loaded.getUnlockMask(first));
        assertEquals(1 << 3, loaded.getUnlockMask(second));
        assertEquals(0, loaded.getUnlockMask(UUID.randomUUID()));
        assertFalse(loaded.isDirty(), "Reading saved progress does not mutate it");
        assertTrue(loaded.addUnlock(first, 2));
        var restarted = StarComputationResearchData.load(loaded.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(7, restarted.getUnlockMask(first));
        assertEquals(1 << 3, restarted.getUnlockMask(second));
    }

    @Test
    void duplicateUnlocksAndUnresolvedOwnershipDoNotCreateOrDirtyProgress() {
        var team = UUID.randomUUID();
        var data = new StarComputationResearchData();
        assertFalse(data.addUnlock(null, 0));
        assertFalse(data.mergeUnlockMask(null, StarComputationRecipes.ALL_UNLOCKS));
        assertFalse(data.addUnlock(team, -1));
        assertFalse(data.addUnlock(team, StarComputationRecipes.NODES.size()));
        assertFalse(data.addUnlock(team, 32), "Java shift wraparound must not grant foundation");
        assertEquals(0, data.getUnlockMask(null));
        assertFalse(data.isDirty());
        assertTrue(data.addUnlock(team, 0));
        assertTrue(data.isDirty());
        data.setDirty(false);
        assertFalse(data.addUnlock(team, 0));
        assertFalse(data.isDirty());
        assertEquals(1, data.getUnlockMask(team));
    }

    @Test
    void legacyImportUnionsProgressWithoutChargingAgainOrRevokingOtherBits() {
        var team = UUID.randomUUID();
        var data = new StarComputationResearchData();
        assertTrue(data.addUnlock(team, 3));
        assertTrue(data.mergeUnlockMask(team, 3));
        assertEquals(11, data.getUnlockMask(team));
        data.setDirty(false);
        assertFalse(data.mergeUnlockMask(team, 1));
        assertFalse(data.mergeUnlockMask(team, 1 << 30));
        assertFalse(data.isDirty());
        assertEquals(11, data.getUnlockMask(team));
        assertTrue(data.mergeUnlockMask(team, -1));
        assertEquals(StarComputationRecipes.ALL_UNLOCKS, data.getUnlockMask(team));
    }

    @Test
    void loadMergesDuplicateTeamsAndIgnoresMalformedOwnershipAndUnknownBits() {
        var team = UUID.randomUUID();
        var entries = new ListTag();
        entries.add(entry(team, 1));
        entries.add(entry(team, 2 | (1 << 30)));
        var missingTeam = new CompoundTag();
        missingTeam.putInt("unlock_mask", StarComputationRecipes.ALL_UNLOCKS);
        entries.add(missingTeam);
        var malformedTeam = new CompoundTag();
        malformedTeam.putString("team", "not a UUID");
        malformedTeam.putInt("unlock_mask", StarComputationRecipes.ALL_UNLOCKS);
        entries.add(malformedTeam);
        entries.add(entry(UUID.randomUUID(), 1 << 30));
        var tag = new CompoundTag();
        tag.put("teams", entries);

        var data = StarComputationResearchData.load(tag, RegistryAccess.EMPTY);
        assertEquals(3, data.getUnlockMask(team));
        assertEquals(0, data.getUnlockMask(null));
        assertEquals(1, data.save(new CompoundTag(), RegistryAccess.EMPTY).getList("teams", 10).size());
        assertFalse(data.isDirty());
    }

    private static CompoundTag entry(UUID team, int mask) {
        var entry = new CompoundTag();
        entry.putUUID("team", team);
        entry.putInt("unlock_mask", mask);
        return entry;
    }
}
