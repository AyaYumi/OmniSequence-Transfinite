package com.atir.molecularmanipulator.integration.eco;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import cn.dancingsnow.neoecoae.api.me.bigorder.ECOExactInventory;
import cn.dancingsnow.neoecoae.crafting.ECOProcessingComponentMatching;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime;
import cn.dancingsnow.neoecoae.crafting.execution.ECOExecutionRuntime.DispatchCandidate;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.PlannedInputAllocation;
import com.atir.molecularmanipulator.mixin.EcoOmniPlanRuntimeInvoker;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** Server-thread job attachment. ECO schedules; Omni owns physical inputs and exact accounting. */
public final class EcoOmniPlanExecution {
    public static final String NBT_KEY = "molecularmanipulator:ecoPlan";
    private static final int VERSION = 1;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    private final ECOExecutionPlan plan;
    private final Map<Integer, IPatternDetails> bindings;
    private final Map<Integer, BigInteger> totals;
    private final Map<Integer, BigInteger> remaining;
    private final Map<AEKey, BigInteger> deferredStock;
    private final Map<AEKey, BigInteger> deferredEmitted;
    private final Set<ResourceLocation> fuzzy;
    private final BigInteger theoreticalBytes;
    private final UUID planningId;
    private final ECOExecutionRuntime runtime;
    private final EcoOmniPlanRuntimeAccess access;
    private long revision;

    private EcoOmniPlanExecution(ECOExecutionPlan plan, Map<Integer, IPatternDetails> bindings,
            Map<Integer, BigInteger> totals, Map<Integer, BigInteger> remaining,
            Map<AEKey, BigInteger> stock, Map<AEKey, BigInteger> emitted, Set<ResourceLocation> fuzzy,
            BigInteger theoreticalBytes, UUID planningId, ECOExecutionRuntime runtime) {
        this(plan, bindings, totals, remaining, stock, emitted, fuzzy, theoreticalBytes, planningId, runtime,
                (EcoOmniPlanRuntimeAccess) (Object) runtime);
    }

    // Package access permits verifying the real ECO runtime without launching Minecraft's mixin loader.
    EcoOmniPlanExecution(ECOExecutionPlan plan, Map<Integer, IPatternDetails> bindings,
            Map<Integer, BigInteger> totals, Map<Integer, BigInteger> remaining,
            Map<AEKey, BigInteger> stock, Map<AEKey, BigInteger> emitted, Set<ResourceLocation> fuzzy,
            BigInteger theoreticalBytes, UUID planningId, ECOExecutionRuntime runtime, EcoOmniPlanRuntimeAccess access) {
        this.plan = plan;
        this.bindings = Map.copyOf(bindings);
        this.totals = Map.copyOf(totals);
        this.remaining = new LinkedHashMap<>(remaining);
        this.deferredStock = new LinkedHashMap<>(EcoOmniPlanBridge.positive(stock));
        this.deferredEmitted = new LinkedHashMap<>(EcoOmniPlanBridge.positive(emitted));
        this.fuzzy = Set.copyOf(fuzzy);
        this.theoreticalBytes = Objects.requireNonNull(theoreticalBytes);
        this.planningId = planningId;
        this.runtime = runtime;
        this.access = access;
        access.ecoOmni$bindRemaining(id -> this.remaining.getOrDefault(id, BigInteger.ZERO));
    }

    static EcoOmniPlanExecution start(EcoOmniPlanBridge.Prepared prepared, Iterable<IPatternDetails> submitted) {
        var bindings = bind(prepared.execution(), submitted);
        return new EcoOmniPlanExecution(prepared.execution(), bindings, prepared.taskTotals(), prepared.taskTotals(),
                prepared.deferredStock(), prepared.deferredEmitted(), prepared.fuzzyPlanningItemIds(),
                prepared.theoreticalBytes(), prepared.planningId(), EcoOmniPlanRuntimeScope.withRemaining(
                        id -> prepared.taskTotals().getOrDefault(id, BigInteger.ZERO),
                        () -> new ECOExecutionRuntime(prepared.execution(), bindings)));
    }

    private static Map<Integer, IPatternDetails> bind(ECOExecutionPlan plan, Iterable<IPatternDetails> submitted) {
        var patterns = new ArrayList<IPatternDetails>();
        submitted.forEach(patterns::add);
        var result = new LinkedHashMap<Integer, IPatternDetails>();
        for (var task : plan.tasks()) {
            var matches = patterns.stream().filter(pattern -> PlanIdentity.samePattern(task.pattern(), pattern)).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("ECO task has no unique submitted pattern binding: " + task.id());
            result.put(task.id(), matches.getFirst());
        }
        return result;
    }

    public ECOExecutionPlan plan() { return plan; }
    public BigInteger theoreticalBytes() { return theoreticalBytes; }
    public UUID planningId() { return planningId; }
    public Set<ResourceLocation> fuzzyPlanningItemIds() { return fuzzy; }
    public Map<AEKey, BigInteger> deferredStock() { return Map.copyOf(deferredStock); }
    public Map<AEKey, BigInteger> deferredEmitted() { return Map.copyOf(deferredEmitted); }
    public Map<Integer, BigInteger> remainingByTask() { return Map.copyOf(remaining); }

    public Map<IPatternDetails, BigInteger> remainingByPattern() {
        var result = new LinkedHashMap<IPatternDetails, BigInteger>();
        remaining.forEach((id, count) -> {
            if (count.signum() > 0) result.merge(bindings.get(id), count, BigInteger::add);
        });
        return result;
    }

    /** Fresh candidates are essential after each accepted batch, even within one AE dispatch pass. */
    public List<IPatternDetails> candidatePatterns() {
        var result = new LinkedHashSet<IPatternDetails>();
        for (var candidate : runtime.candidates(Map.of())) result.add(candidate.pattern());
        return List.copyOf(result);
    }

    /** AE2's iterator.remove must delete the real task row, while ECO controls visit order. */
    public Set<Map.Entry<IPatternDetails, Object>> candidateEntries(Map<IPatternDetails, Object> tasks) {
        var patterns = candidatePatterns().stream().filter(tasks::containsKey).toList();
        return new java.util.AbstractSet<>() {
            @Override public int size() { return patterns.size(); }
            @Override public java.util.Iterator<Map.Entry<IPatternDetails, Object>> iterator() {
                var iterator = patterns.iterator();
                return new java.util.Iterator<>() {
                    private IPatternDetails current;
                    private boolean removable;
                    @Override public boolean hasNext() { return iterator.hasNext(); }
                    @Override public Map.Entry<IPatternDetails, Object> next() {
                        current = iterator.next();
                        removable = true;
                        return Map.entry(current, tasks.get(current));
                    }
                    @Override public void remove() {
                        if (!removable) throw new IllegalStateException();
                        tasks.remove(current);
                        removable = false;
                    }
                };
            }
        };
    }

    /** A token pins the ECO task ID while the CPU probes providers and reserves inputs. */
    public Dispatch begin(IPatternDetails pattern) {
        for (var candidate : runtime.candidates(Map.of())) {
            if (candidate.pattern() == pattern || PlanIdentity.samePattern(candidate.pattern(), pattern)) {
                return new Dispatch(this, candidate, revision);
            }
        }
        return null;
    }

    public boolean isComplete() {
        return deferredStock.isEmpty() && deferredEmitted.isEmpty()
                && remaining.values().stream().allMatch(value -> value.signum() == 0)
                && runtime.isComplete(Map.of());
    }

    /** Re-evaluate after each acceptance: completed feedback tasks must release their last products. */
    public BigInteger reservedInputAmount(AEKey key) {
        // This runtime binds exact progress through our task-ID hook, not ECO's native
        // TaskProgress[]. Use the map overload, as with candidates/isComplete.
        var reserve = BigInteger.valueOf(runtime.reservedInputAmount(key, Map.of()));
        var exactInputs = BigInteger.ZERO;
        for (var task : plan.tasks()) {
            var count = remaining.getOrDefault(task.id(), BigInteger.ZERO);
            exactInputs = exactInputs.add(EcoCpuDispatchLedger.reservedInput(bindings.get(task.id()), count, key));
        }
        // Keep startup stock in addition to exact demand even when ECO's long sum saturates.
        var startup = BigInteger.valueOf(access.ecoOmni$protectedSeed(null).getOrDefault(key, 0L));
        return reserve.max(exactInputs.add(startup));
    }

    public boolean hasPendingMaterials() { return !deferredStock.isEmpty() || !deferredEmitted.isEmpty(); }

    /** One bounded storage extraction per key per tick; extracted ownership transfers into physical stock. */
    public boolean refillDeferred(StockSource source, BiConsumer<AEKey, BigInteger> store,
            BiConsumer<AEKey, BigInteger> queueEmitted) {
        boolean changed = false;
        for (var key : List.copyOf(deferredStock.keySet())) {
            var due = deferredStock.get(key);
            long requested = due.min(LONG_MAX).longValueExact();
            long extracted = source.extract(key, requested);
            if (extracted < 0 || extracted > requested) throw new IllegalStateException("Invalid ECO stock extraction result");
            if (extracted > 0) {
                var amount = BigInteger.valueOf(extracted);
                // The callback inserts into OmniExactInventory, never a virtual inventory.
                store.accept(key, amount);
                subtract(deferredStock, key, amount);
                changed = true;
            }
        }
        for (var key : List.copyOf(deferredEmitted.keySet())) {
            // An emission is an expectation, never spendable inventory.
            queueEmitted.accept(key, deferredEmitted.get(key));
            deferredEmitted.remove(key);
            changed = true;
        }
        return changed;
    }

    @FunctionalInterface public interface StockSource { long extract(AEKey key, long requested); }

    private static void subtract(Map<AEKey, BigInteger> amounts, AEKey key, BigInteger amount) {
        var rest = amounts.get(key).subtract(amount);
        if (rest.signum() == 0) amounts.remove(key); else amounts.put(key, rest);
    }

    public CompoundTag write(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.put("plan", EcoOmniPlanCodec.write(plan, registries));
        var tasks = new ListTag();
        for (var task : plan.tasks()) {
            var entry = new CompoundTag();
            entry.putInt("id", task.id());
            entry.putString("total", totals.get(task.id()).toString());
            entry.putString("remaining", remaining.get(task.id()).toString());
            tasks.add(entry);
        }
        tag.put("tasks", tasks);
        tag.put("stock", EcoOmniPlanCodec.writeAmounts(deferredStock, registries));
        tag.put("emitted", EcoOmniPlanCodec.writeAmounts(deferredEmitted, registries));
        var fuzzyTags = new ListTag();
        fuzzy.stream().sorted().forEach(id -> fuzzyTags.add(StringTag.valueOf(id.toString())));
        tag.put("fuzzy", fuzzyTags);
        tag.putString("bytes", theoreticalBytes.toString());
        if (planningId != null) tag.putUUID("planningId", planningId);
        var runtimeTag = new CompoundTag();
        access.ecoOmni$write(runtimeTag, registries);
        tag.put("runtime", runtimeTag);
        return tag;
    }

    /** Call after Omni restores its exact ledger. Do not discard this tag on any restore error. */
    public static EcoOmniPlanExecution read(CompoundTag tag, HolderLookup.Provider registries,
            Level level, Iterable<IPatternDetails> submitted, Map<IPatternDetails, BigInteger> exactRemaining) {
        if (tag.getInt("version") != VERSION) throw new IllegalArgumentException("Unsupported ECO Omni state version");
        var available = new LinkedHashMap<AEItemKey, IPatternDetails>();
        for (var pattern : submitted) {
            if (available.putIfAbsent(pattern.getDefinition(), pattern) != null) {
                throw new IllegalArgumentException("Ambiguous restored ECO pattern definition");
            }
        }
        var plan = EcoOmniPlanCodec.read(tag.getCompound("plan"), registries, level, available);
        // Finished tasks need their pattern binding too, though AE has removed their task rows.
        plan.tasks().forEach(task -> available.putIfAbsent(task.pattern().getDefinition(), task.pattern()));
        var bindings = bind(plan, available.values());
        var totals = new LinkedHashMap<Integer, BigInteger>();
        var remaining = new LinkedHashMap<Integer, BigInteger>();
        var entries = tag.getList("tasks", Tag.TAG_COMPOUND);
        if (entries.size() != plan.tasks().size()) throw new IllegalArgumentException("ECO task ledger shape changed");
        for (int id = 0; id < entries.size(); id++) {
            var entry = entries.getCompound(id);
            var total = new BigInteger(entry.getString("total"));
            var rest = new BigInteger(entry.getString("remaining"));
            if (entry.getInt("id") != id || total.signum() <= 0 || rest.signum() < 0 || rest.compareTo(total) > 0
                    || total.compareTo(BigInteger.valueOf(plan.task(id).totalCount())) < 0) {
                throw new IllegalArgumentException("Invalid ECO exact task progress");
            }
            totals.put(id, total);
            remaining.put(id, rest);
        }
        var fuzzy = new LinkedHashSet<ResourceLocation>();
        for (var value : tag.getList("fuzzy", Tag.TAG_STRING)) fuzzy.add(ResourceLocation.parse(value.getAsString()));
        var runtime = EcoOmniPlanRuntimeScope.withRemaining(id -> remaining.getOrDefault(id, BigInteger.ZERO),
                () -> EcoOmniPlanRuntimeInvoker.molecularmanipulator$restore(plan, bindings,
                        tag.getCompound("runtime"), registries));
        var restored = new EcoOmniPlanExecution(plan, bindings, totals, remaining,
                EcoOmniPlanCodec.readAmounts(tag.getList("stock", Tag.TAG_COMPOUND), registries),
                EcoOmniPlanCodec.readAmounts(tag.getList("emitted", Tag.TAG_COMPOUND), registries), fuzzy,
                new BigInteger(tag.getString("bytes")), tag.hasUUID("planningId") ? tag.getUUID("planningId") : null, runtime);
        var expected = new LinkedHashMap<AEItemKey, BigInteger>();
        exactRemaining.forEach((pattern, count) -> { if (count.signum() > 0) expected.merge(pattern.getDefinition(), count, BigInteger::add); });
        var actual = new LinkedHashMap<AEItemKey, BigInteger>();
        restored.remainingByPattern().forEach((pattern, count) -> actual.merge(pattern.getDefinition(), count, BigInteger::add));
        if (!expected.equals(actual)) throw new IllegalArgumentException("ECO task IDs disagree with the Omni exact ledger");
        return restored;
    }

    public static final class Dispatch {
        private final EcoOmniPlanExecution owner;
        private final DispatchCandidate candidate;
        private final long revision;
        private boolean accepted;

        private Dispatch(EcoOmniPlanExecution owner, DispatchCandidate candidate, long revision) {
            this.owner = owner;
            this.candidate = candidate;
            this.revision = revision;
        }
        public int taskId() { return candidate.taskId(); }
        public IPatternDetails pattern() { return candidate.pattern(); }
        public boolean hasCycleProtection() {
            return owner.plan.phases().get(candidate.phaseIndex()).type()
                    != cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule.Type.DAG;
        }
        private void check() {
            if (accepted || revision != owner.revision) throw new IllegalStateException("Stale ECO dispatch token");
        }
        public BigInteger maximum() {
            check();
            var allowed = owner.access.ecoOmni$exactAllowance(candidate, owner.remaining.get(taskId()));
            // An input allocation run is an atomic choice. Never batch across a key transition.
            var completed = owner.totals.get(taskId()).subtract(owner.remaining.get(taskId()));
            for (var allocation : owner.plan.task(taskId()).inputAllocations()) {
                // ECO's default single run is a fixed input choice, also valid for the exact tail.
                if (allocation.runs().size() == 1) continue;
                long position = completed.longValueExact();
                long boundary = 0;
                for (var run : allocation.runs()) {
                    boundary = Math.addExact(boundary, run.crafts());
                    if (position < boundary) {
                        allowed = allowed.min(BigInteger.valueOf(boundary - position));
                        break;
                    }
                }
            }
            return allowed;
        }

        public Map<AEKey, Long> protectedSeeds() {
            check();
            return Map.copyOf(owner.access.ecoOmni$protectedSeed(candidate));
        }

        public ICraftingInventory guard(ICraftingInventory physical, Function<AEKey, BigInteger> physicalAmount) {
            check();
            var protectedAmounts = protectedSeeds();
            return new ICraftingInventory() {
                @Override public void insert(AEKey key, long amount, Actionable mode) { physical.insert(key, amount, mode); }
                @Override public long extract(AEKey key, long amount, Actionable mode) {
                    if (amount <= 0) return 0;
                    var free = physicalAmount.apply(key).subtract(BigInteger.valueOf(protectedAmounts.getOrDefault(key, 0L)))
                            .max(BigInteger.ZERO).min(BigInteger.valueOf(amount));
                    return free.signum() == 0 ? 0 : physical.extract(key, free.longValueExact(), mode);
                }
                @Override public Iterable<AEKey> findFuzzyTemplates(AEKey key) { return physical.findFuzzyTemplates(key); }
            };
        }

        /** Pass physical stock BEFORE reserving this batch's prototype. Includes Omni overflow. */
        public BigInteger limit(KeyCounter[] unitInputs, Function<AEKey, BigInteger> physicalAmount,
                BigInteger requested) {
            check();
            var upper = requested.min(maximum());
            var seeds = protectedSeeds();
            var units = unitTotals(unitInputs);
            for (var entry : units.entrySet()) {
                var free = physicalAmount.apply(entry.getKey())
                        .subtract(BigInteger.valueOf(seeds.getOrDefault(entry.getKey(), 0L))).max(BigInteger.ZERO);
                upper = upper.min(free.divide(entry.getValue()));
            }
            if (upper.signum() <= 0) return BigInteger.ZERO;
            if (hasCycleProtection()) {
                // ECO's batch limiter reads all competing inputs, not only this task's keys.
                var snapshot = new ECOExactInventory(key -> {});
                snapshot.setEnabled(true);
                var keys = new LinkedHashSet<AEKey>(units.keySet());
                for (var task : owner.plan.tasks()) for (var input : task.pattern().getInputs()) {
                    for (var possible : input.getPossibleInputs()) keys.add(possible.what());
                }
                var amounts = new LinkedHashMap<AEKey, BigInteger>();
                for (var key : keys) {
                    var amount = physicalAmount.apply(key);
                    if (amount.signum() > 0) amounts.put(key, amount);
                }
                snapshot.restore(amounts);
                long safe = owner.access.ecoOmni$limitCycleBatch(candidate, unitInputs, snapshot,
                        upper.min(LONG_MAX).longValueExact());
                upper = upper.min(BigInteger.valueOf(safe));
            }
            return upper;
        }

        /** Use this instead of CraftingCpuHelper for ECO inputs; provider sees the original pattern. */
        public KeyCounter[] extractInputs(ICraftingInventory guardedPhysical, Level level,
                KeyCounter expectedOutputs, KeyCounter containers) {
            check();
            var original = pattern();
            // AE2 may return a concrete ProcessingInput[]; clone would retain that runtime
            // component type and reject our IInput decorators with ArrayStoreException.
            var inputs = java.util.Arrays.copyOf(original.getInputs(), original.getInputs().length,
                    IPatternDetails.IInput[].class);
            var allocations = owner.plan.task(taskId()).inputAllocations();
            var completed = owner.totals.get(taskId()).subtract(owner.remaining.get(taskId()));
            for (int slot = 0; slot < inputs.length; slot++) {
                var delegate = inputs[slot];
                PlannedInputAllocation.Run selected = null;
                for (var allocation : allocations) if (allocation.slot() == slot) {
                    selected = allocation.runs().size() == 1 ? allocation.runs().getFirst()
                            : allocation.at(completed.longValueExact());
                }
                var allocationRun = selected;
                inputs[slot] = new IPatternDetails.IInput() {
                    @Override public GenericStack[] getPossibleInputs() {
                        return allocationRun == null ? delegate.getPossibleInputs()
                                : new GenericStack[]{new GenericStack(allocationRun.key(),
                                        allocationRun.amount() / delegate.getMultiplier())};
                    }
                    @Override public long getMultiplier() { return delegate.getMultiplier(); }
                    @Override public boolean isValid(AEKey key, Level world) {
                        boolean fuzzyMatch = false;
                        for (var possible : getPossibleInputs()) {
                            if (ECOProcessingComponentMatching.enabled(original, possible.what(), owner.fuzzy)
                                    && ECOProcessingComponentMatching.sameItem(key, possible.what())) fuzzyMatch = true;
                        }
                        if (allocationRun != null && !allocationRun.key().equals(key) && !fuzzyMatch) return false;
                        return delegate.isValid(key, world) || fuzzyMatch;
                    }
                    @Override public AEKey getRemainingKey(AEKey key) { return delegate.getRemainingKey(key); }
                };
            }
            IPatternDetails extractionView = new IPatternDetails() {
                @Override public AEItemKey getDefinition() { return original.getDefinition(); }
                @Override public IInput[] getInputs() { return inputs; }
                @Override public List<GenericStack> getOutputs() { return original.getOutputs(); }
            };
            return CraftingCpuHelper.extractPatternInputs(extractionView, guardedPhysical, level, expectedOutputs, containers);
        }

        public ECOProcessingComponentMatching.Scope openProviderScope() {
            check();
            return ECOProcessingComponentMatching.openDispatch(owner.fuzzy);
        }

        /** Exactly once, after real provider acceptance, with ORIGINAL per-craft inputs. */
        public void accepted(BigInteger count, KeyCounter[] unitInputs) {
            check();
            if (count == null || count.signum() <= 0 || count.compareTo(maximum()) > 0) {
                throw new IllegalArgumentException("ECO accepted count exceeds phase allowance");
            }
            var next = owner.remaining.get(taskId()).subtract(count);
            owner.remaining.put(taskId(), next);
            // ECO records bounded witness progress and startup seed consumption.
            owner.access.ecoOmni$acceptedExact(candidate, count, unitInputs);
            owner.revision++;
            accepted = true;
        }
    }

    private static Map<AEKey, BigInteger> unitTotals(KeyCounter[] inputs) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        for (var slot : inputs) for (var entry : slot) {
            if (entry.getLongValue() > 0) result.merge(entry.getKey(), BigInteger.valueOf(entry.getLongValue()), BigInteger::add);
        }
        return result;
    }
}
