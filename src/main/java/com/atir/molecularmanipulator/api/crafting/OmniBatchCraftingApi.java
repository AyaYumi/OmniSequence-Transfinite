package com.atir.molecularmanipulator.api.crafting;

/** Stable entry point for the Omni batch-provider SPI. */
public final class OmniBatchCraftingApi {
    public static final int API_VERSION = 1;

    private OmniBatchCraftingApi() {
    }

    /** Returns the runtime ABI version without compile-time constant inlining. */
    public static int apiVersion() {
        return API_VERSION;
    }

    /**
     * Returns whether an AE2 crafting-CPU logic instance belongs to an active
     * Omni-Computation Core. The parameter is deliberately {@link Object} so
     * integrations do not need to expose AE2 implementation classes in their
     * public API.
     */
    public static boolean isOmniManagedCpu(Object craftingCpuLogic) {
        return craftingCpuLogic instanceof IOmniCraftingCpu omniCpu
                && omniCpu.isOmniMaterialAllocator();
    }
}
