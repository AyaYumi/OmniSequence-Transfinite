package com.atir.molecularmanipulator.crafting.dispatch;

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
import com.appliedenhancements.api.AelisBatchExecutionContext;
import com.appliedenhancements.api.AelisCycleRuntimeController;
import com.appliedenhancements.runtime.AelisCycleDispatchScope;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchDelivery;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProviderAdapterRegistry;
import com.atir.molecularmanipulator.api.crafting.OmniBatchRequest;
import com.atir.molecularmanipulator.api.crafting.OmniBigIntegerProviderAdapterRegistry;
import com.atir.molecularmanipulator.api.crafting.OmniPostAccountingOutputAdapterRegistry;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.crafting.MolecularOmniBatchDelivery;
import com.atir.molecularmanipulator.crafting.OmniExactInputReservation;
import com.atir.molecularmanipulator.crafting.OmniNativeSmartDoubling;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.atir.molecularmanipulator.integration.ae2.OmniNativeSinglePatterns;
import com.atir.molecularmanipulator.integration.eco.EcoOmniPlanCpuAccess;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** One bounded capacity scheduler for an Omni CPU lane. All commits share the same ownership transaction. */
public final class OmniCapacityDispatcher {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private UUID jobId;
    private Map<IPatternDetails, Object> tasks;
    private Iterator<Map.Entry<IPatternDetails, Object>> taskCursor;
    private final Map<IPatternDetails, Set<ICraftingProvider>> visitedProviders = new LinkedHashMap<>();
    private int visitedTasks;
    private int seekTasks;
    private IPatternDetails pattern;
    private Iterator<ICraftingProvider> providerCursor;
    private boolean dispatching;
    private boolean clearPending;
    private boolean reportedSingleFallback;

    public void tick(EcoOmniPlanCpuAccess host, OmniDispatchAllowance allowance, CraftingService crafting,
            IEnergyService energy, Level level) {
        if (dispatching || !allowance.capacityMode() || !host.omniDispatch$enabled()
                || host.omniDispatch$stopped() || host.ecoOmni$paused()) return;
        var currentJob = host.omniDispatch$jobId();
        if (currentJob == null || allowance.remainingCopies() <= 0) return;
        dispatching = true;
        try {
            tick(host, allowance, crafting, energy, level, currentJob);
        } finally {
            visitedProviders.clear();
            if (clearPending) resetSelection();
            dispatching = false;
        }
    }

    public void clear() {
        if (dispatching) clearPending = true;
        else resetSelection();
    }

    private void resetSelection() {
        clearPending = false;
        reportedSingleFallback = false;
        jobId = null;
        tasks = null;
        taskCursor = null;
        visitedTasks = seekTasks = 0;
        leavePattern();
    }

    private void tick(EcoOmniPlanCpuAccess host, OmniDispatchAllowance allowance, CraftingService crafting,
            IEnergyService energy, Level level, UUID currentJob) {
        var liveTasks = host.ecoOmni$tasks();
        if (!currentJob.equals(jobId) || liveTasks != tasks) {
            resetSelection();
            jobId = currentJob;
            tasks = liveTasks;
        }
        var power = host.omniDispatch$dispatchEnergy();
        boolean batching = ModConfig.OMNI_BATCH_DISPATCH_ENABLED.get();
        Prototype unit = null;
        Boolean nativeSingle = null;
        while (!clearPending && allowance.remainingCopies() > 0 && !host.omniDispatch$stopped()
                && currentJob.equals(host.omniDispatch$jobId())) {
            if (pattern == null) {
                if (!allowance.tryWork()) return;
                if (taskCursor == null) taskCursor = host.ecoOmni$candidates(tasks).iterator();
                try {
                    if (!taskCursor.hasNext()) {
                        taskCursor = null;
                        visitedTasks = seekTasks = 0;
                        return;
                    }
                    var candidate = taskCursor.next().getKey();
                    if (seekTasks > 0) {
                        seekTasks--;
                        continue;
                    }
                    visitedTasks++;
                    pattern = candidate;
                    nativeSingle = null;
                } catch (ConcurrentModificationException changed) {
                    rebaseTasks();
                    continue;
                }
            }

            // Prepay the candidate's topology check, provider operations and transaction.
            // All ownership settlement and output flushing finish within that work reservation.
            if (!allowance.tryWork() || !allowance.tryWork()) return;
            try {
                if (nativeSingle == null) nativeSingle = OmniNativeSinglePatterns.requiresNative(host, pattern);
                if (providerCursor == null) providerCursor = providers(crafting, pattern);
                if (!providerCursor.hasNext()) {
                    unit = null;
                    leavePattern();
                    continue;
                }
            } catch (RuntimeException | LinkageError failure) {
                if (!(failure instanceof ConcurrentModificationException))
                    RateLimitedLog.warn("Could not select an Omni dispatch provider", failure);
                unit = null;
                leavePattern();
                continue;
            }

            if (!nativeSingle && unit == null) {
                // The overlay gives AE2 the real allocation view without touching CPU stock.
                if (!allowance.tryWork() || !allowance.tryWork()) return;
                try {
                    unit = extract(host, pattern, allowance.remainingCopies(), batching, power, energy, level);
                } catch (RuntimeException | LinkageError failure) {
                    RateLimitedLog.warn("Could not obtain an Omni dispatch prototype", failure);
                }
                if (unit == null) {
                    leavePattern();
                    continue;
                }
            }

            ICraftingProvider provider;
            try {
                provider = providerCursor.next();
            } catch (ConcurrentModificationException changed) {
                // AE2's provider topology changed while this lane held its cursor.
                unit = null;
                leavePattern();
                continue;
            }
            if (provider == null || !visitedProviders.computeIfAbsent(pattern,
                    ignored -> Collections.newSetFromMap(new IdentityHashMap<>())).add(provider)) continue;

            boolean accepted = nativeSingle ? tryNativeSingle(host, allowance, pattern, provider, crafting, energy, level)
                    : attempt(host, allowance, provider, unit, batching, power, energy, level).signum() > 0;
            if (clearPending || !currentJob.equals(host.omniDispatch$jobId())) return;
            if (!accepted) continue;

            unit = null; // Availability, allocations and phase tokens can all change on acceptance.
            if (host.ecoOmni$managed()) {
                taskCursor = null;
                visitedTasks = seekTasks = 0;
                leavePattern();
            } else if (!tasks.containsKey(pattern)) {
                visitedTasks--;
                rebaseTasks();
                leavePattern();
            }
        }
    }

    private void leavePattern() {
        pattern = null;
        providerCursor = null;
    }

    private void rebaseTasks() {
        taskCursor = null;
        visitedTasks = Math.min(visitedTasks, tasks.size());
        seekTasks = visitedTasks;
    }

    private static Iterator<ICraftingProvider> providers(CraftingService crafting, IPatternDetails pattern) {
        // The AELIS provider view eagerly wraps the entire list for its old scaled endpoint.
        // Keep the raw provider identity here and apply the cycle guard at allocation/commit.
        try (var ignored = AelisCycleDispatchScope.open((Supplier<AelisCycleRuntimeController>) null)) {
            return crafting.getProviders(pattern).iterator();
        }
    }

    static boolean tryNativeSingle(OmniCpuDispatchHost host, OmniDispatchAllowance allowance, IPatternDetails pattern,
            ICraftingProvider provider, CraftingService crafting, IEnergyService energy, Level level) {
        try {
            if (provider.isBusy()) return false;
        } catch (RuntimeException | LinkageError failure) {
            RateLimitedLog.warn("Could not check an Omni single provider's availability", failure);
            return false;
        }
        if (!allowance.trySingle()) return false;
        return host.omniDispatch$executeNativeSingle(pattern, provider, crafting, energy, level) > 0;
    }

    private static Prototype extract(EcoOmniPlanCpuAccess host, IPatternDetails pattern, long copies, boolean batching,
            OmniCpuDispatchLedger.Energy power, IEnergyService energy, Level level) {
        var maximum = host.omniDispatch$remaining(pattern);
        if (!batching) maximum = maximum.min(BigInteger.ONE);
        if (copies != Long.MAX_VALUE) maximum = maximum.min(BigInteger.valueOf(copies));
        if (maximum.signum() <= 0) return null;

        var state = host.ecoOmni$exactState();
        var infinite = state == null ? Set.<AEKey>of() : state.infiniteKeys();
        ICraftingInventory preview = new PreviewInventory(host);
        if (state != null) preview = state.wrap(preview);
        preview = host.ecoOmni$guard(pattern, preview);
        if (preview == null) return null;
        var runtime = AelisCycleDispatchScope.currentRuntime();
        var cycle = AelisBatchExecutionContext.acquire(runtime, pattern.getDefinition(), preview);
        var guarded = cycle.inventory();
        if (guarded == null) return null;
        var outputs = new KeyCounter();
        var remainders = new KeyCounter();
        var inputs = host.ecoOmni$previewExtract(pattern, guarded, level, outputs, remainders,
                () -> CraftingCpuHelper.extractPatternInputs(pattern, guarded, level, outputs, remainders));
        if (inputs == null) return null;
        // AE2 records zero-valued alternatives that it inspected but could not use.
        for (var slot : inputs) slot.removeZeros();
        outputs.removeZeros();
        remainders.removeZeros();
        var unitInputs = inputs.length == 0 ? Map.<AEKey, BigInteger>of()
                : OmniExactInputReservation.unitInputs(inputs, Set.of());
        var unitOutputs = totals(outputs);
        if (unitOutputs.isEmpty()) return null;
        var unitRemainders = totals(remainders);
        for (var entry : unitInputs.entrySet()) {
            if (!infinite.contains(entry.getKey())) maximum = maximum.min(
                    host.ecoOmni$physicalAmount(entry.getKey()).divide(entry.getValue()));
        }
        if (cycle.hasCycleProtection()) {
            maximum = maximum.min(BigInteger.valueOf(cycle.maximumCrafts()));
            for (var entry : unitInputs.entrySet()) {
                var stillAvailable = guarded.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
                maximum = maximum.min(BigInteger.valueOf(stillAvailable).add(entry.getValue()).divide(entry.getValue()));
            }
        }
        maximum = host.ecoOmni$limit(pattern, maximum);
        if (maximum.signum() <= 0) return null;

        double cost = CraftingCpuHelper.calculatePatternPower(inputs);
        if (!Double.isFinite(cost) || cost < 0) return null;
        var unitPower = cost == 0 ? BigDecimal.ZERO : new BigDecimal(cost);
        var longMaximum = longLimit(unitInputs, unitOutputs, unitRemainders);
        boolean exact = host.omniDispatch$exactOrder();
        if (!exact || !remainders.isEmpty() || cycle.hasCycleProtection()) maximum = maximum.min(longMaximum);
        if (maximum.signum() <= 0) return null;
        maximum = power.maximum(energy, unitPower, maximum);
        return maximum.signum() <= 0 ? null : new Prototype(pattern, inputs, outputs, remainders,
                unitInputs, unitOutputs, unitRemainders, infinite, maximum, maximum.min(longMaximum), unitPower,
                exact, cycle, runtime);
    }

    private BigInteger attempt(EcoOmniPlanCpuAccess host, OmniDispatchAllowance allowance, ICraftingProvider provider,
            Prototype unit, boolean batching, OmniCpuDispatchLedger.Energy power, IEnergyService energy, Level level) {
        OmniCapacityAdmission admission = null;
        BigInteger count;
        boolean partial;
        try {
            if (provider.isBusy()) return BigInteger.ZERO;
            if (batching && !unit.inputTotals().isEmpty() && !MolecularBatchCraftingProvider.requiresSerialDispatch(provider)) {
                admission = host.ecoOmni$withProviderScope(() -> prepare(host, provider, unit, level));
            }
            if (admission == null) {
                if (!allowance.trySingle()) return BigInteger.ZERO;
                count = BigInteger.ONE;
                partial = false;
            } else {
                count = admission.maximumCrafts();
                if (count == null || count.signum() < 0 || count.compareTo(unit.maximum()) > 0)
                    throw new IllegalStateException("Invalid provider capacity");
                partial = admission.acceptsPartial();
            }
        } catch (RuntimeException | LinkageError failure) {
            closeAdmission(admission);
            RateLimitedLog.warn("Omni provider capacity preparation failed", failure);
            return BigInteger.ZERO;
        } catch (Error fatal) {
            closeAdmission(admission);
            throw fatal;
        }

        BigInteger accepted;
        try {
            if (count.signum() == 0) return BigInteger.ZERO;
            accepted = transact(host, allowance, provider, unit, admission, count, partial, power, energy);
        } finally {
            closeAdmission(admission);
        }
        boolean single = admission == null;
        if (accepted.signum() == 0 && admission instanceof PublicAdmission api && api.permitsSingle()
                && !host.omniDispatch$stopped() && allowance.tryWork() && allowance.tryWork()) {
            // The public endpoint explicitly certified that it owns nothing. One separately
            // budgeted single can preserve compatibility with its ordinary input shape.
            accepted = attempt(host, allowance, provider, unit, false, power, energy, level);
            single = true;
        } else if (accepted.signum() > 0) {
            flushOutputs(provider);
        }
        if (single && batching && accepted.signum() > 0 && !reportedSingleFallback
                && unit.inputTotals().size() > 1 && unit.maximum().compareTo(BigInteger.ONE) > 0) {
            reportedSingleFallback = true;
            RateLimitedLog.warn("Omni job {} sent one craft of {} through {} using native dispatch. "
                            + "This provider did not admit a batch of the recipe's inputs.",
                    jobId, unit.outputs().getFirstEntry().getKey(), provider.getClass().getName());
        }
        return accepted;
    }

    private OmniCapacityAdmission prepare(EcoOmniPlanCpuAccess host, ICraftingProvider provider, Prototype unit, Level level) {
        var request = unit.request(jobId, provider, unit.longMaximum(), level);
        boolean consumptive = unit.remainders().isEmpty();
        boolean generic = consumptive && !OmniNativeSmartDoubling.isExternallyManaged(unit.pattern());
        if (generic && request.maximumCrafts().signum() > 0) {
            if (request.maximumCrafts().compareTo(BigInteger.ONE) > 0) {
                var api = preparePublic(request, unit.outputTotals());
                if (api != null) return api;
            }
            if (provider instanceof MolecularBatchCraftingProvider molecular
                    && molecular.molecularmanipulator$supportsBatching(unit.pattern())) {
                long limit = molecular.molecularmanipulator$getBatchLimit(unit.pattern(), unit.outputs());
                if (limit <= 0) return OmniCapacityAdmission.UNAVAILABLE;
                var count = request.maximumCrafts().min(BigInteger.valueOf(limit));
                return admission(count, false, crafts -> {
                    long copies = crafts.longValueExact();
                    var delivery = scale(unit.inputs(), copies);
                    try (var scope = MolecularBatchDispatchContext.openFixed(request.jobId(), unit.pattern(),
                            delivery, unit.inputs(), copies, unit.outputs())) {
                        return provider.pushPattern(unit.pattern(), delivery) ? crafts : BigInteger.ZERO;
                    }
                });
            }
            if (provider instanceof OmniCapacityTarget target) {
                var prepared = target.omniDispatch$prepareTarget(request);
                if (prepared != null) return prepared;
            }
        }
        if (unit.exact() && consumptive) {
            var exact = prepareBigInteger(host, provider, unit);
            if (exact != null) return exact;
        }
        var ecoRequest = unit.maximum().equals(unit.longMaximum()) ? request : unit.request(jobId, provider, unit.maximum(), level);
        var eco = host.ecoOmni$prepareBatch(ecoRequest);
        if (eco != null) return eco;
        return request.maximumCrafts().signum() > 0 && host instanceof OmniCapacityTarget.Thunderbolt thunderbolt
                ? thunderbolt.omniDispatch$prepareThunderbolt(request) : null;
    }

    private static OmniCapacityAdmission preparePublic(OmniCapacityRequest request, Map<AEKey, BigInteger> outputs) {
        var provider = OmniBatchProviderAdapterRegistry.resolve(request.provider(), request.pattern());
        if (provider == null) return null;
        var inputs = new ArrayList<OmniBatchProbe.Input>();
        for (int slot = 0; slot < request.unitInputs().length; slot++) {
            for (var entry : request.unitInputs()[slot]) {
                inputs.add(new OmniBatchProbe.Input(slot, entry.getKey(), entry.getLongValue()));
            }
        }
        var publicLimit = request.maximumCrafts().longValueExact();
        var prepared = provider.prepareOmniBatch(new OmniBatchProbe(request.pattern(), inputs, publicLimit));
        if (prepared == null) return null;
        boolean keep = false;
        try {
            long capacity = prepared.maxCrafts();
            if (capacity < 0) throw new IllegalStateException("Negative Omni batch capacity");
            if (capacity < 2) return capacity == 0 ? OmniCapacityAdmission.UNAVAILABLE : null;
            var count = BigInteger.valueOf(Math.min(publicLimit, capacity));
            var admission = new PublicAdmission(request, prepared, inputs, outputs, count);
            keep = true;
            return admission;
        } finally {
            if (!keep) prepared.close();
        }
    }

    private static final class PublicAdmission implements OmniCapacityAdmission {
        private final OmniCapacityRequest request;
        private final OmniBatchAdmission prepared;
        private final List<OmniBatchProbe.Input> inputs;
        private final Map<AEKey, BigInteger> outputs;
        private final BigInteger maximum;
        private boolean permitsSingle;

        PublicAdmission(OmniCapacityRequest request, OmniBatchAdmission prepared, List<OmniBatchProbe.Input> inputs,
                Map<AEKey, BigInteger> outputs, BigInteger maximum) {
            this.request = request;
            this.prepared = prepared;
            this.inputs = inputs;
            this.outputs = outputs;
            this.maximum = maximum;
        }

        boolean permitsSingle() { return permitsSingle; }
        @Override public BigInteger maximumCrafts() { return maximum; }
        @Override public boolean acceptsPartial() { return false; }

        @Override public BigInteger submit(BigInteger crafts) {
            long copies = crafts.longValueExact();
            var deliveredInputs = new ArrayList<OmniBatchRequest.Input>(inputs.size());
            for (var input : inputs) deliveredInputs.add(new OmniBatchRequest.Input(
                    input.slot(), input.key(), Math.multiplyExact(input.amount(), copies)));
            var deliveredOutputs = new ArrayList<GenericStack>(outputs.size());
            outputs.forEach((key, amount) -> deliveredOutputs.add(new GenericStack(key,
                    amount.multiply(crafts).longValueExact())));
            var delivery = new MolecularOmniBatchDelivery(new OmniBatchRequest(UUID.randomUUID(),
                    request.jobId(), request.pattern(), copies, deliveredInputs, deliveredOutputs));
            try {
                prepared.commit(delivery);
                if (!delivery.accepted() && delivery.rejection() == null)
                    throw new IllegalStateException("Omni batch provider returned without accepting or rejecting");
            } catch (RuntimeException | LinkageError | AssertionError failure) {
                if (!delivery.accepted() && delivery.rejection() == null) throw failure;
                RateLimitedLog.warn("Omni batch provider failed after reporting its terminal ownership", failure);
            } finally {
                delivery.seal();
            }
            if (delivery.accepted()) return crafts;
            permitsSingle = delivery.rejection().reason() != OmniBatchDelivery.RejectReason.CAPACITY_CHANGED;
            return BigInteger.ZERO;
        }

        @Override public void close() { prepared.close(); }
    }

    private static OmniCapacityAdmission prepareBigInteger(EcoOmniPlanCpuAccess host, ICraftingProvider provider,
            Prototype unit) {
        var exact = OmniBigIntegerProviderAdapterRegistry.resolve(provider, unit.pattern());
        if (exact == null) return null;
        if (!exact.usesNativeBigIntegerBatch() && !unit.infinite().containsAll(unit.inputTotals().keySet())) return null;
        var maximum = exact.getMaximumBigIntegerCrafts(unit.pattern(), scale(unit.inputs(), 1), unit.maximum());
        if (maximum == null || maximum.signum() < 0 || maximum.compareTo(unit.maximum()) > 0)
            throw new IllegalStateException("Invalid BigInteger capacity");
        if (maximum.signum() == 0) return OmniCapacityAdmission.UNAVAILABLE;
        return admission(maximum, true, crafts -> {
            var token = host.omniDispatch$bigIntegerToken(unit.pattern(), crafts);
            if (!exact.pushBigIntegerCraftingPattern(unit.pattern(), crafts, scale(unit.inputs(), 1), token))
                return BigInteger.ZERO;
            var accepted = exact.lastAcceptedBigIntegerCrafts();
            return accepted == null ? crafts : accepted;
        });
    }

    private BigInteger transact(EcoOmniPlanCpuAccess host, OmniDispatchAllowance allowance, ICraftingProvider provider,
            Prototype unit, OmniCapacityAdmission admission, BigInteger count, boolean partial,
            OmniCpuDispatchLedger.Energy power, IEnergyService energy) {
        var receipt = unit.receipt(jobId, count);
        var finite = finite(receipt.inputs(), unit.infinite());
        OmniCpuDispatchHost.Lease materials = null;
        OmniCpuDispatchHost.Accounting accounting = null;
        OmniCpuDispatchLedger.Energy.Reservation paid = null;
        AelisBatchExecutionContext.Dispatch cycleDispatch = null;
        AelisCycleRuntimeController.State cycleBefore = null;
        boolean submitted = false;
        try {
            if (!finite.isEmpty()) {
                materials = host.omniDispatch$reserveExact(finite);
                if (materials == null) return BigInteger.ZERO;
            }
            accounting = host.omniDispatch$stage(receipt);
            if (accounting != null) {
                host.omniDispatch$markDirty();
                paid = power.reserve(energy, unit.power(), count);
                if (paid != null && !host.omniDispatch$stopped() && receipt.jobId().equals(host.omniDispatch$jobId())) {
                    if (unit.cycle().hasCycleProtection()) {
                        if (partial && count.compareTo(BigInteger.ONE) > 0 && unit.runtime().currentStep()
                                .filter(step -> step.patternDefinition().equals(unit.pattern().getDefinition())).isPresent()) {
                            cycleBefore = unit.runtime().snapshot();
                        }
                        long copies = count.longValueExact();
                        cycleDispatch = unit.cycle().beginDispatch(scale(unit.inputs(), copies), copies);
                    }
                    // From this point a provider exception may mean the transfer already happened.
                    submitted = true;
                    var accepted = admission == null ? pushSingle(host, provider, unit)
                            : host.ecoOmni$withProviderScope(() -> admission.submit(count));
                    if (accepted == null || accepted.signum() < 0 || accepted.compareTo(count) > 0
                            || !partial && accepted.signum() > 0 && !accepted.equals(count)) {
                        throw new IllegalStateException("Provider returned an invalid accepted count");
                    }
                    if (accepted.signum() > 0) {
                        allowance.recordAccepted(accepted);
                        if (cycleDispatch != null) cycleDispatch.accepted();
                        if (accepted.equals(count)) {
                            if (materials != null) materials.commit();
                            paid.commit();
                            host.ecoOmni$accepted(unit.pattern(), accepted);
                            accounting.commit();
                        } else {
                            var actual = unit.receipt(receipt.jobId(), accepted);
                            if (cycleBefore != null) shrinkCycle(unit, cycleBefore, count.subtract(accepted), accepted);
                            host.ecoOmni$accepted(unit.pattern(), accepted);
                            accounting.commit(actual);
                            if (materials != null) materials.commit(finite(actual.inputs(), unit.infinite()));
                            paid.commit(accepted);
                        }
                        host.omniDispatch$markDirty();
                        return accepted;
                    }
                }
                accounting.rollback();
            }
            submitted = false;
            if (materials != null) materials.refund();
            if (paid != null) paid.refund();
            host.omniDispatch$markDirty();
            return BigInteger.ZERO;
        } catch (RuntimeException | LinkageError failure) {
            settleFailure(host, receipt, accounting, materials, paid, cycleDispatch, submitted, failure);
            RateLimitedLog.error("Omni capacity dispatch suspended after a failed ownership transaction", failure);
            return BigInteger.ZERO;
        } catch (Error fatal) {
            try {
                settleFailure(host, receipt, accounting, materials, paid, cycleDispatch, submitted, fatal);
            } finally {
                throw fatal;
            }
        } finally {
            if (cycleDispatch != null) cycleDispatch.close();
        }
    }

    private static void settleFailure(EcoOmniPlanCpuAccess host, OmniCpuDispatchReceipt receipt,
            OmniCpuDispatchHost.Accounting accounting, OmniCpuDispatchHost.Lease materials,
            OmniCpuDispatchLedger.Energy.Reservation paid, AelisBatchExecutionContext.Dispatch cycle,
            boolean submitted, Throwable failure) {
        try {
            if (submitted) {
                if (cycle != null) cycle.accepted();
                if (materials != null) materials.commit();
                if (paid != null) paid.commit();
                if (accounting != null) accounting.retain();
            } else {
                try {
                    if (accounting != null) accounting.rollback();
                } finally {
                    if (materials != null) materials.refund();
                    if (paid != null) paid.refund();
                }
            }
        } finally {
            host.omniDispatch$pause(receipt, failure.toString());
            host.omniDispatch$markDirty();
        }
    }

    private static BigInteger pushSingle(EcoOmniPlanCpuAccess host, ICraftingProvider provider, Prototype unit) {
        var delivery = scale(unit.inputs(), 1);
        return host.ecoOmni$pushProvider(provider, unit.pattern(), delivery,
                () -> provider.pushPattern(unit.pattern(), delivery)) ? BigInteger.ONE : BigInteger.ZERO;
    }

    private static void shrinkCycle(Prototype unit, AelisCycleRuntimeController.State before, BigInteger rejected,
            BigInteger accepted) {
        var runtime = unit.runtime();
        var phase = runtime.plan().phase();
        var pending = new LinkedHashMap<>(runtime.snapshot().exactPendingOutputs());
        if (phase != null) {
            for (var entry : phase.outputsPerPattern().getOrDefault(unit.pattern().getDefinition(), Map.of()).entrySet()) {
                var remaining = pending.getOrDefault(entry.getKey(), BigInteger.ZERO)
                        .subtract(BigInteger.valueOf(entry.getValue()).multiply(rejected));
                if (remaining.signum() < 0) throw new IllegalStateException("Rejected AELIS output has already settled");
                if (remaining.signum() == 0) pending.remove(entry.getKey());
                else pending.put(entry.getKey(), remaining);
            }
        }
        runtime.restore(new AelisCycleRuntimeController.State(before.stepIndex(),
                before.exactRemainingCrafts().subtract(accepted), pending));
    }

    private static void flushOutputs(ICraftingProvider provider) {
        try {
            OmniPostAccountingOutputAdapterRegistry.flushAfterCpuAccounting(provider);
        } catch (RuntimeException | LinkageError failure) {
            RateLimitedLog.warn("Could not flush outputs after committed Omni dispatch", failure);
        }
    }

    private static void closeAdmission(OmniCapacityAdmission admission) {
        if (admission == null) return;
        try {
            admission.close();
        } catch (RuntimeException | LinkageError failure) {
            RateLimitedLog.warn("Could not close Omni capacity admission", failure);
        }
    }

    private static OmniCapacityAdmission admission(BigInteger maximum, boolean partial,
            Function<BigInteger, BigInteger> submit) {
        return new OmniCapacityAdmission() {
            @Override public BigInteger maximumCrafts() { return maximum; }
            @Override public boolean acceptsPartial() { return partial; }
            @Override public BigInteger submit(BigInteger crafts) { return submit.apply(crafts); }
        };
    }

    private static KeyCounter[] scale(KeyCounter[] inputs, long count) {
        var result = new KeyCounter[inputs.length];
        for (int slot = 0; slot < inputs.length; slot++) {
            var scaled = new KeyCounter();
            for (var entry : inputs[slot]) scaled.set(entry.getKey(), Math.multiplyExact(entry.getLongValue(), count));
            result[slot] = scaled;
        }
        return result;
    }

    private static Map<AEKey, BigInteger> totals(KeyCounter counter) {
        if (counter.isEmpty()) return Map.of();
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var entry : counter) {
            if (entry.getLongValue() <= 0) throw new IllegalArgumentException("Invalid per-craft output");
            result.put(entry.getKey(), BigInteger.valueOf(entry.getLongValue()));
        }
        return result;
    }

    private static Map<AEKey, BigInteger> multiply(Map<AEKey, BigInteger> amounts, BigInteger count) {
        if (count.equals(BigInteger.ONE) || amounts.isEmpty()) return amounts;
        var result = new LinkedHashMap<AEKey, BigInteger>();
        amounts.forEach((key, amount) -> result.put(key, amount.multiply(count)));
        return result;
    }

    private static Map<AEKey, BigInteger> finite(Map<AEKey, BigInteger> amounts, Set<AEKey> infinite) {
        if (infinite.isEmpty()) return amounts;
        var result = new LinkedHashMap<AEKey, BigInteger>();
        amounts.forEach((key, amount) -> { if (!infinite.contains(key)) result.put(key, amount); });
        return result;
    }

    private static BigInteger longLimit(Map<AEKey, BigInteger> inputs, Map<AEKey, BigInteger> outputs,
            Map<AEKey, BigInteger> remainders) {
        var maximum = LONG_MAX;
        for (var amount : inputs.values()) maximum = maximum.min(LONG_MAX.divide(amount));
        for (var entry : outputs.entrySet()) maximum = maximum.min(
                LONG_MAX.divide(entry.getValue().add(remainders.getOrDefault(entry.getKey(), BigInteger.ZERO))));
        for (var entry : remainders.entrySet()) {
            if (!outputs.containsKey(entry.getKey())) maximum = maximum.min(LONG_MAX.divide(entry.getValue()));
        }
        return maximum;
    }

    private record Prototype(IPatternDetails pattern, KeyCounter[] inputs, KeyCounter outputs, KeyCounter remainders,
            Map<AEKey, BigInteger> inputTotals, Map<AEKey, BigInteger> outputTotals, Map<AEKey, BigInteger> remainderTotals,
            Set<AEKey> infinite, BigInteger maximum, BigInteger longMaximum, BigDecimal power, boolean exact,
            AelisBatchExecutionContext cycle, AelisCycleRuntimeController runtime) {
        OmniCapacityRequest request(UUID job, ICraftingProvider provider, BigInteger limit, Level level) {
            return new OmniCapacityRequest(job, provider, pattern, inputs, outputs, remainders, limit, level);
        }

        OmniCpuDispatchReceipt receipt(UUID job, BigInteger count) {
            return new OmniCpuDispatchReceipt(job, pattern, count, multiply(inputTotals, count),
                    multiply(outputTotals, count), multiply(remainderTotals, count), exact);
        }
    }

    private static final class PreviewInventory implements ICraftingInventory {
        private final EcoOmniPlanCpuAccess host;
        private final ICraftingInventory physical;
        private final Map<AEKey, BigInteger> remaining = new LinkedHashMap<>();

        PreviewInventory(EcoOmniPlanCpuAccess host) {
            this.host = host;
            physical = host.omniDispatch$physicalView();
        }

        @Override public long extract(AEKey key, long requested, Actionable mode) {
            if (requested <= 0) return 0;
            var available = remaining.computeIfAbsent(key, host::ecoOmni$physicalAmount);
            var taken = available.min(BigInteger.valueOf(requested));
            if (mode == Actionable.MODULATE && taken.signum() > 0) remaining.put(key, available.subtract(taken));
            return taken.longValueExact();
        }

        @Override public void insert(AEKey key, long amount, Actionable mode) {
            if (mode == Actionable.MODULATE && amount > 0) remaining.compute(key, (ignored, available) -> {
                if (available == null) throw new IllegalStateException("Preview refund has no source material");
                return available.add(BigInteger.valueOf(amount));
            });
        }

        @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) { return physical.findFuzzyTemplates(key); }
    }
}
