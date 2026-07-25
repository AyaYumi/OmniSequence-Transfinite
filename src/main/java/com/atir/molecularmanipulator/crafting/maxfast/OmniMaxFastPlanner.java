package com.atir.molecularmanipulator.crafting.maxfast;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
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
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeNodeBridge;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeProcessBridge;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
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
        private Throwable structuralError;
        private long compileNanos;

        public Session(int maxNodes, int compileBudgetMillis,
                PauseCheckpoint pauseCheckpoint) {
            this.maxNodes = maxNodes;
            this.compileBudgetNanos = TimeUnit.MILLISECONDS.toNanos(compileBudgetMillis);
            this.pauseCheckpoint = pauseCheckpoint;
        }

        public Result tryExecute(CraftingTreeNode requestedRoot, CraftingSimulationState inventory,
                long requestedAmount) throws InterruptedException {
            if (requestedAmount <= 0) {
                return Result.fallback("invalid_request_amount", 0, 0, 0, 0, 0, null);
            }
            if (root != null && root != requestedRoot) {
                return Result.fallback("calculation_root_changed", 0, 0, 0, 0, 0, null);
            }
            root = requestedRoot;

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

            long startedAt = System.nanoTime();
            try {
                execute(graph, inventory, requestedAmount, pauseCheckpoint);
                return Result.applied(graph.nodes.size(), graph.mergedOccurrences, graph.barrierCount,
                        graph.logicalNodeCount, compileNanos, System.nanoTime() - startedAt);
            } catch (CraftBranchFailure failure) {
                return Result.branchFailure(graph.nodes.size(), graph.mergedOccurrences,
                        graph.barrierCount, compileNanos, System.nanoTime() - startedAt, failure);
            } catch (Fallback fallback) {
                return Result.fallback(fallback.reason, graph.nodes.size(), graph.mergedOccurrences,
                        graph.barrierCount,
                        compileNanos, System.nanoTime() - startedAt, null);
            } catch (RuntimeException exception) {
                return Result.fallback("internal_execution_exception", graph.nodes.size(),
                        graph.mergedOccurrences, graph.barrierCount,
                        compileNanos, System.nanoTime() - startedAt, exception);
            }
        }
    }

    public record Result(boolean applied, String fallbackReason, int uniqueNodes,
            long mergedOccurrences, int barrierCount, long logicalNodeCount, long compileNanos,
            long executionNanos, CraftBranchFailure branchFailure, Throwable error) {
        private static Result applied(int uniqueNodes, long mergedOccurrences, int barrierCount,
                long logicalNodeCount, long compileNanos, long executionNanos) {
            return new Result(true, null, uniqueNodes, mergedOccurrences, barrierCount,
                    logicalNodeCount, compileNanos, executionNanos, null, null);
        }

        private static Result branchFailure(int uniqueNodes, long mergedOccurrences, int barrierCount,
                long compileNanos, long executionNanos, CraftBranchFailure failure) {
            return new Result(false, null, uniqueNodes, mergedOccurrences, barrierCount,
                    0, compileNanos, executionNanos, failure, null);
        }

        private static Result fallback(String reason, int uniqueNodes, long mergedOccurrences,
                int barrierCount,
                long compileNanos, long executionNanos, Throwable error) {
            return new Result(false, reason, uniqueNodes, mergedOccurrences, barrierCount,
                    0, compileNanos, executionNanos, null, error);
        }
    }

    private static void execute(Graph graph, CraftingSimulationState parent,
            long requestedAmount, PauseCheckpoint pauseCheckpoint)
            throws Fallback, CraftBranchFailure, InterruptedException {
        var inventory = new ChildCraftingSimulationState(parent);
        var requests = new long[graph.nodes.size()];
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
                if (tryExecuteReusableContainerBoundary(
                        node, inventory, requestMultipliers, pauseCheckpoint)) {
                    continue;
                }
                var bridge = (OmniCraftingTreeNodeBridge) node.occurrences.getFirst();
                bridge.molecularmanipulator$request(inventory, requestMultipliers, null);
                continue;
            }
            validateTemplates(node, inventory, pauseCheckpoint);
            inventory.addStackBytes(node.key, node.amount, requestMultipliers);

            long extractLimit = saturatedMultiply(node.amount, requestMultipliers);
            long available = inventory.extract(node.key, extractLimit, Actionable.SIMULATE);
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

            long totalRequestedItems = saturatedMultiply(node.amount, remainingMultipliers);
            if (node.emitter) {
                inventory.emitItems(node.key, totalRequestedItems);
                continue;
            }
            if (node.terminal) {
                throw new Fallback("missing_terminal_input");
            }

            long patternTimes = ceilDiv(totalRequestedItems, node.outputPerPattern);
            for (Edge edge : node.edges) {
                long childRequests = saturatedMultiply(edge.requestMultiplier, patternTimes);
                requests[edge.childIndex] = saturatedAdd(requests[edge.childIndex], childRequests);
            }

            long produced = saturatedMultiply(node.outputPerPattern, patternTimes);
            long surplus = Math.max(0, produced - totalRequestedItems);
            if (surplus > 0) {
                inventory.insert(node.key, surplus, Actionable.MODULATE);
            }
            inventory.addCrafting(node.details, patternTimes);
            inventory.addBytes(patternTimes);
        }

        inventory.applyDiff(parent);
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
        if (!"container_items".equals(node.barrierReason)) {
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
            long childRequest = mode == BoundaryInputMode.REUSABLE
                    ? multiplier
                    : saturatedMultiply(multiplier, patternTimes);
            inputPlans.add(new BoundaryInputPlan(childBridge, childRequest));
        }

        var containerItems = new KeyCounter();
        for (BoundaryInputPlan inputPlan : inputPlans) {
            inputPlan.child.molecularmanipulator$request(
                    attempt, inputPlan.requestedAmount, containerItems);
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
        BoundaryInputMode mode = classifyRemainingKey(input, child.molecularmanipulator$getWhat());
        if (mode == BoundaryInputMode.UNSAFE) {
            return BoundaryInputClassification.rejected("container_changes_key");
        }
        for (InputTemplate template : child.molecularmanipulator$getValidItemTemplates(inventory)) {
            checkpoint(pauseCheckpoint);
            BoundaryInputMode templateMode = classifyRemainingKey(input, template.key());
            if (templateMode == BoundaryInputMode.UNSAFE) {
                return BoundaryInputClassification.rejected("container_changes_key");
            }
            if (templateMode != mode) {
                return BoundaryInputClassification.rejected(
                        "mixed_consumable_and_reusable_templates");
            }
        }
        return BoundaryInputClassification.accepted(mode);
    }

    private static BoundaryInputMode classifyRemainingKey(IPatternDetails.IInput input, AEKey key) {
        AEKey remainingKey = input.getRemainingKey(key);
        if (remainingKey == null) {
            return BoundaryInputMode.CONSUMABLE;
        }
        return remainingKey.equals(key) ? BoundaryInputMode.REUSABLE : BoundaryInputMode.UNSAFE;
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
        REUSABLE,
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

    private record BoundaryInputPlan(OmniCraftingTreeNodeBridge child, long requestedAmount) {
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

            int[] topologicalOrder = buildTopologicalOrder();
            long logicalNodeCount = countLogicalNodes(rootIndex, topologicalOrder);
            int barrierCount = 0;
            for (Node node : nodes) {
                if (node.reachable && node.barrier) {
                    barrierCount++;
                }
            }
            return new Graph(List.copyOf(nodes), topologicalOrder, rootIndex,
                    logicalNodeCount, mergedOccurrences, barrierCount);
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
                node.terminal = true;
                return;
            }
            if (processes.size() != 1) {
                throw new Barrier("multiple_pattern_candidates");
            }

            var process = (OmniCraftingTreeProcessBridge) processes.getFirst();
            if (process.molecularmanipulator$hasContainerItems()) {
                throw new Barrier("container_items");
            }
            if (process.molecularmanipulator$limitsQuantity()) {
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
                outputPerPattern = saturatedAdd(outputPerPattern, output.amount());
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

                var possibleInputs = input.getPossibleInputs();
                if (possibleInputs.length != 1) {
                    throw new Barrier("substitute_input");
                }
                var possibleInput = possibleInputs[0];
                if (possibleInput == null || possibleInput.what() == null || possibleInput.amount() <= 0) {
                    throw new Barrier("invalid_pattern_input");
                }
                if (!possibleInput.what().equals(childBridge.molecularmanipulator$getWhat())
                        || possibleInput.amount() != childBridge.molecularmanipulator$getAmount()) {
                    throw new Barrier("fuzzy_crafted_input");
                }
                if (!input.isValid(possibleInput.what(), node.level)) {
                    throw new Barrier("dynamic_input_validation");
                }
                if (input.getRemainingKey(possibleInput.what()) != null) {
                    throw new Barrier("container_items");
                }

                long multiplier = input.getMultiplier();
                if (multiplier <= 0 || entry.getValue() == null || entry.getValue() != multiplier) {
                    throw new Barrier("invalid_input_multiplier");
                }

                int childIndex = intern(child);
                var accumulator = accumulators.get(childIndex);
                if (accumulator == null) {
                    accumulators.put(childIndex, new EdgeAccumulator(multiplier));
                } else {
                    accumulator.requestMultiplier = saturatedAdd(accumulator.requestMultiplier, multiplier);
                    accumulator.occurrences++;
                }
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
            var craftingPattern = (AECraftingPattern) details;
            if (craftingPattern.canSubstitute()) {
                return "substitution_enabled:items";
            }
            return null;
        }
        return "unsupported_pattern_type:" + details.getClass().getName();
    }

    private static boolean requiresImmediateFallback(String barrierReason) {
        return "missing_pattern_details".equals(barrierReason)
                || barrierReason.startsWith("substitution_enabled:")
                || barrierReason.startsWith("unsupported_pattern_type:");
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
            long logicalNodeCount, long mergedOccurrences, int barrierCount) {
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
