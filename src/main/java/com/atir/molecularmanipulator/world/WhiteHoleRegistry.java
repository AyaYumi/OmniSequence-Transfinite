package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.blockentity.CosmicSingularityBlockEntity;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent white-hole index per dimension, partitioned by the team bound at placement. */
public final class WhiteHoleRegistry extends SavedData {
    private static final String DATA_NAME = "molecularmanipulator_white_hole";
    private static final String HOLES_TAG = "holes";
    private static final String POS_TAG = "position";
    private static final String TEAM_TAG = "team";

    private final Map<BlockPos, UUID> owners = new HashMap<>();
    private final Map<UUID, Set<BlockPos>> byTeam = new HashMap<>();

    private WhiteHoleRegistry() {
    }

    public static void register(ServerLevel level, BlockPos position, UUID team) {
        var data = get(level);
        if (team == null) {
            unregister(level, position);
        } else if (!team.equals(data.owners.get(position))) {
            data.remove(position);
            data.add(position, team);
            data.setDirty();
        }
    }

    public static void unregister(ServerLevel level, BlockPos position) {
        var data = get(level);
        if (data.remove(position)) data.setDirty();
    }

    public static BlockPos nearest(ServerLevel level, BlockPos origin, UUID team) {
        if (team == null) return null;
        var data = get(level);
        var positions = data.byTeam.get(team);
        if (positions == null) return null;
        BlockPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (var iterator = positions.iterator(); iterator.hasNext();) {
            var pos = iterator.next();
            // White-hole tickets restore unloaded exits. Searching must never
            // load arbitrary chunks or discard an exit still being restored.
            if (!level.hasChunkAt(pos)) continue;
            if (!level.getBlockState(pos).is(ModContent.WHITE_HOLE_BLOCK.get())
                    || !(level.getBlockEntity(pos) instanceof CosmicSingularityBlockEntity hole)
                    || !team.equals(hole.getBoundTeam())) {
                iterator.remove();
                data.owners.remove(pos);
                data.setDirty();
                continue;
            }
            double distance = pos.distSqr(origin);
            if (distance < nearestDistance
                    || distance == nearestDistance && (nearest == null || pos.compareTo(nearest) < 0)) {
                nearestDistance = distance;
                nearest = pos;
            }
        }
        if (positions.isEmpty()) data.byTeam.remove(team);
        return nearest;
    }

    private void add(BlockPos pos, UUID team) {
        pos = pos.immutable();
        owners.put(pos, team);
        byTeam.computeIfAbsent(team, ignored -> new HashSet<>()).add(pos);
    }

    private boolean remove(BlockPos pos) {
        var previous = owners.remove(pos);
        if (previous == null) return false;
        var positions = byTeam.get(previous);
        positions.remove(pos);
        if (positions.isEmpty()) byTeam.remove(previous);
        return true;
    }

    private static WhiteHoleRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WhiteHoleRegistry::load, WhiteHoleRegistry::new, DATA_NAME);
    }

    private static WhiteHoleRegistry load(CompoundTag tag) {
        var data = new WhiteHoleRegistry();
        for (var element : tag.getList(HOLES_TAG, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            if (!entry.contains(POS_TAG, Tag.TAG_LONG) || !entry.hasUUID(TEAM_TAG)) continue;
            var pos = BlockPos.of(entry.getLong(POS_TAG));
            data.remove(pos);
            data.add(pos, entry.getUUID(TEAM_TAG));
        }
        // The old single `position` had no ownership. Do not turn it into a
        // shared exit; re-placing the old block establishes its team binding.
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var list = new ListTag();
        for (var hole : owners.entrySet()) {
            var entry = new CompoundTag();
            entry.putLong(POS_TAG, hole.getKey().asLong());
            entry.putUUID(TEAM_TAG, hole.getValue());
            list.add(entry);
        }
        tag.remove(POS_TAG);
        tag.put(HOLES_TAG, list);
        return tag;
    }
}
