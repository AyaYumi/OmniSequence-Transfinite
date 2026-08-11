package com.atir.molecularmanipulator.api.crafting;

import java.util.Objects;

/**
 * Single-use ownership hand-off for one admitted batch.
 *
 * <p>{@link #accept} is the commit point. Before accepting, the provider must
 * have atomically placed every input in its durable target or persisted its own
 * queue. Rejection guarantees that the provider retained no materials and made
 * no irreversible change. Partial acceptance is never permitted.</p>
 *
 * @since 1.3.7-forge-fix (API version 1)
 */
public interface OmniBatchDelivery {
    OmniBatchRequest request();

    /** Commits ownership of the complete request to the provider. */
    void accept(Receipt receipt);

    /** Rejects the complete request without retaining any input. */
    void reject(Rejection rejection);

    enum Ownership {
        TRANSFERRED_TO_DURABLE_TARGET,
        PERSISTED_PROVIDER_QUEUE
    }

    enum Backpressure {
        MAY_ACCEPT_MORE,
        RECHECK_NEXT_TICK,
        SATURATED
    }

    enum RejectReason {
        CAPACITY_CHANGED,
        PATTERN_UNAVAILABLE,
        UNSUPPORTED_INPUT,
        INTERNAL_ERROR,
        OTHER
    }

    record Receipt(Ownership ownership, Backpressure backpressure) {
        public Receipt {
            Objects.requireNonNull(ownership, "ownership");
            Objects.requireNonNull(backpressure, "backpressure");
        }
    }

    record Rejection(RejectReason reason) {
        public Rejection {
            Objects.requireNonNull(reason, "reason");
        }

        public static Rejection reject(RejectReason reason) {
            return new Rejection(reason);
        }
    }
}
