package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import appeng.crafting.execution.CraftingSubmitResult;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderRequest;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOBigOrderState;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlannerOptions;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount;
import cn.dancingsnow.neoecoae.crafting.planner.ECOBigOrderPlanner;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialDemand;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialProvenance;
import cn.dancingsnow.neoecoae.crafting.planner.provenance.MaterialSource;
import cn.dancingsnow.neoecoae.crafting.planner.result.ComponentPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExecutionDisposition;
import cn.dancingsnow.neoecoae.crafting.planner.result.CycleExternalDemandStatus;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlanningStatus;
import cn.dancingsnow.neoecoae.crafting.planner.trace.ECOPlanTrace;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class EcoOmniParentOrderTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        try { appeng.api.stacks.AEKeyTypesInternal.getRegistry(); }
        catch (IllegalStateException uninitialized) {
            var keys = new net.minecraft.core.MappedRegistry<appeng.api.stacks.AEKeyType>(
                    appeng.api.stacks.AEKeyType.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
            appeng.api.stacks.AEKeyTypesInternal.setRegistry(keys);
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.items());
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.fluids());
            keys.freeze();
        }
    }
    @org.junit.jupiter.api.BeforeAll static void bindCodecSentinel() throws ReflectiveOperationException {
        var item = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        item.setAccessible(true);
        item.set(appeng.core.definitions.AEItems.MISSING_CONTENT,
                net.neoforged.neoforge.registries.DeferredItem.createItem(ResourceLocation.withDefaultNamespace("barrier")));
    }
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final AEKey INPUT = AEItemKey.of(Items.IRON_INGOT);
    private static final AEKey GOAL = AEItemKey.of(Items.DIAMOND);
    private static final ECOPlannerOptions OPTIONS = new ECOPlannerOptions(true, true,
            Set.of(ResourceLocation.withDefaultNamespace("iron_ingot")), true);
    private static final BigInteger HUGE = BigInteger.TEN.pow(70).add(BigInteger.valueOf(13));
    private static final IPatternDetails PATTERN = new IPatternDetails() {
        public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        public IInput[] getInputs() { return new IInput[] { new IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[] {new GenericStack(INPUT, 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return INPUT.equals(key); }
            public AEKey getRemainingKey(AEKey key) { return null; }
        }}; }
        public List<GenericStack> getOutputs() { return List.of(new GenericStack(GOAL, 1)); }
        public boolean supportsPushInputsToExternalInventory() { return false; }
    };

    @Test void pendingPlanningNeverBlocksOrSubmitsPreviewAndCancellationStopsFuture() {
        var host = new Host();
        var answer = new CompletableFuture<ECOBigOrderPlanner.Answer>();
        int[] begins = {0};
        var parent = parent(HUGE, (grid, goal, candidate, bytes, options) -> {
            begins[0]++;
            assertEquals(Long.MAX_VALUE, candidate);
            assertEquals(Long.MAX_VALUE, bytes);
            assertSame(OPTIONS, options);
            return answer;
        });
        parent.tick(host);
        for (int tick = 0; tick < 1000; tick++) parent.tick(host);
        assertEquals(1, begins[0]);
        assertEquals(0, host.submissions);
        assertEquals(HUGE, parent.pending().get(GOAL));
        assertEquals(BigInteger.ZERO, parent.progress(0).completed());
        parent.cancel();
        assertTrue(answer.isCancelled());
        assertEquals(ECOBigOrderState.CANCELLED, parent.progress(0).state());
    }

    @Test void realExecutableChildUsesOriginalSourceAndOnlyDeliveredSegmentsAdvanceExactParent() {
        var host = new Host();
        var answer = new CompletableFuture<ECOBigOrderPlanner.Answer>();
        var source = IActionSource.empty();
        var parent = new EcoOmniParentOrder(UUID.randomUUID(), request(HUGE), source,
                (grid, goal, amount, bytes, options) -> answer);
        parent.tick(host);
        var result = result(PlanningStatus.SUCCESS, false, ComponentPlanningResult.Status.PLANNED);
        assertTrue(result.executionContract().executable());
        answer.complete(new ECOBigOrderPlanner.Answer(result, false, false));
        parent.tick(host);
        assertTrue(parent.running());
        assertEquals(1, host.submissions);
        assertSame(source, host.source);
        assertSame(result.plan(), host.submitted);
        assertSame(result, ECOPlanningResultRegistry.find(result.plan()));
        assertEquals(BigInteger.ZERO, parent.progress(3).completed());
        assertEquals(3, parent.progress(3).childRemaining());
        assertFalse(parent.lastChild());
        assertFalse(parent.childDelivered());
        assertEquals(BigInteger.valueOf(6), parent.progress(0).completed());
        assertEquals(HUGE.subtract(BigInteger.valueOf(6)), parent.progress(0).remaining());
        assertEquals(HUGE.subtract(BigInteger.valueOf(6)), parent.pending().get(GOAL));
        var checkpoint = parent.write(REGISTRIES);
        var restored = EcoOmniParentOrder.read(checkpoint, REGISTRIES);
        assertEquals(parent.id(), restored.id());
        assertEquals(parent.progress(0), restored.progress(0));
        assertEquals(parent.pending(), restored.pending());
        assertEquals(checkpoint, restored.write(REGISTRIES));
    }

    @Test void failedPhysicalAdmissionRetainsWholeOrderWithoutCountingProducts() {
        var host = new Host();
        host.accept = false;
        var parent = parent(HUGE, (grid, goal, amount, bytes, options) -> CompletableFuture.completedFuture(
                new ECOBigOrderPlanner.Answer(result(PlanningStatus.SUCCESS, false, ComponentPlanningResult.Status.PLANNED), false, false)));
        parent.tick(host); parent.tick(host);
        assertFalse(parent.running());
        assertEquals(ECOBigOrderState.WAITING_MATERIALS, parent.progress(0).state());
        assertEquals(HUGE, parent.progress(0).remaining());
        assertEquals(BigInteger.ZERO, parent.progress(0).completed());
    }

    @Test void missingSimulationBlockedOrRemovedPatternsNeverReachChildAdmission() {
        for (var fixture : List.of(
                result(PlanningStatus.MISSING_ITEMS, true, ComponentPlanningResult.Status.PLANNED),
                result(PlanningStatus.SUCCESS, true, ComponentPlanningResult.Status.PLANNED),
                result(PlanningStatus.SUCCESS, false, ComponentPlanningResult.Status.UNRESOLVED))) {
            var host = new Host();
            var parent = parent(HUGE, (grid, goal, amount, bytes, options) -> CompletableFuture.completedFuture(
                    new ECOBigOrderPlanner.Answer(fixture, false, false)));
            parent.tick(host); parent.tick(host);
            assertEquals(0, host.submissions);
            assertEquals(BigInteger.ZERO, parent.progress(0).completed());
            assertFalse(parent.running());
        }
        var host = new Host(); host.patternAvailable = false;
        var parent = parent(HUGE, (grid, goal, amount, bytes, options) -> CompletableFuture.completedFuture(
                new ECOBigOrderPlanner.Answer(result(PlanningStatus.SUCCESS, false, ComponentPlanningResult.Status.PLANNED), false, false)));
        parent.tick(host); parent.tick(host);
        assertEquals(0, host.submissions);
        assertEquals("PLAN_CHANGED_OR_BLOCKED", parent.progress(0).waitingReason());
    }

    @Test void runningChildCheckpointKeepsUuidExactCountsOptionsAndFinalChildBoundary() {
        var host = new Host();
        var parent = parent(BigInteger.valueOf(6), (grid, goal, amount, bytes, options) -> CompletableFuture.completedFuture(
                new ECOBigOrderPlanner.Answer(result(PlanningStatus.SUCCESS, false, ComponentPlanningResult.Status.PLANNED), false, false)));
        parent.tick(host); parent.tick(host);
        assertTrue(parent.lastChild());
        var saved = parent.write(REGISTRIES);
        var restored = EcoOmniParentOrder.read(saved, REGISTRIES);
        assertTrue(restored.running());
        assertTrue(restored.lastChild());
        assertEquals(parent.id(), restored.id());
        assertEquals(saved, restored.write(REGISTRIES));
        assertTrue(restored.childDelivered());
        assertEquals(ECOBigOrderState.COMPLETED, restored.progress(0).state());
        assertEquals(BigInteger.valueOf(6), restored.progress(0).completed());
    }

    @Test void reloadPreservesPlayerAndUnsupportedMachineSourceRatherThanReplacingItWithCpu() {
        var sourceTag = new CompoundTag(); sourceTag.putUUID("player", UUID.randomUUID());
        sourceTag.putBoolean("machine", true);
        var source = EcoOmniParentOrderSource.read(sourceTag);
        assertEquals(sourceTag, source.write());
        assertNull(source.resolve(null));
        var saved = parent(HUGE, (g, k, a, b, o) -> new CompletableFuture<>()).write(REGISTRIES);
        saved.putString("completed", HUGE.add(BigInteger.ONE).toString());
        assertThrows(IllegalArgumentException.class, () -> EcoOmniParentOrder.read(saved, REGISTRIES));
    }

    @Test void carrierScopeCannotBeMistakenForRegisteredExecutableMetadataAndAlwaysUnwinds() {
        var request = request(BigInteger.valueOf(6));
        request.submit(carrier -> {
            var projection = new CraftingPlan(carrier.finalOutput(), 0, true, false,
                    new KeyCounter(), new KeyCounter(), new KeyCounter(), Map.of());
            var preview = new ECOPlanningResult(PlanningStatus.MISSING_ITEMS, projection,
                    new ECOPlanTrace(), List.of(), List.of(), List.of(), 0);
            ECOPlanningResultRegistry.register(projection, preview);
            assertFalse(EcoOmniPlanAdmission.supportsActive(carrier));
            assertThrows(IllegalStateException.class, () -> EcoOmniParentOrder.submitCarrier(carrier, () -> {
                assertTrue(EcoOmniPlanAdmission.supportsActive(carrier));
                assertFalse(EcoOmniPlanBridge.owns(carrier));
                assertFalse(EcoOmniParentOrder.submittingCarrier(projection));
                throw new IllegalStateException();
            }));
            assertFalse(EcoOmniPlanAdmission.supportsActive(carrier));
            assertFalse(EcoOmniParentOrder.submittingCarrier(carrier));
            return null;
        });
    }

    @Test void publicApiCarrierScopeRetainsExactRequestIdentityAndUnwindsOnFailure() {
        var request = request(HUGE);
        request.submit(carrier -> {
            assertSame(request, ECOBigOrderRequest.forSubmission(carrier));
            assertTrue(carrier.usedItems().isEmpty());
            assertTrue(carrier.patternTimes().isEmpty());
            assertEquals(Long.MAX_VALUE, carrier.finalOutput().amount());
            assertThrows(IllegalStateException.class, () -> request(BigInteger.TEN).submit(nested -> {
                assertNull(ECOBigOrderRequest.forSubmission(carrier));
                throw new IllegalStateException();
            }));
            assertSame(request, ECOBigOrderRequest.forSubmission(carrier));
            return null;
        });
        assertNull(ECOBigOrderRequest.forSubmission(request.carrier()));
    }

    private static EcoOmniParentOrder parent(BigInteger amount, EcoOmniParentOrder.Planner planner) {
        return new EcoOmniParentOrder(UUID.randomUUID(), request(amount), IActionSource.empty(), planner);
    }
    private static ECOBigOrderRequest request(BigInteger amount) {
        return new ECOBigOrderRequest(GOAL, amount, true, OPTIONS, Map.of(INPUT, BigInteger.valueOf(17)));
    }
    private static ECOPlanningResult result(PlanningStatus status, boolean simulation, ComponentPlanningResult.Status componentStatus) {
        var used = new KeyCounter(); used.set(INPUT, 6);
        var plan = new CraftingPlan(new GenericStack(GOAL, 6), 16, simulation, false,
                used, new KeyCounter(), new KeyCounter(), Map.of(PATTERN, 6L));
        var component = new ComponentPlanningResult(0, ComponentPlanningResult.Type.ACYCLIC, componentStatus,
                Map.of(GOAL, 6L), Set.of(PATTERN), Set.of(PATTERN), null,
                CycleExternalDemandStatus.SOLVED, Map.of(), "parent fixture", null,
                CycleExecutionDisposition.NOT_REQUIRED, Map.of());
        var attribution = new MaterialProvenance();
        var input = MaterialDemand.input(PATTERN, 0, INPUT, PlannerAmount.of(6));
        attribution.register(input); attribution.allocate(input, INPUT, MaterialSource.Stock.INSTANCE, input.amount());
        var output = MaterialDemand.goal(GOAL, PlannerAmount.of(6));
        attribution.register(output); attribution.allocate(output, GOAL, new MaterialSource.PatternOutput(PATTERN, true), output.amount());
        return new ECOPlanningResult(status, plan, new ECOPlanTrace(), List.of(), List.of(component), List.of(0), 0, attribution.freeze());
    }

    private static final class Host implements EcoOmniParentOrder.Host {
        boolean patternAvailable = true, accept = true;
        int submissions;
        IActionSource source;
        ICraftingPlan submitted;
        final ICraftingService service = proxy(ICraftingService.class, (object, method, args) -> {
            if (method.getName().equals("getCraftingFor")) return patternAvailable ? List.of(PATTERN) : List.of();
            throw new AssertionError("Unexpected service access: " + method);
        });
        final IGrid grid = proxy(IGrid.class, (object, method, args) -> {
            if (method.getName().equals("getCraftingService")) return service;
            throw new AssertionError("Waiting parents must not touch physical storage: " + method);
        });
        public IGrid ecoParent$grid() { return grid; }
        public net.minecraft.server.MinecraftServer ecoParent$server() { return null; }
        public long ecoParent$storage() { return Long.MAX_VALUE; }
        public boolean ecoParent$flushInventory() { return true; }
        public ICraftingSubmitResult ecoParent$submitChild(ICraftingPlan plan, IActionSource source) {
            submissions++; submitted = plan; this.source = source;
            return accept ? CraftingSubmitResult.successful(null) : CraftingSubmitResult.missingIngredient(new GenericStack(INPUT, 1));
        }
        public void ecoParent$dirty() {}
    }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler calls) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, calls);
    }
}
