package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.integration.ae2.ECOExternalProviderBlocking;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Adapts one selected ECO provider. The common CPU owns materials, power and receipts. */
public final class EcoCpuDispatch {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private EcoCpuDispatch() {}

    public static @Nullable OmniCapacityAdmission prepare(OmniCapacityRequest request) {
        var provider = request.provider();
        if (MolecularBatchCraftingProvider.requiresSerialDispatch(provider)
                || ECOExternalProviderBlocking.isEnabled(provider)) return null;

        var fastPath = ECOFastPathFacade.resolveProvider(provider);
        var upper = request.maximumCrafts();
        var fastContext = fastPath == null ? null : context(request);
        if (fastPath != null) {
            if (fastContext.containerItems().isEmpty()) {
                // ExactPreparation records a fixed count and expects the caller's material lease.
                var exact = fastPath.eco$prepareExactFastPath(fastContext, upper);
                if (exact != null) {
                    var count = exact.craftCount();
                    if (count.compareTo(upper) > 0) throw new IllegalArgumentException("ECO exceeded the requested capacity");
                    return new Admission(count, crafts -> crafts.equals(count) && exact.dispatch().getAsBoolean());
                }
            }
            var prepared = fastPath.eco$prepareFastPath(fastContext);
            if (prepared != null && !prepared.statefulCalculatorRequired() && prepared.statefulCalculator() == null) {
                var count = BigInteger.valueOf(linearLimit(fastContext, upper.min(BigInteger.valueOf(prepared.capacity()))));
                return new Admission(count, crafts -> {
                    long copies = crafts.longValueExact();
                    return prepared.push(new ECOFastPathDispatchProvider.Batch(copies,
                            scale(fastContext.inputItems(), copies), scale(fastContext.outputs(), copies),
                            scale(fastContext.containerItems(), copies)));
                });
            }
        }

        // A stateful preparation may substitute physical inputs or expect different returns.
        // Only an independent, linear entry can use the common CPU's reserved receipt.
        var parallel = ECOParallelCraftingProviders.find(provider);
        if (parallel == null) return null;
        int slots = parallel.eco$getAvailableParallelSlots();
        if (slots <= 0) return OmniCapacityAdmission.UNAVAILABLE;
        var parallelContext = fastContext == null ? context(request) : fastContext;
        var count = BigInteger.valueOf(linearLimit(parallelContext, upper.min(BigInteger.valueOf(slots))));
        return new Admission(count, crafts -> {
            long copies = crafts.longValueExact();
            return parallel.eco$pushPatternBatch(request.pattern(), scaleInputs(parallelContext, copies), copies, request.jobId());
        });
    }

    private static ECOBatchDispatchContext context(OmniCapacityRequest request) {
        return ECOBatchDispatchContext.create(request.pattern(), request.unitInputs(), request.unitOutputs(),
                request.unitRemainders(), request.level(), request.jobId());
    }

    public static boolean pushSingle(ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] inputs, @Nullable UUID jobId, Supplier<Boolean> ordinary) {
        if (jobId == null) return ordinary.get();
        var registered = ECOParallelCraftingProviders.pushForJob(provider, pattern, inputs, jobId);
        if (registered != null) return registered;
        if (provider instanceof ECOJobAwareCraftingProvider aware) return aware.pushPattern(pattern, inputs, jobId);
        var parallel = ECOParallelCraftingProviders.find(provider);
        return parallel != null ? parallel.eco$pushPatternBatch(pattern, inputs, 1, jobId) : ordinary.get();
    }

    private static long linearLimit(ECOBatchDispatchContext context, BigInteger requested) {
        long limit = requested.min(LONG_MAX).longValueExact();
        for (var stack : context.inputItems()) limit = Math.min(limit, Long.MAX_VALUE / stack.amount());
        for (var stack : context.outputs()) limit = Math.min(limit, Long.MAX_VALUE / stack.amount());
        for (var stack : context.containerItems()) limit = Math.min(limit, Long.MAX_VALUE / stack.amount());
        return limit;
    }

    private static List<GenericStack> scale(List<GenericStack> unit, long copies) {
        if (copies == 1 || unit.isEmpty()) return unit;
        var result = new ArrayList<GenericStack>(unit.size());
        for (var stack : unit) result.add(new GenericStack(stack.what(), Math.multiplyExact(stack.amount(), copies)));
        return result;
    }

    private static KeyCounter[] scaleInputs(ECOBatchDispatchContext context, long copies) {
        // inputCounters() materializes fresh holders so the prototype stays immutable.
        var inputs = context.inputCounters();
        if (copies != 1) for (var slot : inputs) {
            for (var entry : slot) slot.set(entry.getKey(), Math.multiplyExact(entry.getLongValue(), copies));
        }
        return inputs;
    }

    private static final class Admission implements OmniCapacityAdmission {
        private final BigInteger capacity;
        private final Predicate<BigInteger> push;
        private boolean closed;

        private Admission(BigInteger capacity, Predicate<BigInteger> push) {
            this.capacity = capacity;
            this.push = push;
        }

        @Override public BigInteger maximumCrafts() { return capacity; }
        @Override public boolean acceptsPartial() { return false; }

        @Override public BigInteger submit(BigInteger crafts) {
            if (crafts.signum() <= 0 || crafts.compareTo(capacity) > 0) return BigInteger.ZERO;
            if (closed) throw new IllegalStateException("ECO admission already closed");
            closed = true;
            return push.test(crafts) ? crafts : BigInteger.ZERO;
        }

        @Override public void close() { closed = true; }
    }
}

