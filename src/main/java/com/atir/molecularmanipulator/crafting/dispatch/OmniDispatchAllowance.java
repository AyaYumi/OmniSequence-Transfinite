package com.atir.molecularmanipulator.crafting.dispatch;

import java.math.BigInteger;

/** A single CPU lane's dispatch reservation for the current server tick. */
public interface OmniDispatchAllowance extends AutoCloseable {
    /** The controller's tier snapshot, including when the reservation is empty. */
    boolean capacityMode();

    /** Remaining actual recipe copies. Long.MAX_VALUE denotes overclocked L9. */
    long remainingCopies();

    /** Charges one capacity-scheduler work unit. Native lanes use AE2's iteration limit. */
    boolean tryWork();

    /** Charges one server-wide single-push slot independently of capacity-scheduler work. */
    boolean trySingle();

    /** Charges actual accepted copies once per successful submission. */
    void recordAccepted(BigInteger copies);

    /** Releases any unused reservation to later claims in the same tick. */
    @Override
    void close();
}
