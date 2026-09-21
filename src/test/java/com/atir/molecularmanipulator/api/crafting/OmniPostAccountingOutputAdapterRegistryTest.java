package com.atir.molecularmanipulator.api.crafting;

import appeng.api.networking.crafting.ICraftingProvider;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OmniPostAccountingOutputAdapterRegistryTest {
    private static final String LOW = "test:post_accounting_low";
    private static final String HIGH = "test:post_accounting_high";

    @Test
    void highestPriorityAdapterWinsAndNativeHookIsNotDuplicated() {
        var nativeCalls = new AtomicInteger();
        var lowCalls = new AtomicInteger();
        var highCalls = new AtomicInteger();
        var provider = provider(nativeCalls);
        try {
            OmniPostAccountingOutputAdapterRegistry.register(
                    LOW, 1, candidate -> candidate == provider,
                    candidate -> lowCalls.incrementAndGet());
            OmniPostAccountingOutputAdapterRegistry.register(
                    HIGH, 2, candidate -> candidate == provider,
                    candidate -> highCalls.incrementAndGet());

            assertTrue(OmniPostAccountingOutputAdapterRegistry
                    .flushAfterCpuAccounting(provider));
            assertEquals(0, lowCalls.get());
            assertEquals(1, highCalls.get());
            assertEquals(0, nativeCalls.get());
        } finally {
            unregisterTestAdapters();
        }
    }

    @Test
    void recursiveFlushForSameProviderIsSuppressed() {
        var provider = provider(new AtomicInteger());
        var nestedResult = new boolean[] {true};
        try {
            OmniPostAccountingOutputAdapterRegistry.register(
                    HIGH, 2, candidate -> candidate == provider, candidate ->
                            nestedResult[0] = OmniPostAccountingOutputAdapterRegistry
                                    .flushAfterCpuAccounting(candidate));

            assertTrue(OmniPostAccountingOutputAdapterRegistry
                    .flushAfterCpuAccounting(provider));
            assertFalse(nestedResult[0]);
        } finally {
            unregisterTestAdapters();
        }
    }

    @Test
    void nativeProtocolIsUsedWithoutAnAdapter() {
        var nativeCalls = new AtomicInteger();
        var provider = provider(nativeCalls);
        unregisterTestAdapters();

        assertTrue(OmniPostAccountingOutputAdapterRegistry
                .flushAfterCpuAccounting(provider));
        assertEquals(1, nativeCalls.get());
    }

    private static ICraftingProvider provider(AtomicInteger nativeCalls) {
        return (ICraftingProvider) Proxy.newProxyInstance(
                OmniPostAccountingOutputAdapterRegistryTest.class.getClassLoader(),
                new Class<?>[] {
                        ICraftingProvider.class,
                        OmniPostAccountingOutputProvider.class
                },
                (proxy, method, arguments) -> {
                    if (method.getName().equals("flushOutputsAfterCpuAccounting")) {
                        nativeCalls.incrementAndGet();
                        return null;
                    }
                    if (method.getName().equals("toString")) {
                        return "TestPostAccountingProvider";
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                });
    }

    private static void unregisterTestAdapters() {
        OmniPostAccountingOutputAdapterRegistry.unregister(LOW);
        OmniPostAccountingOutputAdapterRegistry.unregister(HIGH);
    }
}
