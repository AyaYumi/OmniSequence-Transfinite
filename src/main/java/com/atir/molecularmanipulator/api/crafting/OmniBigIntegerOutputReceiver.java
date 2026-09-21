package com.atir.molecularmanipulator.api.crafting;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.Map;
import java.util.function.Consumer;

/** Optional live batch receipt. Debit is called exactly once, only for the accepted amount. */
public interface OmniBigIntegerOutputReceiver {
    BigInteger transferOutput(IGrid sourceGrid, AEKey key, BigInteger offered, Consumer<BigInteger> debitSource);

    /** Batch variant used by the direct return path to avoid per-key callback overhead. */
    default Map<AEKey, BigInteger> transferOutputs(IGrid sourceGrid,
            Map<AEKey, BigInteger> offered,
            Consumer<Map<AEKey, BigInteger>> debitSource) {
        var accepted = new java.util.LinkedHashMap<AEKey, BigInteger>();
        if (offered == null) return Map.of();
        for (var entry : offered.entrySet()) {
            var value = transferOutput(sourceGrid, entry.getKey(), entry.getValue(), amount -> {
                if (amount != null && amount.signum() > 0) accepted.put(entry.getKey(), amount);
            });
            if (value != null && value.signum() > 0) accepted.put(entry.getKey(), value);
        }
        if (!accepted.isEmpty() && debitSource != null) debitSource.accept(Map.copyOf(accepted));
        return Map.copyOf(accepted);
    }
}
