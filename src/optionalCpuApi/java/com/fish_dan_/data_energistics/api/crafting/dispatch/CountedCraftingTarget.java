package com.fish_dan_.data_energistics.api.crafting.dispatch;

/** Compile-only ABI declaration; excluded from runtime and release JARs. */
public record CountedCraftingTarget(boolean providerScoped, String stableIdentity, java.util.Optional<String> machineIdentity) {
    public static CountedCraftingTarget provider() { throw new UnsupportedOperationException(); }
}
