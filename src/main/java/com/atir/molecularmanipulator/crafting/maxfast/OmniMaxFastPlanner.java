package com.atir.molecularmanipulator.crafting.maxfast;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ChildCraftingSimulationState;
import appeng.crafting.inv.CraftingSimulationState;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.MolecularReusableInputAdapters;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeNodeBridge;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeProcessBridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

public final class OmniMaxFastPlanner {
    private OmniMaxFastPlanner() {
    }

    @FunctionalInterface
    public interface PauseCheckpoint {
        void pause() throws InterruptedException;
    }

    public static final class Session {
        private final int maxNodes;
        private final long compileBudgetNanos;
        private final PauseCheckpoint pauseCheckpoint;
        private CraftingTreeNode root;
        private Graph graph;
        private String structuralFailure;
        private String transactionalRuntimeFailure;
        private Throwable structuralError;
        private long compileNanos;

        public Session(int maxNodes, int compileBudgetMillis,
                PauseCheckpoint pauseCheckpoint) {
            this.maxNodes = maxNodes;
            this.compileBudgetNanos = TimeUnit.MILLISECONDS.toNanos(compileBudgetMillis);
            this.pauseCheckpoint = pauseCheckpoint;
        }

        public Result tryExecute(CraftingTreeNode requestedRoot, CraftingSimulationState inventory,
                long requestedAmount, boolean simulation, KeyCounter missingItems)
                throws InterruptedException {
            if (requestedAmount <= 0) {
                return Result.fallback("invalid_request_amount", 0, 0, 0, 0, 0, null);
            }
            if (root != null && root != requestedRoot) {
                return Result.fallback("calculation_root_changed", 0, 0, 0, 0, 0, null);
            }
            root = requestedRoot;

            if (transactionalRuntimeFailure != null) {
                return Result.fallback(transactionalRuntimeFailure, 0, 0,
                        0, compileNanos, 0, null);
            }

            if (graph == null && structuralFailure == null) {
                long startedAt = System.nanoTime();
                var compiler = new Compiler(maxNodes, startedAt + compileBudgetNanos,
                        pauseCheckpoint);
                try {
                    graph = compiler.compile(requestedRoot);
                } catch (Fallback fallback) {
                    structuralFailure = fallback.reason;
                } catch (RuntimeException exception) {
                    structuralFailure = "internal_compile_exception";
                    structuralError = exception;
                } finally {
                    compileNanos = Math.max(0,
                            System.nanoTime() - startedAt - compiler.pausedNanos);
                }
            }

            if (graph == null) {
                return Result.fallback(structuralFailure, 0, 0, 0,
                        compileNanos, 0, structuralError);
            }
            String executionSafetyFailure = graph.executionSafetyFailure();
            if (executionSafetyFailure != null) {
                transactionalRuntimeFailure = executionSafetyFailure;
                return Result.fallback(transactionalRuntimeFailure, graph.nodes.size(),
                        graph.mergedOccurrences, graph.barrierCount,
                        compileNanos, 0, null);
            }

            long startedAt = System.nanoTime();
            try {
                execute(graph, inventory, requestedAmount, simulation, missingItems,
                        pauseCheckpoint);
                return Result.applied(graph.nodes.size(), graph.mergedOccurrences, graph.barrierCount,
                        graph.logicalNodeCount, graph.requiresNativeNodeCount(),
                        compileNanos, System.nanoTime() - startedAt);
            } catch (CraftBranchFailure failure) {
                if (graph.requiresTransactionalFallback()) {
                    transactionalRuntimeFailure = "transactional_graph_failed";
                    return Result.fallback(transactionalRuntimeFailure, graph.nodes.size(),
                            graph.mergedOccurrences, graph.barrierCount,
                            compileNanos, System.nanoTime() - startedAt, null);
                }
                return Result.branchFailure(graph.nodes.size(), graph.mergedOccurrences,
                        graph.barrierCount, compileNanos, System.nanoTime() - startedAt, failure);
            } catch (Fallback fallback) {
                if (graph.requiresTransactionalFallback()) {
                    transactionalRuntimeFailure = "transactional_graph_" + fallback.reason;
                }
                return Result.fallback(fallback.reason, graph.nodes.size(), graph.mergedOccurrences,
                        graph.barrierCount,
                        compileNanos, System.nanoTime() - startedAt, null);
            } catch (RuntimeException exception) {
                if (graph.requiresTransactionalFallback()) {
                    transactionalRuntimeFailure =
                            "transactional_graph_internal_execution_exception";
                }
                return Result.fallback("internal_execution_exception", graph.nodes.size(),
                        graph.mergedOccurrences, graph.barrierCount,
                        compileNanos, System.nanoTime() - startedAt, exception);
            }
        }
    }

    public record Result(boolean applied, String fallbackReason, int uniqueNodes,
            long mergedOccurrences, int barrierCount, long logicalNodeCount, long compileNanos,
            long executionNanos, boolean nativeNodeCount,
            CraftBranchFailure branchFailure, Throwable error) {
        private static Result applied(int uniqueNodes, long mergedOccurrences, int barrierCount,
                long logicalNodeCount, boolean nativeNodeCount,
                long compileNanos, long executionNanos) {
            return new Result(true, null, uniqueNodes, mergedOccurrences, barrierCount,
                    logicalNodeCount, compileNanos, executionNanos, nativeNodeCount, null, null);
        }

        private static Result branchFailure(int uniqueNodes, long mergedOccurrences, int barrierCount,
                long compileNanos, long executionNanos, CraftBranchFailure failure) {
            return new Result(false, null, uniqueNodes, mergedOccurrences, barrierCount,
                    0, compileNanos, executionNanos, false, failure, null);
        }

        private static Result fallback(String reason, int uniqueNodes, long mergedOccurrences,
                int barrierCount,
                long compileNanos, long executionNanos, Throwable error) {
            return new Result(false, reason, uniqueNodes, mergedOccurrences, barrierCount,
                    0, compileNanos, executionNanos, false, null, error);
        }
    }

    private static void execute(Graph graph, CraftingSimulationState parent,
            long requestedAmount, boolean simulation, KeyCounter missingItems,
            PauseCheckpoint pauseCheckpoint)
            throws Fallback, CraftBranchFailure, InterruptedException {
        var inventory = new ChildCraftingSimulationState(parent);
        if (graph.requiresTransactionalFallback()) {
            executeTransactionalNode(
                    graph, graph.rootIndex, inventory, requestedAmount, pauseCheckpoint);
            inventory.applyDiff(parent);
            return;
        }
        var requests = new long[graph.nodes.size()];
        var stagedMissing = new KeyCounter();
        requests[graph.rootIndex] = requestedAmount;

        for (int nodeIndex : graph.topologicalOrder) {
            checkpoint(pauseCheckpoint);
            long requestMultipliers = requests[nodeIndex];
            if (requestMultipliers <= 0) {
                continue;
            }

            Node node = graph.nodes.get(nodeIndex);
            if (node.barrier) {
                if (node.occurrences.size() != 1) {
                    throw new Fallback("shared_unsafe_boundary:" + node.barrierReason);
                }
                boolean nestedRecursiveDurability = node.index != graph.rootIndex
                        && "recursive_durability_input".equals(node.barrierReason);
                if (tryExecuteReusableContainerBoundary(
                        node, inventory, requestMultipliers, pauseCheckpoint)) {
                    continue;
                }
                if (nestedRecursiveDurability) {
                    // A rejected speculative boundary must not invoke the native
                    // node and then continue through the aggregated graph. Abort
                    // the child transaction and let the caller rerun the whole
                    // tree through AE2 instead.
                    throw new Fallback("nested_recursive_durability_boundary_rejected");
                }
                var bridge = (OmniCraftingTreeNodeBridge) node.occurrences.getFirst();
                bridge.molecularmanipulator$request(inventory, requestMultipliers, null);
                continue;
            }
            validateTemplates(node, inventory, pauseCheckpoint);
            long requestedItems = checkedMultiply(node.amount, requestMultipliers,
                    "request_amount_overflow");
            inventory.addStackBytes(node.key, node.amount, requestMultipliers);

            long available = inventory.extract(node.key, requestedItems, Actionable.SIMULATE);
            long extractedMultipliers = Math.min(requestMultipliers, available / node.amount);
            if (extractedMultipliers > 0) {
                long extractedAmount = node.amount * extractedMultipliers;
                long extracted = inventory.extract(node.key, extractedAmount, Actionable.MODULATE);
                if (extracted != extractedAmount) {
                    throw new IllegalStateException("Crafting simulation inventory changed during exact extraction");
                }
            }

            long remainingMultipliers = requestMultipliers - extractedMultipliers;
            if (remainingMultipliers == 0) {
                continue;
            }

            long totalRequestedItems = checkedMultiply(node.amount, remainingMultipliers,
                    "remaining_request_overflow");
            if (node.emitter) {
                inventory.emitItems(node.key, totalRequestedItems);
                continue;
            }
            if (node.terminal) {
                if (!simulation) {
                    throw new CraftBranchFailure(node.key, totalRequestedItems);
                }
                // AE2 records an exact terminal shortfall during the simulated
                // attempt and then lets parent patterns continue building the
                // plan. Stage it until the entire aggregated graph succeeds so
                // a later fallback cannot leak or duplicate missing entries.
                stagedMissing.add(node.key, totalRequestedItems);
                continue;
            }

            long patternTimes = ceilDiv(totalRequestedItems, node.outputPerPattern);
            for (Edge edge : node.edges) {
                long childRequests = checkedMultiply(edge.requestMultiplier, patternTimes,
                        "child_request_overflow");
                requests[edge.childIndex] = checkedAdd(
                        requests[edge.childIndex], childRequests,
                        "merged_request_overflow");
            }

            long remainder = totalRequestedItems % node.outputPerPattern;
            long surplus = remainder == 0 ? 0 : node.outputPerPattern - remainder;
            if (surplus > 0) {
                inventory.insert(node.key, surplus, Actionable.MODULATE);
            }
            inventory.addCrafting(node.details, patternTimes);
            inventory.addBytes(patternTimes);
        }

        inventory.applyDiff(parent);
        missingItems.addAll(stagedMissing);
    }

    private static void executeTransactionalNode(Graph graph, int nodeIndex,
            CraftingSimulationState inventory, long requestMultipliers,
            PauseCheckpoint pauseCheckpoint)
            throws Fallback, InterruptedException {
        checkpoint(pauseCheckpoint);
        if (requestMultipliers <= 0) {
            return;
        }

        Node node = graph.nodes.get(nodeIndex);
        if (node.barrier) {
            throw new Fallback("transactional_barrier:" + node.barrierReason);
        }
        validateTemplates(node, inventory, pauseCheckpoint);

        long requestedItems = checkedMultiply(
                node.amount, requestMultipliers, "request_amount_overflow");
        inventory.addStackBytes(node.key, node.amount, requestMultipliers);

        long available = inventory.extract(node.key, requestedItems, Actionable.SIMULATE);
        long extractedMultipliers = Math.min(
                requestMultipliers, available / node.amount);
        if (extractedMultipliers > 0) {
            long extractedAmount = node.amount * extractedMultipliers;
            long extracted = inventory.extract(
                    node.key, extractedAmount, Actionable.MODULATE);
            if (extracted != extractedAmount) {
                throw new IllegalStateException(
                        "Crafting simulation inventory changed during exact extraction");
            }
        }

        long remainingMultipliers = requestMultipliers - extractedMultipliers;
        if (remainingMultipliers == 0) {
            return;
        }
        long totalRequestedItems = checkedMultiply(
                node.amount, remainingMultipliers, "remaining_request_overflow");
        if (node.emitter) {
            inventory.emitItems(node.key, totalRequestedItems);
            return;
        }
        if (node.terminal) {
            throw new Fallback("missing_terminal_input");
        }

        long patternTimes = ceilDiv(totalRequestedItems, node.outputPerPattern);
        var returnedReusableInputs = new KeyCounter();
        for (OrderedGraphInput orderedInput : node.orderedInputs) {
            checkpoint(pauseCheckpoint);
            if (orderedInput.reusable()) {
                GraphReusableInput reusableInput = orderedInput.reusableInput;
                if (reusableInput.mode != BoundaryInputMode.INVARIANT_REUSABLE
                        || !leaseInvariantReusableInput(
                                inventory, reusableInput, patternTimes,
                                returnedReusableInputs, pauseCheckpoint)) {
                    throw new Fallback("reusable_input_unavailable");
                }
            } else {
                long childRequests = checkedMultiply(
                        orderedInput.multiplier, patternTimes,
                        "child_request_overflow");
                executeTransactionalNode(
                        graph, orderedInput.childIndex, inventory,
                        childRequests, pauseCheckpoint);
            }
        }

        for (var stack : returnedReusableInputs) {
            inventory.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
            long logicalReturns = checkedMultiply(
                    stack.getLongValue(), patternTimes,
                    "reusable_return_bytes_overflow");
            inventory.addStackBytes(stack.getKey(), 1, logicalReturns);
        }

        long remainder = totalRequestedItems % node.outputPerPattern;
        long surplus = remainder == 0 ? 0 : node.outputPerPattern - remainder;
        if (surplus > 0) {
            inventory.insert(node.key, surplus, Actionable.MODULATE);
        }
        inventory.addCrafting(node.details, patternTimes);
        inventory.addBytes(patternTimes);
    }

    private static boolean tryExecuteReusableContainerBoundary(Node node,
            CraftingSimulationState parent, long requestedAmount,
            PauseCheckpoint pauseCheckpoint)
            throws CraftBranchFailure, InterruptedException {
        try {
            return tryExecuteReusableContainerBoundaryUnchecked(
                    node, parent, requestedAmount, pauseCheckpoint);
        } catch (NoSuchElementException exception) {
            return rejectReusableBoundary(node, null, "provider_missing", exception);
        }
    }

    private static boolean tryExecuteReusableContainerBoundaryUnchecked(Node node,
            CraftingSimulationState parent, long requestedAmount,
            PauseCheckpoint pauseCheckpoint)
            throws CraftBranchFailure, InterruptedException {
        if (!"container_items".equals(node.barrierReason)
                && !"recursive_durability_input".equals(node.barrierReason)) {
            return rejectReusableBoundary(node, null,
                    "unsupported_barrier:" + node.barrierReason, null);
        }

        var nodeBridge = (OmniCraftingTreeNodeBridge) node.occurrences.getFirst();
        List<CraftingTreeProcess> processes = nodeBridge.molecularmanipulator$getProcesses();
        if (processes == null || processes.size() != 1) {
            return rejectReusableBoundary(node, null, "pattern_candidate_count", null);
        }
        if (nodeBridge.molecularmanipulator$canEmit()) {
            return rejectReusableBoundary(node, null, "emitting_boundary", null);
        }

        var process = (OmniCraftingTreeProcessBridge) processes.getFirst();
        if (!process.molecularmanipulator$hasContainerItems()) {
            return rejectReusableBoundary(node, null, "container_flag_missing", null);
        }

        IPatternDetails details = process.molecularmanipulator$getDetails();
        if (details == null) {
            return rejectReusableBoundary(node, null, "missing_pattern_details", null);
        }
        IPatternDetails.IInput[] inputs = details.getInputs();
        Map<CraftingTreeNode, Long> childNodes = process.molecularmanipulator$getChildNodes();
        if (inputs == null || childNodes == null || inputs.length != childNodes.size()) {
            return rejectReusableBoundary(node, details, "dynamic_input_layout", null);
        }
        long outputPerPattern = 0;
        for (var output : details.getOutputs()) {
            if (output == null || output.what() == null || output.amount() <= 0) {
                return rejectReusableBoundary(node, details, "invalid_output", null);
            }
            if (!node.key.equals(output.what())) {
                return rejectReusableBoundary(node, details, "secondary_output", null);
            }
            outputPerPattern = saturatedAdd(outputPerPattern, output.amount());
        }
        if (outputPerPattern <= 0) {
            return rejectReusableBoundary(node, details, "missing_primary_output", null);
        }

        var attempt = new ChildCraftingSimulationState(parent);
        attempt.addStackBytes(node.key, node.amount, requestedAmount);
        long remainingAmount = requestedAmount;
        for (InputTemplate template : nodeBridge.molecularmanipulator$getValidItemTemplates(attempt)) {
            long extracted = extractTemplateMultipliers(attempt, template, remainingAmount);
            remainingAmount -= extracted;
            if (remainingAmount == 0) {
                attempt.applyDiff(parent);
                return true;
            }
        }

        long totalRequestedItems = saturatedMultiply(node.amount, remainingAmount);
        long patternTimes = ceilDiv(totalRequestedItems, outputPerPattern);
        var inputPlans = new ArrayList<BoundaryInputPlan>(inputs.length);
        int deterministicDamageInputs = 0;
        int inputIndex = 0;
        for (var entry : childNodes.entrySet()) {
            checkpoint(pauseCheckpoint);
            CraftingTreeNode child = entry.getKey();
            var childBridge = (OmniCraftingTreeNodeBridge) child;
            IPatternDetails.IInput input = inputs[inputIndex++];
            if (childBridge.molecularmanipulator$getParentInput() != input) {
                return rejectReusableBoundary(node, details, "dynamic_input_identity", null);
            }
            if (node.key.equals(childBridge.molecularmanipulator$getWhat())) {
                return rejectReusableBoundary(node, details, "self_referencing_input", null);
            }

            BoundaryInputClassification classification = classifyBoundaryInput(
                    input, childBridge, attempt, pauseCheckpoint);
            if (classification.rejectionReason() != null) {
                return rejectReusableBoundary(node, details, classification.rejectionReason(), null);
            }
            BoundaryInputMode mode = classification.mode();

            long multiplier = input.getMultiplier();
            if (multiplier <= 0 || entry.getValue() == null || entry.getValue() != multiplier) {
                return rejectReusableBoundary(node, details, "invalid_input_multiplier", null);
            }
            if (mode == BoundaryInputMode.DETERMINISTIC_DAMAGE) {
                deterministicDamageInputs++;
                if (multiplier != 1) {
                    return rejectReusableBoundary(node, details,
                            "unsupported_damage_input_multiplier", null);
                }
                if (deterministicDamageInputs > 1) {
                    return rejectReusableBoundary(node, details,
                            "multiple_damage_inputs", null);
                }
            }
            long childRequest = switch (mode) {
                case INVARIANT_REUSABLE -> multiplier;
                case CONSUMABLE -> saturatedMultiply(multiplier, patternTimes);
                case DETERMINISTIC_DAMAGE -> 0;
                case UNSAFE -> throw new IllegalStateException(
                        "Unsafe reusable boundary input escaped classification");
            };
            inputPlans.add(new BoundaryInputPlan(
                    input, childBridge, mode, childRequest, multiplier));
        }

        var containerItems = new KeyCounter();
        // Resolve the speculative durability optimization before issuing any
        // ordinary child request. A rejected durability boundary must not leak
        // missing-item accounting into AE2 before the native planner fallback.
        for (BoundaryInputPlan inputPlan : inputPlans) {
            if (inputPlan.mode == BoundaryInputMode.DETERMINISTIC_DAMAGE) {
                if (!allocateDeterministicDamageInput(attempt, inputPlan.input,
                        inputPlan.child, inputPlan.multiplier, patternTimes,
                        pauseCheckpoint)) {
                    return rejectReusableBoundary(node, details,
                            "insufficient_deterministic_damage_capacity", null);
                }
            }
        }
        for (BoundaryInputPlan inputPlan : inputPlans) {
            if (inputPlan.mode != BoundaryInputMode.DETERMINISTIC_DAMAGE) {
                inputPlan.child.molecularmanipulator$request(
                        attempt, inputPlan.requestedAmount, containerItems);
            }
        }

        for (var stack : containerItems) {
            attempt.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
            attempt.addStackBytes(stack.getKey(), stack.getLongValue(), 1);
        }
        for (var output : details.getOutputs()) {
            attempt.insert(output.what(), saturatedMultiply(output.amount(), patternTimes),
                    Actionable.MODULATE);
        }
        attempt.addCrafting(details, patternTimes);
        attempt.addBytes(patternTimes);

        long produced = attempt.extract(node.key, totalRequestedItems, Actionable.MODULATE);
        if (produced != totalRequestedItems) {
            return rejectReusableBoundary(node, details, "produced_output_mismatch", null);
        }
        attempt.applyDiff(parent);
        return true;
    }

    private static boolean rejectReusableBoundary(Node node, IPatternDetails details,
            String reason, RuntimeException exception) {
        if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
            String pattern = describePattern(details);
            if (exception == null) {
                MolecularManipulator.LOGGER.info(
                        "Omni MAX_FAST reusable boundary fallback: key={}, amount={}, barrier={}, pattern={}, reason={}",
                        node.key, node.amount, node.barrierReason, pattern, reason);
            } else {
                MolecularManipulator.LOGGER.warn(
                        "Omni MAX_FAST reusable boundary failed: key={}, amount={}, barrier={}, pattern={}, reason={}",
                        node.key, node.amount, node.barrierReason, pattern, reason, exception);
            }
        }
        return false;
    }

    private static String describePattern(IPatternDetails details) {
        if (details == null) {
            return "unknown";
        }
        try {
            return String.valueOf(details.getDefinition());
        } catch (RuntimeException exception) {
            return details.getClass().getName();
        }
    }

    private static BoundaryInputClassification classifyBoundaryInput(IPatternDetails.IInput input,
            OmniCraftingTreeNodeBridge child, CraftingSimulationState inventory,
            PauseCheckpoint pauseCheckpoint)
            throws InterruptedException {
        BoundaryInputMode mode = classifyRemainingKey(
                input, child.molecularmanipulator$getWhat(),
                child.molecularmanipulator$getLevel());
        if (mode == BoundaryInputMode.UNSAFE) {
            return BoundaryInputClassification.rejected(
                    "unsupported_container_transition");
        }
        for (InputTemplate template : child.molecularmanipulator$getValidItemTemplates(inventory)) {
            checkpoint(pauseCheckpoint);
            BoundaryInputMode templateMode = classifyRemainingKey(
                    input, template.key(), child.molecularmanipulator$getLevel());
            if (templateMode == BoundaryInputMode.UNSAFE) {
                return BoundaryInputClassification.rejected(
                        "unsupported_container_transition");
            }
            if (templateMode != mode) {
                return BoundaryInputClassification.rejected(
                        "mixed_container_transition_modes");
            }
        }
        return BoundaryInputClassification.accepted(mode);
    }

    private static BoundaryInputMode classifyRemainingKey(IPatternDetails.IInput input,
            AEKey key, net.minecraft.world.level.Level level) {
        var analysis = MolecularReusableInputAdapters.analyze(input, key, level, 2);
        return switch (analysis.mode()) {
            case CONSUMABLE -> BoundaryInputMode.CONSUMABLE;
            case INVARIANT_REUSABLE -> BoundaryInputMode.INVARIANT_REUSABLE;
            case DETERMINISTIC_DAMAGE -> BoundaryInputMode.DETERMINISTIC_DAMAGE;
            case UNSUPPORTED -> BoundaryInputMode.UNSAFE;
        };
    }

    /**
     * Reserves real finite-durability tools for a pattern boundary.
     *
     * <p>Each selected group contains {@code inputMultiplier} tools with the
     * exact same AE key, so a single pattern extraction never has to mix damage
     * states. Existing tools contribute their proven remaining capacity first.
     * If that is insufficient, the fresh primary tool is requested in one
     * recursive batch for the remaining capacity instead of returning to AE2's
     * one-pattern-at-a-time container loop.</p>
     *
     * <p>We intentionally do not credit the final damaged tools back into the
     * planning inventory: execution may choose another valid damage state, and
     * omitting those remainders is conservative while the real CPU still
     * returns every exact remainder produced by the recipe. Input and remainder
     * byte costs are nevertheless recorded for every logical use.</p>
     */
    private static boolean allocateDeterministicDamageInput(
            CraftingSimulationState inventory, IPatternDetails.IInput input,
            OmniCraftingTreeNodeBridge child, long inputMultiplier,
            long patternTimes, PauseCheckpoint pauseCheckpoint)
            throws CraftBranchFailure, InterruptedException {
        if (inputMultiplier != 1 || patternTimes <= 0
                || child.molecularmanipulator$getAmount() != 1) {
            return false;
        }

        var selections = new ArrayList<FiniteToolSelection>();
        var seenKeys = new HashSet<AEKey>();
        long remainingPatterns = patternTimes;

        for (InputTemplate template : child.molecularmanipulator$getValidItemTemplates(inventory)) {
            checkpoint(pauseCheckpoint);
            if (remainingPatterns == 0) {
                break;
            }
            if (template == null || template.key() == null || template.amount() != 1) {
                return false;
            }
            if (!seenKeys.add(template.key())) {
                continue;
            }

            long available = inventory.extract(
                    template.key(), Long.MAX_VALUE, Actionable.SIMULATE);
            long availableGroups = available / inputMultiplier;
            if (availableGroups <= 0) {
                continue;
            }

            var analysis = MolecularReusableInputAdapters.analyze(
                    input, template.key(), child.molecularmanipulator$getLevel(),
                    remainingPatterns);
            if (analysis.mode()
                    != MolecularReusableInputAdapters.Mode.DETERMINISTIC_DAMAGE
                    || analysis.safeCrafts() <= 0) {
                return false;
            }

            long groupsNeeded = ceilDiv(
                    remainingPatterns, analysis.safeCrafts());
            long selectedGroups = Math.min(availableGroups, groupsNeeded);
            long toolAmount;
            try {
                toolAmount = Math.multiplyExact(
                        selectedGroups, inputMultiplier);
            } catch (ArithmeticException exception) {
                return false;
            }

            selections.add(new FiniteToolSelection(
                    template.key(), toolAmount));
            long coveredPatterns = saturatedMultiply(
                    selectedGroups, analysis.safeCrafts());
            long usedPatterns = Math.min(remainingPatterns, coveredPatterns);
            remainingPatterns -= usedPatterns;
        }

        long newToolAmount = 0;
        if (remainingPatterns > 0) {
            AEKey freshTool = child.molecularmanipulator$getWhat();
            var freshAnalysis = MolecularReusableInputAdapters.analyze(
                    input, freshTool, child.molecularmanipulator$getLevel(),
                    remainingPatterns);
            if (freshAnalysis.mode()
                    != MolecularReusableInputAdapters.Mode.DETERMINISTIC_DAMAGE
                    || freshAnalysis.safeCrafts() <= 0) {
                return false;
            }

            long newToolGroups = ceilDiv(
                    remainingPatterns, freshAnalysis.safeCrafts());
            try {
                newToolAmount = Math.multiplyExact(
                        newToolGroups, inputMultiplier);
            } catch (ArithmeticException exception) {
                return false;
            }
        }

        long logicalUses;
        try {
            logicalUses = Math.multiplyExact(inputMultiplier, patternTimes);
        } catch (ArithmeticException exception) {
            return false;
        }
        if (newToolAmount > logicalUses) {
            return false;
        }

        for (FiniteToolSelection selection : selections) {
            long extracted = inventory.extract(
                    selection.key, selection.amount, Actionable.MODULATE);
            if (extracted != selection.amount) {
                throw new IllegalStateException(
                        "Crafting simulation inventory changed during finite-tool extraction");
            }
        }

        if (newToolAmount > 0) {
            child.molecularmanipulator$request(inventory, newToolAmount, null);
        }

        // Requesting newly crafted tools already charged their first logical
        // input use. Existing tools and all later reuses still need that cost.
        long additionalInputUses = logicalUses - newToolAmount;
        if (additionalInputUses > 0) {
            inventory.addStackBytes(
                    child.molecularmanipulator$getWhat(), 1,
                    additionalInputUses);
        }

        // Runtime is allowed to choose another valid damage-state ordering.
        // Charge the maximum possible remainder volume so CPU storage is never
        // underestimated even when fewer tools actually break than planned.
        if (logicalUses > 0) {
            inventory.addStackBytes(
                    child.molecularmanipulator$getWhat(), 1, logicalUses);
        }
        return true;
    }

    private static boolean leaseInvariantReusableInput(
            CraftingSimulationState inventory, GraphReusableInput reusableInput,
            long patternTimes, KeyCounter returned,
            PauseCheckpoint pauseCheckpoint) throws InterruptedException, Fallback {
        if (reusableInput.multiplier <= 0
                || patternTimes <= 0
                || reusableInput.child.molecularmanipulator$getAmount() != 1) {
            return false;
        }

        long remaining = reusableInput.multiplier;
        var selected = new KeyCounter();
        for (InputTemplate template
                : reusableInput.child.molecularmanipulator$getValidItemTemplates(inventory)) {
            checkpoint(pauseCheckpoint);
            if (remaining == 0) {
                break;
            }
            if (template == null || template.key() == null || template.amount() != 1) {
                return false;
            }
            long available = inventory.extract(
                    template.key(), Long.MAX_VALUE, Actionable.SIMULATE);
            available = Math.max(0, available - selected.get(template.key()));
            long selectedAmount = Math.min(remaining, available);
            if (selectedAmount == 0) {
                continue;
            }
            var analysis = MolecularReusableInputAdapters.analyze(
                    reusableInput.input, template.key(),
                    reusableInput.child.molecularmanipulator$getLevel(), 2);
            if (analysis.mode()
                    != MolecularReusableInputAdapters.Mode.INVARIANT_REUSABLE
                    || !template.key().equals(analysis.finalKey())) {
                return false;
            }
            selected.add(template.key(), selectedAmount);
            remaining -= selectedAmount;
        }
        if (remaining != 0) {
            return false;
        }

        for (var selection : selected) {
            checkpoint(pauseCheckpoint);
            long extracted = inventory.extract(
                    selection.getKey(), selection.getLongValue(), Actionable.MODULATE);
            if (extracted != selection.getLongValue()) {
                throw new IllegalStateException(
                        "Crafting simulation inventory changed during reusable extraction");
            }
            long returnedAmount = checkedAdd(
                    returned.get(selection.getKey()), extracted,
                    "reusable_return_count_overflow");
            returned.set(selection.getKey(), returnedAmount);
        }

        long logicalUses = checkedMultiply(
                reusableInput.multiplier, patternTimes,
                "reusable_input_bytes_overflow");
        inventory.addStackBytes(
                reusableInput.child.molecularmanipulator$getWhat(), 1,
                logicalUses);
        return true;
    }

    private static long extractTemplateMultipliers(CraftingSimulationState inventory,
            InputTemplate template, long requestedMultipliers) {
        long extractLimit = saturatedMultiply(template.amount(), requestedMultipliers);
        long available = inventory.extract(template.key(), extractLimit, Actionable.SIMULATE);
        long extractedMultipliers = Math.min(requestedMultipliers, available / template.amount());
        if (extractedMultipliers <= 0) {
            return 0;
        }
        long extractedAmount = template.amount() * extractedMultipliers;
        long extracted = inventory.extract(template.key(), extractedAmount, Actionable.MODULATE);
        if (extracted != extractedAmount) {
            throw new IllegalStateException("Crafting simulation inventory changed during template extraction");
        }
        return extractedMultipliers;
    }

    private enum BoundaryInputMode {
        CONSUMABLE,
        INVARIANT_REUSABLE,
        DETERMINISTIC_DAMAGE,
        UNSAFE
    }

    private record BoundaryInputClassification(BoundaryInputMode mode, String rejectionReason) {
        private static BoundaryInputClassification accepted(BoundaryInputMode mode) {
            return new BoundaryInputClassification(mode, null);
        }

        private static BoundaryInputClassification rejected(String reason) {
            return new BoundaryInputClassification(null, reason);
        }
    }

    private record BoundaryInputPlan(IPatternDetails.IInput input,
            OmniCraftingTreeNodeBridge child, BoundaryInputMode mode,
            long requestedAmount, long multiplier) {
    }

    private record GraphReusableInput(IPatternDetails.IInput input,
            OmniCraftingTreeNodeBridge child, BoundaryInputMode mode,
            long multiplier) {
    }

    private record OrderedGraphInput(int childIndex, long multiplier,
            GraphReusableInput reusableInput) {
        private static OrderedGraphInput consumable(int childIndex, long multiplier) {
            return new OrderedGraphInput(childIndex, multiplier, null);
        }

        private static OrderedGraphInput reusable(GraphReusableInput reusableInput) {
            return new OrderedGraphInput(-1, 0, reusableInput);
        }

        private boolean reusable() {
            return reusableInput != null;
        }
    }

    private record FiniteToolSelection(AEKey key, long amount) {
    }

    private static void validateTemplates(Node node, CraftingSimulationState inventory,
            PauseCheckpoint pauseCheckpoint)
            throws Fallback, InterruptedException {
        for (CraftingTreeNode occurrence : node.occurrences) {
            checkpoint(pauseCheckpoint);
            var bridge = (OmniCraftingTreeNodeBridge) occurrence;
            Iterable<InputTemplate> templates = bridge.molecularmanipulator$getValidItemTemplates(inventory);
            for (InputTemplate template : templates) {
                if (!node.key.equals(template.key()) || template.amount() != node.amount) {
                    throw new Fallback("fuzzy_or_contextual_input");
                }
            }
        }
    }

    private static final class Compiler {
        private final int maxNodes;
        private long deadline;
        private final PauseCheckpoint pauseCheckpoint;
        private final List<Node> nodes = new ArrayList<>();
        private final Map<NodeKey, Integer> nodeIndexes = new HashMap<>();
        private final Map<IPatternDetails, Integer> patternOwners = new IdentityHashMap<>();
        private long mergedOccurrences;
        private long pausedNanos;
        private int orderedChoiceCount;

        private Compiler(int maxNodes, long deadline, PauseCheckpoint pauseCheckpoint) {
            this.maxNodes = maxNodes;
            this.deadline = deadline;
            this.pauseCheckpoint = pauseCheckpoint;
        }

        private Graph compile(CraftingTreeNode root) throws Fallback, InterruptedException {
            int rootIndex = intern(root);
            nodes.get(rootIndex).reachable = true;
            for (int index = 0; index < nodes.size(); index++) {
                checkBudget();
                Node node = nodes.get(index);
                if (!node.reachable) {
                    continue;
                }
                try {
                    inspect(node);
                } catch (Barrier barrier) {
                    node.barrier = true;
                    node.barrierReason = barrier.reason;
                    if (requiresImmediateFallback(barrier.reason)) {
                        if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                            MolecularManipulator.LOGGER.info(
                                    "Omni MAX_FAST compile-time fallback: key={}, amount={}, barrier={}, pattern={}",
                                    node.key, node.amount, node.barrierReason,
                                    describePattern(node.details));
                        }
                        throw new Fallback("unsafe_pattern_boundary:" + barrier.reason);
                    }
                }
            }

            validateReusableGraphConflicts();

            int[] topologicalOrder = buildTopologicalOrder();
            long logicalNodeCount = countLogicalNodes(rootIndex, topologicalOrder);
            int barrierCount = 0;
            for (Node node : nodes) {
                if (node.reachable && node.barrier) {
                    barrierCount++;
                }
            }
            return new Graph(List.copyOf(nodes), topologicalOrder, rootIndex,
                    logicalNodeCount, mergedOccurrences, barrierCount, orderedChoiceCount);
        }

        /**
         * Aggregating all repetitions of an earlier consumable input can change
         * the amount of its surplus that is visible to a later reusable slot.
         * Reject that graph whenever any ordinary graph key is also a valid
         * candidate for a reusable input. Otherwise candidate availability is
         * unchanged by recursive planning, so leasing the first invariant
         * candidate once is equivalent to AE2 leasing and returning it once per
         * pattern execution.
         */
        private void validateReusableGraphConflicts()
                throws Fallback, InterruptedException {
            for (Node owner : nodes) {
                if (!owner.reachable || owner.reusableInputs.isEmpty()) {
                    continue;
                }
                for (GraphReusableInput reusableInput : owner.reusableInputs) {
                    for (Node graphNode : nodes) {
                        if (!graphNode.reachable) {
                            continue;
                        }
                        checkBudget();
                        try {
                            if (reusableInput.input.isValid(
                                    graphNode.key,
                                    reusableInput.child.molecularmanipulator$getLevel())) {
                                throw new Fallback("reusable_candidate_graph_conflict");
                            }
                        } catch (Fallback fallback) {
                            throw fallback;
                        } catch (RuntimeException exception) {
                            throw new Fallback("reusable_candidate_validation_error");
                        }
                    }
                }
            }
        }

        private int intern(CraftingTreeNode occurrence) throws Fallback, InterruptedException {
            checkBudget();
            var bridge = (OmniCraftingTreeNodeBridge) occurrence;
            AEKey key = bridge.molecularmanipulator$getWhat();
            long amount = bridge.molecularmanipulator$getAmount();
            if (key == null || amount <= 0) {
                throw new Fallback("invalid_node_template");
            }

            var nodeKey = new NodeKey(key, amount);
            Integer existing = nodeIndexes.get(nodeKey);
            if (existing != null) {
                nodes.get(existing).occurrences.add(occurrence);
                mergedOccurrences = saturatedAdd(mergedOccurrences, 1);
                return existing;
            }
            if (nodes.size() >= maxNodes) {
                throw new Fallback("node_limit");
            }

            int index = nodes.size();
            var node = new Node(index, key, amount, bridge.molecularmanipulator$getLevel());
            node.occurrences.add(occurrence);
            nodes.add(node);
            nodeIndexes.put(nodeKey, index);
            return index;
        }

        private void inspect(Node node) throws Fallback, Barrier, InterruptedException {
            var occurrence = node.occurrences.getFirst();
            var nodeBridge = (OmniCraftingTreeNodeBridge) occurrence;
            if (nodeBridge.molecularmanipulator$canEmit()) {
                node.emitter = true;
                return;
            }

            nodeBridge.molecularmanipulator$buildChildPatterns();
            List<CraftingTreeProcess> processes = nodeBridge.molecularmanipulator$getProcesses();
            if (processes == null || processes.isEmpty()) {
                // Pattern availability is recursion-contextual in AE2. A key
                // merged from several tree occurrences is a safe terminal only
                // when every occurrence has no viable process; otherwise fast
                // simulation could report craftable items as missing.
                for (int occurrenceIndex = 1;
                        occurrenceIndex < node.occurrences.size(); occurrenceIndex++) {
                    checkBudget();
                    var occurrenceBridge = (OmniCraftingTreeNodeBridge)
                            node.occurrences.get(occurrenceIndex);
                    occurrenceBridge.molecularmanipulator$buildChildPatterns();
                    List<CraftingTreeProcess> occurrenceProcesses =
                            occurrenceBridge.molecularmanipulator$getProcesses();
                    if (occurrenceProcesses == null || !occurrenceProcesses.isEmpty()) {
                        throw new Fallback("contextual_terminal");
                    }
                }
                node.terminal = true;
                return;
            }
            if (processes.size() > 1) {
                // AE2 tries candidates in this exact order and exhausts the first viable one
                // before considering the next. Compile that first choice transactionally so a
                // deterministic, unit-template branch can be requested in bulk. If execution
                // cannot satisfy the whole request, Session discards the child state and falls
                // back to AE2's original multi-candidate search without changing its result.
                orderedChoiceCount++;
            }

            var process = (OmniCraftingTreeProcessBridge) processes.getFirst();
            boolean hasContainerItems = process.molecularmanipulator$hasContainerItems();
            if (process.molecularmanipulator$limitsQuantity() && !hasContainerItems) {
                throw new Barrier("quantity_limited_pattern");
            }

            IPatternDetails details = process.molecularmanipulator$getDetails();
            node.details = details;
            String patternBarrierReason = getPatternBarrierReason(details);
            if (patternBarrierReason != null) {
                throw new Barrier(patternBarrierReason);
            }

            long outputPerPattern = 0;
            for (var output : details.getOutputs()) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    throw new Barrier("invalid_pattern_output");
                }
                if (!node.key.equals(output.what())) {
                    throw new Barrier("secondary_or_fuzzy_output");
                }
                outputPerPattern = checkedAdd(
                        outputPerPattern, output.amount(), "output_count_overflow");
            }
            if (outputPerPattern <= 0) {
                throw new Barrier("missing_primary_output");
            }

            IPatternDetails.IInput[] inputs = details.getInputs();
            Map<CraftingTreeNode, Long> childNodes = process.molecularmanipulator$getChildNodes();
            if (inputs.length != childNodes.size()) {
                throw new Barrier("dynamic_input_layout");
            }

            var accumulators = new LinkedHashMap<Integer, EdgeAccumulator>();
            int inputIndex = 0;
            for (var entry : childNodes.entrySet()) {
                checkBudget();
                CraftingTreeNode child = entry.getKey();
                var childBridge = (OmniCraftingTreeNodeBridge) child;
                IPatternDetails.IInput input = inputs[inputIndex++];
                if (childBridge.molecularmanipulator$getParentInput() != input) {
                    throw new Barrier("dynamic_input_identity");
                }

                GenericStack possibleInput = getPrimaryInputChoice(input);
                if (possibleInput == null) {
                    throw new Barrier("substitute_input");
                }
                if (!possibleInput.what().equals(childBridge.molecularmanipulator$getWhat())
                        || possibleInput.amount() != childBridge.molecularmanipulator$getAmount()) {
                    throw new Barrier("fuzzy_crafted_input");
                }
                if (!input.isValid(possibleInput.what(), node.level)) {
                    throw new Barrier("dynamic_input_validation");
                }

                long multiplier = input.getMultiplier();
                if (multiplier <= 0 || entry.getValue() == null || entry.getValue() != multiplier) {
                    throw new Barrier("invalid_input_multiplier");
                }

                BoundaryInputMode inputMode = classifyRemainingKey(
                        input, possibleInput.what(), node.level);
                if (inputMode == BoundaryInputMode.UNSAFE) {
                    throw new Barrier("container_items");
                }
                if (inputMode == BoundaryInputMode.CONSUMABLE
                        && getSingleExactInputChoice(input) == null) {
                    throw new Barrier("substitute_input");
                }
                if (inputMode != BoundaryInputMode.CONSUMABLE) {
                    if (!hasContainerItems || childBridge.molecularmanipulator$getAmount() != 1
                            || node.key.equals(childBridge.molecularmanipulator$getWhat())) {
                        throw new Barrier("unsupported_reusable_input");
                    }
                    if (inputMode == BoundaryInputMode.DETERMINISTIC_DAMAGE) {
                        throw new Barrier("recursive_durability_input");
                    }
                    var reusableInput = new GraphReusableInput(
                            input, childBridge, inputMode, multiplier);
                    node.reusableInputs.add(reusableInput);
                    node.orderedInputs.add(OrderedGraphInput.reusable(reusableInput));
                    continue;
                }

                int childIndex = intern(child);
                node.orderedInputs.add(OrderedGraphInput.consumable(
                        childIndex, multiplier));
                var accumulator = accumulators.get(childIndex);
                if (accumulator == null) {
                    accumulators.put(childIndex, new EdgeAccumulator(multiplier));
                } else {
                    accumulator.requestMultiplier = checkedAdd(
                            accumulator.requestMultiplier, multiplier,
                            "input_multiplier_overflow");
                    accumulator.occurrences++;
                }
            }
            if (hasContainerItems && node.reusableInputs.isEmpty()) {
                throw new Barrier("container_flag_without_supported_input");
            }

            Integer patternOwner = patternOwners.putIfAbsent(details, node.index);
            if (patternOwner != null && patternOwner != node.index) {
                throw new Barrier("shared_pattern_with_different_request_units");
            }
            node.outputPerPattern = outputPerPattern;
            for (var entry : accumulators.entrySet()) {
                var accumulator = entry.getValue();
                node.edges.add(new Edge(entry.getKey(), accumulator.requestMultiplier,
                        accumulator.occurrences));
                Node child = nodes.get(entry.getKey());
                child.indegree++;
                child.reachable = true;
            }
        }

        private int[] buildTopologicalOrder() throws Fallback, InterruptedException {
            var indegrees = new int[nodes.size()];
            var ready = new ArrayDeque<Integer>();
            for (Node node : nodes) {
                indegrees[node.index] = node.indegree;
                if (node.indegree == 0) {
                    ready.addLast(node.index);
                }
            }

            var order = new int[nodes.size()];
            int position = 0;
            while (!ready.isEmpty()) {
                checkBudget();
                int nodeIndex = ready.removeFirst();
                order[position++] = nodeIndex;
                for (Edge edge : nodes.get(nodeIndex).edges) {
                    if (--indegrees[edge.childIndex] == 0) {
                        ready.addLast(edge.childIndex);
                    }
                }
            }
            if (position != nodes.size()) {
                throw new Fallback("recursive_or_cyclic_tree");
            }
            return order;
        }

        private long countLogicalNodes(int rootIndex, int[] topologicalOrder)
                throws InterruptedException, Fallback {
            var occurrences = new long[nodes.size()];
            occurrences[rootIndex] = 1;
            long total = 0;
            for (int nodeIndex : topologicalOrder) {
                checkBudget();
                long nodeOccurrences = occurrences[nodeIndex];
                total = saturatedAdd(total, nodeOccurrences);
                for (Edge edge : nodes.get(nodeIndex).edges) {
                    long childOccurrences = saturatedMultiply(nodeOccurrences, edge.occurrences);
                    occurrences[edge.childIndex] = saturatedAdd(
                            occurrences[edge.childIndex], childOccurrences);
                }
            }
            return total;
        }

        private void checkBudget() throws InterruptedException, Fallback {
            long beforePause = System.nanoTime();
            checkpoint(pauseCheckpoint);
            long afterPause = System.nanoTime();
            long paused = Math.max(0, afterPause - beforePause);
            pausedNanos = saturatedAdd(pausedNanos, paused);
            deadline = saturatedAdd(deadline, paused);
            if (afterPause > deadline) {
                throw new Fallback("compile_time_budget");
            }
        }
    }

    private static String getPatternBarrierReason(IPatternDetails details) {
        if (details == null) {
            return "missing_pattern_details";
        }
        if (details.getClass() == AEProcessingPattern.class) {
            return null;
        }
        if (details.getClass() == AECraftingPattern.class) {
            return null;
        }
        return "unsupported_pattern_type:" + details.getClass().getName();
    }

    private static boolean requiresImmediateFallback(String barrierReason) {
        return "missing_pattern_details".equals(barrierReason)
                || barrierReason.startsWith("unsupported_pattern_type:");
    }

    private static GenericStack getPrimaryInputChoice(IPatternDetails.IInput input) {
        GenericStack[] choices = input.getPossibleInputs();
        if (choices == null || choices.length == 0) {
            return null;
        }
        GenericStack first = choices[0];
        return first == null || first.what() == null || first.amount() <= 0
                ? null
                : first;
    }

    private static GenericStack getSingleExactInputChoice(IPatternDetails.IInput input) {
        GenericStack first = getPrimaryInputChoice(input);
        if (first == null) {
            return null;
        }
        GenericStack[] choices = input.getPossibleInputs();
        for (int index = 1; index < choices.length; index++) {
            GenericStack choice = choices[index];
            if (choice == null || choice.what() == null || choice.amount() <= 0
                    || choice.amount() != first.amount()
                    || !choice.what().equals(first.what())) {
                return null;
            }
        }
        return first;
    }

    private static void checkInterrupted() throws InterruptedException {
        if (Thread.interrupted()) {
            throw new InterruptedException();
        }
    }

    private static void checkpoint(PauseCheckpoint pauseCheckpoint)
            throws InterruptedException {
        checkInterrupted();
        pauseCheckpoint.pause();
    }

    private static long saturatedAdd(long left, long right) {
        if (left <= 0) {
            return Math.max(0, right);
        }
        if (right <= 0) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long checkedAdd(long left, long right, String reason) throws Fallback {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new Fallback(reason);
        }
        return left + right;
    }

    private static long checkedMultiply(long left, long right, String reason) throws Fallback {
        if (left < 0 || right < 0 || (right != 0 && left > Long.MAX_VALUE / right)) {
            throw new Fallback(reason);
        }
        return left * right;
    }

    private static long ceilDiv(long value, long divisor) {
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private record NodeKey(AEKey key, long amount) {
    }

    private static final class Node {
        private final int index;
        private final AEKey key;
        private final long amount;
        private final net.minecraft.world.level.Level level;
        private final List<CraftingTreeNode> occurrences = new ArrayList<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<GraphReusableInput> reusableInputs = new ArrayList<>();
        private final List<OrderedGraphInput> orderedInputs = new ArrayList<>();
        private int indegree;
        private boolean emitter;
        private boolean terminal;
        private boolean reachable;
        private boolean barrier;
        private String barrierReason;
        private IPatternDetails details;
        private long outputPerPattern;

        private Node(int index, AEKey key, long amount, net.minecraft.world.level.Level level) {
            this.index = index;
            this.key = key;
            this.amount = amount;
            this.level = level;
        }
    }

    private record Edge(int childIndex, long requestMultiplier, int occurrences) {
    }

    private static final class EdgeAccumulator {
        private long requestMultiplier;
        private int occurrences = 1;

        private EdgeAccumulator(long requestMultiplier) {
            this.requestMultiplier = requestMultiplier;
        }
    }

    private record Graph(List<Node> nodes, int[] topologicalOrder, int rootIndex,
            long logicalNodeCount, long mergedOccurrences, int barrierCount,
            int orderedChoiceCount) {
        private boolean hasOnlyUnitRequestAmounts() {
            for (Node node : nodes) {
                if (node.reachable && node.amount != 1) {
                    return false;
                }
            }
            return true;
        }

        private String executionSafetyFailure() {
            if (!requiresTransactionalFallback()) {
                return null;
            }
            if (barrierCount > 0) {
                return "transactional_graph_with_unsafe_boundary";
            }
            if (mergedOccurrences > 0) {
                return "transactional_graph_shared_context";
            }
            if (!hasOnlyUnitRequestAmounts()) {
                return "transactional_graph_non_unit_request";
            }
            return null;
        }

        private boolean requiresNativeNodeCount() {
            return barrierCount > 0 || requiresTransactionalFallback();
        }

        private boolean requiresTransactionalFallback() {
            if (orderedChoiceCount > 0) {
                return true;
            }
            for (Node node : nodes) {
                if (!node.reusableInputs.isEmpty()) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final class Barrier extends Exception {
        private final String reason;

        private Barrier(String reason) {
            this.reason = reason;
        }
    }

    private static final class Fallback extends Exception {
        private final String reason;

        private Fallback(String reason) {
            this.reason = reason;
        }
    }
}
