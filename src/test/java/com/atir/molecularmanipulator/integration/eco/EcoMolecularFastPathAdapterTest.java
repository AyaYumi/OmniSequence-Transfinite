package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimization;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationAdapter;
import cn.dancingsnow.neoecoae.api.me.pattern.ECOExecutionOptimizationRegistry;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.execution.batch.ECOBatchExecutor;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOExtractedPatternExecution;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathKey;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOFastPathStacks;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.atir.molecularmanipulator.integration.eco.EcoMolecularTestPattern.*;
import static org.junit.jupiter.api.Assertions.*;

class EcoMolecularFastPathAdapterTest {
    static { EcoMolecularTestPattern.bootstrap(); }
    @Test
    void publicPreparationUsesActualEcoEligibilityAndSingleUseLease() throws Exception {
        var registration = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                "molecularmanipulator", "unit_molecular_assembler");
        ECOExecutionOptimizationRegistry.register(registration, 1000,
                new ECOExecutionOptimizationAdapter() {
                    public boolean supports(IPatternDetails pattern) { return pattern instanceof EcoMolecularTestPattern; }
                    public ECOExecutionOptimization describe(IPatternDetails pattern) {
                        return ECOExecutionOptimization.VERIFIED_ASSEMBLER;
                    }
                });
        try {
            var catalyst = AEItemKey.of(Items.STICK);
            var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
            var context = context(pattern);
            assertTrue(context.execution().canUseFastPath(), context.execution().fastPathReason());
            var provider = new Provider();
            var preparation = EcoMolecularFastPathAdapter.prepare(provider, context);
            assertNotNull(preparation);
            assertTrue(preparation.statefulCalculatorRequired());
            var inventory = inventory(catalyst, 1, 19);
            var contract = preparation.statefulCalculator().prepareBatch(inventory, 19);
            assertNotNull(contract);
            var batch = new ECOFastPathDispatchProvider.Batch(19, contract.inputs(),
                    List.of(new GenericStack(OUTPUT, 19)), contract.remainders());
            assertTrue(ECOBatchExecutor.executePrepared(inventory, batch.inputTotal(), Map.of(), new Energy(),
                    () -> preparation.push(batch)));
            assertFalse(preparation.push(batch));
            assertEquals(1, provider.pushes);
            assertEquals(context.craftingJobId(), provider.jobId);
            assertTrue(inventory.list.isEmpty());
        } finally {
            ECOExecutionOptimizationRegistry.unregister(registration);
        }
    }

    @Test
    void realEcoCalculatorCountsInvariantOnceAndReturnsItOnce() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
        var model = model(pattern);
        var calculator = calculator(pattern, model);
        var inventory = inventory(catalyst, 1, 3_000_000_000L);
        var contract = calculator.prepareBatch(inventory, 3_000_000_000L);
        assertNotNull(contract);
        assertEquals(3_000_000_000L, contract.craftCount());
        assertEquals(1, counter(contract.inputs()).get(catalyst));
        assertEquals(3_000_000_000L, counter(contract.inputs()).get(MATERIAL));
        assertEquals(1, counter(contract.remainders()).get(catalyst));
        assertEquals(1, inventory.list.get(catalyst), "Planning must not extract");
    }

    @Test
    void realEcoCalculatorPreservesMaxDamageTerminalToolAndIdentity() throws Exception {
        var identity = UUID.randomUUID();
        var tool = tool(2, 5, identity);
        var pattern = new EcoMolecularTestPattern(tool, key -> worn(key, 6));
        var model = model(pattern);
        var calculator = calculator(pattern, model);
        var first = calculator.prepareBatch(inventory(tool, 2, 7), 7);
        assertNotNull(first);
        assertEquals(7, first.craftCount());
        assertEquals(2, counter(first.inputs()).get(tool));
        var finalTool = (AEItemKey) worn(worn(worn(tool, 6), 6), 6);
        assertEquals(5, finalTool.toStack().getDamageValue());
        assertEquals(1, counter(first.remainders()).get(finalTool));
        assertEquals(identity, finalTool.toStack().get(net.minecraft.core.component.DataComponents.CUSTOM_DATA)
                .copyTag().getUUID("tool_identity"));
        assertTrue(model.reusablePlan(7).matchesProvidedInputs(model.inputs(7)));
        var exhausted = calculator.prepareBatch(inventory(tool, 2, 100), 100);
        assertEquals(8, exhausted.craftCount());
        assertTrue(exhausted.remainders().isEmpty());
    }

    @Test
    void realEcoNativePoolCombinesDifferentDamageStatesWithoutLosingTools() throws Exception {
        var identity = UUID.randomUUID();
        var selected = tool(2, 5, identity);
        var worn = tool(4, 5, identity);
        var fresh = tool(0, 5, identity);
        var pattern = new EcoMolecularTestPattern(selected, key -> worn(key, 5));
        var context = context(pattern);
        var model = model(pattern);
        assertInstanceOf(cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECODurabilityBatchModel.class,
                model.calculatorModel());
        var inventory = inventory(selected, 1, 8);
        inventory.list.add(worn, 3);
        inventory.list.add(fresh, 1);
        var contract = calculator(pattern, model).prepareBatch(inventory, 8);
        assertNotNull(contract);
        assertEquals(8, contract.craftCount());
        assertEquals(3, counter(contract.inputs()).get(worn));
        assertEquals(1, counter(contract.inputs()).get(fresh));
        assertEquals(1, counter(contract.remainders()).get(selected));
        var batch = new ECOFastPathDispatchProvider.Batch(8, contract.inputs(),
                List.of(new GenericStack(OUTPUT, 8)), contract.remainders());
        var delivery = model.delivery(context, batch);
        assertNotNull(delivery);
        assertNotNull(delivery.plan());
        assertTrue(delivery.plan().matchesProvidedInputs(delivery.inputs()));
        assertEquals(3, delivery.inputs()[0].get(worn));
        assertEquals(1, delivery.inputs()[0].get(selected));
        assertEquals(1, delivery.inputs()[0].get(fresh));
        assertEquals(1, delivery.plan().expectedRemainders(8).get(selected));
        var provider = new Provider();
        assertTrue(ECOBatchExecutor.executePrepared(inventory, contract.inputs(), Map.of(), new Energy(),
                () -> EcoMolecularFastPathAdapter.dispatch(provider, context, model, batch, 8)));
        assertEquals(context.craftingJobId(), provider.jobId);
        assertTrue(inventory.list.isEmpty());
    }

    @Test
    void mixedPoolLastUseSeedAndEveryBatchBoundaryMatchActualLease() throws Exception {
        var identity = UUID.randomUUID();
        var seed = tool(4, 5, identity);
        var middle = tool(3, 5, identity);
        var fresh = tool(0, 5, identity);
        var foreign = tool(0, 5, UUID.randomUUID());
        var pattern = new EcoMolecularTestPattern(seed, key -> worn(key, 5));
        var context = context(pattern);
        var model = model(pattern);
        for (int requested = 2; requested <= 12; requested++) {
            var stock = inventory(seed, 1, 12);
            stock.list.add(middle, 2);
            stock.list.add(fresh, 1);
            stock.list.add(foreign, 10);
            var contract = calculator(pattern, model).prepareBatch(stock, requested);
            assertNotNull(contract);
            assertEquals(Math.min(requested, 10), contract.craftCount());
            var batch = new ECOFastPathDispatchProvider.Batch(contract.craftCount(), contract.inputs(),
                    List.of(new GenericStack(OUTPUT, contract.craftCount())), contract.remainders());
            var delivery = model.delivery(context, batch);
            assertNotNull(delivery, "requested=" + requested);
            assertTrue(delivery.plan().matchesProvidedInputs(delivery.inputs()));
            assertTrue(EcoMolecularRemainderContext.matches(counter(contract.remainders()),
                    delivery.plan().expectedRemainders(contract.craftCount())));
            assertEquals(0, counter(contract.inputs()).get(foreign));
            assertTrue(ECOBatchExecutor.executePrepared(stock, contract.inputs(), Map.of(), new Energy(),
                    () -> EcoMolecularFastPathAdapter.dispatch(new Provider(), context, model, batch, 12)));
            assertEquals(10, stock.list.get(foreign), "Other component identities must remain in stock");
        }
    }

    @Test
    void realEcoLeaseRollsBackRejectionAndRetainsAcceptance() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
        var context = context(pattern);
        var model = model(pattern);
        var batch = batch(context, model, 23);
        var stock = inventory(catalyst, 1, 23);
        var provider = new Provider();
        provider.reject = true;
        var rejectedEnergy = new Energy();
        assertFalse(ECOBatchExecutor.executePrepared(stock, batch.inputTotal(), Map.of(), rejectedEnergy,
                () -> EcoMolecularFastPathAdapter.dispatch(provider, context, model, batch, 23)));
        assertEquals(1, stock.list.get(catalyst));
        assertEquals(23, stock.list.get(MATERIAL));
        assertTrue(rejectedEnergy.refunded);
        assertFalse(rejectedEnergy.committed);
        assertNull(MolecularBatchDispatchContext.current(pattern, provider.lastInputs));
        assertNull(EcoMolecularRemainderContext.expected(pattern, provider.lastInputs));

        provider.reject = false;
        var acceptedEnergy = new Energy();
        assertTrue(ECOBatchExecutor.executePrepared(stock, batch.inputTotal(), Map.of(), acceptedEnergy,
                () -> EcoMolecularFastPathAdapter.dispatch(provider, context, model, batch, 23)));
        assertEquals(context.craftingJobId(), provider.jobId);
        assertEquals(23, provider.planCount);
        assertTrue(stock.list.isEmpty());
        assertTrue(acceptedEnergy.committed);
        assertFalse(acceptedEnergy.refunded);
        assertEquals(0, provider.flushes, "Provider push must await CPU accounting before returning output");
        provider.flushOutputsAfterCpuAccounting();
        assertEquals(1, provider.flushes);
    }

    @Test
    void malformedContractsNeverReachProviderAndOverflowIsExact() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
        var context = context(pattern);
        var model = model(pattern);
        var correct = batch(context, model, 5);
        var provider = new Provider();
        var extra = new java.util.ArrayList<>(correct.inputTotal());
        extra.add(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1));
        assertFalse(EcoMolecularFastPathAdapter.dispatch(provider, context, model,
                new ECOFastPathDispatchProvider.Batch(5, extra, correct.outputTotal(), correct.remainingTotal()), 5));
        assertFalse(EcoMolecularFastPathAdapter.dispatch(provider, context, model,
                new ECOFastPathDispatchProvider.Batch(5, correct.inputTotal(), correct.outputTotal(), List.of()), 5));
        assertFalse(EcoMolecularFastPathAdapter.dispatch(provider, context, model,
                new ECOFastPathDispatchProvider.Batch(6, correct.inputTotal(), correct.outputTotal(), correct.remainingTotal()), 5));
        assertEquals(0, provider.pushes);
        assertEquals(Long.MAX_VALUE, counter(model.batchInputs(List.of(), Long.MAX_VALUE)).get(MATERIAL));
    }

    @Test
    void ambiguousOwnershipRetainsEcoLeaseAndClosesContexts() throws Exception {
        var catalyst = AEItemKey.of(Items.STICK);
        var pattern = new EcoMolecularTestPattern(catalyst, key -> key);
        var context = context(pattern);
        var model = model(pattern);
        var batch = batch(context, model, 4);
        var inventory = inventory(catalyst, 1, 4);
        var provider = new Provider();
        provider.fail = true;
        var energy = new Energy();
        assertThrows(ECOIndeterminateBatchException.class, () ->
                ECOBatchExecutor.executePrepared(inventory, batch.inputTotal(), Map.of(), energy,
                        () -> EcoMolecularFastPathAdapter.dispatch(provider, context, model, batch, 4)));
        assertTrue(inventory.list.isEmpty());
        assertTrue(energy.committed);
        assertNull(MolecularBatchDispatchContext.current(pattern, provider.lastInputs));
        assertNull(EcoMolecularRemainderContext.expected(pattern, provider.lastInputs));
    }

    @Test
    void foreignSameItemMutationAndRemainderMismatchFallBack() throws Exception {
        var key = AEItemKey.of(Items.STICK);
        var mutatedStack = key.toStack();
        mutatedStack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("mutation"));
        var unsupported = new EcoMolecularTestPattern(key, ignored -> AEItemKey.of(mutatedStack));
        assertNull(EcoMolecularBatchModel.analyze(unsupported, unsupported.first(), level(), unsupported.remainders()));
        var invariant = new EcoMolecularTestPattern(key, actual -> actual);
        assertNull(EcoMolecularBatchModel.analyze(invariant, invariant.first(), level(), new KeyCounter()));
        var container = new EcoMolecularTestPattern(CONTAINER, ignored -> RETURNED);
        assertFalse(model(container).reusable());
        assertEquals(700, counter(model(container).batchRemainders(List.of(), 700)).get(RETURNED));
    }

    private static EcoMolecularBatchModel model(EcoMolecularTestPattern pattern) throws Exception {
        var result = EcoMolecularBatchModel.analyze(pattern, pattern.first(), level(), pattern.remainders());
        assertNotNull(result);
        return result;
    }

    private static ECOBatchDispatchContext context(EcoMolecularTestPattern pattern) throws Exception {
        return ECOBatchDispatchContext.create(pattern, pattern.first(), EcoMolecularTestPattern.counter(OUTPUT, 1),
                pattern.remainders(), level(), UUID.randomUUID());
    }

    private static ECOFastPathDispatchProvider.Batch batch(ECOBatchDispatchContext context,
            EcoMolecularBatchModel model, long crafts) {
        return new ECOFastPathDispatchProvider.Batch(crafts,
                model.batchInputs(context.inputItems(), crafts), List.of(new GenericStack(OUTPUT, crafts)),
                model.batchRemainders(context.containerItems(), crafts));
    }

    private static ListCraftingInventory inventory(AEKey reusable, long tools, long material) {
        var result = new ListCraftingInventory(ignored -> { });
        result.list.add(reusable, tools);
        result.list.add(MATERIAL, material);
        return result;
    }

    private static KeyCounter counter(List<GenericStack> stacks) {
        return EcoMolecularBatchModel.counter(stacks);
    }

    private static ECOStatefulBatchCalculator calculator(EcoMolecularTestPattern pattern,
            EcoMolecularBatchModel model) throws Exception {
        // Create a normalized execution through ECO's test seam. No cache or
        // calculator implementation is mocked: all capacity and lease code is
        // the actual 21.2.9 binary. Eligibility of real patterns is a GameTest.
        var key = ECOFastPathKey.of(pattern, pattern.first(), null,
                AE2PatternIntrospection.reloadGeneration()).orElseThrow();
        var normalized = ECOExtractedPatternExecution.class.getDeclaredMethod("ofNormalizedComponents",
                ECOFastPathKey.class, List.class, List.class, List.class);
        normalized.setAccessible(true);
        var outputs = pattern.getOutputs();
        var remainders = ECOFastPathStacks.copyCounter(pattern.remainders());
        var inputs = ECOFastPathStacks.copyCounters(pattern.first());
        var execution = (ECOExtractedPatternExecution) normalized.invoke(null, key, outputs, remainders, inputs);
        var cache = new ECOCraftingFastPathCache(16);
        cache.putPositive(key, outputs, remainders, inputs, 0, model.calculatorModel());
        var lookup = cache.lookup(execution, 0, AE2PatternIntrospection.reloadGeneration());
        assertTrue(lookup.isVerified(), lookup.reason());
        var result = ECOStatefulBatchCalculator.create(lookup.recipe(), execution);
        assertNotNull(result);
        return result;
    }

    private static final class Energy implements ECOFastPathFacade.Reservation {
        boolean committed;
        boolean refunded;
        public void commit() { committed = true; }
        public void refund() { refunded = true; }
    }

    private static final class Provider implements ICraftingProvider, MolecularBatchCraftingProvider {
        boolean reject;
        boolean fail;
        int pushes;
        int flushes;
        long planCount;
        UUID jobId;
        KeyCounter[] lastInputs;
        public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        public boolean isBusy() { return false; }
        public boolean molecularmanipulator$supportsBatching(IPatternDetails pattern) { return true; }
        public boolean molecularmanipulator$supportsReusableBatching(IPatternDetails pattern) { return true; }
        public void molecularmanipulator$flushOutputsAfterCpuAccounting() { flushes++; }
        public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) {
            pushes++;
            lastInputs = inputs;
            var context = MolecularBatchDispatchContext.current(pattern, inputs);
            assertNotNull(context);
            assertNotNull(context.reusablePlan());
            assertTrue(context.reusablePlan().matchesProvidedInputs(inputs));
            assertNotNull(EcoMolecularRemainderContext.expected(pattern, inputs));
            jobId = context.craftingId();
            planCount = context.craftCount();
            if (reject) return false;
            for (var holder : inputs) holder.clear();
            if (fail) throw new IllegalStateException("after acquiring ownership");
            return true;
        }
    }
}
