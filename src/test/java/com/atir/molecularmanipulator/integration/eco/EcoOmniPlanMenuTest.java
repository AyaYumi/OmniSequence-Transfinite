package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.CpuSelectionMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.execution.ECOCraftingCPU;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Runs the actual ECO 21.2.9 adapter, phase builder and metadata registry. */
class EcoOmniPlanMenuTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    private static final AEKey INPUT = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey OUTPUT = AEItemKey.of(Items.DIAMOND);
    private static final AEKey SEED = AEItemKey.of(Items.GOLD_INGOT);
    private static final AEKey EMITTED = AEItemKey.of(Items.COPPER_INGOT);
    private static final BigInteger HUGE = BigInteger.TEN.pow(35).add(BigInteger.valueOf(17));
    private static final BigInteger LIMIT = BigInteger.valueOf(Long.MAX_VALUE);
    private static final Set<ResourceLocation> FUZZY = Set.of(ResourceLocation.withDefaultNamespace("iron_ingot"));
    private static final ECOPlannerOptions OPTIONS = new ECOPlannerOptions(true, true, FUZZY, true);
    private static final IPatternDetails PATTERN = new IPatternDetails() {
        public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        public IInput[] getInputs() { return new IInput[] { new IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[] { new GenericStack(INPUT, 1) }; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return INPUT.equals(key); }
            public AEKey getRemainingKey(AEKey key) { return null; }
        }}; }
        public List<GenericStack> getOutputs() { return List.of(new GenericStack(OUTPUT, 1)); }
        public boolean supportsPushInputsToExternalInventory() { return false; }
    };

    @AfterEach void clearRegistry() { ECOPlanningResultRegistry.clear(); }

    @Test void exactAdapterKeepsOriginalConfirmationOptionsAmountsAndPhases() {
        var raw = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.PLANNED);
        var order = EcoOmniPlanMenu.prepare(raw.plan(), raw, raw, OPTIONS, false);
        assertSame(raw, order.original());
        assertSame(OPTIONS, order.options());
        assertEquals(HUGE, order.exact().exactTasks().get(PATTERN));
        assertEquals(HUGE, order.exact().theoreticalBytes());
        assertEquals(HUGE.subtract(LIMIT), order.exact().deferredStock().get(INPUT));
        assertEquals(HUGE.subtract(LIMIT), order.exact().deferredEmitted().get(EMITTED));
        assertEquals(Long.MAX_VALUE, order.exact().usedItems().get(INPUT));
        assertEquals(Long.MAX_VALUE, order.exact().emittedItems().get(EMITTED));
        assertEquals(FUZZY, order.exact().fuzzyPlanningItemIds());
        assertFalse(order.exact().simulation());
        assertEquals(ExecutionMode.PHASED_DAG, order.exact().execution().mode());
        assertEquals(List.of(0), order.exact().execution().phases().getFirst().taskIds());
        assertSame(PATTERN, order.exact().execution().tasks().getFirst().pattern());
        assertEquals(raw.status(), PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE);
        assertSame(raw.provenance(), order.metadata().provenance());
        assertSame(raw.trace(), order.metadata().trace());
        assertSame(raw.components(), order.metadata().components());
        assertEquals(raw.calculationNanos(), order.metadata().calculationNanos());
    }

    @Test void existingAdmissionBridgeRecoversOriginalPlanningUuidAndExactTail() {
        var raw = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.PLANNED);
        var order = EcoOmniPlanMenu.prepare(raw.plan(), raw, raw, OPTIONS, false);
        ECOPlanningResultRegistry.register(order.exact(), order.metadata());
        ECOPlanningResultRegistry.withSubmissionAlias(order.exact(), order.metadata(), () -> {
            assertTrue(ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(order.exact()));
            var prepared = EcoOmniPlanBridge.prepare(order.exact());
            assertEquals(raw.planningId(), prepared.planningId());
            assertEquals(HUGE, prepared.exactTasks().get(PATTERN));
            assertEquals(HUGE, prepared.theoreticalBytes());
            assertEquals(FUZZY, prepared.fuzzyPlanningItemIds());
            assertSame(order.exact().execution(), prepared.execution());
            assertEquals(order.exact().deferredStock(), prepared.deferredStock());
            return null;
        });
        assertFalse(ECOPlanningResultRegistry.shouldPreserveSubmissionPlan(order.exact()));
    }

    @Test void cpuAdmissionRejectsForeignSignatureCollisionButAcceptsExplicitSubmissionAlias() {
        var registered = result(PlanningStatus.SUCCESS, false, ComponentPlanningResult.Status.PLANNED);
        var source = registered.plan();
        var foreign = new CraftingPlan(source.finalOutput(), source.bytes(), source.simulation(), source.multiplePaths(),
                source.usedItems(), source.emittedItems(), source.missingItems(), source.patternTimes());
        ECOPlanningResultRegistry.register(source, registered);
        assertNotSame(source, foreign);
        assertSame(registered, ECOPlanningResultRegistry.find(foreign),
                "Reproduce ECO's signature-only recovery for a different planner's identical plan");
        assertTrue(ECOPlanningResultRegistry.isECOOwnedPlan(foreign),
                "The registry ownership helper also matches signatures and cannot establish CPU ownership");
        assertTrue(EcoOmniPlanBridge.owns(source));
        assertEquals(registered.planningId(), EcoOmniPlanBridge.prepare(source).planningId());
        assertFalse(EcoOmniPlanBridge.owns(foreign));
        assertNull(EcoOmniPlanBridge.prepare(foreign));
        ECOPlanningResultRegistry.withSubmissionAlias(foreign, registered, () -> {
            assertTrue(EcoOmniPlanBridge.owns(foreign));
            assertEquals(registered.planningId(), EcoOmniPlanBridge.prepare(foreign).planningId());
            return null;
        });
        assertFalse(EcoOmniPlanBridge.owns(foreign), "Submission scope cannot permanently claim the foreign object");
        assertFalse(EcoOmniPlanAdmission.supportsActive(foreign));
        assertFalse(EcoOmniPlanAdmission.supportsActive(null));
        assertThrows(IllegalStateException.class, () -> EcoOmniPlanAdmission.submittingForeign(foreign, () -> {
            assertTrue(EcoOmniPlanAdmission.supportsActive(foreign));
            assertFalse(EcoOmniPlanAdmission.supportsActive(source));
            assertFalse(EcoOmniPlanBridge.owns(foreign), "Allowing normal CPU validation cannot install ECO ownership");
            EcoOmniPlanAdmission.submittingForeign(source, () -> {
                assertTrue(EcoOmniPlanAdmission.supportsActive(source));
                assertFalse(EcoOmniPlanAdmission.supportsActive(foreign));
                return null;
            });
            assertTrue(EcoOmniPlanAdmission.supportsActive(foreign));
            throw new IllegalStateException("submission failed");
        }));
        assertFalse(EcoOmniPlanAdmission.supportsActive(foreign), "Failed submissions must clear their allowance");
    }

    @Test void missingOrReplacedConfirmationAndOptionsNeverProduceAPlan() {
        var first = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.PLANNED);
        var replacement = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.PLANNED);
        assertThrows(EcoOmniPlanMenu.Rejected.class, () -> EcoOmniPlanMenu.prepare(null, first, first, OPTIONS, false));
        assertThrows(EcoOmniPlanMenu.Rejected.class, () -> EcoOmniPlanMenu.prepare(first.plan(), first, first, null, false));
        assertThrows(EcoOmniPlanMenu.Rejected.class, () -> EcoOmniPlanMenu.prepare(first.plan(), first, replacement, OPTIONS, false));
        assertThrows(EcoOmniPlanMenu.Rejected.class, () -> EcoOmniPlanMenu.prepare(first.plan(), first, null, OPTIONS, true));
    }

    @Test void forceCannotAdmitSimulationOrBlockedComponents() {
        for (boolean forced : List.of(false, true)) {
            var simulation = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, true, ComponentPlanningResult.Status.PLANNED);
            var blocked = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.UNRESOLVED);
            assertEquals("SIMULATION", assertThrows(EcoOmniPlanMenu.Rejected.class,
                    () -> EcoOmniPlanMenu.prepare(simulation.plan(), simulation, simulation, OPTIONS, forced)).getMessage());
            assertThrows(EcoOmniPlanMenu.Rejected.class,
                    () -> EcoOmniPlanMenu.prepare(blocked.plan(), blocked, blocked, OPTIONS, forced));
        }
    }

    @Test void forcedMissingParentAdmitsOnlyGoalAndOptionsWithoutExecutingItsPreview() {
        var raw = result(PlanningStatus.MISSING_ITEMS, true, ComponentPlanningResult.Status.PLANNED);
        var plan = raw.plan();
        var parent = EcoOmniPlanMenu.prepare(plan, raw, raw, OPTIONS, true);
        assertNull(parent.exact());
        assertNull(parent.metadata());
        assertNotNull(parent.parent());
        assertEquals(OUTPUT, parent.parent().goal());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE), parent.parent().requested());
        assertSame(OPTIONS, parent.parent().options());
        assertTrue(parent.parent().carrier().patternTimes().isEmpty());
        assertTrue(parent.parent().carrier().usedItems().isEmpty());
        assertTrue(parent.parent().carrier().emittedItems().isEmpty());
        assertSame(plan, raw.plan());
        assertTrue(plan.simulation());
        assertFalse(ECOPlanningResultRegistry.isECOOwnedPlan(plan));
        var unresolved = result(PlanningStatus.MISSING_ITEMS, true, ComponentPlanningResult.Status.UNRESOLVED);
        assertThrows(EcoOmniPlanMenu.Rejected.class,
                () -> EcoOmniPlanMenu.prepare(unresolved.plan(), unresolved, unresolved, OPTIONS, true));
    }

    @Test void onlyEcoBigOrderStatusesMayReachTheAdapterEvenWhenForced() {
        for (var status : PlanningStatus.values()) {
            if (status == PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE || status == PlanningStatus.MISSING_ITEMS) continue;
            var raw = result(status, false, ComponentPlanningResult.Status.PLANNED);
            assertThrows(EcoOmniPlanMenu.Rejected.class,
                    () -> EcoOmniPlanMenu.prepare(raw.plan(), raw, raw, OPTIONS, true), status.name());
        }
    }

    @Test void fullStockCheckIncludesCycleReservationsAndNeverTruncatesFiniteStock() {
        var raw = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.PLANNED);
        var exact = EcoOmniPlanMenu.prepare(raw.plan(), raw, raw, OPTIONS, false).exact();
        assertEquals(HUGE, EcoOmniPlanMenu.requiredStock(exact).get(INPUT));
        assertEquals(BigInteger.valueOf(7), EcoOmniPlanMenu.requiredStock(exact).get(SEED));
        assertNotNull(EcoOmniPlanMenu.checkStored(exact, key -> false, (key, amount) -> amount));
        assertNotNull(EcoOmniPlanMenu.checkStored(exact, key -> true, (key, amount) -> 0L),
                "Unbounded stock still requires the caller's extraction permission");
        assertNull(EcoOmniPlanMenu.checkStored(exact, key -> true, (key, amount) -> amount));
    }

    @Test void noGridOrAutomaticSelectionIsNeverAnOmniAvailabilityClaim() {
        assertFalse(EcoOmniPlanMenu.isOmni(null));
        assertFalse(EcoOmniPlanMenu.available(null, null));
    }

    @Test void publicOwnershipResolutionPreservesDiagnosticsAndGatesSignatureRecovery() {
        var raw = result(PlanningStatus.PLANNED_BUT_AMOUNT_UNREPRESENTABLE, false, ComponentPlanningResult.Status.PLANNED);
        var attached = new AtomicReference<>(raw);
        var diagnostics = diagnosticPlan(raw.plan(), attached);
        assertSame(raw, EcoOmniPlanMenu.resolveOwnedResult(diagnostics, false));
        assertNull(EcoOmniPlanMenu.resolveOwnedResult(null, true));

        ECOPlanningResultRegistry.register(raw.plan(), raw);
        assertSame(raw, EcoOmniPlanMenu.resolveOwnedResult(raw.plan(), false),
                "Exact object ownership does not require the planner toggle");
        ECOPlanningResultRegistry.clear();
        var order = EcoOmniPlanMenu.prepare(raw.plan(), raw, raw, OPTIONS, false);
        ECOPlanningResultRegistry.register(order.exact(), order.metadata());
        var equivalent = order.metadata().plan();
        assertNull(ECOPlanningResultRegistry.findExact(equivalent));
        assertNull(EcoOmniPlanMenu.resolveOwnedResult(equivalent, false));
        assertEquals(raw.planningId(), EcoOmniPlanMenu.resolveOwnedResult(equivalent, true).planningId());

        attached.set(null);
        diagnostics = diagnosticPlan(equivalent, attached);
        var recovered = EcoOmniPlanMenu.resolveOwnedResult(diagnostics, true);
        assertNotNull(recovered);
        assertSame(recovered, attached.get(), "Recovered ownership must be attached through the public diagnostics API");
    }

    @Test void ecoAvailabilityRespectsExplicitSelectionAndAutomaticMachineOnlyExclusion() {
        var cpu = new EcoCpu();
        var other = new EcoCpu();
        var cpus = new AtomicReference<>(ImmutableSet.<ICraftingCPU>of(cpu));
        var service = (ICraftingService) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ICraftingService.class},
                (object, method, args) -> cpus.get());
        var grid = (IGrid) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{IGrid.class},
                (object, method, args) -> service);
        assertFalse(EcoOmniPlanMenu.ecoAvailable(null, cpu));
        assertTrue(EcoOmniPlanMenu.ecoAvailable(grid, cpu));
        assertTrue(EcoOmniPlanMenu.ecoAvailable(grid, null));
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, other), "Never replace a selected CPU with an available one");
        cpu.mode = CpuSelectionMode.MACHINE_ONLY;
        assertTrue(EcoOmniPlanMenu.ecoAvailable(grid, cpu), "Explicit selection can use a machine-only CPU");
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, null));
        cpu.mode = CpuSelectionMode.ANY;
        cpu.busy = true;
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, cpu));
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, null));
        cpu.busy = false;
        cpu.active = false;
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, cpu));
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, null));
        cpu.active = true;
        cpus.set(ImmutableSet.of());
        assertFalse(EcoOmniPlanMenu.ecoAvailable(grid, cpu));
    }

    private static ICraftingPlan diagnosticPlan(ICraftingPlan delegate, AtomicReference<ECOPlanningResult> attached) {
        return (ICraftingPlan) Proxy.newProxyInstance(EcoOmniPlanMenuTest.class.getClassLoader(),
                new Class<?>[]{ICraftingPlan.class, ECOCraftingPlanDiagnostics.class}, (object, method, args) -> {
                    if (method.getName().equals("neoecoae$getPlanningResult")) return attached.get();
                    if (method.getName().equals("neoecoae$setPlanningResult")) {
                        attached.set((ECOPlanningResult) args[0]);
                        return null;
                    }
                    return method.invoke(delegate, args);
                });
    }

    private static final class EcoCpu extends ECOCraftingCPU {
        boolean busy;
        boolean active = true;
        CpuSelectionMode mode = CpuSelectionMode.ANY;
        EcoCpu() { super(null, (cn.dancingsnow.neoecoae.api.IECOTier) null); }
        @Override public boolean isBusy() { return busy; }
        @Override public boolean isActive() { return active; }
        @Override public CpuSelectionMode getSelectionMode() { return mode; }
    }

    private static ECOPlanningResult result(PlanningStatus status, boolean simulation, ComponentPlanningResult.Status componentStatus) {
        var used = new KeyCounter(); used.set(INPUT, Long.MAX_VALUE);
        var emitted = new KeyCounter(); emitted.set(EMITTED, Long.MAX_VALUE);
        var plan = new CraftingPlan(new GenericStack(OUTPUT, Long.MAX_VALUE), Long.MAX_VALUE, simulation, true,
                used, emitted, new KeyCounter(), Map.of(PATTERN, Long.MAX_VALUE));
        var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.ACYCLIC, componentStatus,
                Map.of(OUTPUT, Long.MAX_VALUE), Set.of(PATTERN), Set.of(PATTERN), null,
                CycleExternalDemandStatus.SOLVED, Map.of(), "confirmed menu fixture", null,
                CycleExecutionDisposition.NOT_REQUIRED, Map.of(SEED, 7L));
        var provenance = new MaterialProvenance();
        var input = MaterialDemand.input(PATTERN, 0, INPUT, PlannerAmount.of(Long.MAX_VALUE));
        provenance.register(input); provenance.allocate(input, INPUT, MaterialSource.Stock.INSTANCE, input.amount());
        var goal = MaterialDemand.goal(OUTPUT, PlannerAmount.of(Long.MAX_VALUE));
        provenance.register(goal); provenance.allocate(goal, OUTPUT, new MaterialSource.PatternOutput(PATTERN, true), goal.amount());
        var raw = new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), List.of(component), List.of(0), 1234,
                provenance.freeze());
        raw.setExactPatternTimes(Map.of(PATTERN, PlannerAmount.of(HUGE)));
        raw.setExactMaterials(Map.of(INPUT, PlannerAmount.of(HUGE)), Map.of(EMITTED, PlannerAmount.of(HUGE)), Map.of());
        raw.setTheoreticalBytes(PlannerAmount.of(HUGE));
        raw.setFuzzyPlanningItemIds(FUZZY);
        return raw;
    }
}
