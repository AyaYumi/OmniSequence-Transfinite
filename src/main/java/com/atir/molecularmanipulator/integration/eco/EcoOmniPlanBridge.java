package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.diagnostics.ECOCraftingPlanDiagnostics;
import cn.dancingsnow.neoecoae.api.me.planning.ECOPlanningResultRegistry;
import cn.dancingsnow.neoecoae.crafting.adapter.ae2.ECOExactCraftingPlan;
import cn.dancingsnow.neoecoae.crafting.planner.identity.PlanIdentity;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionPlan;
import cn.dancingsnow.neoecoae.crafting.planner.result.ECOExecutionSchedule;
import cn.dancingsnow.neoecoae.crafting.planner.result.ExecutionMode;
import com.appliedenhancements.api.AelisExactCraftingPlanApi;
import com.atir.molecularmanipulator.crafting.OmniExactCraftingState;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Captures the original ECO object before an external planner can rewrite it. */
public final class EcoOmniPlanBridge {
    private EcoOmniPlanBridge() {}

    public static boolean owns(ICraftingPlan plan) {
        if (EcoOmniParentOrder.submittingCarrier(plan)) return false;
        return plan instanceof ECOExactCraftingPlan || ownedResult(plan) != null
                || ECOPlanningResultRegistry.activeSubmissionMetadata(plan) != null;
    }

    private static cn.dancingsnow.neoecoae.crafting.planner.result.ECOPlanningResult ownedResult(ICraftingPlan plan) {
        var registered = ECOPlanningResultRegistry.findExact(plan);
        if (registered != null) return registered;
        if (plan instanceof ECOCraftingPlanDiagnostics diagnostics) {
            var attached = diagnostics.neoecoae$getPlanningResult();
            if (attached != null && PlanIdentity.matches(plan, attached.plan())) return attached;
        }
        // Signature recovery alone is not ownership: another planner can produce the same recipe/counts.
        return null;
    }

    public static Prepared prepare(ICraftingPlan source) {
        if (!owns(source)) return null;
        if (source.simulation() || source.finalOutput() == null || source.finalOutput().amount() <= 0) {
            throw new IllegalArgumentException("ECO plan is not executable");
        }
        var result = ownedResult(source);
        var contract = ECOPlanningResultRegistry.resolveContract(source, result);
        ECOExecutionPlan execution;
        Map<IPatternDetails, BigInteger> exactTasks = new LinkedHashMap<>();
        Map<AEKey, BigInteger> stock = Map.of();
        Map<AEKey, BigInteger> emitted = Map.of();
        Set<ResourceLocation> fuzzy = Set.of();
        BigInteger bytes = BigInteger.valueOf(source.bytes());
        if (source instanceof ECOExactCraftingPlan exact) {
            execution = exact.execution();
            exactTasks.putAll(exact.exactTasks());
            stock = exact.deferredStock();
            emitted = exact.deferredEmitted();
            fuzzy = exact.fuzzyPlanningItemIds();
            bytes = exact.theoreticalBytes();
        } else {
            if (contract == null || !contract.executable()) {
                throw new IllegalArgumentException("ECO execution contract is absent or blocked");
            }
            execution = contract.executionPlan();
            fuzzy = contract.fuzzyPlanningItemIds();
            if (result == null && ECOPlanningResultRegistry.activeSubmissionMetadata(source) != null)
                result = ECOPlanningResultRegistry.find(source);
            if (result != null) {
                result.exactPatternTimes().forEach((pattern, count) -> {
                    if (count.signum() > 0) exactTasks.put(pattern, count.toBigInteger());
                });
                stock = excess(result.exactUsedItems(), source.usedItems());
                emitted = excess(result.exactEmittedItems(), source.emittedItems());
                bytes = result.theoreticalBytes();
            }
        }
        if (exactTasks.isEmpty()) source.patternTimes().forEach((pattern, count) -> {
            if (count > 0) exactTasks.put(pattern, BigInteger.valueOf(count));
        });
        validateTasks(exactTasks);
        if (execution == null) execution = nativeExecution(source);
        if (execution.mode() == ExecutionMode.BLOCKED) throw new IllegalArgumentException("Blocked ECO execution");
        for (var task : execution.tasks()) {
            if (task.pattern().getDefinition() == null || task.identity().kind() == PlanIdentity.Kind.OBJECT
                    || !task.identity().equals(PlanIdentity.patternIdentityFor(task.pattern()))) {
                throw new IllegalArgumentException("ECO task identity is not persistable");
            }
            for (var allocation : task.inputAllocations()) {
                long multiplier = task.pattern().getInputs()[allocation.slot()].getMultiplier();
                for (var run : allocation.runs()) {
                    if (multiplier <= 0 || run.amount() % multiplier != 0) {
                        throw new IllegalArgumentException("ECO allocated input is not a whole input multiplier");
                    }
                }
            }
        }
        var totals = taskTotals(execution, exactTasks);
        return new Prepared(source, execution, totals, positive(stock), positive(emitted), Set.copyOf(fuzzy),
                bytes, contract == null ? null : contract.planningId());
    }

    private static Map<AEKey, BigInteger> excess(
            Map<AEKey, cn.dancingsnow.neoecoae.crafting.amount.PlannerAmount> amounts, KeyCounter window) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        amounts.forEach((key, amount) -> {
            var extra = amount.toBigInteger().subtract(BigInteger.valueOf(Math.max(0, window.get(key))));
            if (extra.signum() > 0) result.put(key, extra);
        });
        return result;
    }

    static Map<AEKey, BigInteger> positive(Map<AEKey, BigInteger> amounts) {
        var result = new LinkedHashMap<AEKey, BigInteger>();
        amounts.forEach((key, amount) -> {
            if (key == null || amount == null || amount.signum() <= 0) {
                throw new IllegalArgumentException("Invalid ECO deferred material");
            }
            result.put(key, amount);
        });
        return Map.copyOf(result);
    }

    private static void validateTasks(Map<IPatternDetails, BigInteger> tasks) {
        if (tasks.isEmpty()) throw new IllegalArgumentException("ECO plan has no tasks");
        var definitions = new LinkedHashSet<appeng.api.stacks.AEItemKey>();
        tasks.forEach((pattern, count) -> {
            if (pattern == null || pattern.getDefinition() == null || count == null || count.signum() <= 0
                    || !definitions.add(pattern.getDefinition())) {
                throw new IllegalArgumentException("ECO task cannot be represented by Omni's pattern ledger");
            }
        });
    }

    static Map<Integer, BigInteger> taskTotals(ECOExecutionPlan execution,
            Map<IPatternDetails, BigInteger> exact) {
        var remaining = new LinkedHashMap<>(exact);
        var result = new LinkedHashMap<Integer, BigInteger>();
        var last = new LinkedHashMap<IPatternDetails, Integer>();
        for (var task : execution.tasks()) {
            var matches = remaining.keySet().stream()
                    .filter(pattern -> PlanIdentity.samePattern(task.pattern(), pattern)).toList();
            if (matches.size() != 1) throw new IllegalArgumentException("ECO task identity changed");
            var pattern = matches.getFirst();
            var count = BigInteger.valueOf(task.totalCount());
            var rest = remaining.get(pattern).subtract(count);
            if (rest.signum() < 0) throw new IllegalArgumentException("ECO task vector exceeds exact task count");
            result.put(task.id(), count);
            remaining.put(pattern, rest);
            last.put(pattern, task.id());
        }
        remaining.forEach((pattern, rest) -> {
            Integer id = last.get(pattern);
            if (id == null) throw new IllegalArgumentException("ECO plan has an unscheduled exact task");
            // The final owner retains the exact tail after the long cycle witness.
            if (rest.signum() > 0) {
                if (execution.task(id).inputAllocations().stream().anyMatch(allocation -> allocation.runs().size() != 1)) {
                    throw new IllegalArgumentException("ECO variable input allocation does not cover the exact tail");
                }
                result.merge(id, rest, BigInteger::add);
            }
        });
        return Map.copyOf(result);
    }

    private static ECOExecutionPlan nativeExecution(ICraftingPlan source) {
        var tasks = new ArrayList<ECOExecutionPlan.TaskSpec>();
        source.patternTimes().forEach((pattern, count) -> {
            if (count > 0) tasks.add(new ECOExecutionPlan.TaskSpec(tasks.size(),
                    PlanIdentity.patternIdentityFor(pattern), pattern, ECOExecutionPlan.PatternRuntimeInfo.from(pattern),
                    count, 0, ECOExecutionPlan.TaskKind.DAG));
        });
        var ids = tasks.stream().map(ECOExecutionPlan.TaskSpec::id).toList();
        var phase = new ECOExecutionPlan.PhaseSpec(0, 0, ECOExecutionSchedule.Type.DAG, ids, List.of(), List.of());
        var schedule = new ECOExecutionSchedule(List.of(new ECOExecutionSchedule.ComponentExecutionPhase(
                0, ECOExecutionSchedule.Type.DAG, Set.copyOf(source.patternTimes().keySet()), List.of())));
        return new ECOExecutionPlan(PlanIdentity.of(source), ExecutionMode.NATIVE, List.copyOf(tasks), List.of(phase), schedule);
    }

    public record Prepared(ICraftingPlan original, ECOExecutionPlan execution, Map<Integer, BigInteger> taskTotals,
            Map<AEKey, BigInteger> deferredStock, Map<AEKey, BigInteger> deferredEmitted,
            Set<ResourceLocation> fuzzyPlanningItemIds, BigInteger theoreticalBytes, UUID planningId) {
        public Map<IPatternDetails, BigInteger> exactTasks() {
            var tasks = new LinkedHashMap<IPatternDetails, BigInteger>();
            execution.tasks().forEach(task -> tasks.merge(task.pattern(), taskTotals.get(task.id()), BigInteger::add));
            return Map.copyOf(tasks);
        }

        /** The main CPU explicitly installs this even when all counts happen to fit long. */
        public OmniExactCraftingState newExactState() {
            var state = OmniExactCraftingState.create(exactTasks(), Map.of());
            state.setOutputRemaining(BigInteger.valueOf(original.finalOutput().amount()));
            return state;
        }

        /** Optional public Aelis projection; keep this Prepared alongside it. */
        public ICraftingPlan aelisProjection() {
            return AelisExactCraftingPlanApi.attachExecutionMetadata(original,
                    BigInteger.valueOf(original.finalOutput().amount()), exactTasks(), Map.of());
        }

        public EcoOmniPlanExecution start(Iterable<IPatternDetails> submittedPatterns) {
            return EcoOmniPlanExecution.start(this, submittedPatterns);
        }
    }
}
