package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

/** Resolves exact-count capabilities without replacing provider identity. */
public final class OmniBigIntegerProviderAdapterRegistry {
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final ThreadLocal<IdentityHashMap<ICraftingProvider, Boolean>> ACTIVE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static volatile List<Entry> snapshot = List.of();

    private OmniBigIntegerProviderAdapterRegistry() {
    }

    public static synchronized void register(
            String id,
            int priority,
            BiPredicate<? super ICraftingProvider, ? super IPatternDetails> supports,
            BiFunction<? super ICraftingProvider, ? super IPatternDetails,
                    ? extends OmniBigIntegerCraftingProvider> factory) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Adapter id must not be blank");
        }
        ENTRIES.put(id, new Entry(id, priority,
                Objects.requireNonNull(supports, "supports"),
                Objects.requireNonNull(factory, "factory")));
        rebuildSnapshot();
    }

    public static synchronized void unregister(String id) {
        if (ENTRIES.remove(id) != null) {
            rebuildSnapshot();
        }
    }

    /**
     * The returned capability is separate from {@code provider}; callers must
     * retain the original provider for all identity-sensitive operations.
     */
    public static OmniBigIntegerCraftingProvider resolve(
            ICraftingProvider provider, IPatternDetails pattern) {
        if (provider == null || pattern == null) {
            return null;
        }
        var active = ACTIVE.get();
        if (active.put(provider, Boolean.TRUE) != null) {
            return null;
        }
        try {
            for (var entry : snapshot) {
                if (!entry.supports().test(provider, pattern)) {
                    continue;
                }
                var resolved = entry.factory().apply(provider, pattern);
                if (resolved != null) {
                    return resolved;
                }
            }
            if (provider instanceof OmniBigIntegerCraftingProvider direct) {
                return direct;
            }
            return null;
        } finally {
            active.remove(provider);
            if (active.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    /** Returns whether a direct capability or registered adapter can handle it. */
    public static boolean supports(
            ICraftingProvider provider, IPatternDetails pattern) {
        if (provider == null || pattern == null) {
            return false;
        }
        var active = ACTIVE.get();
        if (active.put(provider, Boolean.TRUE) != null) {
            return false;
        }
        try {
            for (var entry : snapshot) {
                if (entry.supports().test(provider, pattern)) {
                    return true;
                }
            }
            return provider instanceof OmniBigIntegerCraftingProvider;
        } finally {
            active.remove(provider);
            if (active.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    private static void rebuildSnapshot() {
        var ordered = new ArrayList<>(ENTRIES.values());
        ordered.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(Entry::id));
        snapshot = List.copyOf(ordered);
    }

    private record Entry(
            String id,
            int priority,
            BiPredicate<? super ICraftingProvider, ? super IPatternDetails> supports,
            BiFunction<? super ICraftingProvider, ? super IPatternDetails,
                    ? extends OmniBigIntegerCraftingProvider> factory) {
    }
}
