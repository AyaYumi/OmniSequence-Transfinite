package com.atir.molecularmanipulator.api.crafting;

/**
 * Read-only marker mixed into AE2 crafting CPU logic while OmniSequence is
 * installed. Third-party CPU hooks can use it to avoid applying a second
 * material-batching implementation to an Omni-managed CPU.
 *
 * @since 1.3.7-forge-fix (API version 1)
 */
public interface IOmniCraftingCpu {
    boolean isOmniMaterialAllocator();
}
