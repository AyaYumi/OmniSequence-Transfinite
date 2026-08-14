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
import appeng.crafting.pattern.AESmithingTablePattern;
import appeng.crafting.pattern.AEStonecuttingPattern;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class OmniMaxFastPlanner {
    private static final int MAX_CONTEXT_SPLIT_KEYS = 64;
    private static final String ADVANCED_AE_PROCESSING_PATTERN =
            "net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern";
    private static final String AE2LT_OVERLOAD_PATTERN =
            "com.moakiee.ae2lt.overload.pattern.Ae2OverloadPatternDetails";

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
            this(maxNodes, compileBudgetMillis, pauseCheckpoint, OmniMaxFastMode.SAFE);
        }

        public Session(int maxNodes, int compileBudgetMillis,
                PauseCheckpoint pauseCheckpoint, OmniMaxFastMode mode) {
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
                // A Session belongs to exactly one CraftingCalculation and is
                // itself the safe task-local graph cache. Never retain Graph
                // objects globally: they hold mutable AE2 tree/process nodes.
                long startedAt = System.nanoTime();
                long compileDeadline = saturatedAdd(startedAt, compileBudgetNanos);
                long pausedNanos = 0;
                var contextSplitKeys = new HashSet<AEKey>();
                try {
                    while (graph == null && structuralFailure == null) {
                        var compiler = new Compiler(maxNodes, compileDeadline,
                                pauseCheckpoint, Set.copyOf(contextSplitKeys));
                        try {
                            graph = compiler.compile(requestedRoot);
                        } catch (ContextSplit split) {
                            int splitLimit = Math.min(MAX_CONTEXT_SPLIT_KEYS, maxNodes);
                            boolean changed = false;
                            if (contextSplitKeys.size() < splitLimit) {
                                changed = contextSplitKeys.add(split.triggerKey);
                                for (AEKey key : split.keys) {
                                    if (contextSplitKeys.size() >= splitLimit) {
                                        break;
                                    }
                                    changed |= contextSplitKeys.add(key);
                                }
                            }
                            if (!changed) {
                                structuralFailure = contextSplitKeys.size() >= splitLimit
                                        ? "context_split_limit"
                                        : "context_split_unstable:" + split.reason;
                            } else if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                                MolecularManipulator.LOGGER.info(
                                        "Omni MAX_FAST context split retry: key={}, reason={}, splitKeys={}",
                                        split.triggerKey, split.reason,
                                        contextSplitKeys.size());
                            }
                        } catch (Fallback fallback) {
                            structuralFailure = fallback.reason;
                        } catch (RuntimeException exception) {
                            structuralFailure = "internal_compile_exception";
                            structuralError = exception;
                        } finally {
                            compileDeadline = compiler.deadline;
                            pausedNanos = saturatedAdd(pausedNanos, compiler.pausedNanos);
                        }
                    }
                } finally {
                    compileNanos = Math.max(0,
                            System.nanoTime() - startedAt - pausedNanos);
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
                if (graph.hasOrderedChoices()) {
                    // A real attempt can fail only because the compiled first
                    // candidate is unavailable. Native AE2 must still get this
                    // attempt so it can try later candidates, but the graph is
                    // not structurally invalid: the following simulated pass
                    // can use it to produce the same first-candidate missing
                    // list without another native per-craft traversal.
                    if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                        MolecularManipulator.LOGGER.info(
                                "Omni MAX_FAST ordered candidate unavailable; using native AE2 fallback: {}",
                                failure.getMessage());
                    }
                    return Result.fallback("ordered_choice_candidate_failed", graph.nodes.size(),
                            graph.mergedOccurrences, graph.barrierCount,
                            compileNanos, System.nanoTime() - startedAt, null);
                }
                // Reusable-only graphs have no later recipe candidate whose
                // result AE2 could change. Propagate the exact branch failure
                // directly and avoid repeating the same deterministic tree.
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
        var plannedCrafts = new HashMap<IPatternDetails, Long>();
        if (graph.contextSensitive) {
            var stagedMissing = new KeyCounter();
            executeTransactionalNode(
                    graph, graph.rootIndex, inventory, requestedAmount,
                    simulation, stagedMissing, plannedCrafts, pauseCheckpoint);
            inventory.applyDiff(parent);
            missingItems.addAll(stagedMissing);
            return;
        }
        if (graph.requiresTransactionalFallback()) {
            // AE2's simulated multi-pattern search always accepts the first
            // deterministic candidate and records its terminal shortages. Keep
            // those shortages transactional so the final missing-material plan
            // does not have to walk the same choice tree through native AE2 a
            // second time after the real attempt failed.
            KeyCounter stagedMissing = simulation ? new KeyCounter() : null;
            executeTransactionalNode(
                    graph, graph.rootIndex, inventory, requestedAmount,
                    simulation, stagedMissing, plannedCrafts, pauseCheckpoint);
            inventory.applyDiff(parent);
            if (stagedMissing != null) {
                missingItems.addAll(stagedMissing);
            }
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
                if (node.logicalOccurrences != 1) {
                    throw new Fallback("shared_unsafe_boundary:" + node.barrierReason);
                }
                boolean nestedSpeculativeDurability = node.index != graph.rootIndex
                        && ("recursive_durability_input".equals(node.barrierReason)
                                || "fuzzy_crafted_input".equals(node.barrierReason));
                if (tryExecuteReusableContainerBoundary(
                        node, inventory, requestMultipliers, plannedCrafts,
                        pauseCheckpoint)) {
                    continue;
                }
                if (nestedSpeculativeDurability) {
                    throw new Fallback("nested_durability_boundary_rejected");
                }
                var bridge = (OmniCraftingTreeNodeBridge) node.occurrences.get(0);
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
                    if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                        MolecularManipulator.LOGGER.info(
                                "Omni MAX_FAST terminal input unavailable: key={}, requested={}",
                                node.key, totalRequestedItems);
                    }
                    throw new CraftBranchFailure(node.key, totalRequestedItems);
                }
                // AE2 records an exact terminal shortfall during the simulated
                // attempt and then lets parent patterns continue building the
                // plan. Stage it until the entire aggregated graph succeeds so
                // a later fallback cannot leak or duplicate missing entries.
                stagedMissing.add(node.key, totalRequestedItems);
                continue;
            }

            if (node.outputPerPattern <= 0) {
                if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                    MolecularManipulator.LOGGER.warn(
                            "Omni MAX_FAST invalid outputPerPattern: key={}, amount={}, barrier={}, logicalOccurrences={}",
                            node.key, node.amount, node.barrier, node.logicalOccurrences);
                }
                throw new Fallback("invalid_output_per_pattern:node=" + node.key);
            }
            long effectiveOutputPerPattern = node.outputPerPattern;
            long patternTimes = ceilDiv(totalRequestedItems, effectiveOutputPerPattern);
            for (Edge edge : node.edges) {
                long childRequests = checkedMultiply(edge.requestMultiplier, patternTimes,
                        "child_request_overflow");
                requests[edge.childIndex] = checkedAdd(
                        requests[edge.childIndex], childRequests,
                        "merged_request_overflow");
            }

            long remainder = totalRequestedItems % effectiveOutputPerPattern;
            long surplus = remainder == 0 ? 0 : effectiveOutputPerPattern - remainder;
            if (surplus > 0) {
                inventory.insert(node.key, surplus, Actionable.MODULATE);
            }
            addCraftingChecked(inventory, node.details, patternTimes, plannedCrafts);
            inventory.addBytes(patternTimes);
        }

        inventory.applyDiff(parent);
        missingItems.addAll(stagedMissing);
    }

    private static void executeTransactionalNode(Graph graph, int nodeIndex,
            CraftingSimulationState inventory, long requestMultipliers,
            boolean simulation, KeyCounter stagedMissing,
            Map<IPatternDetails, Long> plannedCrafts,
            PauseCheckpoint pauseCheckpoint)
            throws Fallback, CraftBranchFailure, InterruptedException {
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
            if (!simulation) {
                throw new CraftBranchFailure(node.key, totalRequestedItems);
            }
            if (stagedMissing == null) {
                throw new Fallback("missing_terminal_input");
            }
            stagedMissing.add(node.key, totalRequestedItems);
            return;
        }

        if (node.outputPerPattern <= 0) {
            if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                MolecularManipulator.LOGGER.warn(
                        "Omni MAX_FAST invalid outputPerPattern (transactional): key={}, amount={}, barrier={}, barrierReason={}, logicalOccurrences={}",
                        node.key, node.amount, node.barrier, node.barrierReason,
                        node.logicalOccurrences);
            }
            throw new Fallback("invalid_output_per_pattern:node=" + node.key);
        }
        long effectiveOutputPerPattern = node.outputPerPattern;
        long patternTimes = ceilDiv(totalRequestedItems, effectiveOutputPerPattern);
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
                        childRequests, simulation, stagedMissing, plannedCrafts,
                        pauseCheckpoint);
            }
        }

        for (var stack : returnedReusableInputs) {
            inventory.insert(stack.getKey(), stack.getLongValue(), Actionable.MODULATE);
            long logicalReturns = checkedMultiply(
                    stack.getLongValue(), patternTimes,
                    "reusable_return_bytes_overflow");
            inventory.addStackBytes(stack.getKey(), 1, logicalReturns);
        }

        long remainder = totalRequestedItems % effectiveOutputPerPattern;
        long surplus = remainder == 0 ? 0 : effectiveOutputPerPattern - remainder;
        if (surplus > 0) {
            inventory.insert(node.key, surplus, Actionable.MODULATE);
        }
        addCraftingChecked(inventory, node.details, patternTimes, plannedCrafts);
        inventory.addBytes(patternTimes);
    }

    private static boolean tryExecuteReusableContainerBoundary(Node node,
            CraftingSimulationState parent, long requestedAmount,
            Map<IPatternDetails, Long> plannedCrafts,
            PauseCheckpoint pauseCheckpoint)
            throws CraftBranchFailure, InterruptedException, Fallback {
        try {
            return tryExecuteReusableContainerBoundaryUnchecked(
                    node, parent, requestedAmount, plannedCrafts, pauseCheckpoint);
        } catch (NoSuchElementException exception) {
            return rejectReusableBoundary(node, null, "provider_missing", exception);
        }
    }

    private static boolean tryExecuteReusableContainerBoundaryUnchecked(Node node,
            CraftingSimulationState parent, long requestedAmount,
            Map<IPatternDetails, Long> plannedCrafts,
            PauseCheckpoint pauseCheckpoint)
            throws CraftBranchFailure, InterruptedException, Fallback {
        // AE2 reports a fuzzy_crafted_input when it has already selected a valid
        // substitute craftable durability tool instead of the pattern's first key.
        // Let the boundary verifier prove the selected key and every visible
        // template have the same deterministic remainder behavior before the
        // request is batched; all other fuzzy cases still fail those checks.
        if (!"container_items".equals(node.barrierReason)
                && !"recursive_durability_input".equals(node.barrierReason)
                && !"fuzzy_crafted_input".equals(node.barrierReason)) {
            return rejectReusableBoundary(node, null,
                    "unsupported_barrier:" + node.barrierReason, null);
        }

        var nodeBridge = (OmniCraftingTreeNodeBridge) node.occurrences.get(0);
        List<CraftingTreeProcess> processes = nodeBridge.molecularmanipulator$getProcesses();
        if (processes == null || processes.size() != 1) {
            return rejectReusableBoundary(node, null, "pattern_candidate_count", null);
        }
        if (nodeBridge.molecularmanipulator$canEmit()) {
            return rejectReusableBoundary(node, null, "emitting_boundary", null);
        }

        var process = (OmniCraftingTreeProcessBridge) processes.get(0);
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

        if (outputPerPattern <= 0) {
            return rejectReusableBoundary(node, details, "invalid_output_per_pattern", null);
        }
        long totalRequestedItems = saturatedMultiply(node.amount, remainingAmount);
        long patternTimes = ceilDiv(totalRequestedItems, outputPerPattern);
        var inputPlans = new ArrayList<BoundaryInputPlan>(inputs.length);
        boolean fuzzyCraftedBoundary = "fuzzy_crafted_input".equals(node.barrierReason);
        int selectedSubstituteInputs = 0;
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
            if (!input.isValid(
                    childBridge.molecularmanipulator$getWhat(),
                    childBridge.molecularmanipulator$getLevel())) {
                return rejectReusableBoundary(node, details,
                        "selected_input_not_valid", null);
            }
            if (node.key.equals(childBridge.molecularmanipulator$getWhat())) {
                return rejectReusableBoundary(node, details, "self_referencing_input", null);
            }

            GenericStack primaryInput = getPrimaryInputChoice(input);
            if (primaryInput == null) {
                return rejectReusableBoundary(node, details,
                        "missing_primary_input", null);
            }
            boolean selectedSubstitute = !primaryInput.what().equals(
                    childBridge.molecularmanipulator$getWhat())
                    || primaryInput.amount()
                            != childBridge.molecularmanipulator$getAmount();

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
            if (selectedSubstitute) {
                selectedSubstituteInputs++;
                if (!fuzzyCraftedBoundary
                        || mode != BoundaryInputMode.DETERMINISTIC_DAMAGE
                        || multiplier != 1
                        || childBridge.molecularmanipulator$getAmount() != 1) {
                    return rejectReusableBoundary(node, details,
                            "unsupported_selected_substitute", null);
                }
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
        if (fuzzyCraftedBoundary && selectedSubstituteInputs != 1) {
            return rejectReusableBoundary(node, details,
                    "ambiguous_selected_substitute_count", null);
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
        long nextCraftingTotal = reserveCraftingTotal(
                plannedCrafts, details, patternTimes);
        attempt.addCrafting(details, patternTimes);
        attempt.addBytes(patternTimes);

        long produced = attempt.extract(node.key, totalRequestedItems, Actionable.MODULATE);
        if (produced != totalRequestedItems) {
            return rejectReusableBoundary(node, details, "produced_output_mismatch", null);
        }
        attempt.applyDiff(parent);
        plannedCrafts.put(details, nextCraftingTotal);
        if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
            MolecularManipulator.LOGGER.info(
                    "Omni MAX_FAST reusable boundary applied: key={}, amount={}, requests={}, patterns={}, barrier={}, pattern={}",
                    node.key, node.amount, requestedAmount, patternTimes,
                    node.barrierReason, describePattern(details));
        }
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
     * <p>Multi-tool pool extension: When multiple tools of the same base type
     * but different durabilities exist in the network, calculate total capacity
     * across all instances and batch the entire request if capacity allows.</p>
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
            throws CraftBranchFailure, InterruptedException, Fallback {
        if (inputMultiplier != 1 || patternTimes <= 0
                || child.molecularmanipulator$getAmount() != 1) {
            return false;
        }

        // Multi-tool pool: collect all available tools with their capacities
        var toolPool = new ArrayList<ToolInstance>();
        var seenKeys = new HashSet<AEKey>();
        long totalCapacity = 0;

        for (InputTemplate template : child.molecularmanipulator$getValidItemTemplates(inventory)) {
            checkpoint(pauseCheckpoint);
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
                    patternTimes);
            if (analysis.mode()
                    != MolecularReusableInputAdapters.Mode.DETERMINISTIC_DAMAGE
                    || analysis.safeCrafts() <= 0) {
                return false;
            }

            long toolCapacity = saturatedMultiply(availableGroups, analysis.safeCrafts());
            totalCapacity = saturatedAdd(totalCapacity, toolCapacity);

            toolPool.add(new ToolInstance(
                    template.key(),
                    availableGroups,
                    analysis.safeCrafts(),
                    toolCapacity,
                    inputMultiplier));
        }

        // Check if total capacity meets the request
        if (totalCapacity < patternTimes) {
            // Not enough capacity, fall back to single-tool path
            return allocateDeterministicDamageInputLegacy(
                    inventory, input, child, inputMultiplier, patternTimes, pauseCheckpoint);
        }

        // Multi-tool pool path: allocate from pool
        long remainingPatterns = patternTimes;
        for (ToolInstance tool : toolPool) {
            if (remainingPatterns == 0) {
                break;
            }

            long patternsFromThisTool = Math.min(remainingPatterns, tool.capacity);
            long groupsNeeded = ceilDiv(patternsFromThisTool, tool.safeCrafts);
            long selectedGroups = Math.min(tool.availableGroups, groupsNeeded);

            long toolAmount;
            try {
                toolAmount = Math.multiplyExact(selectedGroups, tool.inputMultiplier);
            } catch (ArithmeticException exception) {
                return false;
            }

            long extracted = inventory.extract(tool.key, toolAmount, Actionable.MODULATE);
            if (extracted != toolAmount) {
                return false;
            }

            long craftedFromThisExtraction = Math.min(
                    patternsFromThisTool,
                    saturatedMultiply(selectedGroups, tool.safeCrafts));
            remainingPatterns -= craftedFromThisExtraction;

        }

        if (remainingPatterns != 0) {
            return false;
        }

        long logicalUses = checkedMultiply(
                inputMultiplier, patternTimes, "reusable_tool_bytes_overflow");
        AEKey logicalInput = child.molecularmanipulator$getWhat();
        // Storage accounting follows the logical recipe slot, not whichever
        // damage-state keys happened to supply the finite-tool pool. Every
        // pattern contributes one input stack and one potential remainder.
        inventory.addStackBytes(logicalInput, 1, logicalUses);
        inventory.addStackBytes(logicalInput, 1, logicalUses);

        if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get() && toolPool.size() > 1) {
            MolecularManipulator.LOGGER.info(
                    "Omni MAX_FAST multi-tool pool batch: patterns={}, tools={}, totalCapacity={}",
                    patternTimes, toolPool.size(), totalCapacity);
        }

        return true;
    }

    private static boolean allocateDeterministicDamageInputLegacy(
            CraftingSimulationState inventory, IPatternDetails.IInput input,
            OmniCraftingTreeNodeBridge child, long inputMultiplier,
            long patternTimes, PauseCheckpoint pauseCheckpoint)
            throws CraftBranchFailure, InterruptedException, Fallback {
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

    private record ToolInstance(AEKey key, long availableGroups, long safeCrafts,
            long capacity, long inputMultiplier) {
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
        private final Map<NodeKey, IdentityHashMap<CraftingTreeNode, Integer>>
                splitNodeIndexes = new HashMap<>();
        private final Map<IPatternDetails, NodeKey> patternOwners = new IdentityHashMap<>();
        private final Map<AEKey, KeyContextBehavior> keyContextBehaviors = new HashMap<>();
        private final Set<AEKey> crossAmountContextSensitiveKeys = new HashSet<>();
        private final ArrayDeque<Integer> pendingInspections = new ArrayDeque<>();
        private final Set<AEKey> contextSplitKeys;
        private long mergedOccurrences;
        private long pausedNanos;
        private int orderedChoiceCount;

        private Compiler(int maxNodes, long deadline, PauseCheckpoint pauseCheckpoint,
                Set<AEKey> contextSplitKeys) {
            this.maxNodes = maxNodes;
            this.deadline = deadline;
            this.pauseCheckpoint = pauseCheckpoint;
            this.contextSplitKeys = contextSplitKeys;
        }

        private Graph compile(CraftingTreeNode root)
                throws Fallback, ContextSplit, InterruptedException {
            int rootIndex = intern(root, RecipeContext.ROOT);
            nodes.get(rootIndex).reachable = true;
            // Interning a later branch may add another recursion context to a
            // node that was already inspected. Drain dirty nodes to a fixed
            // point so every merged occurrence and its descendants are proven
            // equivalent before the graph can execute.
            while (!pendingInspections.isEmpty()) {
                checkBudget();
                int index = pendingInspections.removeFirst();
                Node node = nodes.get(index);
                node.inspectionQueued = false;
                if (!node.reachable) {
                    continue;
                }
                try {
                    inspectPendingOccurrences(node);
                } catch (Barrier barrier) {
                    node.barrier = true;
                    node.barrierReason = barrier.reason;
                    node.inspectedOccurrences = node.occurrences.size();
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
                    logicalNodeCount, mergedOccurrences, barrierCount, orderedChoiceCount,
                    !contextSplitKeys.isEmpty()
                            || !crossAmountContextSensitiveKeys.isEmpty());
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

        private int intern(CraftingTreeNode occurrence, RecipeContext context)
                throws Fallback, InterruptedException {
            checkBudget();
            var bridge = (OmniCraftingTreeNodeBridge) occurrence;
            AEKey key = bridge.molecularmanipulator$getWhat();
            long amount = bridge.molecularmanipulator$getAmount();
            if (key == null || amount <= 0) {
                throw new Fallback("invalid_node_template");
            }

            var nodeKey = new NodeKey(key, amount);
            var occurrenceContext = new OccurrenceContext(
                    context, bridge.molecularmanipulator$getParentInput());
            boolean splitByOccurrence = contextSplitKeys.contains(key);
            IdentityHashMap<CraftingTreeNode, Integer> occurrenceIndexes = splitByOccurrence
                    ? splitNodeIndexes.computeIfAbsent(
                            nodeKey, ignored -> new IdentityHashMap<>())
                    : null;
            Integer existing = splitByOccurrence
                    ? occurrenceIndexes.get(occurrence)
                    : nodeIndexes.get(nodeKey);
            if (existing != null) {
                Node node = nodes.get(existing);
                if (node.occurrenceSet.put(occurrence, Boolean.TRUE) == null) {
                    mergedOccurrences = saturatedAdd(mergedOccurrences, 1);
                    if (node.contextOccurrences.putIfAbsent(
                            occurrenceContext, occurrence) == null) {
                        node.occurrences.add(occurrence);
                        node.occurrenceContexts.add(context);
                        scheduleInspection(node);
                    }
                }
                return existing;
            }
            if (nodes.size() >= maxNodes) {
                throw new Fallback("node_limit");
            }

            int index = nodes.size();
            var node = new Node(index, key, amount, bridge.molecularmanipulator$getLevel());
            node.occurrences.add(occurrence);
            node.occurrenceContexts.add(context);
            node.occurrenceSet.put(occurrence, Boolean.TRUE);
            node.contextOccurrences.put(occurrenceContext, occurrence);
            nodes.add(node);
            if (splitByOccurrence) {
                occurrenceIndexes.put(occurrence, index);
            } else {
                nodeIndexes.put(nodeKey, index);
            }
            scheduleInspection(node);
            return index;
        }

        private void scheduleInspection(Node node) {
            if (!node.inspectionQueued) {
                node.inspectionQueued = true;
                pendingInspections.addLast(node.index);
            }
        }

        private void inspectPendingOccurrences(Node node)
                throws Fallback, Barrier, ContextSplit, InterruptedException {
            if (node.barrier) {
                node.inspectedOccurrences = node.occurrences.size();
                return;
            }
            while (node.inspectedOccurrences < node.occurrences.size()) {
                checkBudget();
                int occurrenceIndex = node.inspectedOccurrences;
                CraftingTreeNode occurrence = node.occurrences.get(occurrenceIndex);
                RecipeContext context = node.occurrenceContexts.get(occurrenceIndex);
                if (node.inspectedOccurrences == 0) {
                    inspect(node, occurrence, context);
                } else {
                    validateOccurrence(node, occurrence, context);
                }
                node.inspectedOccurrences++;
            }
        }

        private void inspect(Node node, CraftingTreeNode occurrence, RecipeContext context)
                throws Fallback, Barrier, InterruptedException {
            var nodeBridge = (OmniCraftingTreeNodeBridge) occurrence;
            if (nodeBridge.molecularmanipulator$canEmit()) {
                node.emitter = true;
                recordKeyContextBehavior(node, context, true, List.of());
                return;
            }

            nodeBridge.molecularmanipulator$buildChildPatterns();
            List<CraftingTreeProcess> processes = nodeBridge.molecularmanipulator$getProcesses();
            if (processes == null) {
                throw new Fallback("missing_process_state");
            }
            if (processes.isEmpty()) {
                recordKeyContextBehavior(node, context, false, List.of());
                node.terminal = true;
                return;
            }
            node.candidatePatterns = getCandidatePatterns(processes);
            recordKeyContextBehavior(
                    node, context, false, node.candidatePatterns);
            if (processes.size() > 1) {
                orderedChoiceCount++;
            }

            // Preserve AE2 provider priority exactly. If this ordered choice
            // fails at runtime, the whole attempt is restored and native AE2
            // gets the chance to walk the remaining candidates.
            CraftingTreeProcess selectedProcess = processes.get(0);
            var process = (OmniCraftingTreeProcessBridge) selectedProcess;
            boolean hasContainerItems = process.molecularmanipulator$hasContainerItems();
            node.hasContainerItems = hasContainerItems;
            node.limitsQuantity = process.molecularmanipulator$limitsQuantity();
            if (node.limitsQuantity && !hasContainerItems) {
                throw new Barrier("quantity_limited_pattern");
            }

            IPatternDetails details = process.molecularmanipulator$getDetails();
            node.details = details;
            if (details == null) {
                throw new Barrier("missing_pattern_details");
            }

            long outputPerPattern = 0;
            boolean hasSecondaryOutput = false;
            GenericStack[] outputs = details.getOutputs();
            if (outputs == null) {
                throw new Barrier("invalid_pattern_output");
            }
            for (var output : outputs) {
                if (output == null || output.what() == null || output.amount() <= 0) {
                    throw new Barrier("invalid_pattern_output");
                }
                if (node.key.equals(output.what())) {
                    outputPerPattern = checkedAdd(
                            outputPerPattern, output.amount(), "output_count_overflow");
                } else {
                    hasSecondaryOutput = true;
                }
            }
            if (outputPerPattern <= 0) {
                throw new Barrier("missing_primary_output");
            }

            // Set outputPerPattern immediately after validation, before any further
            // barrier checks. This ensures barrier nodes have valid outputPerPattern
            // for execution-time integrity checks.
            node.outputPerPattern = outputPerPattern;
            if (hasSecondaryOutput) {
                throw new Barrier("secondary_or_fuzzy_output");
            }

            String patternBarrierReason = getPatternBarrierReason(details);
            if (patternBarrierReason != null) {
                throw new Barrier(patternBarrierReason);
            }

            IPatternDetails.IInput[] inputs = details.getInputs();
            Map<CraftingTreeNode, Long> childNodes = process.molecularmanipulator$getChildNodes();
            if (inputs == null || childNodes == null || inputs.length != childNodes.size()) {
                throw new Barrier("dynamic_input_layout");
            }

            var validatedInputs = new ArrayList<ValidatedOccurrenceInput>(inputs.length);
            boolean hasReusableInput = false;
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
                    hasReusableInput = true;
                }
                validatedInputs.add(new ValidatedOccurrenceInput(
                        child, input, inputMode, multiplier));
            }
            if (hasContainerItems && !hasReusableInput) {
                throw new Barrier("container_flag_without_supported_input");
            }

            var patternNodeKey = new NodeKey(node.key, node.amount);
            NodeKey patternOwner = patternOwners.putIfAbsent(details, patternNodeKey);
            if (patternOwner != null && !patternOwner.equals(patternNodeKey)) {
                throw new Barrier("shared_pattern_with_different_request_units");
            }

            var accumulators = new LinkedHashMap<Integer, EdgeAccumulator>();
            RecipeContext childContext = context.extend(node.key);
            for (ValidatedOccurrenceInput validatedInput : validatedInputs) {
                var childBridge = (OmniCraftingTreeNodeBridge) validatedInput.child;
                if (validatedInput.mode != BoundaryInputMode.CONSUMABLE) {
                    var reusableInput = new GraphReusableInput(
                            validatedInput.input, childBridge,
                            validatedInput.mode, validatedInput.multiplier);
                    node.reusableInputs.add(reusableInput);
                    node.orderedInputs.add(OrderedGraphInput.reusable(reusableInput));
                    continue;
                }

                int childIndex = intern(validatedInput.child, childContext);
                node.orderedInputs.add(OrderedGraphInput.consumable(
                        childIndex, validatedInput.multiplier));
                var accumulator = accumulators.get(childIndex);
                if (accumulator == null) {
                    accumulators.put(childIndex,
                            new EdgeAccumulator(validatedInput.multiplier));
                } else {
                    accumulator.requestMultiplier = checkedAdd(
                            accumulator.requestMultiplier, validatedInput.multiplier,
                            "input_multiplier_overflow");
                    accumulator.occurrences++;
                }
            }
            // outputPerPattern was already set after validation at line ~1603
            for (var entry : accumulators.entrySet()) {
                var accumulator = entry.getValue();
                node.edges.add(new Edge(entry.getKey(), accumulator.requestMultiplier,
                        accumulator.occurrences));
                Node child = nodes.get(entry.getKey());
                child.indegree++;
                child.reachable = true;
            }
        }

        /**
         * Verifies that another tree occurrence represented by the same graph
         * node has exactly the same recursion-contextual behavior as the
         * canonical occurrence. Consumable children are interned only after the
         * full occurrence has passed validation, so a rejected context cannot
         * partially mutate the graph.
         */
        private void validateOccurrence(Node node, CraftingTreeNode occurrence,
                RecipeContext context)
                throws Fallback, ContextSplit, InterruptedException {
            var nodeBridge = (OmniCraftingTreeNodeBridge) occurrence;
            if (nodeBridge.molecularmanipulator$getLevel() != node.level) {
                throw new Fallback("contextual_level");
            }

            boolean canEmit = nodeBridge.molecularmanipulator$canEmit();
            if (canEmit != node.emitter) {
                throw new Fallback("contextual_emitter");
            }
            if (canEmit) {
                return;
            }

            nodeBridge.molecularmanipulator$buildChildPatterns();
            List<CraftingTreeProcess> processes = nodeBridge.molecularmanipulator$getProcesses();
            if (processes == null) {
                throw new Fallback("missing_process_state");
            }
            if (processes.isEmpty()) {
                if (!node.terminal) {
                    logContextualTerminalConflict(node, context, processes);
                    throw createContextSplit(node, context, "contextual_terminal");
                }
                return;
            }
            if (node.terminal) {
                logContextualTerminalConflict(node, context, processes);
                throw createContextSplit(node, context, "contextual_terminal");
            }

            List<IPatternDetails> candidatePatterns = getCandidatePatterns(processes);
            if (candidatePatterns.size() != node.candidatePatterns.size()) {
                throw createContextSplit(
                        node, context, "contextual_pattern_candidates");
            }
            for (int index = 0; index < candidatePatterns.size(); index++) {
                if (candidatePatterns.get(index) != node.candidatePatterns.get(index)) {
                    throw createContextSplit(
                            node, context, "contextual_pattern_candidates");
                }
            }

            CraftingTreeProcess selectedProcess = processes.get(0);
            var process = (OmniCraftingTreeProcessBridge) selectedProcess;
            if (process.molecularmanipulator$getDetails() != node.details
                    || process.molecularmanipulator$hasContainerItems()
                            != node.hasContainerItems
                    || process.molecularmanipulator$limitsQuantity()
                            != node.limitsQuantity) {
                throw new Fallback("contextual_pattern_behavior");
            }

            IPatternDetails.IInput[] inputs = node.details.getInputs();
            Map<CraftingTreeNode, Long> childNodes = process.molecularmanipulator$getChildNodes();
            if (childNodes == null || inputs.length != childNodes.size()
                    || inputs.length != node.orderedInputs.size()) {
                throw new Fallback("contextual_input_layout");
            }

            var contextualChildren = new ArrayList<ContextualChild>();
            int inputIndex = 0;
            for (var entry : childNodes.entrySet()) {
                checkBudget();
                CraftingTreeNode child = entry.getKey();
                var childBridge = (OmniCraftingTreeNodeBridge) child;
                IPatternDetails.IInput input = inputs[inputIndex];
                OrderedGraphInput expected = node.orderedInputs.get(inputIndex++);
                if (childBridge.molecularmanipulator$getParentInput() != input) {
                    throw new Fallback("contextual_input_identity");
                }

                GenericStack possibleInput = getPrimaryInputChoice(input);
                if (possibleInput == null
                        || !possibleInput.what().equals(
                                childBridge.molecularmanipulator$getWhat())
                        || possibleInput.amount()
                                != childBridge.molecularmanipulator$getAmount()
                        || !input.isValid(possibleInput.what(), node.level)) {
                    throw new Fallback("contextual_input_template");
                }

                long multiplier = input.getMultiplier();
                if (multiplier <= 0 || entry.getValue() == null
                        || entry.getValue() != multiplier) {
                    throw new Fallback("contextual_input_multiplier");
                }

                BoundaryInputMode inputMode = classifyRemainingKey(
                        input, possibleInput.what(), node.level);
                if (inputMode == BoundaryInputMode.UNSAFE
                        || (inputMode == BoundaryInputMode.CONSUMABLE
                                && getSingleExactInputChoice(input) == null)) {
                    throw new Fallback("contextual_input_behavior");
                }

                if (expected.reusable()) {
                    GraphReusableInput reusable = expected.reusableInput;
                    var canonicalChild = reusable.child;
                    if (inputMode == BoundaryInputMode.CONSUMABLE
                            || reusable.input != input
                            || reusable.mode != inputMode
                            || reusable.multiplier != multiplier
                            || !canonicalChild.molecularmanipulator$getWhat().equals(
                                    childBridge.molecularmanipulator$getWhat())
                            || canonicalChild.molecularmanipulator$getAmount()
                                    != childBridge.molecularmanipulator$getAmount()) {
                        throw new Fallback("contextual_reusable_input");
                    }
                } else {
                    if (inputMode != BoundaryInputMode.CONSUMABLE
                            || expected.multiplier != multiplier) {
                        throw new Fallback("contextual_consumable_input");
                    }
                    Node expectedChild = nodes.get(expected.childIndex);
                    if (!expectedChild.key.equals(childBridge.molecularmanipulator$getWhat())
                            || expectedChild.amount
                                    != childBridge.molecularmanipulator$getAmount()) {
                        throw new Fallback("contextual_child_template");
                    }
                    contextualChildren.add(new ContextualChild(
                            child, expected.childIndex));
                }
            }

            RecipeContext childContext = context.extend(node.key);
            for (ContextualChild contextualChild : contextualChildren) {
                int childIndex = intern(contextualChild.child, childContext);
                if (childIndex != contextualChild.expectedIndex) {
                    throw createContextSplit(node, context, "contextual_child_node");
                }
            }
        }

        /**
         * AE2's recursion filter is keyed by the requested item, not by the
         * amount stored in a particular tree node. Nodes for the same key but
         * different request units therefore still need depth-first execution
         * when their visible pattern candidates differ by recursion context.
         *
         * <p>Same-amount occurrences are validated by {@link #validateOccurrence}
         * and, when necessary, recompiled as split nodes. This index only
         * compares different amounts, allowing context-insensitive multi-amount
         * graphs to retain the aggregated topological fast path.</p>
         */
        private void recordKeyContextBehavior(Node node, RecipeContext context,
                boolean emitter, List<IPatternDetails> candidatePatterns) {
            var behavior = new KeyContextBehavior(
                    node.amount, context, emitter, candidatePatterns);
            KeyContextBehavior existing = keyContextBehaviors.putIfAbsent(
                    node.key, behavior);
            if (existing == null || existing.amount == node.amount
                    || sameKeyContextBehavior(existing, behavior)) {
                return;
            }
            if (crossAmountContextSensitiveKeys.add(node.key)
                    && ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                MolecularManipulator.LOGGER.info(
                        "Omni MAX_FAST cross-amount context sensitivity: key={}, canonicalAmount={}, conflictingAmount={}, canonicalPath={}, conflictingPath={}",
                        node.key, existing.amount, node.amount,
                        describeRecipeContext(existing.context),
                        describeRecipeContext(context));
            }
        }

        private boolean sameKeyContextBehavior(KeyContextBehavior left,
                KeyContextBehavior right) {
            if (left.emitter != right.emitter
                    || left.candidatePatterns.size()
                            != right.candidatePatterns.size()) {
                return false;
            }
            for (int index = 0; index < left.candidatePatterns.size(); index++) {
                if (left.candidatePatterns.get(index)
                        != right.candidatePatterns.get(index)) {
                    return false;
                }
            }
            return true;
        }

        private ContextSplit createContextSplit(Node node, RecipeContext context,
                String reason) {
            var keys = new HashSet<AEKey>();
            keys.add(node.key);
            if (!node.occurrenceContexts.isEmpty()) {
                addContextKeys(keys, node.occurrenceContexts.get(0));
            }
            addContextKeys(keys, context);
            return new ContextSplit(reason, node.key, Set.copyOf(keys));
        }

        private void addContextKeys(Set<AEKey> keys, RecipeContext context) {
            for (RecipeContext cursor = context; cursor.depth > 0; cursor = cursor.parent) {
                keys.add(cursor.key);
            }
        }

        /**
         * Records enough recursion context to identify the exact reversible or
         * cyclic pattern that made a key craftable in one occurrence and a
         * terminal shortage in another. This stays behind the existing
         * diagnostics option because large recipe paths are intentionally
         * omitted from normal logs.
         */
        private void logContextualTerminalConflict(Node node, RecipeContext context,
                List<CraftingTreeProcess> occurrenceProcesses) {
            if (!ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                return;
            }

            RecipeContext canonicalContext = node.occurrenceContexts.isEmpty()
                    ? RecipeContext.ROOT
                    : node.occurrenceContexts.get(0);
            RecipeContext terminalContext = node.terminal ? canonicalContext : context;
            var diagnosticPatterns = new ArrayList<IPatternDetails>();
            String patternSource;
            if (node.terminal) {
                patternSource = "conflicting";
                for (CraftingTreeProcess process : occurrenceProcesses) {
                    diagnosticPatterns.add(((OmniCraftingTreeProcessBridge) process)
                            .molecularmanipulator$getDetails());
                }
            } else {
                patternSource = "canonical";
                diagnosticPatterns.addAll(node.candidatePatterns);
            }

            MolecularManipulator.LOGGER.info(
                    "Omni MAX_FAST contextual terminal conflict: key={}, amount={}, canonicalTerminal={}, conflictingTerminal={}, canonicalPath={}, conflictingPath={}, patternSource={}, patterns={}",
                    node.key, node.amount, node.terminal, occurrenceProcesses.isEmpty(),
                    describeRecipeContext(canonicalContext), describeRecipeContext(context),
                    patternSource,
                    describeDiagnosticPatterns(diagnosticPatterns, terminalContext));
        }

        private String describeDiagnosticPatterns(List<IPatternDetails> patterns,
                RecipeContext terminalContext) {
            if (patterns.isEmpty()) {
                return "[]";
            }
            var result = new StringBuilder("[");
            int limit = Math.min(patterns.size(), 16);
            for (int index = 0; index < limit; index++) {
                if (index > 0) {
                    result.append(", ");
                }
                IPatternDetails details = patterns.get(index);
                result.append(details == null ? "unknown" : details.getClass().getName())
                        .append(':').append(describePattern(details))
                        .append(" blockedBy=")
                        .append(describePatternBlockers(details, terminalContext));
            }
            if (patterns.size() > limit) {
                result.append(", ... +").append(patterns.size() - limit);
            }
            return result.append(']').toString();
        }

        private String describePatternBlockers(IPatternDetails details,
                RecipeContext context) {
            var result = new StringBuilder("[");
            int blockerCount = 0;
            for (RecipeContext cursor = context;
                    cursor.depth > 0 && blockerCount < 16; cursor = cursor.parent) {
                if (!patternMentions(details, cursor.key)) {
                    continue;
                }
                if (blockerCount++ > 0) {
                    result.append(", ");
                }
                result.append(cursor.key);
            }
            return result.append(']').toString();
        }

        private boolean patternMentions(IPatternDetails details, AEKey ancestor) {
            if (details == null || ancestor == null) {
                return false;
            }
            try {
                for (GenericStack output : details.getOutputs()) {
                    if (output != null && ancestor.matches(output)) {
                        return true;
                    }
                }
                for (IPatternDetails.IInput input : details.getInputs()) {
                    if (input == null) {
                        continue;
                    }
                    GenericStack[] choices = input.getPossibleInputs();
                    if (choices != null && choices.length > 0 && choices[0] != null
                            && ancestor.matches(choices[0])) {
                        return true;
                    }
                }
            } catch (RuntimeException exception) {
                return false;
            }
            return false;
        }

        private String describeRecipeContext(RecipeContext context) {
            var path = new ArrayDeque<AEKey>();
            RecipeContext cursor = context;
            while (cursor.depth > 0 && path.size() < 64) {
                path.addFirst(cursor.key);
                cursor = cursor.parent;
            }
            var result = new StringBuilder("[");
            if (cursor.depth > 0) {
                result.append("... -> ");
            }
            boolean first = true;
            for (AEKey key : path) {
                if (!first) {
                    result.append(" -> ");
                }
                result.append(key);
                first = false;
            }
            return result.append(']').toString();
        }

        private List<IPatternDetails> getCandidatePatterns(
                List<CraftingTreeProcess> processes) throws Fallback {
            var result = new ArrayList<IPatternDetails>(processes.size());
            for (CraftingTreeProcess candidate : processes) {
                var candidateBridge = (OmniCraftingTreeProcessBridge) candidate;
                if (!candidateBridge.molecularmanipulator$isPossible()) {
                    throw new Fallback("contextual_pattern_state");
                }
                result.add(candidateBridge.molecularmanipulator$getDetails());
            }
            return result;
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
                nodes.get(nodeIndex).logicalOccurrences = nodeOccurrences;
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
        if (details.getClass() == AESmithingTablePattern.class) {
            return null;
        }
        if (details.getClass() == AEStonecuttingPattern.class) {
            return null;
        }
        // AdvancedAE's processing pattern has the same deterministic quantity
        // semantics as AEProcessingPattern. Keep this an exact-name opt-in so
        // AdvancedAE remains optional and unknown implementations/subclasses
        // still go through the native AE2 compatibility path. The compiler's
        // input, output, remainder and runtime-template checks remain mandatory.
        if (ADVANCED_AE_PROCESSING_PATTERN.equals(details.getClass().getName())) {
            return null;
        }
        // AE2 Lab Tech's overload pattern is deterministic and follows standard
        // input/output semantics. Support it to enable MAX_FAST for creative-tier
        // recipes that use overload patterns.
        if (AE2LT_OVERLOAD_PATTERN.equals(details.getClass().getName())) {
            return null;
        }

        return "unknown_pattern_type:" + details.getClass().getName();
    }

    private static boolean requiresImmediateFallback(String barrierReason) {
        return "missing_pattern_details".equals(barrierReason)
                || "missing_primary_output".equals(barrierReason)
                || "invalid_pattern_output".equals(barrierReason)
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

    static long checkedCraftingTotal(long current, long addition) {
        if (current < 0 || addition <= 0) {
            throw new ArithmeticException("Crafting task counts must be positive");
        }
        return Math.addExact(current, addition);
    }

    private static long reserveCraftingTotal(
            Map<IPatternDetails, Long> plannedCrafts,
            IPatternDetails details, long patternTimes) throws Fallback {
        if (details == null) {
            throw new Fallback("missing_pattern_details");
        }
        try {
            return checkedCraftingTotal(
                    plannedCrafts.getOrDefault(details, 0L), patternTimes);
        } catch (ArithmeticException exception) {
            throw new Fallback("crafting_task_count_overflow");
        }
    }

    private static void addCraftingChecked(
            CraftingSimulationState inventory, IPatternDetails details,
            long patternTimes, Map<IPatternDetails, Long> plannedCrafts)
            throws Fallback {
        long nextCraftingTotal = reserveCraftingTotal(
                plannedCrafts, details, patternTimes);
        inventory.addCrafting(details, patternTimes);
        plannedCrafts.put(details, nextCraftingTotal);
    }

    private static long ceilDiv(long value, long divisor) throws Fallback {
        if (divisor == 0) {
            throw new Fallback("division_by_zero_output_per_pattern");
        }
        return value / divisor + (value % divisor == 0 ? 0 : 1);
    }

    private record NodeKey(AEKey key, long amount) {
    }

    private record ValidatedOccurrenceInput(CraftingTreeNode child,
            IPatternDetails.IInput input, BoundaryInputMode mode, long multiplier) {
    }

    private record ContextualChild(CraftingTreeNode child, int expectedIndex) {
    }

    private record KeyContextBehavior(long amount, RecipeContext context,
            boolean emitter, List<IPatternDetails> candidatePatterns) {
    }

    /**
     * Ordered ancestor-key chain used by AE2's recursion filter. Keeping it
     * persistent makes sibling occurrences cheap, while structural equality
     * safely deduplicates equivalent paths without relying on a hash alone.
     */
    private static final class RecipeContext {
        private static final RecipeContext ROOT = new RecipeContext();

        private final RecipeContext parent;
        private final AEKey key;
        private final int depth;
        private final int hash;

        private RecipeContext() {
            this.parent = null;
            this.key = null;
            this.depth = 0;
            this.hash = 1;
        }

        private RecipeContext(RecipeContext parent, AEKey key) {
            this.parent = parent;
            this.key = key;
            this.depth = parent.depth + 1;
            this.hash = 31 * parent.hash + key.hashCode();
        }

        private RecipeContext extend(AEKey key) {
            return new RecipeContext(this, key);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof RecipeContext other)
                    || depth != other.depth || hash != other.hash) {
                return false;
            }
            RecipeContext left = this;
            RecipeContext right = other;
            while (left.depth > 0) {
                if (!left.key.equals(right.key)) {
                    return false;
                }
                left = left.parent;
                right = right.parent;
            }
            return true;
        }
    }

    /**
     * Recursion context alone is insufficient because two slots may request
     * the same key and amount but use different substitution or remainder
     * rules. Parent inputs therefore participate by identity, matching AE2's
     * own tree-node construction.
     */
    private static final class OccurrenceContext {
        private final RecipeContext recipeContext;
        private final IPatternDetails.IInput parentInput;
        private final int hash;

        private OccurrenceContext(RecipeContext recipeContext,
                IPatternDetails.IInput parentInput) {
            this.recipeContext = recipeContext;
            this.parentInput = parentInput;
            this.hash = 31 * recipeContext.hashCode()
                    + System.identityHashCode(parentInput);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        @Override
        public boolean equals(Object object) {
            return this == object
                    || object instanceof OccurrenceContext other
                            && parentInput == other.parentInput
                            && recipeContext.equals(other.recipeContext);
        }
    }

    private static final class Node {
        private final int index;
        private final AEKey key;
        private final long amount;
        private final net.minecraft.world.level.Level level;
        private final List<CraftingTreeNode> occurrences = new ArrayList<>();
        private final List<RecipeContext> occurrenceContexts = new ArrayList<>();
        private final IdentityHashMap<CraftingTreeNode, Boolean> occurrenceSet =
                new IdentityHashMap<>();
        private final Map<OccurrenceContext, CraftingTreeNode> contextOccurrences =
                new HashMap<>();
        private final List<Edge> edges = new ArrayList<>();
        private final List<GraphReusableInput> reusableInputs = new ArrayList<>();
        private final List<OrderedGraphInput> orderedInputs = new ArrayList<>();
        private int indegree;
        private int inspectedOccurrences;
        private boolean inspectionQueued;
        private boolean emitter;
        private boolean terminal;
        private boolean reachable;
        private boolean barrier;
        private String barrierReason;
        private IPatternDetails details;
        private List<IPatternDetails> candidatePatterns = List.of();
        private boolean hasContainerItems;
        private boolean limitsQuantity;
        private long outputPerPattern;
        private long logicalOccurrences;

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
            int orderedChoiceCount, boolean contextSensitive) {
        private boolean hasOnlyUnitRequestAmounts() {
            for (Node node : nodes) {
                if (node.reachable && node.amount != 1) {
                    return false;
                }
            }
            return true;
        }

        private String executionSafetyFailure() {
            if (contextSensitive) {
                if (barrierCount > 0) {
                    return "context_sensitive_graph_with_unsafe_boundary";
                }
                if (requiresTransactionalFallback()) {
                    return "context_sensitive_graph_with_transactional_features";
                }
                return null;
            }
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
            return contextSensitive || barrierCount > 0 || requiresTransactionalFallback();
        }

        private boolean requiresTransactionalFallback() {
            if (hasOrderedChoices()) {
                return true;
            }
            for (Node node : nodes) {
                if (!node.reusableInputs.isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private boolean hasOrderedChoices() {
            return orderedChoiceCount > 0;
        }
    }

    private static final class Barrier extends Exception {
        private final String reason;

        private Barrier(String reason) {
            this.reason = reason;
        }
    }

    private static final class ContextSplit extends Exception {
        private final String reason;
        private final AEKey triggerKey;
        private final Set<AEKey> keys;

        private ContextSplit(String reason, AEKey triggerKey, Set<AEKey> keys) {
            this.reason = reason;
            this.triggerKey = triggerKey;
            this.keys = keys;
        }
    }

    private static final class Fallback extends Exception {
        private final String reason;

        private Fallback(String reason) {
            this.reason = reason;
        }
    }

}
