package com.atir.molecularmanipulator.api.crafting;

import appeng.api.networking.crafting.ICraftingProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Compatibility registry for providers whose output flush hook cannot be
 * implemented directly, for example optional third-party machines.
 */
public final class OmniPostAccountingOutputAdapterRegistry {
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();
    private static final ThreadLocal<IdentityHashMap<ICraftingProvider, Boolean>> ACTIVE =
            ThreadLocal.withInitial(IdentityHashMap::new);
    private static volatile List<Entry> snapshot = List.of();

    private OmniPostAccountingOutputAdapterRegistry() {
    }

    /**
     * Registers or replaces an adapter. Higher priorities are consulted first;
     * only the first matching adapter is invoked.
     */
    public static synchronized void register(String id, int priority,
            Predicate<? super ICraftingProvider> supports,
            Consumer<? super ICraftingProvider> flush) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Adapter id must not be blank");
        }
        var entry = new Entry(id, priority,
                Objects.requireNonNull(supports, "supports"),
                Objects.requireNonNull(flush, "flush"));
        ENTRIES.put(id, entry);
        var ordered = new ArrayList<>(ENTRIES.values());
        ordered.sort(Comparator.comparingInt(Entry::priority).reversed()
                .thenComparing(Entry::id));
        snapshot = List.copyOf(ordered);
    }

    public static synchronized void unregister(String id) {
        if (ENTRIES.remove(id) != null) {
            var ordered = new ArrayList<>(ENTRIES.values());
            ordered.sort(Comparator.comparingInt(Entry::priority).reversed()
                    .thenComparing(Entry::id));
            snapshot = List.copyOf(ordered);
        }
    }

    /**
     * Returns whether an adapter or native provider hook was invoked.
     * Calls for the same provider cannot recursively re-enter this method.
     */
    public static boolean flushAfterCpuAccounting(ICraftingProvider provider) {
        if (provider == null) {
            return false;
        }
        var active = ACTIVE.get();
        if (active.put(provider, Boolean.TRUE) != null) {
            return false;
        }
        try {
            for (var entry : snapshot) {
                if (entry.supports().test(provider)) {
                    entry.flush().accept(provider);
                    return true;
                }
            }
            if (provider instanceof OmniPostAccountingOutputProvider nativeProvider) {
                nativeProvider.flushOutputsAfterCpuAccounting();
                return true;
            }
            return false;
        } finally {
            active.remove(provider);
            if (active.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }

    private record Entry(String id, int priority,
            Predicate<? super ICraftingProvider> supports,
            Consumer<? super ICraftingProvider> flush) {
    }
}
