package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

class OmniNativeSmartDoublingTest {
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
}
