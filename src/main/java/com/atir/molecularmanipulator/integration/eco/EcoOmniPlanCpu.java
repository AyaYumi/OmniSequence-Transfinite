package com.atir.molecularmanipulator.integration.eco;

/** Implement on the existing Omni CraftingCpuLogic mixin when wiring the optional ECO attachment. */
public interface EcoOmniPlanCpu {
    EcoOmniPlanExecution ecoOmni$getExecution();
    void ecoOmni$setExecution(EcoOmniPlanExecution execution);

    /** True only after the CPU integration below is installed; used by the ECO admission mixin. */
    default boolean ecoOmni$handlesPlans() { return true; }
}
