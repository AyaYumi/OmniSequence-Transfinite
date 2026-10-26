package com.atir.molecularmanipulator.integration.ae2;

import java.math.BigInteger;

/** Persisted job allocation plus actual physical stock, including exact overflow and final output tails. */
public interface OmniCpuStorage {
    BigInteger omni$occupiedStorageBytes();
}
