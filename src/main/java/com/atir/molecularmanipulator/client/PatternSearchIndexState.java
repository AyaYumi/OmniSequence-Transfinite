package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.network.PatternSearchIndexChunk;
import com.atir.molecularmanipulator.network.PatternSearchIndexEntry;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client-side accumulator for ordered pattern-search index chunks.
 */
@OnlyIn(Dist.CLIENT)
public final class PatternSearchIndexState {
    private final List<PatternSearchIndexEntry> entries = new ArrayList<>();
    private int containerId = -1;
    private long generation = -1;
    private int revision = -1;
    private boolean complete;

    /**
     * Applies a chunk if it starts a new snapshot or follows the active snapshot.
     *
     * @return whether the visible state changed
     */
    public boolean accept(PatternSearchIndexChunk chunk) {
        if (chunk.reset()) {
            if (chunk.containerId() == containerId
                    && (chunk.generation() < generation
                            || chunk.generation() == generation && chunk.revision() <= revision)) {
                return false;
            }
            entries.clear();
            containerId = chunk.containerId();
            generation = chunk.generation();
            revision = -1;
            complete = false;
        } else if (chunk.containerId() != containerId || chunk.generation() != generation) {
            return false;
        }

        if (chunk.revision() <= revision) {
            return false;
        }

        entries.addAll(chunk.entries());
        revision = chunk.revision();
        complete = chunk.complete();
        return true;
    }

    public void clear() {
        entries.clear();
        containerId = -1;
        generation = -1;
        revision = -1;
        complete = false;
    }

    public List<PatternSearchIndexEntry> entries() {
        return List.copyOf(entries);
    }

    public int containerId() {
        return containerId;
    }

    public long generation() {
        return generation;
    }

    public int revision() {
        return revision;
    }

    public boolean complete() {
        return complete;
    }
}
