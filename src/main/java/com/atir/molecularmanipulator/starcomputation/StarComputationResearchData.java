package com.atir.molecularmanipulator.starcomputation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/** Permanent, server-wide unlocks keyed by the UUID bound through FtbTeamOwnership at placement. */
public final class StarComputationResearchData extends SavedData {
    private static final String DATA_NAME = "molecularmanipulator_star_computation_research";
    private static final String TEAMS_TAG = "teams";
    private static final String TEAM_TAG = "team";
    private static final String MASK_TAG = "unlock_mask";
    private static final String NODES_TAG = "unlocked_nodes";
    /** Never reorder: these names are the ID representation of the five permanent legacy bits. */
    public static final List<ResourceLocation> LEGACY_NODE_IDS = StarComputationRecipes.NODES.stream()
            .map(node -> ResourceLocation.fromNamespaceAndPath("molecularmanipulator", node.id())).toList();
    private static final SavedData.Factory<StarComputationResearchData> FACTORY =
            new SavedData.Factory<>(StarComputationResearchData::new, StarComputationResearchData::load);

    private final Map<UUID, Set<ResourceLocation>> unlocks = new HashMap<>();

    public StarComputationResearchData() {}

    /** Every dimension resolves to the same save; destroying or unloading an engine never removes progress. */
    public static StarComputationResearchData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /** Null ownership is unresolved, never a shared anonymous team. */
    public int getUnlockMask(@Nullable UUID team) {
        if (team == null) return 0;
        var nodes = unlocks.getOrDefault(team, Set.of());
        int mask = 0;
        for (int index = 0; index < LEGACY_NODE_IDS.size(); index++) {
            if (nodes.contains(LEGACY_NODE_IDS.get(index))) mask |= 1 << index;
        }
        return mask;
    }

    /** Immutable snapshot, including IDs whose data pack is temporarily absent. No anonymous-team grants. */
    public Set<ResourceLocation> getUnlockedNodes(@Nullable UUID team) {
        return team == null ? Set.of() : Set.copyOf(unlocks.getOrDefault(team, Set.of()));
    }

    public boolean isUnlocked(@Nullable UUID team, ResourceLocation id) {
        return team != null && id != null && unlocks.getOrDefault(team, Set.of()).contains(id);
    }

    /** Persistence only: the server purchase entry point must validate and pay before calling this method. */
    public boolean addUnlock(@Nullable UUID team, ResourceLocation id) {
        if (team == null || id == null) return false;
        if (!unlocks.computeIfAbsent(team, ignored -> new HashSet<>()).add(id)) return false;
        setDirty();
        return true;
    }

    /**
     * Record an authorized unlock on the owning server thread. The core owns validation of team membership,
     * prerequisites, structure, entropy and payment; this persistence operation does not charge materials.
     * Returns true only for a newly added bit. Duplicate unlock attempts leave the save unchanged.
     */
    public boolean addUnlock(@Nullable UUID team, int index) {
        if (team == null || index < 0 || index >= StarComputationRecipes.NODES.size()) return false;
        return addUnlock(team, LEGACY_NODE_IDS.get(index));
    }

    /** Import already paid legacy core bits once ownership resolves. Never replaces or revokes other bits. */
    public boolean mergeUnlockMask(@Nullable UUID team, int legacyMask) {
        if (team == null) return false;
        int added = (legacyMask & StarComputationRecipes.ALL_UNLOCKS) & ~getUnlockMask(team);
        if (added == 0) return false;
        var nodes = unlocks.computeIfAbsent(team, ignored -> new HashSet<>());
        for (int index = 0; index < LEGACY_NODE_IDS.size(); index++) {
            if ((added & (1 << index)) != 0) nodes.add(LEGACY_NODE_IDS.get(index));
        }
        setDirty();
        return true;
    }

    public static StarComputationResearchData load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new StarComputationResearchData();
        for (var value : tag.getList(TEAMS_TAG, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value;
            if (!entry.hasUUID(TEAM_TAG)) continue;
            var nodes = new HashSet<ResourceLocation>();
            int mask = entry.getInt(MASK_TAG) & StarComputationRecipes.ALL_UNLOCKS;
            for (int index = 0; index < LEGACY_NODE_IDS.size(); index++) {
                if ((mask & (1 << index)) != 0) nodes.add(LEGACY_NODE_IDS.get(index));
            }
            for (var valueId : entry.getList(NODES_TAG, Tag.TAG_STRING)) {
                String name = valueId.getAsString();
                if (name.isBlank()) continue;
                var id = ResourceLocation.tryParse(name);
                if (id != null) nodes.add(id);
            }
            if (!nodes.isEmpty()) {
                data.unlocks.computeIfAbsent(entry.getUUID(TEAM_TAG), ignored -> new HashSet<>()).addAll(nodes);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var teams = new ListTag();
        unlocks.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(unlock -> {
            var entry = new CompoundTag();
            entry.putUUID(TEAM_TAG, unlock.getKey());
            // Write both representations: older readers still see the five built-in grants.
            entry.putInt(MASK_TAG, getUnlockMask(unlock.getKey()));
            var nodes = new ListTag();
            unlock.getValue().stream().sorted().forEach(id -> nodes.add(StringTag.valueOf(id.toString())));
            entry.put(NODES_TAG, nodes);
            teams.add(entry);
        });
        tag.put(TEAMS_TAG, teams);
        return tag;
    }
}
