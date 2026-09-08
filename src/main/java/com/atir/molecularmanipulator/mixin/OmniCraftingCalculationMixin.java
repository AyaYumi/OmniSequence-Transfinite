package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import com.appliedenhancements.api.AelisCraftingPlanner;
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

import java.util.concurrent.CancellationException;
import java.util.concurrent.Semaphore;

@Mixin(value = CraftingCalculation.class, priority = 1100, remap = false)
public abstract class OmniCraftingCalculationMixin {
    @Unique
    private static final int OMNISEQUENCE_MAX_BACKGROUND_CALCULATIONS =
            Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() / 2));
    @Unique
    private static final Semaphore OMNISEQUENCE_CALCULATION_SLOTS =
            new Semaphore(OMNISEQUENCE_MAX_BACKGROUND_CALCULATIONS, true);
    @Unique
    private static final Semaphore OMNISEQUENCE_INTERACTIVE_SLOT = new Semaphore(1, true);

    @Shadow
    abstract void handlePausing() throws InterruptedException;

    @Shadow
    public abstract KeyCounter getMissingItems();

    @Shadow
    public abstract boolean isSimulation();

    @Unique
    private OmniComputationCoreBlockEntity omnisequence$omniController;
    @Unique
    private boolean omnisequence$interactiveRequest;
    @Unique
    private AelisCraftingPlanner omnisequence$aelisSession;
    @Unique
    private ICraftingService omnisequence$craftingService;
    @Unique
    private boolean omnisequence$automaticAelis;
    @Unique
    private long omnisequence$aelisNodeCount = -1;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void omnisequence$findOmniController(Level level, IGrid grid,
            ICraftingSimulationRequester requester, GenericStack output,
            CalculationStrategy strategy, CallbackInfo callback) {
        omnisequence$craftingService = grid.getCraftingService();
        // Match the prerequisite's per-calculation snapshot. If its automatic
        // integration owns this calculation, do not invoke the API a second time.
        omnisequence$automaticAelis = com.appliedenhancements.Config.ENABLE_AUTOMATIC_AELIS_PLANNER.get();
        for (var controller : grid.getMachines(OmniComputationCoreBlockEntity.class)) {
            if (controller.isMaterialCalculationEnabled()) {
                omnisequence$omniController = controller;
                break;
            }
        }
        var source = requester.getActionSource();
        omnisequence$interactiveRequest = source != null && source.player().isPresent();
    }

    @WrapMethod(method = "run")
    private ICraftingPlan omnisequence$trackOmniCalculation(
            Operation<ICraftingPlan> original) {
        var controller = omnisequence$omniController;
        if (controller == null) {
            return original.call();
        }

        boolean backgroundSlot = false;
        boolean interactiveSlot = false;
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Crafting calculation was cancelled before execution");
        }
        if (!omnisequence$automaticAelis) {
            if (omnisequence$interactiveRequest) {
                backgroundSlot = OMNISEQUENCE_CALCULATION_SLOTS.tryAcquire();
                if (!backgroundSlot) {
                    omnisequence$acquireCalculationSlot(
                            OMNISEQUENCE_INTERACTIVE_SLOT);
                    interactiveSlot = true;
                }
            } else {
                omnisequence$acquireCalculationSlot(
                        OMNISEQUENCE_CALCULATION_SLOTS);
                backgroundSlot = true;
            }
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
                OMNISEQUENCE_CALCULATION_SLOTS.release();
            }
            if (interactiveSlot) {
                OMNISEQUENCE_INTERACTIVE_SLOT.release();
            }
        }
    }

    @Unique
    private static void omnisequence$acquireCalculationSlot(Semaphore semaphore) {
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
    private void omnisequence$aggregateSafeRecipeTree(CraftingTreeNode tree,
            CraftingSimulationState inventory, long requestedAmount, KeyCounter containerItems,
            Operation<Void> original) throws CraftBranchFailure, InterruptedException {
        omnisequence$aelisNodeCount = -1;
        var controller = omnisequence$omniController;
        if (omnisequence$automaticAelis || controller == null
                || !controller.isMaterialCalculationEnabled() || containerItems != null) {
            original.call(tree, inventory, requestedAmount, containerItems);
            return;
        }
        var planner = omnisequence$aelisSession;
        if (planner == null) {
            planner = AelisCraftingPlanner.createConfigured(this::handlePausing,
                    AelisCraftingPlanner.ProgressListener.NONE, omnisequence$craftingService);
            omnisequence$aelisSession = planner;
        }
        var result = planner.tryExecute(tree, inventory, requestedAmount, isSimulation(), getMissingItems());
        if (result.branchFailure() != null) throw result.branchFailure();
        if (result.applied()) {
            if (!result.nativeNodeCount()) {
                omnisequence$aelisNodeCount = Math.min(result.logicalNodeCount(), Long.MAX_VALUE / 8);
            }
            return;
        }
        if (result.error() != null) {
            com.atir.molecularmanipulator.MolecularManipulator.LOGGER.warn(
                    "Omni AELIS API request fell back to AE2", result.error());
        }
        // The public API restores candidate and missing-item state on fallback.
        original.call(tree, inventory, requestedAmount, containerItems);
    }

    @WrapOperation(method = "runCraftAttempt", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/CraftingTreeNode;getNodeCount()J"))
    private long omnisequence$useAggregatedNodeCount(CraftingTreeNode tree,
            Operation<Long> original) {
        return omnisequence$aelisNodeCount >= 0
                ? omnisequence$aelisNodeCount
                : original.call(tree);
    }
}
