package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import cn.dancingsnow.neoecoae.api.me.ECOFastPathFacade;
import cn.dancingsnow.neoecoae.api.me.provider.ECOIndeterminateBatchException;
import cn.dancingsnow.neoecoae.api.me.provider.ECOParallelCraftingProviders;
import cn.dancingsnow.neoecoae.blocks.entity.crafting.ECOCraftingPatternBusBlockEntity;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;

/** Independent Omni dispatcher. ECO owns stock leases; Omni owns exact leases and all accounting. */
public final class EcoCpuDispatch {
    public static final String NBT_KEY = "molecularmanipulator:ecoDispatch";
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final EcoCpuDispatchEnergy energy = new EcoCpuDispatchEnergy();
    private final EcoCpuDispatchJournal journal = new EcoCpuDispatchJournal();
    private CompoundTag failedSnapshot;
    private boolean dispatching;

    public enum Outcome { REJECTED, ACCEPTED, PAUSED }
    @FunctionalInterface interface Submission {
        boolean submit(ECOFastPathFacade.EnergyAccount energy);
    }
    public boolean suspended() {
        return journal.suspended() || failedSnapshot != null || (!dispatching && journal.pending());
    }
    public boolean receivingAllowed() { return !suspended(); }
    public void suspend(String reason) { journal.suspend(reason == null ? "Uncertain ECO ownership" : reason); }

    /** -1 leaves ordinary AE/Omni dispatch available; one accepted batch consumes one dispatch slot. */
    public int execute(EcoOmniPlanCpuAccess host, int maxPatterns, CraftingService crafting,
            IEnergyService power, Level level) {
        if (suspended() || dispatching) return 0;
        if (!host.ecoDispatch$enabled()) return -1;
        if (host.ecoDispatch$stopped() || host.ecoOmni$paused() || maxPatterns <= 0) return 0;
        if (host.ecoDispatch$jobId() == null) return -1;
        dispatching = true;
        try {
            var execution = host instanceof EcoOmniPlanCpu plan ? plan.ecoOmni$getExecution() : null;
            if (host.ecoOmni$managed() && execution == null) return 0;
            var patterns = execution == null ? List.copyOf(host.ecoOmni$tasks().keySet())
                    : execution.candidatePatterns();
            for (var pattern : patterns) {
                if (host.ecoDispatch$stopped()) return 0;
                var token = execution == null ? null : execution.begin(pattern);
                if (execution != null && token == null) continue;
                BigInteger limit = host.ecoDispatch$remaining(pattern);
                if (token != null) limit = limit.min(token.maximum());
                if (limit.signum() <= 0) continue;
                var preview = new PreviewInventory(host.ecoDispatch$physicalView());
                ICraftingInventory guarded = token == null ? preview
                        : token.guard(preview, host::ecoOmni$physicalAmount);
                var outputs = new KeyCounter();
                var remainders = new KeyCounter();
                var inputs = token == null
                        ? CraftingCpuHelper.extractPatternInputs(pattern, guarded, level, outputs, remainders)
                        : token.extractInputs(guarded, level, outputs, remainders);
                if (inputs == null) continue;
                if (token != null && token.hasCycleProtection())
                    limit = token.limit(inputs, host::ecoOmni$physicalAmount, limit);
                if (limit.signum() <= 0) continue;
                final var phase = token;
                for (var provider : crafting.getProviders(pattern)) {
                    if (host.ecoDispatch$stopped()) return 0;
                    var parallel = ECOParallelCraftingProviders.find(provider);
                    if (!ECOFastPathFacade.supports(provider) && parallel == null) continue;
                    if (provider.isBusy()) continue;
                    try (var scope = phase == null ? null : phase.openProviderScope()) {
                        var outcome = limit.compareTo(BigInteger.TWO) >= 0
                                ? attempt(host, provider, pattern, inputs, outputs, remainders, limit, phase, power, level)
                                : Outcome.REJECTED;
                        if (outcome == Outcome.REJECTED && provider instanceof ECOCraftingPatternBusBlockEntity bus)
                            outcome = attemptNativeSingle(host, bus, pattern, inputs, outputs, remainders, phase, power);
                        if (outcome == Outcome.ACCEPTED) return 1;
                        if (outcome == Outcome.PAUSED) return 0;
                    }
                }
            }
            return -1;
        } catch (RuntimeException | Error failure) {
            pause(host, null, failure);
            rethrowFatal(failure);
            return 0;
        } finally { dispatching = false; }
    }

    /**
     * A cold native bus verifies the recipe by accepting one real craft. ECO's legacy
     * pushWithJob wrapper bypasses the shared CPU push hook, leaving exact/phase tasks
     * unchanged. Keep this acceptance inside the same ownership transaction as batches.
     */
    private Outcome attemptNativeSingle(EcoOmniPlanCpuAccess host, ECOCraftingPatternBusBlockEntity bus,
            IPatternDetails pattern, KeyCounter[] prototype, KeyCounter outputs, KeyCounter remainders,
            EcoOmniPlanExecution.Dispatch phase, IEnergyService power) {
        double singlePower = CraftingCpuHelper.calculatePatternPower(prototype);
        if (!Double.isFinite(singlePower) || singlePower < 0) return Outcome.REJECTED;
        var receipt = new EcoCpuDispatchReceipt(host.ecoDispatch$jobId(), pattern, BigInteger.ONE,
                totals(prototype), totals(outputs), totals(remainders), false);
        var lease = host.ecoDispatch$reserveExact(receipt.inputs());
        if (lease == null) return Outcome.REJECTED;
        return transact(host, receipt, lease, power, account -> {
            var paid = account.reserve(singlePower);
            if (paid == null) return false;
            // The bus receives its own mutable holders; the phase token retains the unit
            // allocation needed to advance once after acceptance.
            var delivery = new KeyCounter[prototype.length];
            for (int slot = 0; slot < prototype.length; slot++) {
                delivery[slot] = new KeyCounter();
                delivery[slot].addAll(prototype[slot]);
            }
            boolean accepted;
            try { accepted = bus.pushPattern(pattern, delivery, host.ecoDispatch$jobId()); }
            catch (RuntimeException uncertain) {
                paid.commit();
                throw new ECOIndeterminateBatchException("Native single-craft ownership is uncertain", uncertain);
            }
            if (accepted) paid.commit(); else paid.refund();
            return accepted;
        }, () -> { if (phase != null) phase.accepted(BigInteger.ONE, prototype); });
    }

    private Outcome attempt(EcoOmniPlanCpuAccess host, ICraftingProvider provider, IPatternDetails pattern,
            KeyCounter[] prototype, KeyCounter outputs, KeyCounter remainders, BigInteger limit,
            EcoOmniPlanExecution.Dispatch phase, IEnergyService power, Level level) {
        double singlePower = CraftingCpuHelper.calculatePatternPower(prototype);
        if (!Double.isFinite(singlePower) || singlePower < 0) return Outcome.REJECTED;
        var units = totals(prototype);
        if (host.ecoDispatch$exactOrder() && remainders.isEmpty()) {
            BigInteger exactLimit = limit;
            for (var entry : units.entrySet()) exactLimit = exactLimit.min(
                    host.ecoOmni$physicalAmount(entry.getKey()).divide(entry.getValue()));
            if (phase != null) exactLimit = phase.limit(prototype, host::ecoOmni$physicalAmount, exactLimit);
            exactLimit = energy.maximum(power, singlePower, exactLimit);
            var exact = exactLimit.compareTo(BigInteger.TWO) < 0 ? null
                    : ECOFastPathFacade.prepareExactAllocated(provider, pattern, prototype,
                            exactLimit, level, host.ecoDispatch$jobId());
            if (exact != null) {
                var count = exact.craftCount();
                var receipt = new EcoCpuDispatchReceipt(host.ecoDispatch$jobId(), pattern, count,
                        multiply(units, count), multiply(totals(outputs), count), Map.of(), true);
                var lease = host.ecoDispatch$reserveExact(receipt.inputs());
                if (lease != null) {
                    var result = transact(host, receipt, lease, power, account -> {
                        // Exact API has no energy transaction; the CPU pays every copy before submit.
                        var paid = account.reserve(singlePower);
                        if (paid == null) return false;
                        boolean keep = false;
                        try {
                            keep = exact.submit();
                            return keep;
                        } catch (ECOIndeterminateBatchException failure) {
                            keep = true;
                            throw failure;
                        } catch (RuntimeException rejected) { return false; }
                        finally { if (keep) paid.commit(); else paid.refund(); }
                    }, () -> { if (phase != null) phase.accepted(count, prototype); });
                    if (result != Outcome.REJECTED) return result;
                }
            }
        }
        var parallel = ECOParallelCraftingProviders.find(provider);
        long bounded = limit.min(LONG_MAX).longValueExact();
        var batch = parallel == null
                ? ECOFastPathFacade.prepare(provider, pattern, prototype, outputs, remainders,
                        host.ecoDispatch$inventory(), bounded, singlePower, energy.simulation(power),
                        level, host.ecoDispatch$jobId())
                : ECOFastPathFacade.prepareParallel(parallel, pattern, prototype, outputs, remainders,
                        host.ecoDispatch$inventory(), bounded, singlePower, energy.simulation(power),
                        level, host.ecoDispatch$jobId());
        if (batch == null || batch.craftCount() < 2) return Outcome.REJECTED;
        if (BigInteger.valueOf(batch.craftCount()).compareTo(limit) > 0)
            throw new IllegalStateException("ECO batch exceeds its pinned phase/input-allocation allowance");
        // Exact totals remain exact even when per-key sums exceed the GenericStack long window.
        var receipt = new EcoCpuDispatchReceipt(host.ecoDispatch$jobId(), pattern,
                BigInteger.valueOf(batch.craftCount()), batch.exactInputTotal(), totals(batch.outputs()),
                totals(batch.remainders()), false);
        if (phase != null) {
            // Stateful calculators inspect real stock directly. They must not switch to an input
            // variant outside the allocation pinned by the prototype, even within a DAG phase.
            if (!units.keySet().containsAll(receipt.inputs().keySet())) return Outcome.REJECTED;
            var seeds = phase.protectedSeeds();
            for (var entry : receipt.inputs().entrySet()) {
                var free = host.ecoOmni$physicalAmount(entry.getKey())
                        .subtract(BigInteger.valueOf(seeds.getOrDefault(entry.getKey(), 0L)));
                if (free.compareTo(entry.getValue()) < 0) return Outcome.REJECTED;
            }
        }
        return transact(host, receipt, null, power, account -> {
            // ECO's total double can round down. Debit the exact product of the per-copy
            // double and integer craft count, then give ECO the resulting reservation.
            try { return batch.submit(ignoredTotal -> account.reserve(singlePower)); }
            catch (ECOIndeterminateBatchException failure) { throw failure; }
            catch (RuntimeException rejected) { return false; } // ECO has already rolled back both resources.
        }, () -> { if (phase != null) phase.accepted(receipt.crafts(), prototype); });
    }

    /** One-way boundary, also exercised directly against real ECO batches by isolated tests. */
    Outcome transact(EcoCpuDispatchHost host, EcoCpuDispatchReceipt receipt, EcoCpuDispatchHost.Lease materials,
            IEnergyService power, Submission submission, Runnable phaseAccepted) {
        EcoCpuDispatchHost.Accounting accounting = null;
        boolean retained = false;
        try {
            journal.arm(receipt);
            accounting = host.ecoDispatch$stage(receipt);
            if (accounting == null) {
                if (materials != null) materials.refund();
                journal.complete();
                host.ecoDispatch$markDirty();
                return Outcome.REJECTED;
            }
            host.ecoDispatch$markDirty();
            boolean accepted = submission.submit(amount -> {
                try {
                    var result = energy.reserve(power, amount, receipt.crafts());
                    host.ecoDispatch$markDirty();
                    return result;
                } catch (RuntimeException failure) {
                    throw new ECOIndeterminateBatchException("CPU energy reservation is uncertain", failure);
                }
            });
            if (!accepted) {
                accounting.rollback();
                if (materials != null) materials.refund();
                journal.complete();
                host.ecoDispatch$markDirty();
                return Outcome.REJECTED;
            }
            retained = true;
            if (materials != null) materials.commit();
            phaseAccepted.run();
            accounting.commit();
            journal.complete();
            host.ecoDispatch$markDirty();
            return Outcome.ACCEPTED;
        } catch (RuntimeException | Error failure) {
            if (!journal.pending()) journal.arm(receipt);
            journal.suspend(failure.toString());
            // The same retained outcome applies to provider uncertainty and post-acceptance accounting failures.
            if (materials != null && !retained) materials.commit();
            try { if (accounting != null) accounting.retain(); }
            finally { pause(host, receipt, failure); }
            rethrowFatal(failure);
            return Outcome.PAUSED;
        }
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof Error error && !(error instanceof LinkageError) && !(error instanceof AssertionError))
            throw error;
    }

    private void pause(EcoCpuDispatchHost host, EcoCpuDispatchReceipt receipt, Throwable failure) {
        journal.suspend(failure.toString());
        try { host.ecoDispatch$pause(receipt, failure.toString()); }
        finally {
            host.ecoDispatch$markDirty();
            RateLimitedLog.error("Omni ECO dispatch suspended; retained inputs must not be refunded or replayed", failure);
        }
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        if (failedSnapshot != null) return failedSnapshot.copy();
        var tag = journal.write(registries);
        energy.write(tag);
        return tag;
    }
    public void read(CompoundTag tag) {
        failedSnapshot = null;
        try { journal.read(tag); energy.read(tag); }
        catch (RuntimeException failure) {
            failedSnapshot = tag.copy();
            journal.suspend("Invalid ECO dispatch persistence: " + failure);
        }
    }

    private static Map<AEKey, BigInteger> totals(KeyCounter[] inputs) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var slot : inputs) totals(slot).forEach((key, amount) -> result.merge(key, amount, BigInteger::add));
        return result;
    }
    private static Map<AEKey, BigInteger> totals(KeyCounter counter) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var entry : counter) {
            if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Nonpositive material");
            result.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
        }
        return result;
    }
    private static Map<AEKey, BigInteger> totals(List<GenericStack> stacks) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var stack : stacks) result.merge(stack.what(), BigInteger.valueOf(stack.amount()), BigInteger::add);
        return result;
    }
    private static Map<AEKey, BigInteger> multiply(Map<AEKey, BigInteger> units, BigInteger count) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        units.forEach((key, amount) -> result.put(key, amount.multiply(count)));
        return result;
    }

    /** Local extraction overlay: preview reservations never modify the actual CPU or overflow. */
    private static final class PreviewInventory implements ICraftingInventory {
        private final ICraftingInventory physical;
        private final Map<AEKey, BigInteger> removed = new LinkedHashMap<>();
        PreviewInventory(ICraftingInventory physical) { this.physical = physical; }
        public long extract(AEKey key, long requested, Actionable mode) {
            if (requested <= 0) return 0;
            long visible = physical.extract(key, Long.MAX_VALUE, Actionable.SIMULATE);
            long taken = BigInteger.valueOf(visible).subtract(removed.getOrDefault(key, BigInteger.ZERO))
                    .max(BigInteger.ZERO).min(BigInteger.valueOf(requested)).longValueExact();
            if (mode == Actionable.MODULATE && taken > 0)
                removed.merge(key, BigInteger.valueOf(taken), BigInteger::add);
            return taken;
        }
        public void insert(AEKey key, long amount, Actionable mode) {
            if (amount < 0) throw new IllegalArgumentException("Negative preview refund");
            if (mode == Actionable.MODULATE) {
                var next = removed.getOrDefault(key, BigInteger.ZERO).subtract(BigInteger.valueOf(amount));
                if (next.signum() < 0) throw new IllegalStateException("Preview created physical stock");
                if (next.signum() == 0) removed.remove(key); else removed.put(key, next);
            }
        }
        public Iterable<AEKey> findFuzzyTemplates(AEKey key) { return physical.findFuzzyTemplates(key); }
    }
}
