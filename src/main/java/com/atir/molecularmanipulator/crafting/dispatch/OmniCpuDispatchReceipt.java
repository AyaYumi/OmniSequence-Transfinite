package com.atir.molecularmanipulator.crafting.dispatch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable ownership intent shared with the integration-independent CPU accounting hooks. */
public record OmniCpuDispatchReceipt(UUID jobId, IPatternDetails pattern, BigInteger crafts,
        Map<AEKey, BigInteger> inputs, Map<AEKey, BigInteger> outputs,
        Map<AEKey, BigInteger> remainders, boolean exact) {
    public OmniCpuDispatchReceipt {
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(pattern);
        if (crafts == null || crafts.signum() <= 0) throw new IllegalArgumentException("Invalid craft count");
        inputs = positive(inputs);
        outputs = positive(outputs);
        remainders = positive(remainders);
        if (inputs.isEmpty() || outputs.isEmpty()) throw new IllegalArgumentException("Missing batch materials");
    }
    public Map<AEKey, BigInteger> waiting() {
        var result = new LinkedHashMap<>(outputs);
        remainders.forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        return Map.copyOf(result);
    }
    private static Map<AEKey, BigInteger> positive(Map<AEKey, BigInteger> values) {
        values.forEach((key, amount) -> {
            Objects.requireNonNull(key);
            if (amount == null || amount.signum() <= 0) throw new IllegalArgumentException("Invalid batch amount");
        });
        return Map.copyOf(values);
    }
}
