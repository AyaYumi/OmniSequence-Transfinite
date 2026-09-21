package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniBigIntegerProviderAdapterRegistryTest {
    private static final String LOW = "test:big_integer_low";
    private static final String HIGH = "test:big_integer_high";

    @Test
    void resolvesCapabilityWithoutReplacingProviderIdentity() {
        var provider = proxy(ICraftingProvider.class);
        var pattern = proxy(IPatternDetails.class);
        var low = proxy(OmniBigIntegerCraftingProvider.class);
        var high = proxy(OmniBigIntegerCraftingProvider.class);
        try {
            OmniBigIntegerProviderAdapterRegistry.register(
                    LOW, 1, (candidate, candidatePattern) -> candidate == provider,
                    (candidate, candidatePattern) -> low);
            OmniBigIntegerProviderAdapterRegistry.register(
                    HIGH, 2,
                    (candidate, candidatePattern) -> candidate == provider
                            && candidatePattern == pattern,
                    (candidate, candidatePattern) -> high);

            assertSame(high, OmniBigIntegerProviderAdapterRegistry.resolve(
                    provider, pattern));
            assertTrue(OmniBigIntegerProviderAdapterRegistry.supports(
                    provider, pattern));
        } finally {
            unregister();
        }
    }

    @Test
    void registeredAdapterCanOverrideDirectCapability() {
        var direct = proxy(OmniBigIntegerCraftingProvider.class);
        var pattern = proxy(IPatternDetails.class);
        var adapterCalls = new AtomicInteger();
        try {
            OmniBigIntegerProviderAdapterRegistry.register(
                    HIGH, 100, (candidate, candidatePattern) -> true,
                    (candidate, candidatePattern) -> {
                        adapterCalls.incrementAndGet();
                        return proxy(OmniBigIntegerCraftingProvider.class);
                    });

            var resolved = OmniBigIntegerProviderAdapterRegistry.resolve(
                    direct, pattern);
            org.junit.jupiter.api.Assertions.assertNotSame(direct, resolved);
            org.junit.jupiter.api.Assertions.assertEquals(1, adapterCalls.get());
        } finally {
            unregister();
        }
    }

    @Test
    void unmatchedProviderStaysUnadapted() {
        var provider = proxy(ICraftingProvider.class);
        var pattern = proxy(IPatternDetails.class);
        unregister();
        assertNull(OmniBigIntegerProviderAdapterRegistry.resolve(
                provider, pattern));
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(
                OmniBigIntegerProviderAdapterRegistryTest.class.getClassLoader(),
                new Class<?>[] {type},
                (instance, method, arguments) -> {
                    if (method.getName().equals("toString")) {
                        return "Test" + type.getSimpleName();
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    if (method.getReturnType() == long.class) {
                        return 0L;
                    }
                    return null;
                });
    }

    private static void unregister() {
        OmniBigIntegerProviderAdapterRegistry.unregister(LOW);
        OmniBigIntegerProviderAdapterRegistry.unregister(HIGH);
    }
}
