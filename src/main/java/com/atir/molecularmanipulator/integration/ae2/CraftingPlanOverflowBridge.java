package com.atir.molecularmanipulator.integration.ae2;

/** Marks a completed AE2 plan that must not be submitted to a crafting CPU. */
public interface CraftingPlanOverflowBridge {
    boolean molecularmanipulator$hasUnrepresentableTotal();

    void molecularmanipulator$markUnrepresentableTotal();
}
