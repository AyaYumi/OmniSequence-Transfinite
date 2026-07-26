package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.crafting.MolecularAdaptiveBatchController;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor.BatchExtraction;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety;
import com.atir.molecularmanipulator.integration.ae2.MolecularBalancedBatchProvider;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;

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
    private final MolecularAdaptiveBatchController molecularmanipulator$adaptiveBatchController =
            new MolecularAdaptiveBatchController();
    @Unique
    private IPatternDetails molecularmanipulator$adaptivePattern;
    @Unique
    private KeyCounter[] molecularmanipulator$adaptiveInputs;
    @Unique
    private IPatternDetails molecularmanipulator$directPattern;
    @Unique
    private KeyCounter[] molecularmanipulator$directInputs;
    @Unique
    private OmniComputationCoreBlockEntity molecularmanipulator$dispatchOwner;
    @Unique
    private long molecularmanipulator$dispatchTick;
    @Unique
    private long molecularmanipulator$dispatchDeadlineNanos;
    @Unique
    private long molecularmanipulator$dispatchStartedNanos;
    @Unique
    private int molecularmanipulator$dispatchAllowance;
    @Unique
    private int molecularmanipulator$dispatchUsed;
    @Unique
    private boolean molecularmanipulator$dispatchStopped;
    @Unique
    private static volatile Field molecularmanipulator$tasksField;
    @Unique
    private static volatile Field molecularmanipulator$taskValueField;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionAvailable = true;
    @Unique
    private static volatile boolean molecularmanipulator$reflectionFailureLogged;

    @Inject(method = "tickCraftingLogic", at = @At("HEAD"))
    private void molecularmanipulator$beginOmniDispatch(IEnergyService energyService,
            CraftingService craftingService, CallbackInfo callback) {
        molecularmanipulator$clearDispatch();
        molecularmanipulator$adaptiveBatchController.setActiveJob(job);
        var owner = OmniComputationCoreBlockEntity.ownerOf(cluster);
        if (owner == null || job == null) {
            return;
        }

        long tick = TickHandler.instance().getCurrentTick();
        var allowance = owner.claimDispatchAllowance(cluster, tick);
        molecularmanipulator$dispatchOwner = owner;
        molecularmanipulator$dispatchTick = tick;
        molecularmanipulator$dispatchDeadlineNanos = allowance.deadlineNanos();
        molecularmanipulator$dispatchAllowance = allowance.workUnits();
        molecularmanipulator$dispatchStartedNanos = System.nanoTime();
        molecularmanipulator$dispatchStopped = allowance.workUnits() <= 0;
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void molecularmanipulator$endOmniDispatch(IEnergyService energyService,
            CraftingService craftingService, CallbackInfo callback) {
        var owner = molecularmanipulator$dispatchOwner;
        if (owner != null) {
            owner.recordDispatchWork(molecularmanipulator$dispatchTick,
                    molecularmanipulator$dispatchAllowance,
                    molecularmanipulator$dispatchUsed,
                    System.nanoTime() - molecularmanipulator$dispatchStartedNanos);
        }
        molecularmanipulator$clearDispatch();
    }

    @Inject(method = "executeCrafting", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$beginBatchContext(int maxPatterns, CraftingService craftingService,
            IEnergyService energyService, Level level, CallbackInfoReturnable<Integer> callback) {
        molecularmanipulator$clearBatch();
        if (molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$shouldStopDispatch()) {
            callback.setReturnValue(0);
            return;
        }
        molecularmanipulator$craftingService = craftingService;
        molecularmanipulator$energyService = energyService;
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
            // complete logical scheduling window without overflowing. Expensive work is
            // bounded independently, so one batch push may still represent any craft count.
            return Integer.MAX_VALUE - 1;
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
        if (!molecularmanipulator$consumeDispatchWork()) {
            return null;
        }

        var firstInputs = original.call(patternDetails, inventory, level, expectedOutputs,
                expectedContainerItems);
        if (firstInputs == null) {
            return null;
        }

        var currentJob = job;
        var craftingService = molecularmanipulator$craftingService;
        var energyService = molecularmanipulator$energyService;
        if (currentJob == null || craftingService == null || energyService == null) {
            return firstInputs;
        }

        var task = molecularmanipulator$getTasks(currentJob).get(patternDetails);
        if (task == null) {
            return firstInputs;
        }
        long taskValue = molecularmanipulator$getTaskValue(task);
        if (taskValue <= 0) {
            return firstInputs;
        }

        if (molecularmanipulator$dispatchOwner != null) {
            var offers = MolecularBatchDispatchSafety.getAvailableBatchOffers(
                    craftingService, patternDetails, firstInputs,
                    provider -> MolecularBatchCraftingProvider.supports(provider, patternDetails));

            long directLimit = 0;
            for (var offer : offers) {
                if (MolecularBatchCraftingProvider.supports(
                        offer.provider(), patternDetails)) {
                    directLimit = Math.max(directLimit, offer.batchLimit());
                }
            }
            if (directLimit > 0) {
                molecularmanipulator$directPattern = patternDetails;
                molecularmanipulator$directInputs = firstInputs;

                long maxCrafts = Math.min(taskValue, directLimit);
                if (maxCrafts <= 1) {
                    return firstInputs;
                }

                var extraction = MolecularBatchCraftingExtractor.expandFromFirst(patternDetails, inventory,
                        energyService, firstInputs, expectedOutputs, expectedContainerItems, maxCrafts);
                if (extraction == null) {
                    return firstInputs;
                }

                molecularmanipulator$batchPattern = patternDetails;
                molecularmanipulator$batchExtraction = extraction;
                molecularmanipulator$directInputs = extraction.inputs();
                return extraction.inputs();
            }

            long maxAdaptiveWindow = Math.max(1, Math.min(
                    taskValue, ModConfig.OMNI_PROVIDER_MAX_QUEUED_ITEMS.get()));
            long adaptiveLimit = 0;
            boolean foundAdaptiveProvider = false;
            for (var provider : craftingService.getProviders(patternDetails)) {
                if (!molecularmanipulator$supportsAdaptiveProvider(
                        provider, patternDetails)) {
                    continue;
                }
                foundAdaptiveProvider = true;
                if (provider.isBusy()) {
                    continue;
                }

                long initialWindow = 1;
                if (!molecularmanipulator$adaptiveBatchController.hasState(
                        provider, patternDetails)
                        && provider instanceof MolecularBalancedBatchProvider balancedProvider) {
                    long freeCrafts = balancedProvider.molecularmanipulator$estimateFreeCrafts(
                            patternDetails, firstInputs, maxAdaptiveWindow);
                    if (freeCrafts == 0) {
                        continue;
                    }
                    if (freeCrafts > 0) {
                        initialWindow = Math.min(freeCrafts, maxAdaptiveWindow);
                    }
                }
                long available = molecularmanipulator$adaptiveBatchController.getAvailableCrafts(
                        provider, patternDetails, initialWindow, maxAdaptiveWindow);
                adaptiveLimit = Math.max(adaptiveLimit, available);
            }
            if (!foundAdaptiveProvider) {
                return firstInputs;
            }
            if (adaptiveLimit <= 0) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, firstInputs);
                expectedOutputs.reset();
                expectedContainerItems.reset();
                return null;
            }

            molecularmanipulator$adaptivePattern = patternDetails;
            molecularmanipulator$adaptiveInputs = firstInputs;
            return firstInputs;
        }
        long batchLimit = molecularmanipulator$getAvailableBatchLimit(
                craftingService, patternDetails, firstInputs);
        long maxCrafts = Math.min(taskValue, batchLimit);
        if (maxCrafts <= 1) {
            return firstInputs;
        }

        var extraction = MolecularBatchCraftingExtractor.expandFromFirst(patternDetails, inventory,
                energyService, firstInputs, expectedOutputs, expectedContainerItems, maxCrafts);
        if (extraction == null) {
            return firstInputs;
        }

        molecularmanipulator$batchPattern = patternDetails;
        molecularmanipulator$batchExtraction = extraction;
        return extraction.inputs();
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/me/service/CraftingService;getProviders(Lappeng/api/crafting/IPatternDetails;)Ljava/lang/Iterable;"))
    private Iterable<ICraftingProvider> molecularmanipulator$repeatAdaptiveProviders(
            CraftingService craftingService, IPatternDetails patternDetails,
            Operation<Iterable<ICraftingProvider>> original) {
        var providers = original.call(craftingService, patternDetails);
        if (molecularmanipulator$dispatchOwner == null || providers == null) {
            return providers;
        }

        var snapshot = new ArrayList<ICraftingProvider>();
        for (var provider : providers) {
            snapshot.add(provider);
        }
        if (snapshot.isEmpty()) {
            return snapshot;
        }

        return () -> new Iterator<>() {
            private int initialIndex;
            private int repeatIndex;
            private ICraftingProvider nextProvider;
            private boolean prepared;

            @Override
            public boolean hasNext() {
                if (prepared) {
                    return true;
                }
                if (initialIndex < snapshot.size()) {
                    nextProvider = snapshot.get(initialIndex++);
                    prepared = true;
                    return true;
                }

                int checked = 0;
                while (checked++ < snapshot.size()) {
                    var provider = snapshot.get(repeatIndex++);
                    if (repeatIndex >= snapshot.size()) {
                        repeatIndex = 0;
                    }
                    if (molecularmanipulator$canRepeatAdaptiveProvider(
                            provider, patternDetails)) {
                        nextProvider = provider;
                        prepared = true;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public ICraftingProvider next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                prepared = false;
                var result = nextProvider;
                nextProvider = null;
                return result;
            }
        };
    }

    @Unique
    private boolean molecularmanipulator$canRepeatAdaptiveProvider(
            ICraftingProvider provider, IPatternDetails patternDetails) {
        return provider != null
                && molecularmanipulator$adaptivePattern == patternDetails
                && molecularmanipulator$adaptiveInputs != null
                && !provider.isBusy()
                && !MolecularBatchCraftingProvider.supports(provider, patternDetails)
                && molecularmanipulator$supportsAdaptiveProvider(provider, patternDetails)
                && molecularmanipulator$adaptiveBatchController.getAvailableCrafts(
                        provider, patternDetails) > 0;
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean molecularmanipulator$pushBatch(ICraftingProvider provider, IPatternDetails patternDetails,
            KeyCounter[] inputs, Operation<Boolean> original) {
        var extraction = molecularmanipulator$batchExtraction;
        boolean expandedContext = extraction != null
                && molecularmanipulator$batchPattern == patternDetails
                && extraction.inputs() == inputs;
        boolean adaptiveContext = molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$adaptivePattern == patternDetails
                && molecularmanipulator$adaptiveInputs == inputs;
        boolean directContext = molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$directPattern == patternDetails
                && molecularmanipulator$directInputs == inputs;
        if (!expandedContext && !adaptiveContext && !directContext) {
            return original.call(provider, patternDetails, inputs);
        }

        long craftCount = expandedContext ? extraction.craftCount() : 1;
        KeyCounter[] firstInputs = expandedContext ? extraction.firstInputs() : inputs;
        boolean explicitBatchProvider = MolecularBatchCraftingProvider.supports(
                provider, patternDetails);
        boolean incompatibleProvider = directContext
                ? !explicitBatchProvider
                : adaptiveContext
                        ? !molecularmanipulator$supportsAdaptiveProvider(
                                provider, patternDetails)
                        : !molecularmanipulator$supportsProvider(provider, patternDetails);
        boolean insufficientBatchLimit = !adaptiveContext
                && MolecularBatchCraftingProvider.getBatchLimit(
                        provider, patternDetails, firstInputs) < craftCount;
        if (incompatibleProvider || insufficientBatchLimit) {
            return false;
        }
        if (adaptiveContext
                && molecularmanipulator$adaptiveBatchController.getAvailableCrafts(
                        provider, patternDetails) < craftCount) {
            return false;
        }

        boolean accepted;
        if (provider instanceof MolecularBalancedBatchProvider balancedProvider) {
            if (adaptiveContext && !explicitBatchProvider) {
                balancedProvider.molecularmanipulator$beginAdaptiveBatch(firstInputs);
            } else {
                balancedProvider.molecularmanipulator$beginBalancedBatch(firstInputs);
            }
            try {
                accepted = original.call(provider, patternDetails, inputs);
            } finally {
                balancedProvider.molecularmanipulator$endBalancedBatch();
            }
        } else {
            accepted = original.call(provider, patternDetails, inputs);
        }

        if (accepted) {
            if (expandedContext) {
                molecularmanipulator$consumeBatchTask(patternDetails, craftCount);
            }
            if (adaptiveContext) {
                molecularmanipulator$adaptiveBatchController.onAccepted(
                        provider, patternDetails, craftCount, provider.isBusy());
            }
            molecularmanipulator$clearBatch();
        } else if (adaptiveContext) {
            molecularmanipulator$adaptiveBatchController.onRejected(provider, patternDetails);
        }
        return accepted;
    }

    @ModifyExpressionValue(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Ljava/util/Iterator;hasNext()Z", ordinal = 0))
    private boolean molecularmanipulator$limitTaskIteration(boolean original) {
        if (!original || molecularmanipulator$dispatchOwner == null) {
            return original;
        }
        return !molecularmanipulator$shouldStopDispatch();
    }

    @ModifyExpressionValue(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Ljava/util/Iterator;hasNext()Z", ordinal = 1))
    private boolean molecularmanipulator$limitProviderIteration(boolean original) {
        if (!original || molecularmanipulator$dispatchOwner == null) {
            return original;
        }
        return molecularmanipulator$consumeDispatchWork();
    }

    @Unique
    private long molecularmanipulator$getAvailableBatchLimit(CraftingService craftingService,
            IPatternDetails patternDetails, KeyCounter[] firstInputs) {
        return MolecularBatchDispatchSafety.getAvailableBatchLimit(
                craftingService, patternDetails, firstInputs,
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
    private boolean molecularmanipulator$supportsAdaptiveProvider(
            ICraftingProvider provider, IPatternDetails patternDetails) {
        return molecularmanipulator$dispatchOwner != null
                && provider != null
                && patternDetails != null
                && !MolecularBatchCraftingProvider.supports(provider, patternDetails);
    }

    @Unique
    private void molecularmanipulator$clearBatch() {
        molecularmanipulator$batchPattern = null;
        molecularmanipulator$batchExtraction = null;
        molecularmanipulator$adaptivePattern = null;
        molecularmanipulator$adaptiveInputs = null;
        molecularmanipulator$directPattern = null;
        molecularmanipulator$directInputs = null;
    }

    @Unique
    private boolean molecularmanipulator$consumeDispatchWork() {
        if (molecularmanipulator$dispatchOwner == null) {
            return true;
        }
        if (molecularmanipulator$shouldStopDispatch()) {
            return false;
        }
        molecularmanipulator$dispatchUsed++;
        return true;
    }

    @Unique
    private boolean molecularmanipulator$shouldStopDispatch() {
        if (molecularmanipulator$dispatchStopped) {
            return true;
        }
        if (molecularmanipulator$dispatchUsed >= molecularmanipulator$dispatchAllowance
                || molecularmanipulator$dispatchDeadlineNanos <= 0
                || System.nanoTime() >= molecularmanipulator$dispatchDeadlineNanos) {
            molecularmanipulator$dispatchStopped = true;
        }
        return molecularmanipulator$dispatchStopped;
    }

    @Unique
    private void molecularmanipulator$clearDispatch() {
        molecularmanipulator$dispatchOwner = null;
        molecularmanipulator$dispatchTick = 0;
        molecularmanipulator$dispatchDeadlineNanos = 0;
        molecularmanipulator$dispatchStartedNanos = 0;
        molecularmanipulator$dispatchAllowance = 0;
        molecularmanipulator$dispatchUsed = 0;
        molecularmanipulator$dispatchStopped = false;
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
