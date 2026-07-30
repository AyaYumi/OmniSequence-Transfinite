package com.atir.molecularmanipulator.network;

import java.util.List;
import java.util.Objects;

/**
 * One ordered chunk of a menu's pattern-search index.
 *
 * <p>A new snapshot starts with {@code reset}. All chunks in that snapshot use the same
 * {@code generation}, while {@code revision} increases for every chunk. {@code complete} marks the last chunk.
 */
public record PatternSearchIndexChunk(
        int containerId,
        long generation,
        int revision,
        boolean reset,
        boolean complete,
        List<PatternSearchIndexEntry> entries) {
    public PatternSearchIndexChunk {
        if (containerId < 0) {
            throw new IllegalArgumentException("containerId must be non-negative");
        }
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be non-negative");
        }
        if (revision < 0) {
            throw new IllegalArgumentException("revision must be non-negative");
        }
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }
}
