package com.atir.molecularmanipulator.integration.eco;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime.DispatchCandidate;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.Type;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** Executes ECO 21.2.9 itself. Reflection is confined to this test's substitute for mixin invokers. */
class EcoOmniPlanExecutionTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        try { appeng.api.stacks.AEKeyTypesInternal.getRegistry(); }
        catch (IllegalStateException uninitialized) {
            var keyTypes = new net.minecraft.core.MappedRegistry<appeng.api.stacks.AEKeyType>(
                    appeng.api.stacks.AEKeyType.REGISTRY_KEY, com.mojang.serialization.Lifecycle.stable(), false);
            appeng.api.stacks.AEKeyTypesInternal.setRegistry(keyTypes);
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.items());
            appeng.api.stacks.AEKeyTypesInternal.register(appeng.api.stacks.AEKeyType.fluids());
            keyTypes.freeze();
        }
    }
    @org.junit.jupiter.api.BeforeAll static void bindCodecSentinel() throws ReflectiveOperationException {
        var marker = appeng.core.definitions.AEItems.MISSING_CONTENT;
        var item = appeng.core.definitions.ItemDefinition.class.getDeclaredField("item");
        item.setAccessible(true);
        item.set(marker, net.neoforged.neoforge.registries.DeferredItem.createItem(
                ResourceLocation.withDefaultNamespace("barrier")));
    }
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final HolderLookup.Provider REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);

    @Test void samePatternInTwoPhasesKeepsSeparateTaskIdentityAndRejectsStaleTokens() {
        var pattern = pattern(Items.PAPER, IRON, 1, 1);
        var plan = plan(List.of(task(0, pattern, 2, 0), task(1, pattern, 5, 1)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 2)), List.of(), Map.of(), Map.of()),
                phase(1, Type.CYCLE, List.of(1), List.of(new ECOExecutionPlan.ExecutionStep(1, 5)), List.of(0), Map.of(), Map.of())),
                ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.TWO, 1, BigInteger.valueOf(5)), Map.of(), Map.of());
        var first = fixture.execution.begin(pattern);
        var stale = fixture.execution.begin(pattern);
        assertEquals(0, first.taskId());
        first.accepted(BigInteger.TWO, unit(IRON, 1));
        assertThrows(IllegalStateException.class, stale::maximum);
        var second = fixture.execution.begin(pattern);
        assertNotNull(second);
        assertEquals(1, second.taskId());
        assertEquals(BigInteger.valueOf(5), second.maximum());
        assertFalse(fixture.execution.isComplete());
        second.accepted(BigInteger.valueOf(5), unit(IRON, 1));
        assertTrue(fixture.execution.isComplete());
    }

    @Test void exactTailIsOneNativeAdmissionAndNeverCreatesFiniteStock() {
        var pattern = pattern(Items.PAPER, IRON, 3, 1);
        var huge = BigInteger.TEN.pow(35).add(BigInteger.valueOf(17));
        var plan = plan(List.of(task(0, pattern, Long.MAX_VALUE, 0)),
                List.of(phase(0, Type.DAG, List.of(0), List.of(), List.of(), Map.of(), Map.of())), ExecutionMode.NATIVE);
        var fixture = fixture(plan, Map.of(0, huge), Map.of(), Map.of());
        var dispatch = fixture.execution.begin(pattern);
        assertEquals(huge, dispatch.maximum());
        assertEquals(BigInteger.TWO, dispatch.limit(unit(IRON, 3), key -> BigInteger.valueOf(8), huge));
        assertEquals(BigInteger.ZERO, dispatch.limit(unit(IRON, 3), key -> BigInteger.ZERO, huge));
        dispatch.accepted(huge, unit(IRON, 3));
        assertTrue(fixture.execution.isComplete());
    }

    @Test void outputReservationUsesExactRemainingAndReleasesConsumedStartupSeeds() {
        var pattern = pattern(Items.PAPER, IRON, 3, 2);
        var huge = BigInteger.TEN.pow(35);
        var nativePlan = plan(List.of(task(0, pattern, Long.MAX_VALUE, 0)), List.of(
                phase(0, Type.DAG, List.of(0), List.of(), List.of(), Map.of(), Map.of())), ExecutionMode.NATIVE);
        var exact = fixture(nativePlan, Map.of(0, huge), Map.of(), Map.of());
        assertEquals(huge.multiply(BigInteger.valueOf(6)), exact.execution.reservedInputAmount(IRON));
        exact.execution.begin(pattern).accepted(huge.subtract(BigInteger.ONE), unit(IRON, 6));
        assertEquals(BigInteger.valueOf(6), exact.execution.reservedInputAmount(IRON));
        exact.execution.begin(pattern).accepted(BigInteger.ONE, unit(IRON, 6));
        assertEquals(BigInteger.ZERO, exact.execution.reservedInputAmount(IRON));

        var seedPattern = pattern(Items.MAP, IRON, 1, 1);
        var combinedPlan = plan(List.of(task(0, pattern, Long.MAX_VALUE, 0), task(1, seedPattern, 2, 1)), List.of(
                phase(0, Type.DAG, List.of(0), List.of(), List.of(), Map.of(), Map.of()),
                phase(1, Type.CYCLE, List.of(1), List.of(new ECOExecutionPlan.ExecutionStep(1, 2)),
                        List.of(0), Map.of(), Map.of(IRON, 2L))), ExecutionMode.ORDERED_CYCLE);
        var combined = fixture(combinedPlan, Map.of(0, huge, 1, BigInteger.TWO), Map.of(), Map.of());
        assertEquals(huge.multiply(BigInteger.valueOf(6)).add(BigInteger.valueOf(4)),
                combined.execution.reservedInputAmount(IRON), "Long saturation must not erase another phase's startup seed");
        var cycle = plan(List.of(task(0, seedPattern, 2, 0)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 2)),
                        List.of(), Map.of(), Map.of(IRON, 2L))), ExecutionMode.ORDERED_CYCLE);
        var seeded = fixture(cycle, Map.of(0, BigInteger.TWO), Map.of(), Map.of());
        assertTrue(seeded.execution.reservedInputAmount(IRON).compareTo(BigInteger.TWO) >= 0);
        seeded.execution.begin(seedPattern).accepted(BigInteger.TWO, unit(IRON, 1));
        assertEquals(BigInteger.ZERO, seeded.execution.reservedInputAmount(IRON),
                "A completed cycle must not hold its final products forever as feedback");
    }

    @Test void otherPhaseStartupSeedsAreProtectedAgainstExtractionAndNativeBatching() {
        var seedOwner = pattern(Items.PAPER, IRON, 1, 1);
        var consumer = pattern(Items.MAP, IRON, 1, 1);
        var plan = plan(List.of(task(0, seedOwner, 2, 0), task(1, consumer, 10, 1)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 2)), List.of(), Map.of(), Map.of(IRON, 2L)),
                phase(1, Type.DAG, List.of(1), List.of(), List.of(), Map.of(), Map.of())), ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.TWO, 1, BigInteger.TEN), Map.of(), Map.of());
        var stock = new ListCraftingInventory(key -> {});
        stock.insert(IRON, 2, Actionable.MODULATE);
        var dispatch = fixture.execution.begin(consumer);
        var guarded = dispatch.guard(stock, key -> BigInteger.valueOf(stock.list.get(key)));
        assertEquals(0, guarded.extract(IRON, 2, Actionable.MODULATE));
        assertEquals(2, stock.list.get(IRON));
        assertEquals(BigInteger.ZERO, dispatch.limit(unit(IRON, 1), key -> BigInteger.TWO, BigInteger.TEN));
        fixture.execution.begin(seedOwner).accepted(BigInteger.TWO, unit(IRON, 1));
        assertTrue(fixture.execution.begin(consumer).protectedSeeds().isEmpty());
    }

    @Test void dynamicCycleLeavesSharedInputForCompetingFirings() {
        var first = pattern(Items.PAPER, IRON, 1, 1);
        var second = pattern(Items.MAP, IRON, 1, 1);
        var plan = plan(List.of(task(0, first, 10, 0), task(1, second, 4, 0)), List.of(
                phase(0, Type.DYNAMIC_CYCLE, List.of(0, 1), List.of(), List.of(), Map.of(0, 10L, 1, 4L), Map.of())),
                ExecutionMode.DYNAMIC_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.TEN, 1, BigInteger.valueOf(4)), Map.of(), Map.of());
        assertEquals(BigInteger.TWO, fixture.execution.begin(first).limit(unit(IRON, 1), key -> BigInteger.valueOf(6), BigInteger.TEN));
    }

    @Test void inputAllocationAmountsIncludeMultiplierAndBatchStopsAtKeyTransition() {
        var pattern = pattern(Items.PAPER, IRON, 2, 3);
        var allocation = new PlannedInputAllocation(0, List.of(
                new PlannedInputAllocation.Run(IRON, 6, 2), new PlannedInputAllocation.Run(GOLD, 6, 3)));
        var task = new ECOExecutionPlan.TaskSpec(0, PlanIdentity.patternIdentityFor(pattern), pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), 5, 0, ECOExecutionPlan.TaskKind.DAG, List.of(allocation));
        var plan = plan(List.of(task), List.of(phase(0, Type.DAG, List.of(0), List.of(), List.of(), Map.of(), Map.of())), ExecutionMode.NATIVE);
        var fixture = fixture(plan, Map.of(0, BigInteger.valueOf(5)), Map.of(), Map.of());
        var stock = new ListCraftingInventory(key -> {});
        stock.insert(IRON, 12, Actionable.MODULATE);
        var first = fixture.execution.begin(pattern);
        assertEquals(BigInteger.TWO, first.maximum());
        var extracted = first.extractInputs(stock, null, new KeyCounter(), new KeyCounter());
        assertNotNull(extracted);
        assertEquals(6, extracted[0].get(IRON));
        assertEquals(6, stock.list.get(IRON));
        first.accepted(BigInteger.TWO, extracted);
        var next = fixture.execution.begin(pattern);
        assertEquals(BigInteger.valueOf(3), next.maximum());
        assertNull(next.extractInputs(stock, null, new KeyCounter(), new KeyCounter()), "The planned gold run must not consume remaining iron");
        assertEquals(6, stock.list.get(IRON));
    }

    @Test void deferredStockTransfersOnlyRealExtractionAndEmissionsRemainExpectations() {
        var pattern = pattern(Items.PAPER, IRON, 1, 1);
        var plan = plan(List.of(task(0, pattern, 1, 0)), List.of(phase(0, Type.DAG, List.of(0), List.of(), List.of(), Map.of(), Map.of())), ExecutionMode.NATIVE);
        var huge = BigInteger.TEN.pow(30);
        var fixture = fixture(plan, Map.of(0, BigInteger.ONE), Map.of(IRON, huge), Map.of(GOLD, huge));
        var stock = new LinkedHashMap<AEKey, BigInteger>();
        var emitted = new LinkedHashMap<AEKey, BigInteger>();
        assertTrue(fixture.execution.refillDeferred((key, requested) -> { assertEquals(Long.MAX_VALUE, requested); return 7; },
                (key, amount) -> stock.merge(key, amount, BigInteger::add), emitted::put));
        assertEquals(Map.of(IRON, BigInteger.valueOf(7)), stock);
        assertEquals(Map.of(GOLD, huge), emitted);
        assertEquals(huge.subtract(BigInteger.valueOf(7)), fixture.execution.deferredStock().get(IRON));
        assertTrue(fixture.execution.deferredEmitted().isEmpty());
        fixture.execution.begin(pattern).accepted(BigInteger.ONE, unit(IRON, 1));
        assertFalse(fixture.execution.isComplete());
    }

    @Test void nbtPreservesNativeRuntimeCursorExactAmountsSignatureAndPlanningMetadata() {
        var a = pattern(Items.PAPER, IRON, 1, 1);
        var b = pattern(Items.MAP, GOLD, 1, 1);
        var plan = plan(List.of(task(0, a, 6, 0), task(1, b, 6, 0)), List.of(
                phase(0, Type.CYCLE, List.of(0, 1), List.of(new ECOExecutionPlan.ExecutionStep(0, 2),
                        new ECOExecutionPlan.ExecutionStep(1, 2, 2, 3)), List.of(), Map.of(), Map.of(IRON, 2L))), ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.valueOf(6), 1, BigInteger.valueOf(6)), Map.of(IRON, BigInteger.TEN.pow(28)), Map.of());
        fixture.execution.begin(a).accepted(BigInteger.TWO, unit(IRON, 1));
        var tag = fixture.execution.write(REGISTRIES);
        assertEquals("10000000000000000000000000000", tag.getList("stock", Tag.TAG_COMPOUND).getCompound(0).getString("omniAmount"));
        assertEquals("test:fuzzy", tag.getList("fuzzy", Tag.TAG_STRING).getString(0));
        assertEquals(fixture.execution.planningId(), tag.getUUID("planningId"));
        var envelope = tag.getCompound("plan");
        assertFalse(envelope.getCompound("signature").getList("used", Tag.TAG_COMPOUND).isEmpty());
        assertEquals("DEFINITION", envelope.getList("identities", Tag.TAG_COMPOUND).getCompound(0).getString("kind"));
        var bindings = Map.of(0, a, 1, b);
        var restoredRuntime = ReflectiveRuntime.restore(plan, bindings, fixture.execution.remainingByTask(), tag.getCompound("runtime"));
        var candidates = restoredRuntime.runtime.candidates(Map.of(a, 4L, b, 6L));
        assertEquals(b, candidates.getFirst().pattern());
        assertEquals(1, candidates.getFirst().orderedStepIndex());
        assertEquals(6, candidates.getFirst().maxDispatchCount(), "ECO preserves its repeat-group batch allowance");
        assertTrue(restoredRuntime.ecoOmni$protectedSeed(candidates.getFirst()).isEmpty());
    }

    @Test void exactTaskDistributionRetainsAllOwnersAndHugeFixedInputTail() {
        var a = pattern(Items.PAPER, IRON, 1, 1);
        var plan = plan(List.of(task(0, a, 2, 0), task(1, a, 3, 1)), List.of(
                phase(0, Type.DAG, List.of(0), List.of(), List.of(), Map.of(), Map.of()),
                phase(1, Type.DAG, List.of(1), List.of(), List.of(0), Map.of(), Map.of())), ExecutionMode.PHASED_DAG);
        var huge = BigInteger.TEN.pow(30);
        var totals = EcoOmniPlanBridge.taskTotals(plan, Map.of(a, huge));
        assertEquals(BigInteger.TWO, totals.get(0));
        assertEquals(huge.subtract(BigInteger.TWO), totals.get(1));
        assertThrows(IllegalArgumentException.class, () -> EcoOmniPlanBridge.taskTotals(plan, Map.of(a, BigInteger.valueOf(4))));
    }

    @Test void candidateIteratorRemovesTheActualAeTaskAndRefreshesAfterAcceptance() {
        var first = pattern(Items.PAPER, IRON, 1, 1);
        var second = pattern(Items.MAP, GOLD, 1, 1);
        var plan = plan(List.of(task(0, first, 1, 0), task(1, second, 1, 1)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 1)), List.of(), Map.of(), Map.of()),
                phase(1, Type.DAG, List.of(1), List.of(), List.of(0), Map.of(), Map.of())), ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.ONE, 1, BigInteger.ONE), Map.of(), Map.of());
        var tasks = new LinkedHashMap<IPatternDetails, Object>();
        tasks.put(second, new Object());
        tasks.put(first, new Object());
        var iterator = fixture.execution.candidateEntries(tasks).iterator();
        assertEquals(first, iterator.next().getKey());
        assertFalse(iterator.hasNext());
        fixture.execution.begin(first).accepted(BigInteger.ONE, unit(IRON, 1));
        iterator.remove();
        assertFalse(tasks.containsKey(first), "AE2 removal must reach the actual job map");
        assertThrows(IllegalStateException.class, iterator::remove);
        assertEquals(List.of(second), fixture.execution.candidateEntries(tasks).stream().map(Map.Entry::getKey).toList());
    }

    @Test void guardAndNativeReservationIncludeRealOmniOverflowWithoutSpendingOtherPhaseSeeds() {
        var seed = pattern(Items.PAPER, IRON, 1, 1);
        var consume = pattern(Items.MAP, IRON, 3, 1);
        var huge = BigInteger.TEN.pow(35);
        var plan = plan(List.of(task(0, seed, 2, 0), task(1, consume, Long.MAX_VALUE, 1)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 2)), List.of(), Map.of(), Map.of(IRON, 2L)),
                phase(1, Type.DAG, List.of(1), List.of(), List.of(), Map.of(), Map.of())), ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.TWO, 1, huge), Map.of(), Map.of());
        var window = new ListCraftingInventory(key -> {});
        var physical = new com.atir.molecularmanipulator.crafting.OmniExactInventory();
        var amount = huge.multiply(BigInteger.valueOf(3)).add(BigInteger.TWO);
        physical.insert(window, IRON, amount);
        var dispatch = fixture.execution.begin(consume);
        var limit = dispatch.limit(unit(IRON, 3), key -> physical.amount(window, key), huge);
        assertEquals(huge, limit);
        var guarded = dispatch.guard(physical.wrap(window, key -> {}), key -> physical.amount(window, key));
        assertEquals(3, guarded.extract(IRON, 3, Actionable.MODULATE));
        var units = com.atir.molecularmanipulator.crafting.OmniExactInputReservation.unitInputs(unit(IRON, 3), Set.of());
        try (var reservation = com.atir.molecularmanipulator.crafting.OmniExactInputReservation.reserve(physical, window, units, limit)) {
            assertNotNull(reservation);
            reservation.commit();
        }
        dispatch.accepted(limit, unit(IRON, 3));
        assertEquals(BigInteger.TWO, physical.amount(window, IRON));
        assertFalse(fixture.execution.isComplete(), "The seed-owning phase still has its two real items");
    }

    @Test void cpuHooksApplyAllocatedExtractionCapsRejectionAndAnImmutableAcceptedPrototype() throws Exception {
        var pattern = pattern(Items.PAPER, IRON, 1, 1);
        var plan = plan(List.of(task(0, pattern, 2, 0)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 2)), List.of(), Map.of(), Map.of(IRON, 2L))),
                ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.TWO), Map.of(), Map.of());
        var cpu = new CpuFixture();
        cpu.ecoOmni$setExecution(fixture.execution);
        cpu.stock.insert(IRON, 7, Actionable.MODULATE);
        var guarded = cpu.ecoOmni$guard(pattern, cpu.stock);
        var inputs = cpu.ecoOmni$extract(pattern, guarded, null, new KeyCounter(), new KeyCounter(),
                () -> { fail("ECO allocation must own this extraction"); return null; });
        assertNotNull(inputs);
        assertEquals(6, cpu.stock.list.get(IRON));
        assertEquals(BigInteger.TWO, cpu.ecoOmni$limit(pattern, BigInteger.TEN));
        assertFalse(cpu.ecoOmni$pushProvider(null, pattern, inputs, () -> false));
        assertEquals(BigInteger.TWO, fixture.execution.remainingByTask().get(0));
        // A real provider may consume/clear the supplied counters. Seed accounting needs its own prototype.
        assertTrue(cpu.ecoOmni$pushProvider(null, pattern, inputs, () -> {
            inputs[0].reset();
            cpu.ecoOmni$accepted(pattern, BigInteger.ONE);
            return true;
        }));
        assertEquals(BigInteger.ONE, fixture.execution.remainingByTask().get(0));
        var seeds = fixture.execution.write(REGISTRIES).getCompound("runtime").getList("startupSeeds", Tag.TAG_COMPOUND);
        assertEquals(1, GenericStack.readTag(REGISTRIES, seeds.getCompound(0)).amount());
        assertEquals(BigInteger.ZERO, cpu.ecoOmni$limit(pattern, BigInteger.TEN), "Accepted token cannot be reused");
        assertThrows(IllegalStateException.class, () -> cpu.ecoOmni$accepted(pattern, BigInteger.ONE));
    }

    @Test void cpuHooksStopDeferredPhaseBeforeTouchingStockAndCapTheNativeExactRequest() throws Exception {
        var first = pattern(Items.PAPER, IRON, 1, 1);
        var second = pattern(Items.MAP, GOLD, 2, 1);
        var plan = plan(List.of(task(0, first, 1, 0), task(1, second, 10, 1)), List.of(
                phase(0, Type.CYCLE, List.of(0), List.of(new ECOExecutionPlan.ExecutionStep(0, 1)), List.of(), Map.of(), Map.of()),
                phase(1, Type.DAG, List.of(1), List.of(), List.of(0), Map.of(), Map.of())), ExecutionMode.ORDERED_CYCLE);
        var fixture = fixture(plan, Map.of(0, BigInteger.ONE, 1, BigInteger.TEN), Map.of(), Map.of());
        var cpu = new CpuFixture();
        cpu.ecoOmni$setExecution(fixture.execution);
        cpu.stock.insert(GOLD, 9, Actionable.MODULATE);
        assertNull(cpu.ecoOmni$guard(second, cpu.stock));
        assertEquals(9, cpu.stock.list.get(GOLD));
        fixture.execution.begin(first).accepted(BigInteger.ONE, unit(IRON, 1));
        var guarded = cpu.ecoOmni$guard(second, cpu.stock);
        assertNotNull(cpu.ecoOmni$extract(second, guarded, null, new KeyCounter(), new KeyCounter(), () -> null));
        assertEquals(BigInteger.valueOf(4), cpu.ecoOmni$limit(second, BigInteger.TEN.pow(40)),
                "Native requests are bounded using physical stock before prototype extraction");
    }

    @Test void failedCpuRestoreKeepsAnImmutableFullNbtSnapshotAndBlocksResubmission() throws Exception {
        var cpu = new CpuFixture();
        var original = new CompoundTag();
        original.put("molecularmanipulator:ecoPlan", new CompoundTag());
        original.putString("unknownFutureData", "preserve");
        var exact = new CompoundTag(); exact.putString("huge", BigInteger.TEN.pow(40).toString());
        original.put("molecularmanipulator:exactCraftingState", exact);
        var expected = original.copy();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> broken = args -> {
            throw new IllegalArgumentException("Unavailable saved pattern");
        };
        cpuHandler("molecularmanipulator$restoreEcoJob", CompoundTag.class, HolderLookup.Provider.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class).invoke(cpu, original, REGISTRIES, broken);
        assertTrue(cpu.ecoOmni$paused());
        assertTrue(cpu.ecoOmni$managed());
        original.putString("unknownFutureData", "changed by caller");
        var saved = new CompoundTag();
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> mustNotSavePartialState = args -> {
            fail("A failed restoration must not serialize partial state"); return null;
        };
        cpuHandler("molecularmanipulator$saveEcoJob", CompoundTag.class, HolderLookup.Provider.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class).invoke(cpu, saved, REGISTRIES, mustNotSavePartialState);
        assertEquals(expected, saved);
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<appeng.api.networking.crafting.ICraftingSubmitResult> submit = args -> {
            fail("A retained failed job must not admit new extraction"); return null;
        };
        var result = cpuHandler("molecularmanipulator$submitEcoPlan", appeng.api.networking.IGrid.class,
                appeng.api.networking.crafting.ICraftingPlan.class, appeng.api.networking.security.IActionSource.class,
                appeng.api.networking.crafting.ICraftingRequester.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class).invoke(cpu, null, null, null, null, submit);
        assertSame(appeng.crafting.execution.CraftingSubmitResult.CPU_BUSY, result);
    }

    @Test void failedCpuCancellationRetainsSnapshotWhenPhysicalRefundCannotBeDecoded() throws Exception {
        var cpu = new CpuFixture();
        var tag = new CompoundTag();
        tag.put("molecularmanipulator:ecoPlan", new CompoundTag());
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> broken = args -> {
            throw new IllegalArgumentException("Missing physical item");
        };
        cpuHandler("molecularmanipulator$restoreEcoJob", CompoundTag.class, HolderLookup.Provider.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class).invoke(cpu, tag, REGISTRIES, broken);
        com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> cancel = args -> {
            fail("Undecodable material must remain recoverable instead of being discarded"); return null;
        };
        cpuHandler("molecularmanipulator$cancelEcoJob", com.llamalad7.mixinextras.injector.wrapoperation.Operation.class)
                .invoke(cpu, cancel);
        assertTrue(cpu.ecoOmni$paused());
        var saved = new CompoundTag();
        cpuHandler("molecularmanipulator$saveEcoJob", CompoundTag.class, HolderLookup.Provider.class,
                com.llamalad7.mixinextras.injector.wrapoperation.Operation.class).invoke(cpu, saved, REGISTRIES, cancel);
        assertEquals(tag, saved);
    }

    @Test void taskProgressConstructionScopeRestoresNestedAndExceptionalCallers() {
        assertNull(EcoOmniPlanRuntimeScope.remaining());
        EcoOmniPlanRuntimeScope.withRemaining(id -> BigInteger.TWO, () -> {
            assertEquals(BigInteger.TWO, EcoOmniPlanRuntimeScope.remaining().apply(7));
            assertThrows(IllegalArgumentException.class, () -> EcoOmniPlanRuntimeScope.withRemaining(id -> BigInteger.TEN,
                    () -> { throw new IllegalArgumentException(); }));
            assertEquals(BigInteger.TWO, EcoOmniPlanRuntimeScope.remaining().apply(7));
            return null;
        });
        assertNull(EcoOmniPlanRuntimeScope.remaining());
    }

    private static Method cpuHandler(String name, Class<?>... types) throws ReflectiveOperationException {
        var method = com.atir.molecularmanipulator.mixin.EcoOmniCpuPlanMixin.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
    private static final class CpuFixture extends com.atir.molecularmanipulator.mixin.EcoOmniCpuPlanMixin {
        private final ListCraftingInventory stock = new ListCraftingInventory(key -> {});
        private com.atir.molecularmanipulator.crafting.OmniExactCraftingState state;
        CpuFixture() throws ReflectiveOperationException {
            var field = com.atir.molecularmanipulator.mixin.EcoOmniCpuPlanMixin.class.getDeclaredField("cluster");
            field.setAccessible(true);
            field.set(this, new appeng.me.cluster.implementations.CraftingCPUCluster(net.minecraft.core.BlockPos.ZERO, net.minecraft.core.BlockPos.ZERO));
        }
        @Override public com.atir.molecularmanipulator.crafting.OmniExactCraftingState ecoOmni$exactState() { return state; }
        @Override public void ecoOmni$installExactState(com.atir.molecularmanipulator.crafting.OmniExactCraftingState state) { this.state = state; }
        @Override public Map<IPatternDetails, Object> ecoOmni$tasks() { return Map.of(); }
        @Override public BigInteger ecoOmni$physicalAmount(AEKey key) { return BigInteger.valueOf(stock.list.get(key)); }
        @Override public void ecoOmni$storePhysical(AEKey key, BigInteger amount) { stock.insert(key, amount.longValueExact(), Actionable.MODULATE); }
        @Override public void ecoOmni$queueEmitted(AEKey key, BigInteger amount) { fail("Unexpected emission"); }
        @Override public void ecoOmni$restoreRefundSnapshot(CompoundTag data, HolderLookup.Provider registries) {
            throw new IllegalArgumentException("Missing physical item");
        }
    }

    private record Fixture(EcoOmniPlanExecution execution, ReflectiveRuntime runtime) {}
    private static Fixture fixture(ECOExecutionPlan plan, Map<Integer, BigInteger> totals,
            Map<AEKey, BigInteger> stock, Map<AEKey, BigInteger> emitted) {
        var bindings = new LinkedHashMap<Integer, IPatternDetails>();
        plan.tasks().forEach(task -> bindings.put(task.id(), task.pattern()));
        var runtime = new ReflectiveRuntime(plan, bindings, totals);
        var execution = new EcoOmniPlanExecution(plan, bindings, totals, totals, stock, emitted,
                Set.of(ResourceLocation.parse("test:fuzzy")), BigInteger.TEN.pow(25), UUID.randomUUID(), runtime.runtime, runtime);
        return new Fixture(execution, runtime);
    }

    private static ECOExecutionPlan.TaskSpec task(int id, IPatternDetails pattern, long count, int phase) {
        return new ECOExecutionPlan.TaskSpec(id, PlanIdentity.patternIdentityFor(pattern), pattern,
                ECOExecutionPlan.PatternRuntimeInfo.from(pattern), count, phase, ECOExecutionPlan.TaskKind.DAG);
    }
    private static ECOExecutionPlan.PhaseSpec phase(int id, Type type, List<Integer> tasks,
            List<ECOExecutionPlan.ExecutionStep> steps, List<Integer> dependencies, Map<Integer, Long> firings, Map<AEKey, Long> seed) {
        return new ECOExecutionPlan.PhaseSpec(id, id, type, tasks, steps, dependencies, firings, seed);
    }
    private static ECOExecutionPlan plan(List<ECOExecutionPlan.TaskSpec> tasks, List<ECOExecutionPlan.PhaseSpec> phases, ExecutionMode mode) {
        var signatureTasks = new LinkedHashMap<PlanIdentity.PatternIdentity, Long>();
        tasks.forEach(task -> signatureTasks.merge(task.identity(), task.totalCount(), Math::addExact));
        var signature = new PlanIdentity.Signature(GOLD, 1, signatureTasks, Map.of(IRON, 1L), Map.of(), Map.of());
        var schedulePhases = new ArrayList<ECOExecutionSchedule.ComponentExecutionPhase>();
        for (var phase : phases) {
            var patterns = new java.util.LinkedHashSet<IPatternDetails>();
            phase.taskIds().forEach(id -> patterns.add(tasks.get(id).pattern()));
            schedulePhases.add(new ECOExecutionSchedule.ComponentExecutionPhase(phase.componentId(), phase.type(), patterns, List.of()));
        }
        return new ECOExecutionPlan(signature, mode, tasks, phases, new ECOExecutionSchedule(schedulePhases));
    }
    private static IPatternDetails pattern(net.minecraft.world.item.Item definition, AEKey inputKey, long amount, long multiplier) {
        return new IPatternDetails() {
            @Override public AEItemKey getDefinition() { return AEItemKey.of(definition); }
            @Override public IInput[] getInputs() { return new IInput[]{new IInput() {
                @Override public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(inputKey, amount)}; }
                @Override public long getMultiplier() { return multiplier; }
                @Override public boolean isValid(AEKey key, Level level) { return IRON.equals(key) || GOLD.equals(key); }
                @Override public AEKey getRemainingKey(AEKey key) { return null; }
            }}; }
            @Override public List<GenericStack> getOutputs() { return List.of(new GenericStack(GOLD, 1)); }
            @Override public boolean supportsPushInputsToExternalInventory() { return true; }
        };
    }
    private static KeyCounter[] unit(AEKey key, long count) {
        var slot = new KeyCounter(); slot.add(key, count); return new KeyCounter[]{slot};
    }

    /** Uses ECO's own live TaskProgress binding in place of our taskRemaining injection. */
    private static final class ReflectiveRuntime implements EcoOmniPlanRuntimeAccess {
        private final ECOExecutionRuntime runtime;
        private final Object progress;
        private final Map<Integer, BigInteger> totals;
        private IntFunction<BigInteger> remaining;
        private static final Class<?> PROGRESS;
        static {
            try { PROGRESS = Class.forName("cn.dancingsnow.neoecoae.crafting.execution.ExecutingCraftingJob$TaskProgress"); }
            catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        ReflectiveRuntime(ECOExecutionPlan plan, Map<Integer, IPatternDetails> bindings, Map<Integer, BigInteger> totals) {
            this.totals = totals;
            progress = Array.newInstance(PROGRESS, plan.tasks().size());
            try {
                var constructor = PROGRESS.getDeclaredConstructor(); constructor.setAccessible(true);
                for (int id = 0; id < plan.tasks().size(); id++) {
                    var value = constructor.newInstance();
                    Array.set(progress, id, value);
                    method(PROGRESS, "setExact", BigInteger.class, BigInteger.class).invoke(value, totals.get(id), totals.get(id));
                }
                var runtimeConstructor = ECOExecutionRuntime.class.getDeclaredConstructor(ECOExecutionPlan.class, Map.class, progress.getClass());
                runtimeConstructor.setAccessible(true);
                runtime = runtimeConstructor.newInstance(plan, bindings, progress);
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        private ReflectiveRuntime(ECOExecutionRuntime runtime, Object progress, Map<Integer, BigInteger> totals) {
            this.runtime = runtime; this.progress = progress; this.totals = totals;
        }
        static ReflectiveRuntime restore(ECOExecutionPlan plan, Map<Integer, IPatternDetails> bindings,
                Map<Integer, BigInteger> remaining, CompoundTag tag) {
            var totals = new LinkedHashMap<Integer, BigInteger>();
            plan.tasks().forEach(task -> totals.put(task.id(), BigInteger.valueOf(task.totalCount())));
            var base = new ReflectiveRuntime(plan, bindings, totals);
            base.ecoOmni$bindRemaining(id -> remaining.getOrDefault(id, BigInteger.ZERO));
            try {
                var runtime = (ECOExecutionRuntime) method(ECOExecutionRuntime.class, "fromNBT", ECOExecutionPlan.class,
                        Map.class, base.progress.getClass(), CompoundTag.class, HolderLookup.Provider.class)
                        .invoke(null, plan, bindings, base.progress, tag, REGISTRIES);
                // Production binds the same task-ID progress during construction through RuntimeScope.
                return new ReflectiveRuntime(runtime, base.progress, remaining);
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        private static Method method(Class<?> type, String name, Class<?>... types) {
            try { var method = type.getDeclaredMethod(name, types); method.setAccessible(true); return method; }
            catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        private Object invoke(String name, Class<?>[] types, Object... args) {
            try { return method(ECOExecutionRuntime.class, name, types).invoke(runtime, args); }
            catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        private void synchronizeProgress() {
            try {
                for (int id = 0; id < totals.size(); id++) method(PROGRESS, "setExact", BigInteger.class, BigInteger.class)
                        .invoke(Array.get(progress, id), totals.get(id), remaining.apply(id));
            } catch (ReflectiveOperationException exception) { throw new AssertionError(exception); }
        }
        @Override public void ecoOmni$bindRemaining(IntFunction<BigInteger> remaining) { this.remaining = remaining; synchronizeProgress(); }
        @Override public BigInteger ecoOmni$exactAllowance(DispatchCandidate candidate, BigInteger remaining) {
            return (BigInteger) invoke("exactAllowance", new Class<?>[]{DispatchCandidate.class, BigInteger.class}, candidate, remaining);
        }
        @Override public void ecoOmni$acceptedExact(DispatchCandidate candidate, BigInteger count, KeyCounter[] inputs) {
            synchronizeProgress();
            invoke("onAcceptedExact", new Class<?>[]{DispatchCandidate.class, BigInteger.class, KeyCounter[].class}, candidate, count, inputs);
        }
        @SuppressWarnings("unchecked") @Override public Map<AEKey, Long> ecoOmni$protectedSeed(DispatchCandidate candidate) {
            return (Map<AEKey, Long>) invoke("protectedStartupSeed", new Class<?>[]{DispatchCandidate.class}, candidate);
        }
        @Override public long ecoOmni$limitCycleBatch(DispatchCandidate candidate, KeyCounter[] inputs, ListCraftingInventory inventory, long upper) {
            return (long) invoke("limitCycleBatch", new Class<?>[]{DispatchCandidate.class, KeyCounter[].class, ListCraftingInventory.class, long.class}, candidate, inputs, inventory, upper);
        }
        @Override public void ecoOmni$write(CompoundTag tag, HolderLookup.Provider registries) {
            invoke("writeToNBT", new Class<?>[]{CompoundTag.class, HolderLookup.Provider.class}, tag, registries);
        }
    }
}
