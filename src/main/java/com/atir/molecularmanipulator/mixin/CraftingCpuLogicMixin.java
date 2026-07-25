package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor.BatchExtraction;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Map;

@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {
    @Unique
    private static final int MOLECULARMANIPULATOR_UNBOUNDED_COPROCESSOR_THRESHOLD = 256;
    @Unique
    private static final int MOLECULARMANIPULATOR_SAFE_COPROCESSOR_LIMIT = 255;
    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    @Unique
    private CraftingService molecularmanipulator$craftingService;
    @Unique
    private IEnergyService molecularmanipulator$energyService;
    @Unique
    private IPatternDetails molecularmanipulator$batchPattern;
    @Unique
    private BatchExtraction molecularmanipulator$batchExtraction;
    @Unique
    private long molecularmanipulator$omniDispatchDeadline;
    @Unique
    private static volatile Field molecularmanipulator$tasksField;
    @Unique
    private static volatile Field molecularmanipulator$taskValueField;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionAvailable = true;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionFailureLogged;

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void molecularmanipulator$beginOmniDispatchWindow(IEnergyService energyService,
            CraftingService craftingService, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        var owner = OmniComputationCoreBlockEntity.ownerOf(cluster);
        molecularmanipulator$omniDispatchDeadline = owner == null
                ? 0
                : owner.getDispatchDeadlineNanos(cluster.getLevel().getGameTime());
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void molecularmanipulator$endOmniDispatchWindow(IEnergyService energyService,
            CraftingService craftingService, org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        molecularmanipulator$omniDispatchDeadline = 0;
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$beginBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> callback) {
        molecularmanipulator$craftingService = craftingService;
        molecularmanipulator$energyService = energyService;
        molecularmanipulator$clearBatch();
        if (molecularmanipulator$omniDispatchDeadline != 0
                && System.nanoTime() >= molecularmanipulator$omniDispatchDeadline) {
            callback.setReturnValue(0);
        }
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void molecularmanipulator$endBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> callback) {
        molecularmanipulator$craftingService = null;
        molecularmanipulator$energyService = null;
        molecularmanipulator$clearBatch();
    }

    @ModifyExpressionValue(method = "tickCraftingLogic", at = @At(value = "INVOKE",
            target = "Lappeng/me/cluster/implementations/CraftingCPUCluster;getCoProcessors()I"))
    private int molecularmanipulator$limitUnboundedCraftingBurst(int coProcessors) {
        if (OmniComputationCoreBlockEntity.ownerOf(cluster) != null) {
            // AE2 adds one to this value. MAX_VALUE - 1 therefore exposes the
            // complete 2,147,483,647-operation scheduling window without overflowing.
            // Actual calls are stopped by the controller's wall-clock budget; batch
            // capable providers can consume the whole logical window in one push.
            return System.nanoTime() < molecularmanipulator$omniDispatchDeadline
                    ? Integer.MAX_VALUE - 1
                    : -1;
        }
        if (coProcessors > MOLECULARMANIPULATOR_UNBOUNDED_COPROCESSOR_THRESHOLD) {
            return MOLECULARMANIPULATOR_SAFE_COPROCESSOR_LIMIT;
        }
        return coProcessors;
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] molecularmanipulator$extractBatch(IPatternDetails patternDetails,
            ICraftingInventory inventory, Level level, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, Operation<KeyCounter[]> original) {
        molecularmanipulator$clearBatch();
        if (molecularmanipulator$omniDispatchDeadline != 0
                && System.nanoTime() >= molecularmanipulator$omniDispatchDeadline) {
            return null;
        }
        var currentJob = job;
        var craftingService = molecularmanipulator$craftingService;
        var energyService = molecularmanipulator$energyService;
        if (currentJob == null || craftingService == null || energyService == null) {
            return original.call(patternDetails, inventory, level, expectedOutputs, expectedContainerItems);
        }

        var task = molecularmanipulator$getTasks(currentJob).get(patternDetails);
        if (task == null) {
            return original.call(patternDetails, inventory, level, expectedOutputs, expectedContainerItems);
        }

        long batchLimit = molecularmanipulator$getAvailableBatchLimit(craftingService, patternDetails);
        long maxCrafts = Math.min(molecularmanipulator$getTaskValue(task), batchLimit);
        if (maxCrafts <= 1) {
            return original.call(patternDetails, inventory, level, expectedOutputs, expectedContainerItems);
        }

        var extraction = MolecularBatchCraftingExtractor.extractLargest(patternDetails, inventory, level,
                energyService, expectedOutputs, expectedContainerItems, maxCrafts);
        if (extraction == null) {
            return original.call(patternDetails, inventory, level, expectedOutputs, expectedContainerItems);
        }
        if (extraction.craftCount() <= 1) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, extraction.inputs());
            expectedOutputs.reset();
            expectedContainerItems.reset();
            return original.call(patternDetails, inventory, level, expectedOutputs, expectedContainerItems);
        }

        molecularmanipulator$batchPattern = patternDetails;
        molecularmanipulator$batchExtraction = extraction;
        return extraction.inputs();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean molecularmanipulator$pushBatch(ICraftingProvider provider, IPatternDetails patternDetails,
            KeyCounter[] inputs, Operation<Boolean> original) {
        var extraction = molecularmanipulator$batchExtraction;
        if (extraction == null || molecularmanipulator$batchPattern != patternDetails
                || extraction.inputs() != inputs) {
            return original.call(provider, patternDetails, inputs);
        }

        if (!molecularmanipulator$supportsProvider(provider, patternDetails)
                || MolecularBatchCraftingProvider.getBatchLimit(provider, patternDetails) < extraction.craftCount()) {
            return false;
        }

        boolean accepted = original.call(provider, patternDetails, inputs);
        if (accepted) {
            molecularmanipulator$consumeBatchTask(patternDetails, extraction.craftCount());
            molecularmanipulator$clearBatch();
        }
        return accepted;
    }

    @Unique
    private long molecularmanipulator$getAvailableBatchLimit(CraftingService craftingService,
            IPatternDetails patternDetails) {
        return MolecularBatchDispatchSafety.getAvailableBatchLimit(craftingService, patternDetails,
                provider -> molecularmanipulator$supportsProvider(provider, patternDetails));
    }

    @Unique
    private boolean molecularmanipulator$supportsProvider(ICraftingProvider provider,
            IPatternDetails patternDetails) {
        if (OmniComputationCoreBlockEntity.ownerOf(cluster) != null) {
            return MolecularBatchCraftingProvider.supportsOmniDispatch(provider, patternDetails);
        }
        return MolecularBatchCraftingProvider.supports(provider, patternDetails);
    }

    @Unique
    private void molecularmanipulator$clearBatch() {
        molecularmanipulator$batchPattern = null;
        molecularmanipulator$batchExtraction = null;
    }

    @Unique
    private void molecularmanipulator$consumeBatchTask(IPatternDetails patternDetails, long craftCount) {
        var currentJob = job;
        if (currentJob == null) {
            return;
        }
        var task = molecularmanipulator$getTasks(currentJob).get(patternDetails);
        if (task == null) {
            return;
        }
        long valueBeforeOriginalDecrement =
                molecularmanipulator$getTaskValue(task) - craftCount + 1;
        molecularmanipulator$setTaskValue(task, Math.max(1, valueBeforeOriginalDecrement));
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static Map<IPatternDetails, Object> molecularmanipulator$getTasks(ExecutingCraftingJob job) {
        if (!molecularmanipulator$reflectionAvailable) {
            return Map.of();
        }
        try {
            var field = molecularmanipulator$tasksField;
            if (field == null) {
                field = job.getClass().getDeclaredField("tasks");
                field.setAccessible(true);
                molecularmanipulator$tasksField = field;
            }
            return (Map<IPatternDetails, Object>) field.get(job);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return Map.of();
        }
    }

    @Unique
    private static long molecularmanipulator$getTaskValue(Object task) {
        try {
            var field = molecularmanipulator$getTaskValueField(task);
            return field.getLong(task);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return 1;
        }
    }

    @Unique
    private static void molecularmanipulator$setTaskValue(Object task, long value) {
        try {
            var field = molecularmanipulator$getTaskValueField(task);
            field.setLong(task, value);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
        }
    }

    @Unique
    private static Field molecularmanipulator$getTaskValueField(Object task) throws NoSuchFieldException {
        var field = molecularmanipulator$taskValueField;
        if (field == null) {
            field = task.getClass().getDeclaredField("value");
            field.setAccessible(true);
            molecularmanipulator$taskValueField = field;
        }
        return field;
    }

    @Unique
    private static void molecularmanipulator$disableReflection(Exception exception) {
        molecularmanipulator$reflectionAvailable = false;
        if (!molecularmanipulator$reflectionFailureLogged) {
            molecularmanipulator$reflectionFailureLogged = true;
            com.atir.molecularmanipulator.MolecularManipulator.LOGGER.error(
                    "Normal AE2 crafting CPU batch integration was disabled", exception);
        }
    }
}
