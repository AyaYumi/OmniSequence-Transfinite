package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.CraftingTreeProcess;
import appeng.crafting.inv.CraftingSimulationState;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastMode;
import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastPlanner;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeNodeBridge;
import com.atir.molecularmanipulator.integration.ae2.OmniCraftingTreeProcessBridge;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;

@Mixin(value = CraftingCalculation.class, remap = false)
public abstract class OmniCraftingCalculationMixin {
    @Unique
    private static final int MOLECULARMANIPULATOR_MAX_BACKGROUND_CALCULATIONS =
            Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
    @Unique
    private static final Semaphore MOLECULARMANIPULATOR_CALCULATION_SLOTS =
            new Semaphore(MOLECULARMANIPULATOR_MAX_BACKGROUND_CALCULATIONS, true);
    @Unique
    private static final Semaphore MOLECULARMANIPULATOR_INTERACTIVE_SLOT = new Semaphore(1, true);

    @Shadow
    abstract void handlePausing() throws InterruptedException;

    @Shadow
    public abstract KeyCounter getMissingItems();

    @Shadow
    public abstract boolean isSimulation();

    @Unique
    private OmniComputationCoreBlockEntity molecularmanipulator$omniController;
    @Unique
    private boolean molecularmanipulator$interactiveRequest;
    @Unique
    private OmniMaxFastPlanner.Session molecularmanipulator$maxFastSession;
    @Unique
    private long molecularmanipulator$maxFastNodeCount = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void molecularmanipulator$findOmniController(Level level, IGrid grid,
            ICraftingSimulationRequester requester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo callback) {
        for (var controller : grid.getMachines(OmniComputationCoreBlockEntity.class)) {
            if (controller.isMaterialCalculationEnabled()) {
                molecularmanipulator$omniController = controller;
                break;
            }
        }
        var source = requester.getActionSource();
        molecularmanipulator$interactiveRequest = source != null && source.player().isPresent();
    }

    @WrapMethod(method = "run")
    private ICraftingPlan molecularmanipulator$trackOmniCalculation(
            Operation<ICraftingPlan> original) {
        var controller = molecularmanipulator$omniController;
        if (controller == null) {
            return original.call();
        }

        boolean backgroundSlot = false;
        boolean interactiveSlot = false;
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Crafting calculation was cancelled before execution");
        }
        if (molecularmanipulator$interactiveRequest) {
            backgroundSlot = MOLECULARMANIPULATOR_CALCULATION_SLOTS.tryAcquire();
            if (!backgroundSlot) {
                molecularmanipulator$acquireCalculationSlot(
                        MOLECULARMANIPULATOR_INTERACTIVE_SLOT);
                interactiveSlot = true;
            }
        } else {
            molecularmanipulator$acquireCalculationSlot(
                    MOLECULARMANIPULATOR_CALCULATION_SLOTS);
            backgroundSlot = true;
        }
        long startedAt = System.nanoTime();
        boolean controllerStarted = false;
        try {
            if (Thread.currentThread().isInterrupted()) {
                throw new CancellationException(
                        "Crafting calculation was cancelled before execution");
            }
            controller.beginMaterialCalculation();
            controllerStarted = true;
            return original.call();
        } finally {
            if (controllerStarted) {
                controller.finishMaterialCalculation(System.nanoTime() - startedAt);
            }
            if (backgroundSlot) {
                MOLECULARMANIPULATOR_CALCULATION_SLOTS.release();
            }
            if (interactiveSlot) {
                MOLECULARMANIPULATOR_INTERACTIVE_SLOT.release();
            }
        }
    }

    @Unique
    private static void molecularmanipulator$acquireCalculationSlot(Semaphore semaphore) {
        try {
            semaphore.acquire();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CancellationException(
                    "Crafting calculation was cancelled while waiting for an execution slot");
        }
    }


    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V"))
    private void molecularmanipulator$aggregateSafeRecipeTree(CraftingTreeNode tree,
            CraftingSimulationState inventory, long requestedAmount, KeyCounter containerItems,
            Operation<Void> original) throws CraftBranchFailure, InterruptedException {
        molecularmanipulator$maxFastNodeCount = -1;
        var controller = molecularmanipulator$omniController;
        OmniMaxFastMode mode = ModConfig.OMNI_MAX_FAST_MODE.get();

        if (controller == null || !controller.isMaterialCalculationEnabled()
                || containerItems != null || mode == OmniMaxFastMode.OFF) {
            original.call(tree, inventory, requestedAmount, containerItems);
            return;
        }

        var session = molecularmanipulator$maxFastSession;
        if (session == null) {
            session = new OmniMaxFastPlanner.Session(
                    ModConfig.OMNI_MAX_FAST_MAX_NODES.get(),
                    ModConfig.OMNI_MAX_FAST_COMPILE_BUDGET_MS.get(),
                    this::handlePausing,
                    mode);
            molecularmanipulator$maxFastSession = session;
        }

        KeyCounter missingItems = getMissingItems();
        AEKey requestedKey = ((OmniCraftingTreeNodeBridge) tree)
                .molecularmanipulator$getWhat();
        var missingSnapshot = new KeyCounter();
        missingSnapshot.addAll(missingItems);
        var possibleSnapshot = molecularmanipulator$snapshotPossibleStates(tree);

        OmniMaxFastPlanner.Result result;
        try {
            result = session.tryExecute(
                    tree, inventory, requestedAmount, isSimulation(), missingItems);
        } catch (InterruptedException failure) {
            try {
                molecularmanipulator$restoreAttemptState(
                        tree, missingItems, missingSnapshot, possibleSnapshot);
            } catch (InterruptedException restoreFailure) {
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        } catch (RuntimeException | Error failure) {
            try {
                molecularmanipulator$restoreAttemptState(
                        tree, missingItems, missingSnapshot, possibleSnapshot);
            } catch (InterruptedException restoreFailure) {
                Thread.currentThread().interrupt();
                failure.addSuppressed(restoreFailure);
            }
            throw failure;
        }
        if (!result.applied()) {
            molecularmanipulator$restoreAttemptState(
                    tree, missingItems, missingSnapshot, possibleSnapshot);
        }
        if (result.branchFailure() != null) {
            throw result.branchFailure();
        }
        if (result.applied()) {
            if (!result.nativeNodeCount()) {
                molecularmanipulator$maxFastNodeCount = Math.min(
                        result.logicalNodeCount(), Long.MAX_VALUE / 8);
            }
            if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                com.atir.molecularmanipulator.MolecularManipulator.LOGGER.info(
                        "Omni MAX_FAST applied: key={}, amount={}, simulation={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, logicalNodes={}, compileMs={}, executeMs={}",
                        requestedKey, requestedAmount, isSimulation(),
                        result.uniqueNodes(), result.mergedOccurrences(),
                        result.barrierCount(), result.logicalNodeCount(),
                        result.compileNanos() / 1_000_000.0,
                        result.executionNanos() / 1_000_000.0);
            }
            return;
        }

        if (result.error() != null) {
            com.atir.molecularmanipulator.MolecularManipulator.LOGGER.warn(
                    "Omni MAX_FAST encountered an internal compatibility error and fell back to AE2",
                    result.error());
        } else if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
            com.atir.molecularmanipulator.MolecularManipulator.LOGGER.info(
                    "Omni MAX_FAST fallback: key={}, amount={}, simulation={}, reason={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, compileMs={}, executeMs={}",
                    requestedKey, requestedAmount, isSimulation(),
                    result.fallbackReason(), result.uniqueNodes(),
                    result.mergedOccurrences(), result.barrierCount(),
                    result.compileNanos() / 1_000_000.0,
                    result.executionNanos() / 1_000_000.0);
        }
        original.call(tree, inventory, requestedAmount, containerItems);
    }

    @Unique
    private IdentityHashMap<CraftingTreeProcess, Boolean>
            molecularmanipulator$snapshotPossibleStates(CraftingTreeNode root)
                    throws InterruptedException {
        var result = new IdentityHashMap<CraftingTreeProcess, Boolean>();
        molecularmanipulator$visitBuiltProcesses(root, process -> result.put(
                process,
                ((OmniCraftingTreeProcessBridge) process).molecularmanipulator$isPossible()),
                false);
        return result;
    }

    @Unique
    private void molecularmanipulator$restoreAttemptState(
            CraftingTreeNode root, KeyCounter missingItems, KeyCounter missingSnapshot,
            IdentityHashMap<CraftingTreeProcess, Boolean> possibleSnapshot)
                    throws InterruptedException {
        missingItems.clear();
        missingItems.addAll(missingSnapshot);
        molecularmanipulator$visitBuiltProcesses(root, process -> {
            Boolean previous = possibleSnapshot.get(process);
            ((OmniCraftingTreeProcessBridge) process).molecularmanipulator$setPossible(
                    previous == null || previous);
        }, true);
    }

    @Unique
    private void molecularmanipulator$visitBuiltProcesses(
            CraftingTreeNode root,
            java.util.function.Consumer<CraftingTreeProcess> visitor,
            boolean finishAfterInterruption) throws InterruptedException {
        var pending = new ArrayDeque<CraftingTreeNode>();
        var visited = new IdentityHashMap<CraftingTreeNode, Boolean>();
        InterruptedException deferredInterruption = null;
        pending.addLast(root);
        while (!pending.isEmpty()) {
            deferredInterruption = molecularmanipulator$treeTraversalCheckpoint(
                    deferredInterruption, finishAfterInterruption);
            CraftingTreeNode node = pending.removeFirst();
            if (visited.put(node, Boolean.TRUE) != null) {
                continue;
            }
            var bridge = (OmniCraftingTreeNodeBridge) node;
            var processes = bridge.molecularmanipulator$getProcesses();
            if (processes == null) {
                continue;
            }
            for (CraftingTreeProcess process : processes) {
                deferredInterruption = molecularmanipulator$treeTraversalCheckpoint(
                        deferredInterruption, finishAfterInterruption);
                visitor.accept(process);
                var processBridge = (OmniCraftingTreeProcessBridge) process;
                var children = processBridge.molecularmanipulator$getChildNodes();
                if (children != null) {
                    for (CraftingTreeNode child : children.keySet()) {
                        pending.addLast(child);
                    }
                }
            }
        }
        if (deferredInterruption != null) {
            throw deferredInterruption;
        }
    }

    @Unique
    private InterruptedException molecularmanipulator$treeTraversalCheckpoint(
            InterruptedException deferredInterruption,
            boolean finishAfterInterruption) throws InterruptedException {
        if (deferredInterruption != null) {
            return deferredInterruption;
        }
        try {
            if (Thread.interrupted()) {
                throw new InterruptedException(
                        "Crafting calculation was cancelled during MAX_FAST tree traversal");
            }
            handlePausing();
            return null;
        } catch (InterruptedException interruption) {
            if (!finishAfterInterruption) {
                throw interruption;
            }
            // Restoration must finish so a cancelled or failed speculative
            // MAX_FAST attempt cannot leak process state into native AE2.
            return interruption;
        }
    }

    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;getNodeCount()J"))
    private long molecularmanipulator$useAggregatedNodeCount(CraftingTreeNode tree,
            Operation<Long> original) {
        return molecularmanipulator$maxFastNodeCount >= 0
                ? molecularmanipulator$maxFastNodeCount
                : original.call(tree);
    }
}
