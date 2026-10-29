package com.atir.molecularmanipulator.integration.ae2;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Authorizes only a specific native provider call inside Omni's admitted CPU window. */
public final class OmniNativeDispatchScope implements AutoCloseable {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    private final Context previous;

    private record Context(Object pattern, Set<Object> providers, boolean delivering) { }

    private OmniNativeDispatchScope(Context context) {
        previous = CURRENT.get();
        if (context == null) CURRENT.remove();
        else CURRENT.set(context);
    }

    public static OmniNativeDispatchScope open(Object pattern, Collection<?> providers) {
        Set<Object> identities = Collections.newSetFromMap(new IdentityHashMap<>());
        identities.addAll(providers);
        return new OmniNativeDispatchScope(new Context(pattern, identities, false));
    }

    public static OmniNativeDispatchScope enterProvider(Object provider, Object pattern) {
        Context request = CURRENT.get();
        // Suppress unmatched and reentrant provider calls, even on this thread.
        boolean allowed = request != null && !request.delivering
                && request.pattern == pattern && request.providers.contains(provider);
        return new OmniNativeDispatchScope(allowed
                ? new Context(pattern, request.providers, true) : null);
    }

    public static boolean active() {
        Context context = CURRENT.get();
        return context != null && context.delivering;
    }

    @Override public void close() {
        if (previous == null) CURRENT.remove();
        else CURRENT.set(previous);
    }
}
