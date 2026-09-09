package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.api.crafting.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.ToLongFunction;
import net.minecraft.world.level.Level;

/** Optional AAE CPU state, outside the mixin package so its nested types remain loadable. */
public final class AdvancedAEBatchDispatch implements AutoCloseable {
    private static final long SLICE_NANOS = 2_000_000;
    private final Map<ICraftingProvider, Set<IPatternDetails>> backpressure = new IdentityHashMap<>();
    private final Map<ICraftingProvider, Set<IPatternDetails>> singleOnly = new IdentityHashMap<>();
    private Level tickLevel;
    private long tick, started;
    private int attempts;
    private Object job;
    private OmniBatchAdmission admission;
    private ICraftingProvider provider;
    private IPatternDetails pattern;
    private MolecularBatchCraftingExtractor.BatchExtraction extraction;
    private KeyCounter expectedOutputs;

    public void begin(Level level, Object currentJob) {
        if (tickLevel != level || tick != level.getGameTime()) {
            tickLevel = level;
            tick = level.getGameTime();
            started = System.nanoTime();
            attempts = 0;
            backpressure.clear();
        }
        if (job != currentJob) {
            job = currentJob;
            singleOnly.clear();
        }
    }

    /** Shared across all executeCrafting calls on this CPU in the same game tick. */
    public boolean allowExtraction() {
        if (attempts > 0 && System.nanoTime() - started >= SLICE_NANOS) return false;
        attempts++;
        return true;
    }

    public ICraftingProvider provider() { return provider; }

    public void endJob() {
        close();
        job = null;
        singleOnly.clear();
        backpressure.clear();
    }

    public boolean isBackpressured(ICraftingProvider candidate, IPatternDetails details) {
        return contains(backpressure, candidate, details);
    }

    public MolecularBatchCraftingExtractor.BatchExtraction prepare(
            Iterable<ICraftingProvider> providers, IPatternDetails details,
            ICraftingInventory inventory, IEnergyService energy, Level level,
            KeyCounter[] firstInputs, KeyCounter outputs, KeyCounter containers, long maximum) {
        if (maximum < 2 || !MolecularBatchDispatchSafety.isBatchablePattern(details)) return null;
        var inputs = new ArrayList<OmniBatchProbe.Input>();
        for (int slot = 0; slot < firstInputs.length; slot++) {
            for (var entry : firstInputs[slot])
                inputs.add(new OmniBatchProbe.Input(slot, entry.getKey(), entry.getLongValue()));
        }
        var probe = new OmniBatchProbe(details, inputs, maximum);
        for (var candidate : providers) {
            if (!(candidate instanceof OmniBatchCraftingProvider api)
                    || contains(singleOnly, candidate, details)
                    || isBackpressured(candidate, details) || candidate.isBusy()) continue;
            OmniBatchAdmission prepared = null;
            try {
                prepared = api.prepareOmniBatch(probe);
                if (prepared == null) continue;
                long limit = Math.min(maximum, prepared.maxCrafts());
                if (limit < 2) continue;
                var expanded = MolecularBatchCraftingExtractor.expandFromFirst(details, inventory, energy,
                        level, firstInputs, outputs, containers, limit, false);
                if (expanded == null) continue;
                admission = prepared;
                provider = candidate;
                pattern = details;
                extraction = expanded;
                expectedOutputs = outputs;
                prepared = null;
                return expanded;
            } catch (Throwable error) {
                logFailure("admission", error);
                mark(singleOnly, candidate, details);
            } finally {
                closeAdmission(prepared);
            }
        }
        return null;
    }

    public boolean commit(ICraftingLink link) {
        var selected = provider;
        var details = pattern;
        MolecularOmniBatchDelivery delivery = null;
        try {
            var inputs = new ArrayList<OmniBatchRequest.Input>();
            var holders = extraction.inputs();
            for (int slot = 0; slot < holders.length; slot++) for (var entry : holders[slot])
                inputs.add(new OmniBatchRequest.Input(slot, entry.getKey(), entry.getLongValue()));
            var outputs = new ArrayList<GenericStack>();
            for (var entry : expectedOutputs)
                outputs.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            delivery = new MolecularOmniBatchDelivery(new OmniBatchRequest(UUID.randomUUID(),
                    link == null ? null : link.getCraftingID(), details, extraction.craftCount(), inputs, outputs));
            admission.commit(delivery);
        } catch (Throwable error) {
            // A provider may accept ownership and then throw. Do not reinject accepted materials.
            logFailure("commit", error);
        } finally {
            if (delivery != null) delivery.seal();
            close();
        }
        if (delivery != null && delivery.accepted()) {
            if (delivery.receipt().backpressure() != OmniBatchDelivery.Backpressure.MAY_ACCEPT_MORE)
                mark(backpressure, selected, details);
            return true;
        }
        if (delivery != null && delivery.rejection() != null
                && delivery.rejection().reason() == OmniBatchDelivery.RejectReason.CAPACITY_CHANGED)
            mark(backpressure, selected, details);
        else mark(singleOnly, selected, details);
        return false;
    }

    /** Both output buckets are inserted into AAE's waitingFor after an accepted dispatch. */
    public static long waitingLimit(KeyCounter outputs, KeyCounter containers, ToLongFunction<AEKey> waiting) {
        var amounts = new HashMap<AEKey, Long>();
        try {
            for (var counter : List.of(outputs, containers)) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return 0;
                amounts.merge(entry.getKey(), entry.getLongValue(), Math::addExact);
            }
            long limit = Long.MAX_VALUE;
            for (var entry : amounts.entrySet()) {
                long stored = waiting.applyAsLong(entry.getKey());
                if (stored < 0) return 0;
                limit = Math.min(limit, (Long.MAX_VALUE - stored) / entry.getValue());
            }
            return limit;
        } catch (ArithmeticException error) { return 0; }
    }

    @Override
    public void close() {
        var previous = admission;
        admission = null;
        provider = null;
        pattern = null;
        extraction = null;
        expectedOutputs = null;
        closeAdmission(previous);
    }

    private static void closeAdmission(OmniBatchAdmission previous) {
        if (previous == null) return;
        try { previous.close(); }
        catch (Throwable error) { logFailure("cleanup", error); }
    }

    private static void logFailure(String phase, Throwable error) {
        if (error instanceof Error fatal && !(fatal instanceof LinkageError) && !(fatal instanceof AssertionError))
            throw fatal;
        MolecularManipulator.LOGGER.warn("AdvancedAE batch API failure during {}; preserving input ownership", phase, error);
    }

    private static boolean contains(Map<ICraftingProvider, Set<IPatternDetails>> map,
            ICraftingProvider provider, IPatternDetails pattern) {
        var patterns = map.get(provider);
        return patterns != null && patterns.contains(pattern);
    }

    private static void mark(Map<ICraftingProvider, Set<IPatternDetails>> map,
            ICraftingProvider provider, IPatternDetails pattern) {
        map.computeIfAbsent(provider, ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(pattern);
    }

    public record TaskAdjustment(Object task, Field valueField, long originalValue) {}
}
