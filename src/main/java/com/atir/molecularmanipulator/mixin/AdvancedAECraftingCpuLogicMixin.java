package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ICraftingInventory;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor.BatchExtraction;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety;
import com.atir.molecularmanipulator.integration.ae2.MolecularBalancedBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAECraftingCpuLogicMixin {
    @Unique
    private static volatile Field molecularmanipulator$jobField;
    @Unique
    private static volatile Field molecularmanipulator$tasksField;
    @Unique
    private static volatile Field molecularmanipulator$taskValueField;
    @Unique
    private static volatile Field molecularmanipulator$cpuField;
    @Unique
    private static volatile Method molecularmanipulator$getLastLinkMethod;
    @Unique
    private static volatile Method molecularmanipulator$cpuGetLevelMethod;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionAvailable = true;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionFailureLogged;

    @Unique
    private CraftingService molecularmanipulator$craftingService;
    @Unique
    private IEnergyService molecularmanipulator$energyService;
    @Unique
    private IPatternDetails molecularmanipulator$batchPattern;
    @Unique
    private BatchExtraction molecularmanipulator$batchExtraction;
    @Unique
    private Level molecularmanipulator$lastLevel;

    @Inject(method = "executeCrafting", at = @At("HEAD"))
    private void molecularmanipulator$beginBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> callback) {
        molecularmanipulator$craftingService = craftingService;
        molecularmanipulator$energyService = energyService;
        molecularmanipulator$lastLevel = level;
        molecularmanipulator$clearBatch();
    }

    @Inject(method = "executeCrafting", at = @At("RETURN"))
    private void molecularmanipulator$endBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> callback) {
        molecularmanipulator$craftingService = null;
        molecularmanipulator$energyService = null;
        molecularmanipulator$clearBatch();
    }

    @Inject(method = "cancel", at = @At("HEAD"))
    private void molecularmanipulator$recordReusableBatchCancellation(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfo callback) {
        var link = molecularmanipulator$getLastLink();
        var level = molecularmanipulator$resolveLevel();
        if (link != null && level != null) {
            MolecularBatchCancellationData.markCanceled(
                    level, link.getCraftingID());
        }
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/execution/CraftingCpuHelper;extractPatternInputs(Lappeng/api/crafting/IPatternDetails;Lappeng/crafting/inv/ICraftingInventory;Lnet/minecraft/world/level/Level;Lappeng/api/stacks/KeyCounter;Lappeng/api/stacks/KeyCounter;)[Lappeng/api/stacks/KeyCounter;"))
    private KeyCounter[] molecularmanipulator$extractBatch(IPatternDetails patternDetails,
            ICraftingInventory inventory, Level level, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, Operation<KeyCounter[]> original) {
        molecularmanipulator$clearBatch();
        var firstInputs = original.call(patternDetails, inventory, level, expectedOutputs,
                expectedContainerItems);
        if (firstInputs == null) {
            return null;
        }

        var craftingService = molecularmanipulator$craftingService;
        var energyService = molecularmanipulator$energyService;
        if (craftingService == null || energyService == null) {
            return firstInputs;
        }

        long remainingCrafts = molecularmanipulator$getRemainingCrafts(patternDetails);
        long batchLimit = molecularmanipulator$getAvailableBatchLimit(
                craftingService, patternDetails, firstInputs);
        long maxCrafts = Math.min(remainingCrafts, batchLimit);
        if (maxCrafts <= 1) {
            return firstInputs;
        }

        var extraction = MolecularBatchCraftingExtractor.expandFromFirst(patternDetails, inventory,
                energyService, level, firstInputs, expectedOutputs,
                expectedContainerItems, maxCrafts, true);
        if (extraction == null) {
            return firstInputs;
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

        if (!MolecularBatchCraftingProvider.supports(provider, patternDetails)
                || MolecularBatchCraftingProvider.getBatchLimit(
                        provider, patternDetails, extraction.firstInputs()) < extraction.craftCount()) {
            return false;
        }

        java.util.UUID reusableCraftingId = null;
        if (extraction.reusablePlan() != null) {
            var link = molecularmanipulator$getLastLink();
            if (link == null || link.isCanceled() || link.isDone()) {
                return false;
            }
            reusableCraftingId = link.getCraftingID();
        }

        var taskAdjustment = molecularmanipulator$prepareBatchTask(
                patternDetails, extraction.craftCount());
        if (taskAdjustment == null) {
            return false;
        }

        boolean accepted = false;
        MolecularBatchDispatchContext.Scope reusableScope = null;
        try {
            if (reusableCraftingId != null) {
                reusableScope = MolecularBatchDispatchContext.open(
                        reusableCraftingId, patternDetails, inputs,
                        extraction.reusablePlan());
            }
            if (provider instanceof MolecularBalancedBatchProvider balancedProvider) {
                balancedProvider.molecularmanipulator$beginBalancedBatch(extraction.firstInputs());
                try {
                    accepted = original.call(provider, patternDetails, inputs);
                } finally {
                    try {
                        balancedProvider.molecularmanipulator$endBalancedBatch();
                    } catch (RuntimeException cleanupException) {
                        MolecularManipulator.LOGGER.warn(
                                "AdvancedAE balanced batch cleanup failed after provider dispatch",
                                cleanupException);
                    }
                }
            } else {
                accepted = original.call(provider, patternDetails, inputs);
            }
        } finally {
            if (reusableScope != null) {
                reusableScope.close();
            }
            if (!accepted) {
                molecularmanipulator$restoreBatchTask(taskAdjustment);
            }
        }
        if (accepted) {
            molecularmanipulator$clearBatch();
        }
        return accepted;
    }

    @Unique
    private long molecularmanipulator$getAvailableBatchLimit(CraftingService craftingService,
            IPatternDetails patternDetails, KeyCounter[] firstInputs) {
        return MolecularBatchDispatchSafety.getAvailableBatchLimit(
                craftingService, patternDetails, firstInputs,
                provider -> MolecularBatchCraftingProvider.supports(provider, patternDetails));
    }

    @Unique
    private long molecularmanipulator$getRemainingCrafts(IPatternDetails patternDetails) {
        var task = molecularmanipulator$getTask(patternDetails);
        if (task == null) {
            return 0;
        }
        try {
            return molecularmanipulator$getTaskValueField(task).getLong(task);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return 0;
        }
    }

    @Unique
    private TaskAdjustment molecularmanipulator$prepareBatchTask(
            IPatternDetails patternDetails, long craftCount) {
        if (craftCount <= 1) {
            return null;
        }
        var task = molecularmanipulator$getTask(patternDetails);
        if (task == null) {
            return null;
        }
        try {
            var valueField = molecularmanipulator$getTaskValueField(task);
            long currentValue = valueField.getLong(task);
            if (currentValue < craftCount) {
                return null;
            }
            valueField.setLong(task, currentValue - craftCount + 1);
            return new TaskAdjustment(task, valueField, currentValue);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return null;
        }
    }

    @Unique
    private void molecularmanipulator$restoreBatchTask(
            TaskAdjustment adjustment) {
        try {
            adjustment.valueField().setLong(
                    adjustment.task(), adjustment.originalValue());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
        }
    }

    @Unique
    private Object molecularmanipulator$getTask(IPatternDetails patternDetails) {
        if (!molecularmanipulator$reflectionAvailable) {
            return null;
        }
        try {
            var jobField = molecularmanipulator$jobField;
            if (jobField == null) {
                jobField = getClass().getDeclaredField("job");
                jobField.setAccessible(true);
                molecularmanipulator$jobField = jobField;
            }
            var job = jobField.get(this);
            if (job == null) {
                return null;
            }

            var tasksField = molecularmanipulator$tasksField;
            if (tasksField == null) {
                tasksField = job.getClass().getDeclaredField("tasks");
                tasksField.setAccessible(true);
                molecularmanipulator$tasksField = tasksField;
            }
            var tasks = (Map<?, ?>) tasksField.get(job);
            return tasks.get(patternDetails);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return null;
        }
    }

    @Unique
    private ICraftingLink molecularmanipulator$getLastLink() {
        try {
            var method = molecularmanipulator$getLastLinkMethod;
            if (method == null) {
                method = getClass().getMethod("getLastLink");
                molecularmanipulator$getLastLinkMethod = method;
            }
            return (ICraftingLink) method.invoke(this);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return null;
        }
    }

    @Unique
    private Level molecularmanipulator$resolveLevel() {
        if (molecularmanipulator$lastLevel != null) {
            return molecularmanipulator$lastLevel;
        }
        try {
            var cpuField = molecularmanipulator$cpuField;
            if (cpuField == null) {
                cpuField = getClass().getDeclaredField("cpu");
                cpuField.setAccessible(true);
                molecularmanipulator$cpuField = cpuField;
            }
            Object cpu = cpuField.get(this);
            if (cpu == null) {
                return null;
            }

            var getLevelMethod = molecularmanipulator$cpuGetLevelMethod;
            if (getLevelMethod == null) {
                getLevelMethod = cpu.getClass().getMethod("getLevel");
                molecularmanipulator$cpuGetLevelMethod = getLevelMethod;
            }
            Object level = getLevelMethod.invoke(cpu);
            return level instanceof Level resolvedLevel
                    ? resolvedLevel
                    : null;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return null;
        }
    }

    @Unique
    private static Field molecularmanipulator$getTaskValueField(Object task) throws NoSuchFieldException {
        var valueField = molecularmanipulator$taskValueField;
        if (valueField == null) {
            valueField = task.getClass().getDeclaredField("value");
            valueField.setAccessible(true);
            molecularmanipulator$taskValueField = valueField;
        }
        return valueField;
    }

    @Unique
    private static void molecularmanipulator$disableReflection(Exception exception) {
        molecularmanipulator$reflectionAvailable = false;
        if (!molecularmanipulator$reflectionFailureLogged) {
            molecularmanipulator$reflectionFailureLogged = true;
            MolecularManipulator.LOGGER.error("AdvancedAE quantum CPU batching was disabled", exception);
        }
    }

    @Unique
    private void molecularmanipulator$clearBatch() {
        molecularmanipulator$batchPattern = null;
        molecularmanipulator$batchExtraction = null;
    }

    @Unique
    private record TaskAdjustment(Object task, Field valueField,
            long originalValue) {
    }
}
