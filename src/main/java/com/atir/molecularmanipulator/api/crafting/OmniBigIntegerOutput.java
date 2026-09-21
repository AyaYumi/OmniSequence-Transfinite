package com.atir.molecularmanipulator.api.crafting;

import appeng.api.stacks.AEKey;

import java.math.BigInteger;
import java.util.Objects;

/** Exact output of one native BigInteger provider batch. */
public record OmniBigIntegerOutput(AEKey key, BigInteger amount) {
    public OmniBigIntegerOutput {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
    }
}
