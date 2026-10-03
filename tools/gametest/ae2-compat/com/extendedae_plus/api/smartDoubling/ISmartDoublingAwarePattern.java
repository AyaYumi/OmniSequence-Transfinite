package com.extendedae_plus.api.smartDoubling;

/** Test-only optional enabled-state ABI. */
public interface ISmartDoublingAwarePattern {
    boolean eap$allowScaling();
    void eap$setAllowScaling(boolean enabled);
    int eap$getMultiplierLimit();
    void eap$setMultiplierLimit(int limit);
}
