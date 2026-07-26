package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.execution.ExecutingCraftingJob;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.hooks.ticking.TickHandler;
import appeng.me.service.CraftingService;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.crafting.MolecularAdaptiveBatchController;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor;
import com.atir.molecularmanipulator.crafting.MolecularBatchCraftingExtractor.BatchExtraction;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchSafety;
import com.atir.molecularmanipulator.crafting.MolecularScaledPatternFactory;
import com.atir.molecularmanipulator.integration.ae2.MolecularBalancedBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularScaledBatchProvider.PushResult;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

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

    @Shadow
    public abstract long getWaitingFor(AEKey template);

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
    private KeyCounter[] molecularmanipulator$adaptiveFirstInputs;
    @Unique
    private IPatternDetails molecularmanipulator$adaptiveDispatchPattern;
    @Unique
    private ICraftingProvider molecularmanipulator$adaptiveProvider;
    @Unique
    private long molecularmanipulator$adaptiveCraftCount;
    @Unique
    private long molecularmanipulator$adaptiveRequestedCrafts;
    @Unique
    private long molecularmanipulator$adaptiveSerial;
    @Unique
    private KeyCounter molecularmanipulator$adaptiveExpectedOutputs;
    @Unique
    private KeyCounter molecularmanipulator$adaptiveExpectedContainerItems;
    @Unique
    private IPatternDetails molecularmanipulator$directPattern;
    @Unique
    private KeyCounter[] molecularmanipulator$directInputs;
    @Unique
    private IPatternDetails molecularmanipulator$fallbackPattern;
    @Unique
    private KeyCounter[] molecularmanipulator$fallbackInputs;
    @Unique
    private OmniComputationCoreBlockEntity molecularmanipulator$dispatchOwner;
    @Unique
    private long molecularmanipulator$dispatchTick;
    @Unique
    private long molecularmanipulator$dispatchAllowance;
    @Unique
    private long molecularmanipulator$dispatchUsed;
    @Unique
    private boolean molecularmanipulator$dispatchStopped;
    @Unique
    private int molecularmanipulator$unscaledDispatchAllowance;
    @Unique
    private int molecularmanipulator$unscaledDispatchUsed;
    @Unique
    private boolean molecularmanipulator$unscaledDispatchNeedsMore;
    @Unique
    private final Set<IPatternDetails> molecularmanipulator$unscaledQuotaBlockedPatterns =
            Collections.newSetFromMap(new IdentityHashMap<>());
    @Unique
    private final KeyCounter molecularmanipulator$statusExpected = new KeyCounter();
    @Unique
    private final KeyCounter molecularmanipulator$statusObserved = new KeyCounter();
    @Unique
    private Object molecularmanipulator$statusJob;
    @Unique
    private ICraftingProvider molecularmanipulator$statusProvider;
    @Unique
    private IPatternDetails molecularmanipulator$statusPattern;
    @Unique
    private boolean molecularmanipulator$statusPending;
    @Unique
    private boolean molecularmanipulator$statusOverflow;
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
        molecularmanipulator$dispatchAllowance = allowance.workUnits();
        molecularmanipulator$unscaledDispatchAllowance = allowance.unscaledAttempts();
        molecularmanipulator$dispatchStopped = allowance.workUnits() <= 0;
    }

    @Inject(method = "tickCraftingLogic", at = @At("RETURN"))
    private void molecularmanipulator$endOmniDispatch(IEnergyService energyService,
            CraftingService craftingService, CallbackInfo callback) {
        var owner = molecularmanipulator$dispatchOwner;
        if (owner != null) {
            owner.recordDispatchWork(cluster, molecularmanipulator$dispatchTick,
                    molecularmanipulator$dispatchAllowance,
                    molecularmanipulator$dispatchUsed,
                    molecularmanipulator$unscaledDispatchAllowance,
                    molecularmanipulator$unscaledDispatchUsed,
                    molecularmanipulator$unscaledDispatchNeedsMore);
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
        molecularmanipulator$verifyPendingStatus();
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
        molecularmanipulator$verifyPendingStatus();
        molecularmanipulator$clearBatch();
        if (molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$unscaledQuotaBlockedPatterns.contains(
                        patternDetails)) {
            return null;
        }
        if (molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$isUnscaledQuotaExhausted()
                && !molecularmanipulator$canDispatchWithoutUnscaled(
                        patternDetails)) {
            molecularmanipulator$unscaledQuotaBlockedPatterns.add(
                    patternDetails);
            molecularmanipulator$unscaledDispatchNeedsMore = true;
            return null;
        }
        if (!molecularmanipulator$consumeDispatchWork()) {
            return null;
        }

        var firstInputs = original.call(patternDetails, inventory, level, expectedOutputs,
                expectedContainerItems);
        if (firstInputs == null) {
            return null;
        }
        long waitingForCraftLimit = molecularmanipulator$getWaitingForCraftLimit(
                expectedOutputs, expectedContainerItems);
        if (waitingForCraftLimit <= 0) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, firstInputs);
            expectedOutputs.reset();
            expectedContainerItems.reset();
            if (molecularmanipulator$dispatchOwner != null) {
                molecularmanipulator$dispatchStopped = true;
            }
            return null;
        }
        if (molecularmanipulator$dispatchOwner != null) {
            // Until a verified aggregate context is established, this extraction is
            // a conservative one-recipe fallback. Repeated real calls are bounded by
            // the core-wide per-tick unscaled-attempt allowance.
            molecularmanipulator$fallbackPattern = patternDetails;
            molecularmanipulator$fallbackInputs = firstInputs;
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
            boolean unscaledQuotaExhausted =
                    molecularmanipulator$isUnscaledQuotaExhausted();
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
                long maxCrafts = Math.min(
                        Math.min(taskValue, directLimit),
                        waitingForCraftLimit);
                if (maxCrafts > 1) {
                    var extraction = MolecularBatchCraftingExtractor.expandFromFirst(
                            patternDetails, inventory, energyService, firstInputs,
                            expectedOutputs, expectedContainerItems, maxCrafts);
                    if (extraction != null) {
                        molecularmanipulator$batchPattern = patternDetails;
                        molecularmanipulator$batchExtraction = extraction;
                        molecularmanipulator$directPattern = patternDetails;
                        molecularmanipulator$directInputs = extraction.inputs();
                        return extraction.inputs();
                    }
                }
                if (!unscaledQuotaExhausted) {
                    return firstInputs;
                }
            }

            if (!MolecularBatchDispatchSafety.isBatchablePattern(patternDetails)) {
                if (unscaledQuotaExhausted) {
                    CraftingCpuHelper.reinjectPatternInputs(
                            inventory, firstInputs);
                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    molecularmanipulator$unscaledQuotaBlockedPatterns.add(
                            patternDetails);
                    return null;
                }
                return firstInputs;
            }

            long maxAdaptiveWindow = Math.max(
                    1L, Math.min(taskValue, waitingForCraftLimit));
            ICraftingProvider selectedProvider = null;
            long selectedWindow = 0;
            long selectedLastAttempt = Long.MAX_VALUE;
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

                long available = molecularmanipulator$adaptiveBatchController.getAvailableCrafts(
                        provider, patternDetails, maxAdaptiveWindow);
                if (unscaledQuotaExhausted && available <= 1) {
                    continue;
                }
                long lastAttempt = molecularmanipulator$adaptiveBatchController.getLastAttemptOrder(
                        provider, patternDetails);
                if (available > 0
                        && (selectedProvider == null
                                || lastAttempt < selectedLastAttempt
                                || (lastAttempt == selectedLastAttempt
                                        && available > selectedWindow))) {
                    selectedProvider = provider;
                    selectedWindow = available;
                    selectedLastAttempt = lastAttempt;
                }
            }
            if (!foundAdaptiveProvider) {
                if (unscaledQuotaExhausted) {
                    CraftingCpuHelper.reinjectPatternInputs(
                            inventory, firstInputs);
                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    molecularmanipulator$unscaledQuotaBlockedPatterns.add(
                            patternDetails);
                    return null;
                }
                return firstInputs;
            }
            if (selectedProvider == null || selectedWindow <= 0) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, firstInputs);
                expectedOutputs.reset();
                expectedContainerItems.reset();
                if (unscaledQuotaExhausted) {
                    molecularmanipulator$unscaledQuotaBlockedPatterns.add(
                            patternDetails);
                }
                return null;
            }

            long craftCount = 1;
            KeyCounter[] completeInputs = firstInputs;
            KeyCounter[] unscaledInputs = firstInputs;
            BatchExtraction adaptiveExtraction = null;
            if (selectedWindow > 1) {
                var extraction = MolecularBatchCraftingExtractor.expandFromFirst(patternDetails, inventory,
                        energyService, firstInputs, expectedOutputs, expectedContainerItems,
                        selectedWindow);
                if (extraction != null) {
                    adaptiveExtraction = extraction;
                    craftCount = extraction.craftCount();
                    completeInputs = extraction.inputs();
                    unscaledInputs = extraction.firstInputs();
                }
            }

            IPatternDetails dispatchPattern;
            try {
                dispatchPattern = craftCount > 1
                        ? MolecularScaledPatternFactory.create(
                                patternDetails, craftCount)
                        : patternDetails;
            } catch (RuntimeException exception) {
                if (adaptiveExtraction != null) {
                    adaptiveExtraction.rollbackAdditional(inventory, expectedOutputs);
                }
                molecularmanipulator$adaptiveBatchController.forceSingle(
                        selectedProvider, patternDetails);
                molecularmanipulator$unscaledDispatchNeedsMore = true;
                return firstInputs;
            }

            molecularmanipulator$adaptivePattern = patternDetails;
            molecularmanipulator$adaptiveInputs = completeInputs;
            molecularmanipulator$adaptiveFirstInputs = unscaledInputs;
            molecularmanipulator$adaptiveProvider = selectedProvider;
            molecularmanipulator$adaptiveCraftCount = craftCount;
            molecularmanipulator$adaptiveRequestedCrafts = selectedWindow;
            molecularmanipulator$adaptiveDispatchPattern = dispatchPattern;
            molecularmanipulator$adaptiveExpectedOutputs = expectedOutputs;
            molecularmanipulator$adaptiveExpectedContainerItems = expectedContainerItems;
            molecularmanipulator$adaptiveSerial++;
            return completeInputs;
        }
        long batchLimit = molecularmanipulator$getAvailableBatchLimit(
                craftingService, patternDetails, firstInputs);
        long maxCrafts = Math.min(
                Math.min(taskValue, batchLimit),
                waitingForCraftLimit);
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
    private Iterable<ICraftingProvider> molecularmanipulator$selectAdaptiveProvider(
            CraftingService craftingService, IPatternDetails patternDetails,
            Operation<Iterable<ICraftingProvider>> original) {
        if (molecularmanipulator$dispatchOwner == null
                || molecularmanipulator$adaptivePattern != patternDetails
                || molecularmanipulator$adaptiveProvider == null) {
            return original.call(craftingService, patternDetails);
        }

        return () -> new Iterator<>() {
            private long deliveredSerial = Long.MIN_VALUE;

            @Override
            public boolean hasNext() {
                var provider = molecularmanipulator$adaptiveProvider;
                return molecularmanipulator$adaptivePattern == patternDetails
                        && provider != null
                        && !provider.isBusy()
                        && deliveredSerial != molecularmanipulator$adaptiveSerial;
            }

            @Override
            public ICraftingProvider next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                deliveredSerial = molecularmanipulator$adaptiveSerial;
                return molecularmanipulator$adaptiveProvider;
            }
        };
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
                && molecularmanipulator$adaptiveInputs == inputs
                && molecularmanipulator$adaptiveProvider == provider;
        boolean directContext = molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$directPattern == patternDetails
                && molecularmanipulator$directInputs == inputs;
        boolean fallbackContext = molecularmanipulator$dispatchOwner != null
                && molecularmanipulator$fallbackPattern == patternDetails
                && molecularmanipulator$fallbackInputs == inputs;
        if (!expandedContext && !adaptiveContext && !directContext) {
            if (fallbackContext
                    && !molecularmanipulator$consumeUnscaledDispatchAttempt(
                            patternDetails)) {
                return false;
            }
            boolean accepted = original.call(
                    provider, patternDetails, inputs);
            if (accepted && fallbackContext) {
                molecularmanipulator$unscaledDispatchNeedsMore = true;
            }
            return accepted;
        }

        long craftCount = expandedContext
                ? extraction.craftCount()
                : adaptiveContext
                        ? molecularmanipulator$adaptiveCraftCount
                        : 1;
        KeyCounter[] firstInputs = expandedContext
                ? extraction.firstInputs()
                : adaptiveContext
                        ? molecularmanipulator$adaptiveFirstInputs
                        : inputs;
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
        if (adaptiveContext && craftCount == 1
                && !molecularmanipulator$consumeUnscaledDispatchAttempt(
                        patternDetails)) {
            return false;
        }

        var taskAdjustment = craftCount > 1
                ? molecularmanipulator$prepareBatchTask(patternDetails, craftCount)
                : null;
        if (craftCount > 1 && taskAdjustment == null) {
            // Do not hand an aggregate batch to a provider unless the matching AE
            // task can be adjusted first. This also lets EAP's virtual-completion
            // hook observe the post-batch task count while it is inside pushPattern.
            if (adaptiveContext) {
                molecularmanipulator$adaptiveBatchController.forceSingle(
                        provider, patternDetails);
                molecularmanipulator$unscaledDispatchNeedsMore = true;
            }
            return false;
        }

        var acceptedJob = job;
        boolean providerAccepted = false;
        try {
            boolean accepted;
            PushResult adaptiveResult = null;
            if (adaptiveContext
                    && provider instanceof MolecularScaledBatchProvider scaledProvider
                    && scaledProvider.molecularmanipulator$supportsScaledBatch(patternDetails)) {
                try {
                    scaledProvider.molecularmanipulator$beginScaledBatch(inputs, firstInputs);
                } catch (RuntimeException exception) {
                    molecularmanipulator$adaptiveBatchController.onRejected(
                            provider, patternDetails, craftCount);
                    molecularmanipulator$markSingleOnlyDemand(
                            provider, patternDetails);
                    return false;
                }

                boolean finished = false;
                try {
                    accepted = molecularmanipulator$pushScaledPattern(
                            provider, patternDetails,
                            molecularmanipulator$adaptiveDispatchPattern,
                            inputs, original);
                    providerAccepted = accepted;
                    try {
                        adaptiveResult =
                                scaledProvider.molecularmanipulator$endScaledBatch(accepted);
                    } catch (RuntimeException exception) {
                        // The provider has already made its ownership decision. Do not
                        // let a save/cleanup hook prevent AE2 from recording waitingFor
                        // and decrementing the task exactly once.
                        molecularmanipulator$logProviderCleanupFailure(
                                "scaled batch completion", exception);
                        adaptiveResult = accepted
                                ? PushResult.ACCEPTED_UNVERIFIED
                                : PushResult.REJECTED;
                    }
                    finished = true;
                } finally {
                    if (!finished) {
                        try {
                            scaledProvider.molecularmanipulator$abortScaledBatch();
                        } catch (RuntimeException cleanupException) {
                            molecularmanipulator$logProviderCleanupFailure(
                                    "scaled batch abort", cleanupException);
                        }
                    }
                }
            } else if (adaptiveContext) {
                // Third-party providers are allowed to consume the same runtime-scaled
                // pattern directly. Their public AE contract only exposes acceptance
                // and busy state, so use those signals when precise queue ownership is
                // unavailable.
                accepted = molecularmanipulator$pushScaledPattern(
                        provider, patternDetails,
                        molecularmanipulator$adaptiveDispatchPattern,
                        inputs, original);
                providerAccepted = accepted;
                adaptiveResult = molecularmanipulator$classifyGenericScaledPush(
                        accepted);
            } else if (provider instanceof MolecularBalancedBatchProvider balancedProvider) {
                if (adaptiveContext && !explicitBatchProvider) {
                    balancedProvider.molecularmanipulator$beginAdaptiveBatch(inputs);
                } else {
                    balancedProvider.molecularmanipulator$beginBalancedBatch(firstInputs);
                }
                try {
                    accepted = original.call(provider, patternDetails, inputs);
                    providerAccepted = accepted;
                } finally {
                    try {
                        balancedProvider.molecularmanipulator$endBalancedBatch();
                    } catch (RuntimeException cleanupException) {
                        molecularmanipulator$logProviderCleanupFailure(
                                "balanced batch completion", cleanupException);
                    }
                }
            } else {
                accepted = original.call(provider, patternDetails, inputs);
                providerAccepted = accepted;
            }

            if (accepted) {
                if (adaptiveContext) {
                    molecularmanipulator$armStatusVerification(
                            acceptedJob, provider, patternDetails,
                            molecularmanipulator$adaptiveExpectedOutputs,
                            molecularmanipulator$adaptiveExpectedContainerItems);
                    molecularmanipulator$adaptiveBatchController.onAccepted(
                            provider, patternDetails, craftCount,
                            molecularmanipulator$adaptiveRequestedCrafts,
                            adaptiveResult == null
                                    ? PushResult.ACCEPTED_UNVERIFIED
                                    : adaptiveResult);
                    molecularmanipulator$markSingleOnlyDemand(
                            provider, patternDetails);
                }
                molecularmanipulator$clearBatch();
            } else if (adaptiveContext) {
                molecularmanipulator$adaptiveBatchController.onRejected(
                        provider, patternDetails, craftCount);
                molecularmanipulator$markSingleOnlyDemand(
                        provider, patternDetails);
            }
            return accepted;
        } finally {
            if (!providerAccepted && taskAdjustment != null) {
                taskAdjustment.rollback();
            }
        }
    }

    @WrapOperation(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/crafting/inv/ListCraftingInventory;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)V"),
            require = 0)
    private void molecularmanipulator$trackWaitingForAccounting(
            ListCraftingInventory waitingFor, AEKey what, long amount, Actionable mode,
            Operation<Void> original) {
        original.call(waitingFor, what, amount, mode);
        if (!molecularmanipulator$statusPending
                || mode != Actionable.MODULATE || what == null || amount <= 0) {
            return;
        }
        try {
            molecularmanipulator$statusObserved.add(what, amount);
        } catch (RuntimeException exception) {
            molecularmanipulator$statusOverflow = true;
        }
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
    private long molecularmanipulator$getWaitingForCraftLimit(
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        var perCraft = new HashMap<AEKey, Long>();
        if (!molecularmanipulator$mergeExpectedAmounts(
                perCraft, expectedOutputs)
                || !molecularmanipulator$mergeExpectedAmounts(
                        perCraft, expectedContainerItems)) {
            return 0;
        }

        long limit = Long.MAX_VALUE;
        for (var entry : perCraft.entrySet()) {
            long amountPerCraft = entry.getValue();
            if (amountPerCraft <= 0) {
                return 0;
            }

            long currentlyWaiting;
            try {
                currentlyWaiting = getWaitingFor(entry.getKey());
            } catch (RuntimeException exception) {
                return 0;
            }
            if (currentlyWaiting < 0) {
                return 0;
            }
            limit = Math.min(
                    limit,
                    (Long.MAX_VALUE - currentlyWaiting) / amountPerCraft);
            if (limit <= 0) {
                return 0;
            }
        }
        return limit;
    }

    @Unique
    private static boolean molecularmanipulator$mergeExpectedAmounts(
            Map<AEKey, Long> perCraft, KeyCounter expected) {
        if (expected == null) {
            return true;
        }
        try {
            for (var entry : expected) {
                var key = entry.getKey();
                long amount = entry.getLongValue();
                if (key == null || amount <= 0) {
                    return false;
                }
                perCraft.merge(key, amount, Math::addExact);
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
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
    private boolean molecularmanipulator$canDispatchWithoutUnscaled(
            IPatternDetails patternDetails) {
        var currentJob = job;
        var craftingService = molecularmanipulator$craftingService;
        if (currentJob == null || craftingService == null
                || patternDetails == null) {
            return false;
        }

        var task = molecularmanipulator$getTasks(currentJob).get(patternDetails);
        long taskValue = task == null
                ? 0
                : molecularmanipulator$getTaskValue(task);
        if (taskValue <= 1) {
            return false;
        }

        try {
            for (var provider : craftingService.getProviders(patternDetails)) {
                if (provider == null || provider.isBusy()) {
                    continue;
                }
                if (MolecularBatchCraftingProvider.supports(
                        provider, patternDetails)) {
                    return true;
                }
                if (molecularmanipulator$supportsAdaptiveProvider(
                        provider, patternDetails)
                        && molecularmanipulator$adaptiveBatchController
                                .getAvailableCrafts(
                                        provider, patternDetails, taskValue)
                                > 1) {
                    return true;
                }
            }
        } catch (RuntimeException exception) {
            return false;
        }
        return false;
    }

    @Unique
    private PushResult molecularmanipulator$classifyGenericScaledPush(
            boolean accepted) {
        return accepted ? PushResult.ACCEPTED_FULL : PushResult.REJECTED;
    }

    @Unique
    private boolean molecularmanipulator$pushScaledPattern(
            ICraftingProvider provider, IPatternDetails basePattern,
            IPatternDetails dispatchPattern, KeyCounter[] inputs,
            Operation<Boolean> original) {
        java.util.List<IPatternDetails> availablePatterns = null;
        boolean temporarilyAdded = false;
        try {
            try {
                availablePatterns = provider.getAvailablePatterns();
                if (availablePatterns != null
                        && !availablePatterns.contains(dispatchPattern)
                        && molecularmanipulator$ownsBasePattern(
                                availablePatterns, basePattern)) {
                    temporarilyAdded = availablePatterns.add(dispatchPattern);
                }
            } catch (RuntimeException ignored) {
                // Some providers expose an immutable view or a snapshot. Providers
                // that natively support scaled patterns do not need temporary
                // registration, so continue with the actual push.
                availablePatterns = null;
                temporarilyAdded = false;
            }
            return original.call(provider, dispatchPattern, inputs);
        } finally {
            if (temporarilyAdded && availablePatterns != null) {
                try {
                    for (int index = availablePatterns.size() - 1; index >= 0; index--) {
                        if (availablePatterns.get(index) == dispatchPattern) {
                            availablePatterns.remove(index);
                            break;
                        }
                    }
                } catch (RuntimeException cleanupException) {
                    molecularmanipulator$logProviderCleanupFailure(
                            "temporary scaled-pattern removal", cleanupException);
                }
            }
        }
    }

    @Unique
    private boolean molecularmanipulator$ownsBasePattern(
            java.util.List<IPatternDetails> availablePatterns,
            IPatternDetails basePattern) {
        for (var availablePattern : availablePatterns) {
            try {
                if (availablePattern == basePattern
                        || (availablePattern != null
                                && (availablePattern.equals(basePattern)
                                        || availablePattern.getDefinition().equals(
                                                basePattern.getDefinition())))) {
                    return true;
                }
            } catch (RuntimeException ignored) {
                // Keep checking other advertised patterns.
            }
        }
        return false;
    }

    @Unique
    private void molecularmanipulator$clearBatch() {
        molecularmanipulator$batchPattern = null;
        molecularmanipulator$batchExtraction = null;
        molecularmanipulator$adaptivePattern = null;
        molecularmanipulator$adaptiveInputs = null;
        molecularmanipulator$adaptiveFirstInputs = null;
        molecularmanipulator$adaptiveDispatchPattern = null;
        molecularmanipulator$adaptiveProvider = null;
        molecularmanipulator$adaptiveCraftCount = 0;
        molecularmanipulator$adaptiveRequestedCrafts = 0;
        molecularmanipulator$adaptiveExpectedOutputs = null;
        molecularmanipulator$adaptiveExpectedContainerItems = null;
        molecularmanipulator$directPattern = null;
        molecularmanipulator$directInputs = null;
        molecularmanipulator$fallbackPattern = null;
        molecularmanipulator$fallbackInputs = null;
    }

    @Unique
    private void molecularmanipulator$armStatusVerification(Object acceptedJob,
            ICraftingProvider provider, IPatternDetails patternDetails,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
        molecularmanipulator$verifyPendingStatus();
        molecularmanipulator$statusExpected.reset();
        molecularmanipulator$statusObserved.reset();
        molecularmanipulator$statusOverflow = false;
        try {
            if (expectedOutputs != null) {
                molecularmanipulator$statusExpected.addAll(expectedOutputs);
            }
            if (expectedContainerItems != null) {
                molecularmanipulator$statusExpected.addAll(expectedContainerItems);
            }
        } catch (RuntimeException exception) {
            molecularmanipulator$statusOverflow = true;
        }
        molecularmanipulator$statusJob = acceptedJob;
        molecularmanipulator$statusProvider = provider;
        molecularmanipulator$statusPattern = patternDetails;
        molecularmanipulator$statusPending = true;
    }

    @Unique
    private void molecularmanipulator$verifyPendingStatus() {
        if (!molecularmanipulator$statusPending) {
            return;
        }

        // If a virtual-completion hook already finished this job there is no live
        // waiting-for state left to validate, and completion itself is conclusive.
        boolean verified = job != molecularmanipulator$statusJob
                || (!molecularmanipulator$statusOverflow
                        && molecularmanipulator$statusAmountsMatch());
        molecularmanipulator$adaptiveBatchController.onStatusVerification(
                molecularmanipulator$statusProvider,
                molecularmanipulator$statusPattern,
                verified);
        if (!verified) {
            molecularmanipulator$markSingleOnlyDemand(
                    molecularmanipulator$statusProvider,
                    molecularmanipulator$statusPattern);
        }

        molecularmanipulator$statusExpected.reset();
        molecularmanipulator$statusObserved.reset();
        molecularmanipulator$statusJob = null;
        molecularmanipulator$statusProvider = null;
        molecularmanipulator$statusPattern = null;
        molecularmanipulator$statusPending = false;
        molecularmanipulator$statusOverflow = false;
    }

    @Unique
    private boolean molecularmanipulator$statusAmountsMatch() {
        if (molecularmanipulator$statusExpected.size()
                != molecularmanipulator$statusObserved.size()) {
            return false;
        }
        for (var entry : molecularmanipulator$statusExpected) {
            if (molecularmanipulator$statusObserved.get(entry.getKey())
                    != entry.getLongValue()) {
                return false;
            }
        }
        return true;
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
    private void molecularmanipulator$markSingleOnlyDemand(
            ICraftingProvider provider, IPatternDetails patternDetails) {
        if (molecularmanipulator$adaptiveBatchController.isSingleOnly(
                provider, patternDetails)) {
            molecularmanipulator$unscaledDispatchNeedsMore = true;
        }
    }

    @Unique
    private boolean molecularmanipulator$isUnscaledQuotaExhausted() {
        return molecularmanipulator$unscaledDispatchUsed
                >= molecularmanipulator$unscaledDispatchAllowance;
    }

    @Unique
    private boolean molecularmanipulator$consumeUnscaledDispatchAttempt(
            IPatternDetails patternDetails) {
        if (molecularmanipulator$dispatchOwner == null) {
            return true;
        }
        if (molecularmanipulator$isUnscaledQuotaExhausted()) {
            molecularmanipulator$unscaledQuotaBlockedPatterns.add(
                    patternDetails);
            molecularmanipulator$unscaledDispatchNeedsMore = true;
            return false;
        }
        molecularmanipulator$unscaledDispatchUsed++;
        if (molecularmanipulator$isUnscaledQuotaExhausted()) {
            molecularmanipulator$unscaledDispatchNeedsMore = true;
        }
        return true;
    }

    @Unique
    private boolean molecularmanipulator$shouldStopDispatch() {
        if (molecularmanipulator$dispatchStopped) {
            return true;
        }
        if (molecularmanipulator$dispatchUsed >= molecularmanipulator$dispatchAllowance) {
            molecularmanipulator$dispatchStopped = true;
        }
        return molecularmanipulator$dispatchStopped;
    }

    @Unique
    private void molecularmanipulator$clearDispatch() {
        molecularmanipulator$dispatchOwner = null;
        molecularmanipulator$dispatchTick = 0;
        molecularmanipulator$dispatchAllowance = 0;
        molecularmanipulator$dispatchUsed = 0;
        molecularmanipulator$dispatchStopped = false;
        molecularmanipulator$unscaledDispatchAllowance = 0;
        molecularmanipulator$unscaledDispatchUsed = 0;
        molecularmanipulator$unscaledDispatchNeedsMore = false;
        molecularmanipulator$unscaledQuotaBlockedPatterns.clear();
    }

    @Unique
    private MolecularBatchTaskAdjustment molecularmanipulator$prepareBatchTask(
            IPatternDetails patternDetails, long craftCount) {
        var currentJob = job;
        if (currentJob == null || craftCount <= 1) {
            return null;
        }
        var task = molecularmanipulator$getTasks(currentJob).get(patternDetails);
        if (task == null) {
            return null;
        }
        long originalValue = molecularmanipulator$getTaskValue(task);
        if (originalValue < craftCount) {
            return null;
        }
        long valueBeforeOriginalDecrement = originalValue - craftCount + 1;
        if (valueBeforeOriginalDecrement < 1
                || !molecularmanipulator$setTaskValue(task, valueBeforeOriginalDecrement)) {
            return null;
        }
        return new MolecularBatchTaskAdjustment(task, originalValue);
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
    private static boolean molecularmanipulator$setTaskValue(Object task, long value) {
        try {
            var field = molecularmanipulator$getTaskValueField(task);
            field.setLong(task, value);
            return true;
        } catch (ReflectiveOperationException | RuntimeException exception) {
            molecularmanipulator$disableReflection(exception);
            return false;
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

    @Unique
    private static void molecularmanipulator$logProviderCleanupFailure(
            String phase, RuntimeException exception) {
        com.atir.molecularmanipulator.MolecularManipulator.LOGGER.error(
                "Pattern provider {} failed after its push decision; "
                        + "AE2 accounting will continue and scaled dispatch will be downgraded",
                phase, exception);
    }

    @Unique
    private static final class MolecularBatchTaskAdjustment {
        private final Object task;
        private final long originalValue;

        private MolecularBatchTaskAdjustment(Object task, long originalValue) {
            this.task = task;
            this.originalValue = originalValue;
        }

        private void rollback() {
            molecularmanipulator$setTaskValue(task, originalValue);
        }
    }
}
