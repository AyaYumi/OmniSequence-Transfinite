package com.atir.molecularmanipulator.network;

import appeng.api.stacks.AEKey;
import java.util.List;
import java.util.Objects;

/**
 * Searchable data decoded from one encoded-pattern source slot.
 */
public record PatternSearchIndexEntry(int sourceSlot, List<AEKey> inputs, List<AEKey> outputs) {
    public PatternSearchIndexEntry {
        if (sourceSlot < 0) {
            throw new IllegalArgumentException("sourceSlot must be non-negative");
        }
        inputs = copyKeys(inputs, "inputs");
        outputs = copyKeys(outputs, "outputs");
    }

    public int keyCount() {
        return inputs.size() + outputs.size();
    }

    private static List<AEKey> copyKeys(List<AEKey> keys, String name) {
        Objects.requireNonNull(keys, name);
        for (var key : keys) {
            Objects.requireNonNull(key, name + " contains a null AEKey");
        }
        return List.copyOf(keys);
    }
}
