package com.atir.molecularmanipulator.api.crafting;

import java.math.BigInteger;
import java.util.List;

/** Receives terminal output accounting for a provider-owned exact batch. */
public interface OmniBigIntegerBatchCallbacks {
    /** Called immediately after the provider commits ownership of the batch. */
    default void onAdmitted(List<OmniBigIntegerOutput> plannedOutputs) {
    }

    /** Receives the actual output list, preserving one exact amount per AE key. */
    default void onOutputs(List<OmniBigIntegerOutput> outputs) {
        BigInteger total = BigInteger.ZERO;
        if (outputs != null) {
            for (var output : outputs) {
                if (output != null) {
                    total = total.add(output.amount());
                }
            }
        }
        onOutputs(total);
    }

    /** Compatibility hook for older adapters that only tracked a total. */
    default void onOutputs(BigInteger amount) {
    }

    default void onCancelled(BigInteger plannedAmount) {
    }
}
