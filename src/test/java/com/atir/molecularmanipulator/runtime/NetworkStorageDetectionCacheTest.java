package com.atir.molecularmanipulator.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class NetworkStorageDetectionCacheTest {
    @Test
    void reusesAStableProbeUntilItsRecheckInterval() {
        var cache = new NetworkStorageDetectionCache<String>(3);
        var calls = new AtomicInteger();

        for (int refresh = 0; refresh < 3; refresh++) {
            cache.beginRefresh();
            assertTrue(cache.resolve("item", 64, () -> {
                calls.incrementAndGet();
                return Optional.of(true);
            }));
            cache.endRefresh();
        }
        assertEquals(1, calls.get());

        cache.beginRefresh();
        assertFalse(cache.resolve("item", 64, () -> {
            calls.incrementAndGet();
            return Optional.of(false);
        }));
        cache.endRefresh();
        assertEquals(2, calls.get());
    }

    @Test
    void amountChangesAndUnknownResultsInvalidateEntries() {
        var cache = new NetworkStorageDetectionCache<String>(20);

        cache.beginRefresh();
        assertTrue(cache.resolve("item", 64, () -> Optional.of(true)));
        cache.endRefresh();

        cache.beginRefresh();
        assertFalse(cache.resolve("item", 65, Optional::empty));
        cache.endRefresh();

        cache.beginRefresh();
        assertFalse(cache.resolve("item", 65, () -> Optional.of(false)));
        cache.endRefresh();
    }

    @Test
    void enforcesRefreshBoundaries() {
        var cache = new NetworkStorageDetectionCache<String>();
        assertThrows(IllegalStateException.class,
                () -> cache.resolve("item", 1, () -> Optional.of(true)));
        cache.beginRefresh();
        assertThrows(IllegalStateException.class, cache::beginRefresh);
        cache.endRefresh();
        assertThrows(IllegalStateException.class, cache::endRefresh);
    }
}
