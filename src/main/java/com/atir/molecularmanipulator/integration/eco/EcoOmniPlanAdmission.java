package com.atir.molecularmanipulator.integration.eco;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu;
import java.util.function.Supplier;

/** Restricts ECO's plan-only check to a verified Omni CPU during submission. */
public final class EcoOmniPlanAdmission {
    private static final ThreadLocal<EcoOmniPlanBridge.Prepared> ACTIVE = new ThreadLocal<>();
    private static final ThreadLocal<ICraftingPlan> FOREIGN = new ThreadLocal<>();
    private EcoOmniPlanAdmission() {}

    public static boolean supports(ICraftingCPU cpu, ICraftingPlan plan) {
        if (!(cpu instanceof CraftingCPUCluster cluster)
                || !((Object) cluster.craftingLogic instanceof EcoOmniPlanCpu wired) || !wired.ecoOmni$handlesPlans()
                || !((Object) cluster.craftingLogic instanceof IOmniCraftingCpu omni) || !omni.isOmniMaterialAllocator()) return false;
        if (cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest.forSubmission(plan) != null
                && (Object) cluster.craftingLogic instanceof EcoOmniParentOrderCpu) return true;
        // Foreign planners remain under the normal Omni/AE2 validation, even on an ECO signature collision.
        if (!EcoOmniPlanBridge.owns(plan)) return true;
        try {
            var prepared = EcoOmniPlanBridge.prepare(plan);
            return prepared != null;
        } catch (RuntimeException | LinkageError invalid) {
            return false;
        }
    }

    public static boolean supportsActive(ICraftingPlan plan) {
        var active = ACTIVE.get();
        var foreign = FOREIGN.get();
        return EcoOmniParentOrder.submittingCarrier(plan) || active != null && active.original() == plan
                || foreign != null && foreign == plan;
    }

    /** A signature collision cannot let ECO reject another planner on a verified Omni CPU. */
    public static <T> T submittingForeign(ICraftingPlan plan, Supplier<T> action) {
        var previous = FOREIGN.get();
        FOREIGN.set(plan);
        try { return action.get(); }
        finally { if (previous == null) FOREIGN.remove(); else FOREIGN.set(previous); }
    }

    /** Wrap the whole original trySubmitJob call after prepare and before any initial extraction. */
    public static <T> T submitting(EcoOmniPlanBridge.Prepared prepared, Supplier<T> action) {
        var previous = ACTIVE.get();
        ACTIVE.set(prepared);
        try { return action.get(); }
        finally { if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous); }
    }
}
