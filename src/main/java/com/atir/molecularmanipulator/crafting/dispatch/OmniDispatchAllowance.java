package com.atir.molecularmanipulator.crafting.dispatch;

import java.math.BigInteger;

/** A single CPU lane's dispatch reservation for the current server tick. */
public interface OmniDispatchAllowance extends AutoCloseable {
    /** The controller's tier snapshot, including when the reservation is empty. */
    boolean capacityMode();

    /**
     * A funded capacity lane returns {@link Long#MAX_VALUE} to allow exact batches of any size.
     * Native lanes return the remaining copy count. Empty and expired reservations return zero.
     */
    long remainingCopies();

    /** Charges one capacity-scheduler work unit. Native lanes use AE2's iteration limit. */
    boolean tryWork();

    /** Charges one server-wide single-push slot independently of capacity-scheduler work. */
    boolean trySingle();

    /** Records actual admissions for the native four-tick window, including across tier changes. */
    void recordAccepted(BigInteger copies);

    /** Releases any unused reservation to later claims in the same tick. */
    @Override
    void close();
}
