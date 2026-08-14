package com.atir.molecularmanipulator.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.atir.molecularmanipulator.runtime.NetworkStorageCollectionDepth;
import org.junit.jupiter.api.Test;

class NetworkStorageMixinDepthGuardTest {
    @Test
    void nestedCollectionStateIsRestoredAfterAnException() {
        assertFalse(NetworkStorageCollectionDepth.isCollecting());
        try {
            NetworkStorageCollectionDepth.enter(null);
            assertTrue(NetworkStorageCollectionDepth.isCollecting());
            throw new IllegalStateException("probe failed");
        } catch (IllegalStateException expected) {
            // The production call site performs this cleanup in its finally.
        } finally {
            NetworkStorageCollectionDepth.exit(null);
        }
        assertFalse(NetworkStorageCollectionDepth.isCollecting());
    }

    @Test
    void restoresAnExistingOuterDepth() {
        NetworkStorageCollectionDepth.enter(null);
        try {
            assertTrue(NetworkStorageCollectionDepth.isCollecting());
            NetworkStorageCollectionDepth.enter(1);
            try {
                assertTrue(NetworkStorageCollectionDepth.isCollecting());
            } finally {
                NetworkStorageCollectionDepth.exit(1);
            }
            assertTrue(NetworkStorageCollectionDepth.isCollecting());
        } finally {
            NetworkStorageCollectionDepth.exit(null);
        }
        assertFalse(NetworkStorageCollectionDepth.isCollecting());
    }
}
