package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

/** Persists the one white-hole position allowed in each dimension. */
public final class WhiteHoleRegistry extends SavedData {
    private static final String DATA_NAME = "molecularmanipulator_white_hole";
    private static final String POS_TAG = "position";

    private BlockPos position;

    private WhiteHoleRegistry() {
    }

    public static boolean canPlace(ServerLevel level) {
        var data = get(level);
        if (data.position == null) return true;
        if (level.getBlockState(data.position).is(ModContent.WHITE_HOLE_BLOCK.get())) return false;
        data.position = null;
        data.setDirty();
        return true;
    }

    public static void register(ServerLevel level, BlockPos position) {
        var data = get(level);
        if (data.position == null) {
            data.position = position.immutable();
            data.setDirty();
        }
    }

    public static void unregister(ServerLevel level, BlockPos position) {
        var data = get(level);
        if (data.position != null && data.position.equals(position)) {
            data.position = null;
            data.setDirty();
        }
    }

    public static BlockPos position(Level level) {
        return level instanceof ServerLevel server ? get(server).position : null;
    }

    private static WhiteHoleRegistry get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(WhiteHoleRegistry::load, WhiteHoleRegistry::new, DATA_NAME);
    }

    private static WhiteHoleRegistry load(CompoundTag tag) {
        var data = new WhiteHoleRegistry();
        if (tag.contains(POS_TAG, Tag.TAG_LONG)) {
            data.position = BlockPos.of(tag.getLong(POS_TAG));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (position != null) tag.putLong(POS_TAG, position.asLong());
        return tag;
    }
}
