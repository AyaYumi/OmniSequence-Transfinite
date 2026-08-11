package com.atir.molecularmanipulator.integration.ae2;

/** Tracks arithmetic that a long-backed AE2 key counter can no longer represent. */
public interface KeyCounterOverflowBridge {
    boolean molecularmanipulator$hasUnrepresentableAmount();

    void molecularmanipulator$markUnrepresentableAmount();
}
