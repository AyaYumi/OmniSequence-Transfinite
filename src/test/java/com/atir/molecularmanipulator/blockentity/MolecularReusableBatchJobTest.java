package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class MolecularReusableBatchJobTest {
    @Test
    void legacyMissingBatchIdIsMigratedOnceAndPersistedInPlace() {
        var tag = new CompoundTag();

        assertFalse(tag.hasUUID("batch_id"));
        UUID migratedBatchId = MolecularReusableBatchJob
                .readOrCreateBatchId(tag);

        assertTrue(tag.hasUUID("batch_id"));
        assertEquals(migratedBatchId, tag.getUUID("batch_id"));
        assertEquals(migratedBatchId, MolecularReusableBatchJob
                .readOrCreateBatchId(tag));
    }

    @Test
    void existingBatchIdIsPreserved() {
        UUID existingBatchId = UUID.randomUUID();
        var tag = new CompoundTag();
        tag.putUUID("batch_id", existingBatchId);

        assertEquals(existingBatchId, MolecularReusableBatchJob
                .readOrCreateBatchId(tag));
        assertEquals(existingBatchId, tag.getUUID("batch_id"));
    }
}
