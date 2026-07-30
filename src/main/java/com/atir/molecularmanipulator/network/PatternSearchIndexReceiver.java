package com.atir.molecularmanipulator.network;

/**
 * Implemented by a menu that accepts client-bound pattern-search index chunks.
 */
public interface PatternSearchIndexReceiver {
    void acceptPatternSearchIndexChunk(PatternSearchIndexChunk chunk);
}
