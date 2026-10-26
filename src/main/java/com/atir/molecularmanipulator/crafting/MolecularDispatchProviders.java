package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Provider identity/order and pure API classification for one executeCrafting call.
 * Busy flags, capacities, reservations and direct-machine capabilities stay live.
 * A fresh invocation must clear the snapshot, including same-tick retries after
 * a rejected delivery: topology changes must not leave a retained admission.
 */
public final class MolecularDispatchProviders {
    private final Map<IPatternDetails, List<ICraftingProvider>> providers = new IdentityHashMap<>();
    private final Map<IPatternDetails, IdentityHashMap<ICraftingProvider, Boolean>> api = new IdentityHashMap<>();
    private long adapterRevision = Long.MIN_VALUE;

    public List<ICraftingProvider> providers(IPatternDetails pattern,
            Supplier<? extends Iterable<ICraftingProvider>> source) {
        var cached = providers.get(pattern);
        if (cached != null) return cached;
        var snapshot = new ArrayList<ICraftingProvider>();
        for (var provider : Objects.requireNonNull(source.get(), "Provider topology is unavailable")) {
            snapshot.add(provider);
        }
        // Publish only after successful enumeration. Preserve order, identity,
        // duplicates and null entries rather than changing AE2's topology.
        var result = Collections.unmodifiableList(snapshot);
        providers.put(pattern, result);
        return result;
    }

    public boolean supportsApi(ICraftingProvider provider, IPatternDetails pattern,
            long revision, BooleanSupplier query) {
        if (adapterRevision != revision) {
            adapterRevision = revision;
            api.clear();
        }
        var classifications = api.computeIfAbsent(pattern, ignored -> new IdentityHashMap<>());
        var cached = classifications.get(provider);
        if (cached != null) return cached;
        boolean supported = query.getAsBoolean();
        // A throwing predicate is not a negative capability result. Let the
        // caller retain its conservative failure handling and retry later.
        classifications.put(provider, supported);
        return supported;
    }

    public void clear() {
        providers.clear();
        api.clear();
        adapterRevision = Long.MIN_VALUE;
    }
}
