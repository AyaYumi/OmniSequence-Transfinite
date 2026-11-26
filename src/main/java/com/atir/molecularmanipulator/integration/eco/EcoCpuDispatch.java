package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOJobAwareCraftingProvider;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.grid.crafting.execution.fastpath.classify.ECORecipeClassifier;
import cn.dancingsnow.neoecoae.integration.ae2.ECOExternalProviderBlocking;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jetbrains.annotations.Nullable;

/** Adapts one selected ECO provider. The common CPU owns materials, power and receipts. */
public final class EcoCpuDispatch {
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
            if (prepared != null) {
                long limit = outputLimit(request, fastContext, prepared.capacity());
                if (limit == 0) return OmniCapacityAdmission.UNAVAILABLE;
                var admission = prepareLinear(fastContext, prepared, limit);
                if (admission != null) return admission;
            }
        }

        // A different stateful material contract cannot use the CPU's linear reservation.
        // An independent parallel endpoint can still offer its own capacity.
        var parallel = ECOParallelCraftingProviders.find(provider);
        if (parallel == null) return null;
        int slots = parallel.eco$getAvailableParallelSlots();
        if (slots <= 0) return OmniCapacityAdmission.UNAVAILABLE;
        var parallelContext = fastContext == null ? context(request) : fastContext;
        long limit = outputLimit(request, parallelContext, slots);
        var count = BigInteger.valueOf(stackLimit(parallelContext.inputItems(), limit));
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

    private static @Nullable OmniCapacityAdmission prepareLinear(ECOBatchDispatchContext context,
            ECOFastPathDispatchProvider.Preparation prepared, long limit) {
        long inputLimit = stackLimit(context.inputItems(), limit);
        var calculator = prepared.statefulCalculator();
        if (calculator != null) {
            // Calculate against exactly the materials this CPU can reserve. The provider may
            // choose a smaller batch, different tools, or a different set of final remainders.
            var stock = new ListCraftingInventory(key -> {});
            for (var stack : context.inputItems()) {
                stock.list.add(stack.what(), stack.amount() * inputLimit);
            }
            var contract = calculator.prepareBatch(stock, inputLimit);
            if (contract == null) return null;
            long copies = contract.craftCount();
            if (copies <= 0 || copies > inputLimit
                    || !matchesScaled(context.inputItems(), copies, contract.inputs())
                    || !matchesScaled(context.containerItems(), copies, contract.remainders())) return null;
            var batch = new ECOFastPathDispatchProvider.Batch(copies, contract.inputs(),
                    scale(context.outputs(), copies), contract.remainders());
            var count = BigInteger.valueOf(copies);
            return new Admission(count, crafts -> crafts.equals(count) && prepared.push(batch));
        }

        // ECO's own planner uses the ordinary vector for NORMAL even when its bus sets
        // statefulCalculatorRequired. Other recipe classes require a real calculator.
        if (prepared.statefulCalculatorRequired() && context.execution().fastPathType() != ECORecipeClassifier.Type.NORMAL) {
            return null;
        }
        var count = BigInteger.valueOf(prepared.supportsExactInputs() ? limit : inputLimit);
        return new Admission(count, crafts -> {
            long copies = crafts.longValueExact();
            var outputs = scale(context.outputs(), copies);
            var remainders = scale(context.containerItems(), copies);
            if (copies <= inputLimit) {
                return prepared.push(new ECOFastPathDispatchProvider.Batch(copies,
                        scale(context.inputItems(), copies), outputs, remainders));
            }
            Map<AEKey, BigInteger> exactInputs = new HashMap<>();
            for (var stack : context.inputItems()) {
                exactInputs.put(stack.what(), crafts.multiply(BigInteger.valueOf(stack.amount())));
            }
            return prepared.push(new ECOFastPathDispatchProvider.Batch(copies, List.of(), outputs, remainders, exactInputs));
        });
    }

    private static long outputLimit(OmniCapacityRequest request, ECOBatchDispatchContext context, long capacity) {
        long limit = request.maximumCrafts().min(BigInteger.valueOf(capacity)).longValueExact();
        // ECO represents an output and a container of the same key in one long waiting counter.
        for (var output : context.outputs()) {
            long returned = request.unitRemainders().get(output.what());
            if (returned > Long.MAX_VALUE - output.amount()) return 0;
            limit = Math.min(limit, Long.MAX_VALUE / (output.amount() + returned));
        }
        return stackLimit(context.containerItems(), limit);
    }

    private static long stackLimit(List<GenericStack> stacks, long limit) {
        for (var stack : stacks) limit = Math.min(limit, Long.MAX_VALUE / stack.amount());
        return limit;
    }

    private static boolean matchesScaled(List<GenericStack> unit, long copies, List<GenericStack> actual) {
        var totals = EcoMolecularBatchModel.counter(actual);
        if (totals.size() != unit.size()) return false;
        for (var stack : unit) {
            if (totals.get(stack.what()) != stack.amount() * copies) return false;
        }
        return true;
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

