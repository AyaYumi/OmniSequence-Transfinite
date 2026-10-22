package com.atir.molecularmanipulator.blockentity;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/**
 * Per-host, per-slot decoded patterns, including invalid (null) results.
 * Only definitions are cached: network contents, protections and output limits
 * must still be evaluated for every crafting attempt.
 */
final class MolecularAutoCraftPatternCache<D, P> {
    private final Map<Integer, Entry<D, P>> entries = new HashMap<>();
    private Object level;
    private Object recipeSource;

    @Nullable
    P get(int slot, D definition, Object level, Object recipeSource,
            Supplier<P> decoder) {
        // Identity, not equals: a reload can replace recipes without changing
        // their IDs, number or equality. Advancing game time is NOT a reload.
        if (this.level != level || this.recipeSource != recipeSource) {
            clear();
            this.level = level;
            this.recipeSource = recipeSource;
        }
        var cached = entries.get(slot);
        if (cached == null || !cached.definition().equals(definition)) {
            cached = new Entry<>(definition, decoder.get());
            entries.put(slot, cached);
        }
        return cached.details();
    }

    void invalidate(int slot) {
        entries.remove(slot);
    }

    void clear() {
        entries.clear();
        level = null;
        recipeSource = null;
    }

    private record Entry<D, P>(D definition, @Nullable P details) {
    }
}
