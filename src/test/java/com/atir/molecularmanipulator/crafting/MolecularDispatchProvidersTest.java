package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MolecularDispatchProvidersTest {
    private static final ClassLoader LOADER = MolecularDispatchProvidersTest.class.getClassLoader();

    @Test
    void topologyIsEnumeratedOncePerPatternButNotSharedAcrossPatterns() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var otherPattern = pattern();
        var first = provider();
        var second = provider();
        var calls = new AtomicInteger();

        var snapshot = dispatch.providers(pattern, () -> {
            calls.incrementAndGet();
            return List.of(first, second);
        });

        assertEquals(1, calls.get());
        assertEquals(2, snapshot.size());
        assertSame(snapshot, dispatch.providers(pattern, () -> {
            calls.incrementAndGet();
            return List.of(second);
        }));
        assertEquals(1, calls.get(), "The cached topology must not be re-enumerated");

        var otherSnapshot = dispatch.providers(otherPattern, () -> {
            calls.incrementAndGet();
            return List.of(second);
        });
        assertEquals(2, calls.get(), "A different pattern identity has its own snapshot");
        assertEquals(1, otherSnapshot.size());
        assertSame(second, otherSnapshot.get(0));
    }

    @Test
    void snapshotPreservesProviderOrderIdentityDuplicatesAndNulls() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var first = provider();
        var second = provider();
        var third = provider();
        var live = new ArrayList<ICraftingProvider>(Arrays.asList(first, null, second, first, third));

        var snapshot = dispatch.providers(pattern, () -> live);

        assertEquals(5, snapshot.size());
        assertSame(first, snapshot.get(0));
        assertNull(snapshot.get(1));
        assertSame(second, snapshot.get(2));
        assertSame(first, snapshot.get(3), "Duplicate providers keep their original identity and position");
        assertSame(third, snapshot.get(4));

        live.clear();
        assertEquals(5, snapshot.size(), "The snapshot is a copy, not the live source view");
        assertSame(third, snapshot.get(4));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.add(first),
                "The cached topology is read-only");
    }

    @Test
    void liveBusyStateIsNotFrozenIntoTheTopologySnapshot() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var state = new ProviderState();
        var provider = provider(state);
        var calls = new AtomicInteger();

        state.busy = true;
        var snapshot = dispatch.providers(pattern, () -> {
            calls.incrementAndGet();
            return List.of(provider);
        });
        state.busy = false;

        assertEquals(0, state.busyQueries, "Snapshotting topology must not query live busy state");
        assertSame(snapshot, dispatch.providers(pattern, () -> {
            calls.incrementAndGet();
            return List.of();
        }));
        assertEquals(1, calls.get());
        assertFalse(snapshot.get(0).isBusy(), "The cached list exposes the same live provider instance");
        state.busy = true;
        assertTrue(snapshot.get(0).isBusy());
        assertEquals(2, state.busyQueries);
    }

    @Test
    void clearRefetchesTopologyAndReclassifiesTheSameRevision() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var provider = provider();
        var sources = new AtomicInteger();
        var queries = new AtomicInteger();

        dispatch.providers(pattern, () -> {
            sources.incrementAndGet();
            return List.of(provider);
        });
        assertTrue(dispatch.supportsApi(provider, pattern, 7L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertSame(provider, dispatch.providers(pattern, () -> {
            sources.incrementAndGet();
            return List.of();
        }).get(0));
        assertTrue(dispatch.supportsApi(provider, pattern, 7L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(1, sources.get());
        assertEquals(1, queries.get());

        dispatch.clear();

        var afterClear = dispatch.providers(pattern, () -> {
            sources.incrementAndGet();
            return List.of();
        });
        assertTrue(afterClear.isEmpty());
        assertEquals(2, sources.get(), "clear must forget the provider topology");
        assertFalse(dispatch.supportsApi(provider, pattern, 7L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(2, queries.get(), "clear must forget the classification even for the same adapter revision");
    }

    @Test
    void classificationIsCachedByProviderAndPatternIdentityRatherThanEquals() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var equalPattern = pattern();
        var provider = provider();
        var equalProvider = provider();
        var queries = new AtomicInteger();

        assertTrue(dispatch.supportsApi(provider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertTrue(dispatch.supportsApi(provider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(1, queries.get());

        assertFalse(dispatch.supportsApi(equalProvider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(2, queries.get(), "An equals-equal provider is still a distinct identity");
        assertFalse(dispatch.supportsApi(equalProvider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertEquals(2, queries.get());

        assertTrue(dispatch.supportsApi(provider, equalPattern, 1L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertEquals(3, queries.get(), "An equals-equal pattern is still a distinct identity");
        assertTrue(dispatch.supportsApi(provider, equalPattern, 1L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(3, queries.get());

        assertFalse(dispatch.supportsApi(equalProvider, equalPattern, 1L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(4, queries.get());
    }

    @Test
    void revisionChangeInvalidatesOnlyTheApiClassificationCache() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var provider = provider();
        var sources = new AtomicInteger();
        var queries = new AtomicInteger();

        dispatch.providers(pattern, () -> {
            sources.incrementAndGet();
            return List.of(provider);
        });
        assertTrue(dispatch.supportsApi(provider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertTrue(dispatch.supportsApi(provider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(1, queries.get());

        assertFalse(dispatch.supportsApi(provider, pattern, 2L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(2, queries.get());
        assertFalse(dispatch.supportsApi(provider, pattern, 2L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertEquals(2, queries.get(), "The new revision remains cached");

        assertTrue(dispatch.supportsApi(provider, pattern, 1L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertEquals(3, queries.get(), "Returning to an old revision must still invalidate");

        assertEquals(1, sources.get(), "Revision invalidation keeps the topology snapshot");
        assertSame(provider, dispatch.providers(pattern, () -> {
            sources.incrementAndGet();
            return List.of();
        }).get(0));
        assertEquals(1, sources.get());
    }

    @Test
    void sourceFailurePublishesNeitherPartialNorNullTopology() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var first = provider();
        var second = provider();
        var calls = new AtomicInteger();

        Iterable<ICraftingProvider> partial = () -> new Iterator<>() {
            private int index;

            @Override
            public boolean hasNext() {
                return true;
            }

            @Override
            public ICraftingProvider next() {
                if (index++ == 0) return first;
                throw new IllegalStateException("source failed after a partial enumeration");
            }
        };
        assertThrows(IllegalStateException.class, () -> dispatch.providers(pattern, () -> {
            calls.incrementAndGet();
            return partial;
        }));

        var retry = dispatch.providers(pattern, () -> {
            calls.incrementAndGet();
            return List.of(second);
        });
        assertEquals(2, calls.get(), "A failed enumeration must not be cached");
        assertEquals(1, retry.size());
        assertSame(second, retry.get(0), "No partial provider may survive the failure");

        var nullPattern = pattern();
        assertThrows(NullPointerException.class, () -> dispatch.providers(nullPattern, () -> null));
        var nullRetry = dispatch.providers(nullPattern, () -> {
            calls.incrementAndGet();
            return List.of(first);
        });
        assertEquals(3, calls.get(), "A null source result must not be cached");
        assertEquals(1, nullRetry.size());
        assertSame(first, nullRetry.get(0));
    }

    @Test
    void capabilityFailureIsRetriedAndNeverCachedAsANegativeResult() {
        var dispatch = new MolecularDispatchProviders();
        var pattern = pattern();
        var otherPattern = pattern();
        var provider = provider();
        var otherProvider = provider();
        var queries = new AtomicInteger();

        assertTrue(dispatch.supportsApi(otherProvider, otherPattern, 4L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertEquals(1, queries.get());

        assertThrows(IllegalStateException.class, () -> dispatch.supportsApi(provider, pattern, 4L, () -> {
            queries.incrementAndGet();
            throw new IllegalStateException("capability probe failed");
        }));
        assertEquals(2, queries.get());

        assertFalse(dispatch.supportsApi(provider, pattern, 4L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(3, queries.get(), "A throwing probe must not leave a negative cache entry");
        assertFalse(dispatch.supportsApi(provider, pattern, 4L, () -> {
            queries.incrementAndGet();
            return true;
        }));
        assertEquals(3, queries.get(), "The successful retry is cached");

        assertTrue(dispatch.supportsApi(otherProvider, otherPattern, 4L, () -> {
            queries.incrementAndGet();
            return false;
        }));
        assertEquals(3, queries.get(), "An unrelated failure must not discard a valid classification");
    }

    private static ICraftingProvider provider() {
        return provider(new ProviderState());
    }

    private static ICraftingProvider provider(ProviderState state) {
        return (ICraftingProvider) Proxy.newProxyInstance(LOADER, new Class<?>[]{ICraftingProvider.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "isBusy":
                            state.busyQueries++;
                            return state.busy;
                        case "equals":
                            return args != null && args.length == 1 && args[0] instanceof ICraftingProvider;
                        case "hashCode":
                            return 1;
                        case "toString":
                            return "TestCraftingProvider";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static IPatternDetails pattern() {
        return (IPatternDetails) Proxy.newProxyInstance(LOADER, new Class<?>[]{IPatternDetails.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "equals":
                            return args != null && args.length == 1 && args[0] instanceof IPatternDetails;
                        case "hashCode":
                            return 1;
                        case "toString":
                            return "TestPatternDetails";
                        default:
                            return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return false;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == char.class) return '\0';
        return null;
    }

    private static final class ProviderState {
        boolean busy;
        int busyQueries;
    }
}
