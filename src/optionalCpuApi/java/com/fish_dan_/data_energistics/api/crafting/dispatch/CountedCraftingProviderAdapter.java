package com.fish_dan_.data_energistics.api.crafting.dispatch;

/** Compile-only ABI declaration; excluded from runtime and release JARs. */
public interface CountedCraftingProviderAdapter {
    CountedCraftingAdmission prepareBatch(appeng.api.crafting.IPatternDetails pattern, appeng.api.stacks.KeyCounter[] inputs, long requested);
    it.unimi.dsi.fastutil.objects.ObjectList<CountedCraftingCapacity> captureCapacityFast(appeng.api.crafting.IPatternDetails pattern,
            appeng.api.stacks.KeyCounter[] inputs, long requested);
}
