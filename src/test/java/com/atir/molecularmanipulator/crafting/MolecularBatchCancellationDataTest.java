package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class MolecularBatchCancellationDataTest {
    @Test
    void ignoresCancellationWithoutAnActiveBatch() {
        var data = new MolecularBatchCancellationData();
        var craftingId = UUID.randomUUID();

        assertFalse(data.markCraftCanceled(craftingId));
        assertFalse(data.isCraftCanceled(craftingId));
    }

    @Test
    void retainsTombstoneUntilTheLastBatchIsReleased() {
        var data = new MolecularBatchCancellationData();
        var craftingId = UUID.randomUUID();
        var firstBatch = UUID.randomUUID();
        var secondBatch = UUID.randomUUID();

        assertTrue(data.registerBatch(firstBatch, craftingId));
        assertTrue(data.registerBatch(secondBatch, craftingId));
        assertTrue(data.markCraftCanceled(craftingId));

        data.releaseBatch(firstBatch, craftingId);
        assertTrue(data.isCraftCanceled(craftingId));
        assertEquals(1, data.activeBatchCount());

        data.releaseBatch(secondBatch, craftingId);
        assertFalse(data.isCraftCanceled(craftingId));
        assertEquals(0, data.activeBatchCount());
    }

    @Test
    void registerAndReleaseAreIdempotentAndIdentitySafe() {
        var data = new MolecularBatchCancellationData();
        var batchId = UUID.randomUUID();
        var craftingId = UUID.randomUUID();
        var otherCraftingId = UUID.randomUUID();

        assertTrue(data.registerBatch(batchId, craftingId));
        assertTrue(data.registerBatch(batchId, craftingId));
        assertFalse(data.registerBatch(batchId, otherCraftingId));
        assertEquals(1, data.activeBatchCount());

        data.releaseBatch(batchId, otherCraftingId);
        assertTrue(data.hasActiveBatch(batchId, craftingId));
        data.releaseBatch(batchId, craftingId);
        data.releaseBatch(batchId, craftingId);
        assertEquals(0, data.activeBatchCount());
    }

    @Test
    void persistsActiveRegistryAndCancellationState() {
        var data = new MolecularBatchCancellationData();
        var craftingId = UUID.randomUUID();
        var batchId = UUID.randomUUID();
        assertTrue(data.registerBatch(batchId, craftingId));
        assertTrue(data.markCraftCanceled(craftingId));

        var restored = MolecularBatchCancellationData.load(
                data.save(new CompoundTag()));

        assertTrue(restored.hasActiveBatch(batchId, craftingId));
        assertTrue(restored.isCraftCanceled(craftingId));
        restored.releaseBatch(batchId, craftingId);
        assertFalse(restored.isCraftCanceled(craftingId));
    }

    @Test
    void legacyTombstoneSurvivesUntilMigratedBatchReleases() {
        var craftingId = UUID.randomUUID();
        var legacyEntry = new CompoundTag();
        legacyEntry.putUUID("id", craftingId);
        var legacyEntries = new ListTag();
        legacyEntries.add(legacyEntry);
        var legacyTag = new CompoundTag();
        legacyTag.put("entries", legacyEntries);

        var restored = MolecularBatchCancellationData.load(legacyTag);
        assertTrue(restored.isCraftCanceled(craftingId));

        var migratedBatchId = UUID.randomUUID();
        assertTrue(restored.registerBatch(migratedBatchId, craftingId));
        restored.releaseBatch(migratedBatchId, craftingId);
        assertFalse(restored.isCraftCanceled(craftingId));
    }
}
