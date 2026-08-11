package com.atir.molecularmanipulator.integration.ae2;

/** Carries an unrepresentable-total marker through AE2 child simulation states. */
public interface CraftingSimulationOverflowBridge {
    boolean molecularmanipulator$hasUnrepresentableTotal();

    void molecularmanipulator$markUnrepresentableTotal();
}
