package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;

/** Resolves two-phase batch capabilities without replacing provider identity. */
public final class OmniBatchProviderAdapterRegistry {
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final ThreadLocal<IdentityHashMap<ICraftingProvider, Boolean>> ACTIVE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static volatile List<Entry> snapshot = List.of();
    private static volatile long revision;

    private OmniBatchProviderAdapterRegistry() {
    }

    /** Registers or replaces a stable ID. Higher priority resolves first. */
    public static synchronized void register(
            String id,
            int priority,
            BiPredicate<? super ICraftingProvider, ? super IPatternDetails> supports,
            BiFunction<? super ICraftingProvider, ? super IPatternDetails,
                    ? extends OmniBatchCraftingProvider> factory) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Adapter id must not be blank");
        }
        ENTRIES.put(id, new Entry(id, priority,
                Objects.requireNonNull(supports, "supports"),
                Objects.requireNonNull(factory, "factory")));
        rebuildSnapshot();
    }

    /** Provider-only convenience overload for optional integrations. */
    public static void register(String id, int priority,
            Predicate<? super ICraftingProvider> supports,
            Function<? super ICraftingProvider, ? extends OmniBatchCraftingProvider> factory) {
        Objects.requireNonNull(supports, "supports");
        Objects.requireNonNull(factory, "factory");
        register(id, priority, (provider, pattern) -> supports.test(provider),
                (provider, pattern) -> factory.apply(provider));
    }

    /** Changes on registration/removal so per-tick topology caches can invalidate. */
    public static long revision() {
        return revision;
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
    @Nullable
    public static OmniBatchCraftingProvider resolve(
            ICraftingProvider provider, IPatternDetails pattern) {
        if (provider == null || pattern == null) {
            return null;
        }
        var entries = snapshot;
        if (entries.isEmpty()) {
            return provider instanceof OmniBatchCraftingProvider direct ? direct : null;
        }
        var active = ACTIVE.get();
        if (active.put(provider, Boolean.TRUE) != null) {
            return null;
        }
        try {
            for (var entry : entries) {
                if (!entry.supports().test(provider, pattern)) {
                    continue;
                }
                var resolved = entry.factory().apply(provider, pattern);
                if (resolved != null) {
                    return resolved;
                }
            }
            if (provider instanceof OmniBatchCraftingProvider direct) {
                return direct;
            }
            return null;
        } finally {
            active.remove(provider);
            // Reuse the empty identity map on the server thread. No provider
            // reference survives resolution, and topology queries do not allocate.
        }
    }

    /**
     * Tests predicates without instantiating capabilities. Predicates must be cheap,
     * side-effect free and narrow; a matching predicate reserves the atomic path
     * even when its factory temporarily returns null.
     */
    public static boolean supports(
            ICraftingProvider provider, IPatternDetails pattern) {
        if (provider == null || pattern == null) {
            return false;
        }
        var entries = snapshot;
        if (entries.isEmpty()) {
            return provider instanceof OmniBatchCraftingProvider;
        }
        var active = ACTIVE.get();
        if (active.put(provider, Boolean.TRUE) != null) {
            return false;
        }
        try {
            for (var entry : entries) {
                if (entry.supports().test(provider, pattern)) {
                    return true;
                }
            }
            return provider instanceof OmniBatchCraftingProvider;
        } finally {
            active.remove(provider);
            // Reuse the empty identity map on the server thread. No provider
            // reference survives resolution, and topology queries do not allocate.
        }
    }

    private static void rebuildSnapshot() {
        var ordered = new ArrayList<>(ENTRIES.values());
        ordered.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(Entry::id));
        snapshot = List.copyOf(ordered);
        revision++;
    }

    private record Entry(
            String id,
            int priority,
            BiPredicate<? super ICraftingProvider, ? super IPatternDetails> supports,
            BiFunction<? super ICraftingProvider, ? super IPatternDetails,
                    ? extends OmniBatchCraftingProvider> factory) {
    }
}
