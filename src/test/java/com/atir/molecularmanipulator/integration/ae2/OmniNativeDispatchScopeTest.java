package com.atir.molecularmanipulator.integration.ae2;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class OmniNativeDispatchScopeTest {
    @Test void ordinaryCallsAndUnmatchedIdentitiesAreNotAuthorized() {
        Object provider = new String("provider"), pattern = new String("pattern");
        assertFalse(OmniNativeDispatchScope.active());
        try (var ordinary = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            assertFalse(OmniNativeDispatchScope.active());
        }
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider))) {
            assertFalse(OmniNativeDispatchScope.active());
            try (var wrongProvider = OmniNativeDispatchScope.enterProvider(new String("provider"), pattern)) {
                assertFalse(OmniNativeDispatchScope.active());
            }
            try (var wrongPattern = OmniNativeDispatchScope.enterProvider(provider, new String("pattern"))) {
                assertFalse(OmniNativeDispatchScope.active());
            }
            try (var matching = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
                assertTrue(OmniNativeDispatchScope.active());
            }
            assertFalse(OmniNativeDispatchScope.active());
        }
        assertFalse(OmniNativeDispatchScope.active());
    }

    @Test void reentrantOrdinaryCallIsSuppressedAndOuterDeliveryRestored() {
        var provider = new Object(); var pattern = new Object();
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider));
                var delivery = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            assertTrue(OmniNativeDispatchScope.active());
            try (var nested = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
                assertFalse(OmniNativeDispatchScope.active());
            }
            assertTrue(OmniNativeDispatchScope.active());
        }
        assertFalse(OmniNativeDispatchScope.active());
    }

    @Test void exceptionalNestedCpuUnwindsToItsOwnProviderWindow() {
        var provider = new Object(); var pattern = new Object();
        var innerProvider = new Object(); var innerPattern = new Object();
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider));
                var delivery = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            assertThrows(IllegalStateException.class, () -> {
                try (var nestedCpu = OmniNativeDispatchScope.open(innerPattern, List.of(innerProvider))) {
                    assertFalse(OmniNativeDispatchScope.active());
                    try (var inner = OmniNativeDispatchScope.enterProvider(innerProvider, innerPattern)) {
                        assertTrue(OmniNativeDispatchScope.active());
                        throw new IllegalStateException("provider fails after entering scope");
                    }
                }
            });
            assertTrue(OmniNativeDispatchScope.active());
        }
        assertFalse(OmniNativeDispatchScope.active());
    }

    @Test void authorizationNeverLeaksToAnotherThread() throws InterruptedException {
        var provider = new Object(); var pattern = new Object();
        var observed = new AtomicBoolean(true);
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider));
                var delivery = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            var thread = new Thread(() -> {
                try (var other = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
                    observed.set(OmniNativeDispatchScope.active());
                }
            });
            thread.start(); thread.join();
            assertFalse(observed.get());
            assertTrue(OmniNativeDispatchScope.active());
        }
    }
}
