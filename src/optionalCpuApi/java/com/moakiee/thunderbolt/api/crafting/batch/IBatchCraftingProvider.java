package com.moakiee.thunderbolt.api.crafting.batch;

/** Compile-only ABI declaration; excluded from runtime and release JARs. */
public interface IBatchCraftingProvider extends appeng.api.networking.crafting.ICraftingProvider {
    long getBatchCapacity(appeng.api.crafting.IPatternDetails pattern);
    long pushBatch(appeng.api.crafting.IPatternDetails pattern, appeng.api.stacks.KeyCounter[] inputs, long requested);
}
