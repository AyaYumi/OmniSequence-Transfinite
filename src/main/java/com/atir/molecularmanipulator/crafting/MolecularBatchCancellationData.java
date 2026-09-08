package com.atir.molecularmanipulator.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent cancellation tombstones. They intentionally outlive unloaded
 * providers so a canceled CPU job cannot resume after a server restart.
 */
public final class MolecularBatchCancellationData extends SavedData {
    private static final String DATA_NAME =
            "molecularmanipulator_reusable_batch_cancellations";
    private static final String ENTRIES_TAG = "entries";
    private static final String ID_TAG = "id";

    private final Set<UUID> canceledCrafts = new HashSet<>();

    public static void markCanceled(Level level, UUID craftingId) {
        if (level instanceof ServerLevel serverLevel && craftingId != null) {
            var data = get(serverLevel);
            if (data.canceledCrafts.add(craftingId)) {
                data.setDirty();
            }
        }
    }

    public static boolean isCanceled(Level level, UUID craftingId) {
        return level instanceof ServerLevel serverLevel
                && craftingId != null
                && get(serverLevel).canceledCrafts.contains(craftingId);
    }

    private static MolecularBatchCancellationData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage()
                .computeIfAbsent(MolecularBatchCancellationData::load, MolecularBatchCancellationData::new, DATA_NAME);
    }

    private static MolecularBatchCancellationData load(CompoundTag tag) {
        var data = new MolecularBatchCancellationData();
        var entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (var entry : entries) {
            var compound = (CompoundTag) entry;
            if (compound.hasUUID(ID_TAG)) {
                data.canceledCrafts.add(compound.getUUID(ID_TAG));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        var entries = new ListTag();
        for (var craftingId : canceledCrafts) {
            var entry = new CompoundTag();
            entry.putUUID(ID_TAG, craftingId);
            entries.add(entry);
        }
        tag.put(ENTRIES_TAG, entries);
        return tag;
    }
}
