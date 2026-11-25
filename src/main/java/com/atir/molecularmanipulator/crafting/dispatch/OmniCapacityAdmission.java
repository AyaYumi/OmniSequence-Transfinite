package com.atir.molecularmanipulator.crafting.dispatch;

import java.math.BigInteger;

/** Short-lived capacity result for one provider. Submit is called at most once under CPU reservation. */
public interface OmniCapacityAdmission extends AutoCloseable {
    OmniCapacityAdmission UNAVAILABLE = new OmniCapacityAdmission() {
        @Override public BigInteger maximumCrafts() { return BigInteger.ZERO; }
        @Override public BigInteger submit(BigInteger crafts) { throw new IllegalStateException("No capacity"); }
    };

    /** Zero denotes a target whose capacity is currently unavailable. */
    BigInteger maximumCrafts();

    /** Whether a multi-copy submission may accept less than its requested count. */
    default boolean acceptsPartial() { return true; }

    /**
     * Submit reserved CPU materials and return the accepted prefix. Zero guarantees the target
     * retained no materials. An exception means the transfer may have happened and cannot be replayed.
     */
    BigInteger submit(BigInteger crafts);

    @Override default void close() {}
}
