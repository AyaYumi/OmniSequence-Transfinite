package com.fish_dan_.data_energistics.api.crafting.dispatch;

/** Compile-only ABI declaration; excluded from runtime and release JARs. */
public interface CountedCraftingAdmission {
    long count();
    boolean hasTransferredInputOwnership();
    boolean commit(appeng.api.stacks.KeyCounter[] inputs);
}
