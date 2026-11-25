package com.atir.molecularmanipulator.api.crafting;

/**
 * A short-lived capacity admission returned by an
 * {@link OmniBatchCraftingProvider}.
 *
 * <p>The admission is used at most once and is always closed by Omni. It must
 * not be retained after {@link #commit(OmniBatchDelivery)} returns.</p>
 *
 * @since 1.3.9 (API version 1)
 */
public interface OmniBatchAdmission extends AutoCloseable {
    /**
     * Maximum number of complete crafts this admission can accept. Zero
     * reports that the target currently has no room. One declines batching
     * and allows a bounded single push. A batch may contain anywhere from two
     * through the admitted maximum.
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
