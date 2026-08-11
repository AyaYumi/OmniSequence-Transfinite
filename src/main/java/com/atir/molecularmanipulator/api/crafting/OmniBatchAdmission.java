package com.atir.molecularmanipulator.api.crafting;

/**
 * A short-lived capacity admission returned by an
 * {@link OmniBatchCraftingProvider}.
 *
 * <p>The admission is used at most once and is always closed by Omni. It must
 * not be retained after {@link #commit(OmniBatchDelivery)} returns.</p>
 *
 * @since 1.3.7-forge-fix (API version 1)
 */
public interface OmniBatchAdmission extends AutoCloseable {
    /**
     * Maximum number of complete crafts this admission can accept. Values
     * below two decline batching. Inventory or AE power may reduce the final
     * delivery to any value from two through this maximum.
     */
    long maxCrafts();

    /**
     * Attempts to take ownership of the immutable delivery request.
     * Implementations must synchronously call exactly one of
     * {@link OmniBatchDelivery#accept} or {@link OmniBatchDelivery#reject}
     * before returning.
     */
    void commit(OmniBatchDelivery delivery);

    /** Releases any temporary reservation. This must not discard accepted input. */
    @Override
    default void close() {
    }
}
