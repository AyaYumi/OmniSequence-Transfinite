package com.atir.molecularmanipulator.integration.thunderbolt;

import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import java.math.BigInteger;

/** Converts the native remainder into CPU-owned accepted copies for exactly one submission. */
abstract class ThunderboltBatchAdmission implements OmniCapacityAdmission {
    private final BigInteger maximum;
    private boolean closed;

    ThunderboltBatchAdmission(long maximum) {
        this.maximum = BigInteger.valueOf(maximum);
    }

    @Override public final BigInteger maximumCrafts() {
        return maximum;
    }

    @Override public final BigInteger submit(BigInteger crafts) {
        if (closed) throw new IllegalStateException("Thunderbolt admission is closed");
        closed = true;
        if (crafts.signum() <= 0 || crafts.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Submission exceeds Thunderbolt admission");
        }
        long requested = crafts.longValueExact();
        long remainder = push(requested);
        if (remainder < 0 || remainder > requested) {
            throw new IllegalStateException("Thunderbolt returned invalid remainder " + remainder + " for " + requested);
        }
        return BigInteger.valueOf(requested - remainder);
    }

    abstract long push(long requested);

    @Override public final void close() {
        closed = true;
    }
}
