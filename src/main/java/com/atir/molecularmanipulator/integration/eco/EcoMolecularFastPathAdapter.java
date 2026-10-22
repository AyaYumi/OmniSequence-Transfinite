package com.atir.molecularmanipulator.integration.eco;

import appeng.api.networking.crafting.ICraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.compat.ae2.AE2PatternIntrospection;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOCraftingFastPathCache;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOStatefulBatchCalculator;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/** ECO 21.2.9 optional integration. Loaded only through the gated mixins. */
public final class EcoMolecularFastPathAdapter {
    private EcoMolecularFastPathAdapter() { }

    @Nullable
    public static ECOFastPathDispatchProvider.Preparation prepare(
            ICraftingProvider provider, ECOBatchDispatchContext context) {
        if (context == null || context.level() == null || context.craftingJobId() == null
                || !MolecularBatchCraftingProvider.supportsReusable(provider, context.pattern())) {
            return null;
        }
        try {
            var model = EcoMolecularBatchModel.analyze(context);
            if (model == null) {
                return null;
            }
            var execution = context.execution();
            if (!execution.canUseFastPath() || execution.key() == null) {
                return null;
            }
            // The public calculator requires a cache-issued credential bound
            // to this precise execution. Issue our independently checked input
            // model; the molecular batcher verifies actual assembly and all
            // remainders again before acquiring ownership. The native ECO
            // pool is admitted only after proving its breaking convention.
            var cache = new ECOCraftingFastPathCache(ECOCraftingFastPathCache.MIN_CACHE_SIZE);
            long tick = context.level().getGameTime();
            cache.putPositive(execution.key(), context.outputs(), context.containerItems(),
                    context.inputItems(), tick, model.calculatorModel());
            var lookup = cache.lookup(execution, tick, AE2PatternIntrospection.reloadGeneration());
            if (!lookup.isVerified()) {
                return null;
            }
            var calculator = ECOStatefulBatchCalculator.create(lookup.recipe(), execution);
            if (calculator == null) {
                return null;
            }
            long capacity = Math.min(calculator.arithmeticBatchLimit(),
                    MolecularBatchCraftingProvider.getBatchLimit(provider, context.pattern()));
            if (capacity < 2) {
                return null;
            }
            var submitted = new AtomicBoolean();
            return new ECOFastPathDispatchProvider.Preparation(capacity, calculator, true,
                    batch -> submitted.compareAndSet(false, true)
                            && dispatch(provider, context, model, batch, capacity));
        } catch (RuntimeException exception) {
            // Preparation owns no materials. Let ECO try the existing route.
            return null;
        }
    }

    static boolean dispatch(ICraftingProvider provider, ECOBatchDispatchContext context,
            EcoMolecularBatchModel model, ECOFastPathDispatchProvider.Batch batch, long capacity) {
        if (batch == null || batch.craftCount() < 2 || batch.craftCount() > capacity
                || !batch.exactInputTotal().isEmpty()
                || !MolecularBatchCraftingProvider.supportsReusable(provider, context.pattern())) {
            return false;
        }
        var count = batch.craftCount();
        var delivery = model.delivery(context, batch);
        if (delivery == null) {
            return false;
        }
        var inputs = delivery.inputs();
        var plan = delivery.plan();
        var expectedInputs = new appeng.api.stacks.KeyCounter();
        for (var holder : inputs) {
            for (var entry : holder) {
                Math.addExact(expectedInputs.get(entry.getKey()), entry.getLongValue());
                expectedInputs.add(entry.getKey(), entry.getLongValue());
            }
        }
        var expectedRemainders = plan == null
                ? EcoMolecularBatchModel.counter(model.batchRemainders(context.containerItems(), count))
                : plan.expectedRemainders(count);
        var expectedOutputs = new appeng.api.stacks.KeyCounter();
        for (var stack : context.outputs()) {
            var amount = Math.multiplyExact(stack.amount(), count);
            Math.addExact(expectedOutputs.get(stack.what()), amount);
            expectedOutputs.add(stack.what(), amount);
        }
        if (!EcoMolecularRemainderContext.matches(expectedInputs,
                    EcoMolecularBatchModel.counter(batch.inputTotal()))
                || !EcoMolecularRemainderContext.matches(expectedOutputs,
                    EcoMolecularBatchModel.counter(batch.outputTotal()))
                || !EcoMolecularRemainderContext.matches(expectedRemainders,
                    EcoMolecularBatchModel.counter(batch.remainingTotal()))) {
            return false;
        }
        try (var molecular = MolecularBatchDispatchContext.open(context.craftingJobId(),
                context.pattern(), inputs, context.inputCounters(), count, plan);
                var remainders = EcoMolecularRemainderContext.open(context.pattern(),
                        inputs, expectedRemainders)) {
            try {
                // ECO already holds the entire material lease here. The
                // machine either rejects unchanged or persists every input.
                // Its normal post-accounting/tick output path returns results.
                return provider.pushPattern(context.pattern(), inputs);
            } catch (RuntimeException exception) {
                // A provider exception may follow an ownership commit. ECO's
                // explicit indeterminate result retains the lease and suspends
                // the CPU instead of replaying potentially owned materials.
                throw new ECOIndeterminateBatchException(
                        "Molecular batch provider ownership is uncertain", exception);
            }
        }
    }
}
