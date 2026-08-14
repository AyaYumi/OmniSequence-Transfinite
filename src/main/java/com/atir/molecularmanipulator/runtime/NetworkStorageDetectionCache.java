package com.atir.molecularmanipulator.runtime;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.jetbrains.annotations.ApiStatus;

/** Refresh-scoped cache for one mounted storage's infinite-key probes. */
@ApiStatus.Internal
public final class NetworkStorageDetectionCache<K> {
    static final int DEFAULT_RECHECK_INTERVAL = 20;

    private final int recheckInterval;
    private final Map<K, Entry> entries = new HashMap<>();
    private final Set<K> seenKeys = new HashSet<>();
    private long refreshNumber;
    private boolean refreshActive;

    public NetworkStorageDetectionCache() {
        this(DEFAULT_RECHECK_INTERVAL);
    }

    public NetworkStorageDetectionCache(int recheckInterval) {
        if (recheckInterval <= 0) {
            throw new IllegalArgumentException("Recheck interval must be positive");
        }
        this.recheckInterval = recheckInterval;
    }

    public void beginRefresh() {
        if (refreshActive) {
            throw new IllegalStateException("A refresh is already active");
        }
        if (refreshNumber == Long.MAX_VALUE) {
            entries.clear();
            refreshNumber = 0;
        }
        refreshNumber++;
        seenKeys.clear();
        refreshActive = true;
    }

    public boolean resolve(
            K key, long advertisedAmount, Supplier<Optional<Boolean>> probe) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(probe, "probe");
        if (!refreshActive) {
            throw new IllegalStateException("No refresh is active");
        }
        if (advertisedAmount <= 0) {
            throw new IllegalArgumentException("Advertised amount must be positive");
        }

        seenKeys.add(key);
        Entry previous = entries.get(key);
        if (previous != null
                && refreshNumber - previous.checkedRefresh < recheckInterval
                && (!previous.infinite
                        || previous.advertisedAmount == advertisedAmount)) {
            return previous.infinite;
        }

        Optional<Boolean> result;
        try {
            result = Objects.requireNonNull(probe.get(), "probe result");
        } catch (RuntimeException ignored) {
            result = Optional.empty();
        }

        if (result.isEmpty()) {
            // A provider that cannot answer the unbounded-extraction probe is
            // treated as finite for one recheck interval. Retrying every
            // network refresh made incompatible third-party cells a hot loop.
            entries.put(key, new Entry(advertisedAmount, false, refreshNumber));
            return false;
        }

        boolean infinite = result.get();
        entries.put(key, new Entry(advertisedAmount, infinite, refreshNumber));
        return infinite;
    }

    public void endRefresh() {
        if (!refreshActive) {
            throw new IllegalStateException("No refresh is active");
        }
        entries.keySet().retainAll(seenKeys);
        seenKeys.clear();
        refreshActive = false;
    }

    private record Entry(long advertisedAmount, boolean infinite, long checkedRefresh) {
    }
}
