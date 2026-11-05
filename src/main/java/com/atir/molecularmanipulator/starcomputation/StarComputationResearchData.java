package com.atir.molecularmanipulator.starcomputation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Permanent, server-wide unlocks keyed by the UUID bound through FtbTeamOwnership at placement. */
public final class StarComputationResearchData extends SavedData {
    private static final String DATA_NAME = "molecularmanipulator_star_computation_research";
    private static final String TEAMS_TAG = "teams";
    private static final String TEAM_TAG = "team";
    private static final String MASK_TAG = "unlock_mask";
    private static final SavedData.Factory<StarComputationResearchData> FACTORY =
            new SavedData.Factory<>(StarComputationResearchData::new, StarComputationResearchData::load);

    private final Map<UUID, Integer> unlocks = new HashMap<>();

    public StarComputationResearchData() {}

    /** Every dimension resolves to the same save; destroying or unloading an engine never removes progress. */
    public static StarComputationResearchData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Null ownership is unresolved, never a shared anonymous team. */
    public int getUnlockMask(@Nullable UUID team) {
        return team == null ? 0 : unlocks.getOrDefault(team, 0);
    }

    /**
     * Record an authorized unlock on the owning server thread. The core owns validation of team membership,
     * prerequisites, structure, entropy and payment; this persistence operation does not charge materials.
     * Returns true only for a newly added bit. Duplicate unlock attempts leave the save unchanged.
     */
    public boolean addUnlock(@Nullable UUID team, int index) {
        if (team == null || index < 0 || index >= StarComputationRecipes.NODES.size()) return false;
        return mergeUnlockMask(team, 1 << index);
    }

    /** Import already paid legacy core bits once ownership resolves. Never replaces or revokes other bits. */
    public boolean mergeUnlockMask(@Nullable UUID team, int legacyMask) {
        if (team == null) return false;
        int previous = getUnlockMask(team);
        int combined = previous | (legacyMask & StarComputationRecipes.ALL_UNLOCKS);
        if (combined == previous) return false;
        unlocks.put(team, combined);
        setDirty();
        return true;
    }

    public static StarComputationResearchData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new StarComputationResearchData();
        for (var value : tag.getList(TEAMS_TAG, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (!entry.hasUUID(TEAM_TAG)) continue;
            int mask = entry.getInt(MASK_TAG) & StarComputationRecipes.ALL_UNLOCKS;
            if (mask != 0) data.unlocks.merge(entry.getUUID(TEAM_TAG), mask, (first, second) -> first | second);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var teams = new ListTag();
        unlocks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(unlock -> {
            var entry = new CompoundTag();
            entry.putUUID(TEAM_TAG, unlock.getKey());
            entry.putInt(MASK_TAG, unlock.getValue());
            teams.add(entry);
        });
        tag.put(TEAMS_TAG, teams);
        return tag;
    }
}
