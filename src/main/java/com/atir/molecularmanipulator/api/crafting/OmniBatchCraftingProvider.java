package com.atir.molecularmanipulator.api.crafting;

import appeng.api.networking.crafting.ICraftingProvider;
import org.jetbrains.annotations.Nullable;

/**
 * Opt-in endpoint for pattern-holding machines that want an Omni crafting CPU
 * to allocate and deliver several complete crafts as one atomic transaction.
 *
 * <p>Implementing {@link ICraftingProvider} alone remains sufficient for normal
 * one-craft AE2 dispatch. Implement this interface only when the provider can
 * take durable ownership of an entire multi-craft delivery without partial
 * insertion.</p>
 *
 * @since 1.3.9 (API version 1)
 */
public interface OmniBatchCraftingProvider extends ICraftingProvider {
    /**
     * Probes or reserves capacity for an Omni batch.
     *
     * <p>This method runs synchronously on the server thread. Returning
     * {@code null} declines batching without affecting normal AE2 dispatch.
     * Any reservation represented by a returned admission must be released by
     * {@link OmniBatchAdmission#close()}.</p>
     */
    @Nullable
    OmniBatchAdmission prepareOmniBatch(OmniBatchProbe probe);
}
