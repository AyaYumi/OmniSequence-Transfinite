package com.atir.molecularmanipulator.integration.ae2;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.mixin.LightningWirelessFairPassAccessor;
import com.atir.molecularmanipulator.mixin.LightningWirelessOmniDispatchMixin;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class OmniWirelessFairShareTest {
    private static final class Hook extends LightningWirelessOmniDispatchMixin { }
    private static final class Lease implements LightningWirelessFairPassAccessor {
        long raised; int calls;
        @Override public long molecularmanipulator$raiseAllowance(Object target, long minimumAllowance) {
            calls++; raised = minimumAllowance;
            // Native raiseAllowance may retain a larger historical lease.
            return Long.MAX_VALUE;
        }
    }

    private long share(Lease lease, long remaining, int budget, int attempts, boolean fast) throws Exception {
        Method method = LightningWirelessOmniDispatchMixin.class.getDeclaredMethod(
                "molecularmanipulator$shareCurrentCpuWindow", Object.class, Object.class,
                Operation.class, long.class, int.class, int.class, boolean.class);
        method.setAccessible(true);
        Operation<Long> nativeAllowance = args -> 12345L;
        return (long) method.invoke(new Hook(), lease, new Object(), nativeAllowance, remaining, budget, attempts, fast);
    }

    @Test void ordinaryCpuUsesUnmodifiedNativeAllowance() throws Exception {
        var lease = new Lease();
        assertEquals(12345, share(lease, 170, 3, 0, true));
        assertEquals(0, lease.calls);
    }

    @Test void normalProbeModeUsesUnmodifiedNativeAllowance() throws Exception {
        var provider = new Object(); var pattern = new Object(); var lease = new Lease();
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider));
                var delivery = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            assertEquals(12345, share(lease, 170, 3, 0, false));
            assertEquals(0, lease.calls);
        }
    }

    @Test void equalCurrentSharesDoNotInheritHistoricalCeiling() throws Exception {
        var provider = new Object(); var pattern = new Object(); var lease = new Lease();
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider));
                var delivery = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            assertEquals(57, share(lease, 170, 3, 0, true));
            assertEquals(57, lease.raised);
            assertEquals(57, share(lease, 113, 3, 1, true));
            assertEquals(56, share(lease, 56, 3, 2, true));
            assertEquals(56, lease.raised);
        }
    }

    @Test void ceilingDivisionDoesNotOverflowLargeNativeWindows() throws Exception {
        var provider = new Object(); var pattern = new Object(); var lease = new Lease();
        try (var cpu = OmniNativeDispatchScope.open(pattern, List.of(provider));
                var delivery = OmniNativeDispatchScope.enterProvider(provider, pattern)) {
            assertEquals(Long.MAX_VALUE, share(lease, Long.MAX_VALUE, 1, 0, true));
            assertEquals(Long.MAX_VALUE / 2 + 1, share(lease, Long.MAX_VALUE, 2, 0, true));
            assertEquals(0, share(lease, 0, 1, 0, true));
        }
    }
}
