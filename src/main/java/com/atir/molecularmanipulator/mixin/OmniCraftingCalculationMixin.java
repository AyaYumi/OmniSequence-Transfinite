package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftBranchFailure;
import appeng.crafting.CraftingCalculation;
import appeng.crafting.CraftingTreeNode;
import appeng.crafting.inv.CraftingSimulationState;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastMode;
import com.atir.molecularmanipulator.crafting.maxfast.OmniMaxFastPlanner;
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
        if (molecularmanipulator$interactiveRequest) {
            backgroundSlot = MOLECULARMANIPULATOR_CALCULATION_SLOTS.tryAcquire();
            if (!backgroundSlot) {
                MOLECULARMANIPULATOR_INTERACTIVE_SLOT.acquireUninterruptibly();
                interactiveSlot = true;
            }
        } else {
            MOLECULARMANIPULATOR_CALCULATION_SLOTS.acquireUninterruptibly();
            backgroundSlot = true;
        }
        long startedAt = System.nanoTime();
        controller.beginMaterialCalculation();
        try {
            return original.call();
        } finally {
            controller.finishMaterialCalculation(System.nanoTime() - startedAt);
            if (backgroundSlot) {
                MOLECULARMANIPULATOR_CALCULATION_SLOTS.release();
            }
            if (interactiveSlot) {
                MOLECULARMANIPULATOR_INTERACTIVE_SLOT.release();
            }
        }
    }


    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;request(Lappeng/crafting/inv/CraftingSimulationState;JLappeng/api/stacks/KeyCounter;)V"))
    private void molecularmanipulator$aggregateSafeRecipeTree(CraftingTreeNode tree,
            CraftingSimulationState inventory, long requestedAmount, KeyCounter containerItems,
            Operation<Void> original) throws CraftBranchFailure, InterruptedException {
        molecularmanipulator$maxFastNodeCount = -1;
        var controller = molecularmanipulator$omniController;
        if (controller == null || !controller.isMaterialCalculationEnabled()
                || containerItems != null || ModConfig.OMNI_MAX_FAST_MODE.get() != OmniMaxFastMode.SAFE) {
            original.call(tree, inventory, requestedAmount, containerItems);
            return;
        }

        var session = molecularmanipulator$maxFastSession;
        if (session == null) {
            session = new OmniMaxFastPlanner.Session(
                    ModConfig.OMNI_MAX_FAST_MAX_NODES.get(),
                    ModConfig.OMNI_MAX_FAST_COMPILE_BUDGET_MS.get(),
                    this::handlePausing);
            molecularmanipulator$maxFastSession = session;
        }

        var result = session.tryExecute(tree, inventory, requestedAmount);
        if (result.branchFailure() != null) {
            throw result.branchFailure();
        }
        if (result.applied()) {
            molecularmanipulator$maxFastNodeCount = Math.min(
                    result.logicalNodeCount(), Long.MAX_VALUE / 8);
            if (ModConfig.OMNI_MAX_FAST_DIAGNOSTICS.get()) {
                com.atir.molecularmanipulator.MolecularManipulator.LOGGER.info(
                        "Omni MAX_FAST applied: amount={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, logicalNodes={}, compileMs={}, executeMs={}",
                        requestedAmount, result.uniqueNodes(), result.mergedOccurrences(),
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
                    "Omni MAX_FAST fallback: amount={}, reason={}, uniqueNodes={}, mergedOccurrences={}, barriers={}, compileMs={}, executeMs={}",
                    requestedAmount, result.fallbackReason(), result.uniqueNodes(),
                    result.mergedOccurrences(), result.barrierCount(),
                    result.compileNanos() / 1_000_000.0,
                    result.executionNanos() / 1_000_000.0);
        }
        original.call(tree, inventory, requestedAmount, containerItems);
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
