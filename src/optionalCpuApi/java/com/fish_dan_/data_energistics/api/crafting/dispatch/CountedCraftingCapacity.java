package com.fish_dan_.data_energistics.api.crafting.dispatch;

/** Compile-only ABI declaration; excluded from runtime and release JARs. */
public record CountedCraftingCapacity(CountedCraftingTarget target, CountedCraftingRoutingMode routingMode,
        java.util.OptionalLong logicalCrafts, java.util.OptionalLong maximumSingleBatch) {}
