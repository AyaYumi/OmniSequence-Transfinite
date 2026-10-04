package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.integration.ae2.OmniSmartDoublingProvider;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import com.github.appliedenhancements.integration.ae2.AelisScaledPattern;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OmniSmartDoublingBypassTest {
    @Test void enabledNativePatternSkipsProviderLookupAndRewrite() {
        var pattern = pattern(true, false);
        var plan = plan(pattern);
        assertSame(plan, OmniSmartDoublingPlanner.rewriteForSubmission(plan,
                ignored -> { throw new AssertionError("External native doubling must bypass local lookup"); }));
    }

    @Test void existingAppliedBatchIsNeverScaledTwice() {
        var pattern = pattern(false, true);
        var plan = plan(pattern);
        assertSame(plan, OmniSmartDoublingPlanner.rewriteForSubmission(plan,
                ignored -> { throw new AssertionError("Existing batch must not be rewritten"); }));
    }

    @Test void nativeSmartProviderWinsOverLocalProviderInEitherOrder() {
        var pattern = pattern(false, false);
        var plan = plan(pattern);
        var local = provider(false);
        var external = provider(true);
        assertSame(plan, OmniSmartDoublingPlanner.rewriteForSubmission(plan, ignored -> List.of(local, external)));
        assertSame(plan, OmniSmartDoublingPlanner.rewriteForSubmission(plan, ignored -> List.of(external, local)));
    }

    @Test void disabledNativePatternStillUsesOrdinaryProviderPath() {
        var pattern = pattern(false, false);
        int[] lookups = {0};
        var plan = plan(pattern);
        assertSame(plan, OmniSmartDoublingPlanner.rewriteForSubmission(plan, ignored -> { lookups[0]++; return List.of(); }));
        assertEquals(1, lookups[0]);
    }

    @Test void cachedContractReadsChangedNativeEnabledState() {
        boolean[] enabled = {true};
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IPatternDetails.class, ISmartDoublingAwarePattern.class},
                (p, m, a) -> {
                    if (m.getName().equals("eap$allowScaling")) return enabled[0];
                    throw new AssertionError(m.getName());
                });
        assertTrue(OmniNativeSmartDoubling.isExternallyManaged(pattern));
        enabled[0] = false;
        assertFalse(OmniNativeSmartDoubling.isExternallyManaged(pattern));
        enabled[0] = true;
        assertTrue(OmniNativeSmartDoubling.isExternallyManaged(pattern));
    }

    @Test void failingNativeEnabledQueryRetainsExternalOwnership() {
        var pattern = (IPatternDetails) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{IPatternDetails.class, ISmartDoublingAwarePattern.class},
                (p, m, a) -> { throw new IllegalStateException("Optional API unavailable"); });
        assertTrue(OmniNativeSmartDoubling.isExternallyManaged(pattern));
    }

    private static IPatternDetails pattern(boolean enabled, boolean scaled) {
        Class<?>[] contracts = scaled ? new Class<?>[]{IPatternDetails.class, AelisScaledPattern.class}
                : new Class<?>[]{IPatternDetails.class, ISmartDoublingAwarePattern.class};
        return (IPatternDetails) Proxy.newProxyInstance(OmniSmartDoublingBypassTest.class.getClassLoader(),
                contracts, (p,m,a) -> switch (m.getName()) {
                    case "hashCode" -> System.identityHashCode(p);
                    case "equals" -> p == a[0];
                    case "eap$allowScaling" -> enabled;
                    default -> throw new AssertionError(m.getName());
                });
    }
    private static ICraftingProvider provider(boolean external) {
        Class<?>[] contracts = external ? new Class<?>[]{ICraftingProvider.class, com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider.class}
                : new Class<?>[]{ICraftingProvider.class, OmniSmartDoublingProvider.class};
        return (ICraftingProvider) Proxy.newProxyInstance(OmniSmartDoublingBypassTest.class.getClassLoader(), contracts,
                (p,m,a) -> { throw new AssertionError(m.getName()); });
    }
    private static ICraftingPlan plan(IPatternDetails pattern) {
        return new ICraftingPlan() {
            public GenericStack finalOutput() { return null; }
            public long bytes() { return 0; }
            public boolean simulation() { return false; }
            public boolean multiplePaths() { return false; }
            public KeyCounter usedItems() { return new KeyCounter(); }
            public KeyCounter emittedItems() { return new KeyCounter(); }
            public KeyCounter missingItems() { return new KeyCounter(); }
            public Map<IPatternDetails, Long> patternTimes() { return Map.of(pattern, 64L); }
        };
    }
}
