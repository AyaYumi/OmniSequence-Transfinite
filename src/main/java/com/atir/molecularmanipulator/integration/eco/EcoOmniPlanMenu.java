package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderAdmission;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.planner.ECOPlannerInventory;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionRequirement;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import com.atir.molecularmanipulator.api.crafting.IOmniCraftingCpu;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Predicate;

/** ECO 21.2.9 menu adapter. Referenced only by the version-gated menu mixin. */
public final class EcoOmniPlanMenu {
    private EcoOmniPlanMenu() {}

    public static boolean isOmni(ICraftingCPU cpu) {
        return cpu instanceof CraftingCPUCluster cluster
                && (Object) cluster.craftingLogic instanceof IOmniCraftingCpu omni && omni.isOmniMaterialAllocator()
                && (Object) cluster.craftingLogic instanceof EcoOmniPlanCpu wired && wired.ecoOmni$handlesPlans();
    }

    /** Explicit selection only: never substitute another CPU or impersonate an ECO CPU. */
    public static boolean available(IGrid grid, ICraftingCPU selected) {
        return grid != null && isOmni(selected) && selected instanceof CraftingCPUCluster cluster
                && !cluster.isDestroyed() && cluster.isActive() && !cluster.isBusy() && cluster.getGrid() == grid
                && grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu == selected);
    }

    /** ECO CPU availability through public CPU APIs, including its automatic-selection policy. */
    public static boolean ecoAvailable(IGrid grid, ICraftingCPU selected) {
        if (grid == null) return false;
        var cpus = grid.getCraftingService().getCpus();
        if (selected != null) {
            return selected instanceof ECOCraftingCPU eco && !eco.isBusy() && eco.isActive()
                    && cpus.contains(eco);
        }
        return cpus.stream().anyMatch(cpu -> cpu instanceof ECOCraftingCPU eco && !eco.isBusy() && eco.isActive()
                && eco.getSelectionMode() != CpuSelectionMode.MACHINE_ONLY);
    }

    /** Recover ownership using ECO's public diagnostics/registry contract, without its renamed menu helper. */
    public static ECOPlanningResult resolveOwnedResult(ICraftingPlan plan, boolean ecoPlannerAvailable) {
        if (plan == null) return null;
        var resolved = plan instanceof ECOCraftingPlanDiagnostics diagnostics ? diagnostics.neoecoae$getPlanningResult() : null;
        if (resolved == null) resolved = ECOPlanningResultRegistry.findExact(plan);
        if (resolved == null && ecoPlannerAvailable) resolved = ECOPlanningResultRegistry.find(plan);
        if (resolved != null && plan instanceof ECOCraftingPlanDiagnostics diagnostics) {
            diagnostics.neoecoae$setPlanningResult(resolved);
        }
        return resolved;
    }

    public static Confirmation prepare(ICraftingPlan visible, ECOPlanningResult confirmed,
            ECOPlanningResult resolved, ECOPlannerOptions options, boolean forced) {
        if (visible == null) throw rejected("NO_PLAN");
        if (options == null) throw rejected("NO_PLANNER_OPTIONS");
        if (confirmed == null || resolved != confirmed) throw rejected("CONFIRMED_RESULT_CHANGED");
        if (!ECOBigOrderAdmission.allows(confirmed, forced)) throw rejected("ADMISSION_DENIED");
        if (ECOBigOrderAdmission.waitsForMaterials(confirmed, forced)) {
            // The preview may be a simulation. Only its goal/options are admitted; no preview tasks or stock execute.
            return new Confirmation(confirmed, options, null, null,
                    ECOBigOrderRequest.fromPlanningResult(confirmed, forced, options));
        }
        if (visible.simulation() || confirmed.plan().simulation()) throw rejected("SIMULATION");
        if (confirmed.executionRequirement() == ECOExecutionRequirement.BLOCKED) throw rejected("BLOCKED");

        // ECO interprets its own confirmed graph and splits its exact material ledger.
        // No planner, graph solver, candidate selection or amount projection is implemented here.
        var exact = new ECOExactCraftingPlan(confirmed, forced);
        if (exact.execution() == null || exact.execution().mode() == ExecutionMode.BLOCKED
                || exact.execution().phases().isEmpty()) throw rejected("BLOCKED");

        // ECO's registry accepts executable metadata only with SUCCESS and a matching
        // complete material/task signature. Adapt that metadata to the exact plan's
        // physical window, keeping the original UUID, provenance and all exact values.
        // This is a metadata view of the confirmed order, not another planning request.
        var projection = new CraftingPlan(exact.finalOutput(), exact.bytes(), false, exact.multiplePaths(),
                exact.usedItems(), exact.emittedItems(), exact.missingItems(), exact.patternTimes());
        var metadata = new ECOPlanningResult(PlanningStatus.SUCCESS, projection, confirmed.trace(),
                confirmed.cycles(), confirmed.components(), confirmed.executionComponentOrder(),
                confirmed.calculationNanos(), confirmed.planningId(), confirmed.provenance());
        metadata.setTheoreticalBytes(cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount.of(confirmed.theoreticalBytes()));
        metadata.setExactPatternTimes(confirmed.exactPatternTimes());
        metadata.setExactMaterials(confirmed.exactUsedItems(), confirmed.exactEmittedItems(), confirmed.exactMissingItems());
        metadata.setFuzzyPlanningItemIds(confirmed.fuzzyPlanningItemIds());
        return new Confirmation(confirmed, options, exact, metadata, null);
    }

    /** Includes the entire exact reservation, including cycle seeds and stock beyond long. */
    static Map<AEKey, BigInteger> requiredStock(ECOExactCraftingPlan plan) {
        var required = new LinkedHashMap<AEKey, BigInteger>(plan.deferredStock());
        for (var entry : plan.usedItems()) required.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
        return required;
    }

    static ICraftingSubmitResult checkStored(ECOExactCraftingPlan plan, Predicate<AEKey> unbounded,
            BiFunction<AEKey, Long, Long> simulate) {
        for (var entry : requiredStock(plan).entrySet()) {
            if (!ECOBigOrderAdmission.hasStoredAmount(entry.getValue(), unbounded.test(entry.getKey()),
                    amount -> simulate.apply(entry.getKey(), amount))) {
                return CraftingSubmitResult.missingIngredient(new GenericStack(entry.getKey(),
                        ECOExactCraftingPlan.bounded(entry.getValue())));
            }
        }
        return null;
    }

    public static Outcome submit(IGrid grid, ICraftingCPU selected, ICraftingPlan visible,
            ECOPlanningResult confirmed, ECOPlanningResult resolved, ECOPlannerOptions options,
            boolean forced, IActionSource source) {
        try {
            var order = prepare(visible, confirmed, resolved, options, forced);
            if (!available(grid, selected)) return new Outcome("NO_ELIGIBLE_OMNI_CPU", CraftingSubmitResult.NO_CPU_FOUND);
            if (order.parent() != null) {
                if (!((Object) ((CraftingCPUCluster) selected).craftingLogic instanceof EcoOmniParentOrderCpu))
                    return new Outcome("OMNI_PARENT_BRIDGE_MISSING", CraftingSubmitResult.INCOMPLETE_PLAN);
                var submitted = order.parent().submit(carrier -> ((CraftingCPUCluster) selected).submitJob(grid, carrier, source, null));
                return new Outcome(submitted.successful() ? "" : "PARENT_SUBMISSION_FAILED", submitted);
            }
            if (!forced) {
                var inventory = ECOPlannerInventory.capture(grid);
                var missing = checkStored(order.exact(), inventory::isUnbounded,
                        (key, amount) -> grid.getStorageService().getInventory().extract(key, amount, Actionable.SIMULATE, source));
                if (missing != null) return new Outcome("MISSING_INGREDIENT", missing);
            }
            // Register the exact window's metadata so the existing CPU bridge can retain
            // the planning UUID. The alias also protects this synchronous submission from
            // other planners' optimizers. Both APIs belong to ECO; no GPL algorithm is copied.
            ECOPlanningResultRegistry.register(order.exact(), order.metadata());
            return ECOPlanningResultRegistry.withSubmissionAlias(order.exact(), order.metadata(), () -> {
                if (!EcoOmniPlanAdmission.supports(selected, order.exact())) {
                    return new Outcome("UNSUPPORTED_OMNI_PLAN", CraftingSubmitResult.INCOMPLETE_PLAN);
                }
                if (!available(grid, selected)) return new Outcome("NO_ELIGIBLE_OMNI_CPU", CraftingSubmitResult.NO_CPU_FOUND);
                var submitted = ((CraftingCPUCluster) selected).submitJob(grid, order.exact(), source, null);
                return new Outcome(submitted.successful() ? "" : "CPU_SUBMISSION_FAILED", submitted);
            });
        } catch (Rejected invalid) {
            return new Outcome(invalid.getMessage(), CraftingSubmitResult.INCOMPLETE_PLAN);
        } catch (RuntimeException | LinkageError invalid) {
            org.slf4j.LoggerFactory.getLogger(EcoOmniPlanMenu.class).warn("Omni rejected ECO confirmation", invalid);
            return new Outcome("EXACT_ORDER_INVALID", CraftingSubmitResult.INCOMPLETE_PLAN);
        }
    }

    private static Rejected rejected(String reason) { return new Rejected(reason); }

    public static final class Rejected extends IllegalArgumentException {
        private Rejected(String reason) { super(reason); }
    }

    public record Confirmation(ECOPlanningResult original, ECOPlannerOptions options,
            ECOExactCraftingPlan exact, ECOPlanningResult metadata, ECOBigOrderRequest parent) {}
    public record Outcome(String reason, ICraftingSubmitResult submitted) {}
}
