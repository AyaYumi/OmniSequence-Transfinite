package com.atir.molecularmanipulator.crafting;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    private static final String ACTIVE_BATCHES_TAG = "active_batches";
    private static final String BATCH_ID_TAG = "batch_id";
    private static final String CRAFTING_ID_TAG = "crafting_id";

    private final Set<UUID> canceledCrafts = new HashSet<>();
    private final Map<UUID, UUID> activeBatches = new HashMap<>();

    /**
     * Registers one provider-owned batch before it can observe cancellation.
     * Repeating the same mapping is a no-op. A conflicting mapping for an
     * existing batch ID is rejected without changing either job.
     */
    public static boolean register(Level level, UUID batchId, UUID craftingId) {
        return level instanceof ServerLevel serverLevel
                && get(serverLevel).registerBatch(batchId, craftingId);
    }

    /**
     * Releases one provider-owned batch. The cancellation tombstone is removed
     * only when no other active batch belongs to the same crafting job.
     */
    public static void release(Level level, UUID batchId, UUID craftingId) {
        if (level instanceof ServerLevel serverLevel) {
            get(serverLevel).releaseBatch(batchId, craftingId);
        }
    }

    public static void markCanceled(Level level, UUID craftingId) {
        if (level instanceof ServerLevel serverLevel && craftingId != null) {
            get(serverLevel).markCraftCanceled(craftingId);
        }
    }

    public static boolean isCanceled(Level level, UUID craftingId) {
        return level instanceof ServerLevel serverLevel
                && craftingId != null
                && get(serverLevel).canceledCrafts.contains(craftingId);
    }

    private static MolecularBatchCancellationData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                MolecularBatchCancellationData::load,
                MolecularBatchCancellationData::new,
                DATA_NAME);
    }

    static MolecularBatchCancellationData load(CompoundTag tag) {
        var data = new MolecularBatchCancellationData();
        var entries = tag.getList(ENTRIES_TAG, Tag.TAG_COMPOUND);
        for (var entry : entries) {
            var compound = (CompoundTag) entry;
            if (compound.hasUUID(ID_TAG)) {
                data.canceledCrafts.add(compound.getUUID(ID_TAG));
            }
        }

        var activeEntries = tag.getList(ACTIVE_BATCHES_TAG, Tag.TAG_COMPOUND);
        for (var entry : activeEntries) {
            var compound = (CompoundTag) entry;
            if (compound.hasUUID(BATCH_ID_TAG)
                    && compound.hasUUID(CRAFTING_ID_TAG)) {
                data.activeBatches.putIfAbsent(
                        compound.getUUID(BATCH_ID_TAG),
                        compound.getUUID(CRAFTING_ID_TAG));
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

        var activeEntries = new ListTag();
        for (var entry : activeBatches.entrySet()) {
            var activeEntry = new CompoundTag();
            activeEntry.putUUID(BATCH_ID_TAG, entry.getKey());
            activeEntry.putUUID(CRAFTING_ID_TAG, entry.getValue());
            activeEntries.add(activeEntry);
        }
        tag.put(ACTIVE_BATCHES_TAG, activeEntries);
        return tag;
    }

    boolean registerBatch(UUID batchId, UUID craftingId) {
        if (batchId == null || craftingId == null) {
            return false;
        }
        var existing = activeBatches.get(batchId);
        if (existing != null) {
            return existing.equals(craftingId);
        }
        activeBatches.put(batchId, craftingId);
        setDirty();
        return true;
    }

    void releaseBatch(UUID batchId, UUID craftingId) {
        if (batchId == null || craftingId == null
                || !craftingId.equals(activeBatches.get(batchId))) {
            return;
        }
        activeBatches.remove(batchId);
        if (!activeBatches.containsValue(craftingId)) {
            canceledCrafts.remove(craftingId);
        }
        setDirty();
    }

    boolean markCraftCanceled(UUID craftingId) {
        if (craftingId == null || !activeBatches.containsValue(craftingId)) {
            return false;
        }
        if (canceledCrafts.add(craftingId)) {
            setDirty();
        }
        return true;
    }

    boolean isCraftCanceled(UUID craftingId) {
        return craftingId != null && canceledCrafts.contains(craftingId);
    }

    boolean hasActiveBatch(UUID batchId, UUID craftingId) {
        return batchId != null && craftingId != null
                && craftingId.equals(activeBatches.get(batchId));
    }

    int activeBatchCount() {
        return activeBatches.size();
    }
}
