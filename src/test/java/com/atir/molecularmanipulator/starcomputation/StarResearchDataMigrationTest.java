package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StarResearchDataMigrationTest {
    @Test
    void fullIdProgressSurvivesReloadWithoutFiveOr32NodeLimitAndIsTeamIsolated() {
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var data = new StarComputationResearchData();
        var expected = new HashSet<ResourceLocation>();
        for (int index = 0; index < 128; index++) {
            var id = id("kubejs:research/" + index);
            expected.add(id);
            assertTrue(data.addUnlock(first, id));
        }
        var other = id("another_pack:independent");
        assertTrue(data.addUnlock(second, other));
        var loaded = reload(data);
        assertEquals(expected, loaded.getUnlockedNodes(first));
        assertEquals(Set.of(other), loaded.getUnlockedNodes(second));
        assertEquals(0, loaded.getUnlockMask(first), "Custom IDs never wrap into legacy bits");
        assertTrue(loaded.isUnlocked(first, id("kubejs:research/100")));
        assertFalse(loaded.isUnlocked(second, id("kubejs:research/100")));
        assertEquals(Set.of(), loaded.getUnlockedNodes(UUID.randomUUID()));
        assertFalse(loaded.isDirty());
        // There is intentionally no catalog lookup during loading: a removed data pack cannot erase paid progress.
        assertEquals(expected, reload(loaded).getUnlockedNodes(first));
    }

    @Test
    void snapshotsAreImmutableDuplicatesAreCleanAndNullTeamsNeverAuthorize() {
        var team = UUID.randomUUID();
        var node = id("pack:node");
        var data = new StarComputationResearchData();
        assertFalse(data.addUnlock(null, node));
        assertFalse(data.isUnlocked(null, node));
        assertEquals(Set.of(), data.getUnlockedNodes(null));
        assertFalse(data.isDirty());
        assertTrue(data.addUnlock(team, node));
        var snapshot = data.getUnlockedNodes(team);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(id("pack:forged")));
        data.setDirty(false);
        assertFalse(data.addUnlock(team, node));
        assertFalse(data.isDirty());
        assertTrue(data.addUnlock(team, id("pack:later")));
        assertEquals(Set.of(node), snapshot, "Previously returned sets are snapshots, not live mutable views");
        assertEquals(0, data.getUnlockMask(null));
    }

    @Test
    void legacyCoreMasksBecomeStableIdsAndNewBuiltinIdsWriteOldMasks() {
        var team = UUID.randomUUID();
        var data = new StarComputationResearchData();
        var ids = List.of(id("molecularmanipulator:foundation"), id("molecularmanipulator:material"),
                id("molecularmanipulator:quantum"), id("molecularmanipulator:terminal"),
                id("molecularmanipulator:transfinite"));
        assertEquals(ids, StarComputationResearchData.LEGACY_NODE_IDS);
        assertTrue(data.addUnlock(team, id("pack:kept")));
        assertTrue(data.mergeUnlockMask(team, 1 | (1 << 3)));
        assertTrue(data.isUnlocked(team, ids.get(0)));
        assertTrue(data.isUnlocked(team, ids.get(3)));
        data.setDirty(false);
        assertFalse(data.addUnlock(team, ids.get(0)), "Imported grants cannot be purchased again");
        assertFalse(data.mergeUnlockMask(team, 9));
        assertFalse(data.isDirty());
        assertTrue(data.addUnlock(team, ids.get(1)));
        assertTrue(data.addUnlock(team, 2));
        assertTrue(data.addUnlock(team, ids.get(4)));
        assertEquals(31, data.getUnlockMask(team));
        var entry = data.save(new CompoundTag(), RegistryAccess.EMPTY).getList("teams", Tag.TAG_COMPOUND).getCompound(0);
        assertEquals(31, entry.getInt("unlock_mask"));
        assertEquals(6, entry.getList("unlocked_nodes", Tag.TAG_STRING).size());
        assertEquals(data.getUnlockedNodes(team), reload(data).getUnlockedNodes(team));
    }

    @Test
    void loadingUnionsOldMasksIdOnlyEntriesDuplicatesAndUnknownIdsWithoutDirtying() {
        var team = UUID.randomUUID();
        var entries = new ListTag();
        var old = entry(team);
        old.putInt("unlock_mask", 3 | (1 << 30));
        entries.add(old);
        var modern = entry(team);
        modern.put("unlocked_nodes", strings("molecularmanipulator:quantum", "removed_pack:still_paid",
                "removed_pack:still_paid", "INVALID ID", ""));
        entries.add(modern);
        var missingOwnership = new CompoundTag();
        missingOwnership.put("unlocked_nodes", strings("pack:must_not_grant"));
        entries.add(missingOwnership);
        var tag = new CompoundTag();
        tag.put("teams", entries);
        var data = StarComputationResearchData.load(tag, RegistryAccess.EMPTY);
        assertEquals(7, data.getUnlockMask(team));
        assertEquals(Set.of(id("molecularmanipulator:foundation"), id("molecularmanipulator:material"),
                id("molecularmanipulator:quantum"), id("removed_pack:still_paid")), data.getUnlockedNodes(team));
        assertFalse(data.isDirty());
        assertEquals(1, data.save(new CompoundTag(), RegistryAccess.EMPTY).getList("teams", Tag.TAG_COMPOUND).size());
        assertEquals(data.getUnlockedNodes(team), reload(data).getUnlockedNodes(team));
    }

    private static CompoundTag entry(UUID team) {
        var tag = new CompoundTag();
        tag.putUUID("team", team);
        return tag;
    }

    private static ListTag strings(String... ids) {
        var tags = new ListTag();
        for (var id : ids) tags.add(StringTag.valueOf(id));
        return tags;
    }

    private static StarComputationResearchData reload(StarComputationResearchData data) {
        return StarComputationResearchData.load(data.save(new CompoundTag(), RegistryAccess.EMPTY), RegistryAccess.EMPTY);
    }

    private static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
}
