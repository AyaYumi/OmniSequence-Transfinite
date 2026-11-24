package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.crafting.MolecularScaledPattern;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.integration.ae2.MolecularBalancedBatchProvider;
import com.atir.molecularmanipulator.integration.ae2.MolecularProcessingBatchProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin
        implements MolecularBalancedBatchProvider, MolecularProcessingBatchProvider {
    @Unique
    private static final long MOLECULARMANIPULATOR_FAIR_SEND_BUDGET_NANOS = 2_000_000L;
    @Unique
    private static final long MOLECULARMANIPULATOR_FAIR_SEND_OPERATIONS = 65_536L;
    @Unique
    private static final long MOLECULARMANIPULATOR_LEGACY_BATCH_THRESHOLD = 1_000_000L;
    @Unique
    private static final String MOLECULARMANIPULATOR_LEGACY_REFUND_TAG =
            "molecularmanipulatorLegacyBatchRefund";
    @Unique
    private static final String MOLECULARMANIPULATOR_BATCH_RECIPE_TAG =
            "molecularmanipulatorBatchRecipe";
    @Unique
    private static final String MOLECULARMANIPULATOR_SMART_QUEUE_VERSION_TAG =
            "molecularmanipulatorSmartQueueVersion";
    @Unique
    private static final int MOLECULARMANIPULATOR_SMART_QUEUE_VERSION = 2;
    @Unique
    private static final int MOLECULARMANIPULATOR_QUEUED_BATCH_VERSION = 3;
    @Unique
    private static final int MOLECULARMANIPULATOR_SEED_MOVED = 1;
    @Unique
    private static final int MOLECULARMANIPULATOR_SEED_COMPLETE = 2;

    @Shadow
    @Final
    private List<GenericStack> sendList;

    @Shadow
    @Final
    private List<IPatternDetails> patterns;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private IActionSource actionSource;

    @Shadow
    private Direction sendDirection;

    @Shadow
    private int roundRobinIndex;

    @Shadow
    private void onPushPatternSuccess(IPatternDetails pattern) {
        throw new AssertionError();
    }

    @Shadow
    private boolean sendStacksOut() {
        throw new AssertionError();
    }

    @Shadow
    public abstract void saveChanges();

    @Unique
    private final List<GenericStack> molecularmanipulator$legacyBatchRefund = new ArrayList<>();
    @Unique
    private final Object2LongOpenHashMap<AEKey> molecularmanipulator$initialBatchAmounts =
            new Object2LongOpenHashMap<>();
    @Unique
    private final Object2LongLinkedOpenHashMap<AEKey> molecularmanipulator$queuedBatchAmounts =
            new Object2LongLinkedOpenHashMap<>();
    @Unique
    private boolean molecularmanipulator$balancingBatch;
    @Unique
    private int molecularmanipulator$smartQueueVersion;
    @Unique
    private boolean molecularmanipulator$smartQueueProgress;
    @Unique
    private boolean molecularmanipulator$sendingSmartQueue;
    @Unique
    private boolean molecularmanipulator$committingProcessingBatch;
    @Unique
    private KeyCounter[] molecularmanipulator$queuedProbeInputs;
    @Unique
    private boolean molecularmanipulator$queuedProbeDedicatedMachine;
    @Unique
    private PatternProviderTarget molecularmanipulator$queuedProbeTarget;
    @Unique
    private MolecularScaledPattern molecularmanipulator$temporaryScaledPattern;

    @Shadow
    private PatternProviderTarget findAdapter(Direction direction) {
        throw new AssertionError();
    }

    @Shadow
    private boolean adapterAcceptsAll(PatternProviderTarget target, KeyCounter[] inputs) {
        throw new AssertionError();
    }

    @Override
    public void molecularmanipulator$beginBalancedBatch(KeyCounter[] firstInputs) {
        molecularmanipulator$initialBatchAmounts.clear();
        molecularmanipulator$balancingBatch = false;
        if (firstInputs == null || !sendList.isEmpty()) {
            return;
        }
        molecularmanipulator$queuedBatchAmounts.clear();

        try {
            for (var input : firstInputs) {
                if (input == null) {
                    molecularmanipulator$initialBatchAmounts.clear();
                    return;
                }
                for (var entry : input) {
                    if (entry.getKey() == null || entry.getLongValue() <= 0) {
                        molecularmanipulator$initialBatchAmounts.clear();
                        return;
                    }
                    molecularmanipulator$initialBatchAmounts.put(entry.getKey(), Math.addExact(
                            molecularmanipulator$initialBatchAmounts.getLong(entry.getKey()),
                            entry.getLongValue()));
                }
            }
            molecularmanipulator$balancingBatch =
                    !molecularmanipulator$initialBatchAmounts.isEmpty();
        } catch (ArithmeticException exception) {
            molecularmanipulator$initialBatchAmounts.clear();
        }
    }

    @Override
    @Nullable
    public Target molecularmanipulator$prepareProcessingBatch(IPatternDetails pattern, KeyCounter[] oneCraft) {
        var provider = (PatternProviderLogic) (Object) this;
        if (molecularmanipulator$queuedProbeInputs != null || molecularmanipulator$committingProcessingBatch
                || molecularmanipulator$balancingBatch || provider.isBlocking() || provider.isBusy()
                || !mainNode.isActive() || !patterns.contains(pattern)
                || provider.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE) != LockCraftingMode.NONE
                || provider.getCraftingLockedReason() != LockCraftingMode.NONE) {
            return null;
        }
        var slots = pattern.getInputs();
        if (oneCraft.length != slots.length) return null;
        for (int slot = 0; slot < slots.length; slot++) {
            var input = oneCraft[slot];
            var options = slots[slot].getPossibleInputs();
            if (input == null || input.size() != 1 || options.length != 1 || options[0].amount() != 1
                    || slots[slot].getMultiplier() <= 0 || input.get(options[0].what()) != slots[slot].getMultiplier()) {
                return null;
            }
        }

        // AE2 chooses the face, checks connectivity and simulates a complete recipe.
        // Restore its selection state until a material has actually reached that face.
        var oldDirection = sendDirection;
        int oldIndex = roundRobinIndex;
        molecularmanipulator$queuedProbeInputs = oneCraft;
        try {
            sendDirection = null;
            if (!provider.pushPattern(pattern, oneCraft) || sendDirection == null
                    || molecularmanipulator$queuedProbeTarget == null) return null;
            var direction = sendDirection;
            int nextIndex = roundRobinIndex;
            var target = molecularmanipulator$queuedProbeTarget;
            var ordered = new ArrayList<GenericStack>();
            for (var stack : ((AEProcessingPattern) pattern).getSparseInputs()) {
                if (stack != null) ordered.add(stack);
            }
            return count -> molecularmanipulator$deliverProcessing(provider, pattern, oneCraft,
                    target, direction, nextIndex, ordered, count);
        } finally {
            sendDirection = oldDirection;
            roundRobinIndex = oldIndex;
            molecularmanipulator$queuedProbeInputs = null;
            molecularmanipulator$queuedProbeDedicatedMachine = false;
            molecularmanipulator$queuedProbeTarget = null;
        }
    }

    @Unique
    private long molecularmanipulator$deliverProcessing(PatternProviderLogic provider, IPatternDetails pattern,
            KeyCounter[] oneCraft, PatternProviderTarget target, Direction direction, int nextIndex,
            List<GenericStack> recipe, long count) {
        if (provider.isBusy() || !mainNode.isActive() || !patterns.contains(pattern)) return 0;
        molecularmanipulator$committingProcessingBatch = true;
        try {
            long started = recipe.size() == 1
                    ? molecularmanipulator$deliverSingleIngredient(target, recipe.get(0), count)
                    : molecularmanipulator$deliverOrderedRecipes(target, oneCraft, recipe, count);
            if (started == 0) return 0;
            roundRobinIndex = nextIndex;
            if (!sendList.isEmpty()) {
                sendDirection = direction;
                molecularmanipulator$smartQueueVersion = MOLECULARMANIPULATOR_QUEUED_BATCH_VERSION;
            }
            try {
                onPushPatternSuccess(pattern);
                if (!sendList.isEmpty()) {
                    saveChanges();
                    mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
                }
            } catch (RuntimeException | LinkageError exception) {
                RateLimitedLog.warn("Could not finish a pattern provider update after delivering processing inputs",
                        exception);
            }
            return started;
        } finally {
            molecularmanipulator$committingProcessingBatch = false;
        }
    }

    @Unique
    private long molecularmanipulator$deliverSingleIngredient(PatternProviderTarget target, GenericStack input,
            long count) {
        long perCraft = input.amount();
        long planned = Math.multiplyExact(perCraft, count);
        long fits = target.insert(input.what(), planned, Actionable.SIMULATE) / perCraft;
        if (fits == 0) return 0;
        long sent = target.insert(input.what(), perCraft * fits, Actionable.MODULATE);
        long complete = sent / perCraft;
        long partial = sent % perCraft;
        if (partial == 0) return complete;
        sendList.add(new GenericStack(input.what(), perCraft - partial));
        return complete + 1;
    }

    @Unique
    private long molecularmanipulator$deliverOrderedRecipes(PatternProviderTarget target, KeyCounter[] oneCraft,
            List<GenericStack> recipe, long count) {
        // Simulating different keys cannot reserve a shared inventory slot. Confirm each
        // recipe before starting the next and keep only the last started recipe's remainder.
        for (long complete = 0; complete < count; complete++) {
            if (!adapterAcceptsAll(target, oneCraft)) return complete;
            for (int slot = 0; slot < recipe.size(); slot++) {
                var input = recipe.get(slot);
                long sent = target.insert(input.what(), input.amount(), Actionable.MODULATE);
                if (sent < input.amount()) {
                    if (slot == 0 && sent == 0) return complete;
                    sendList.add(new GenericStack(input.what(), input.amount() - sent));
                    sendList.addAll(recipe.subList(slot + 1, recipe.size()));
                    return complete + 1;
                }
            }
        }
        return count;
    }

    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$preventReentrantPushDuringProbe(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$committingProcessingBatch
                || (molecularmanipulator$queuedProbeInputs != null && molecularmanipulator$queuedProbeInputs != inputs)) {
            callback.setReturnValue(false);
        }
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))
    private boolean molecularmanipulator$skipDedicatedMachineDuringProbe(ICraftingMachine machine,
            IPatternDetails pattern, KeyCounter[] inputs, Direction side, Operation<Boolean> original) {
        if (molecularmanipulator$queuedProbeInputs != null) {
            molecularmanipulator$queuedProbeDedicatedMachine = true;
            return false;
        }
        return original.call(machine, pattern, inputs, side);
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderLogic;onPushPatternSuccess(Lappeng/api/crafting/IPatternDetails;)V"))
    private void molecularmanipulator$deferPushSuccessDuringProbe(PatternProviderLogic provider,
            IPatternDetails pattern, Operation<Void> original) {
        if (molecularmanipulator$queuedProbeInputs == null) {
            original.call(provider, pattern);
        }
    }


    @Override
    public void molecularmanipulator$endBalancedBatch() {
        boolean wasBalancingBatch = molecularmanipulator$balancingBatch;
        boolean saveBatchState = wasBalancingBatch && !sendList.isEmpty();
        if (wasBalancingBatch) {
            if (sendList.isEmpty()) {
                molecularmanipulator$queuedBatchAmounts.clear();
            } else {
                molecularmanipulator$queuedBatchAmounts.clear();
                for (var entry : molecularmanipulator$initialBatchAmounts.object2LongEntrySet()) {
                    molecularmanipulator$queuedBatchAmounts.put(
                            entry.getKey(), entry.getLongValue());
                }
            }
        }
        molecularmanipulator$balancingBatch = false;
        molecularmanipulator$initialBatchAmounts.clear();
        if (saveBatchState) {
            saveChanges();
        }
    }

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void molecularmanipulator$recognizeScaledPattern(
            IPatternDetails patternDetails, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        // Recover defensively from a previous exceptional push before arming a new one.
        molecularmanipulator$forgetTemporaryScaledPattern();
        if (!(patternDetails instanceof MolecularScaledPattern scaledPattern)
                || patterns.contains(scaledPattern)) {
            return;
        }

        boolean ownsBasePattern = false;
        var basePattern = scaledPattern.base();
        for (var availablePattern : patterns) {
            if (availablePattern.equals(basePattern)
                    || availablePattern.getDefinition().equals(basePattern.getDefinition())) {
                ownsBasePattern = true;
                break;
            }
        }
        if (ownsBasePattern) {
            patterns.add(scaledPattern);
            molecularmanipulator$temporaryScaledPattern = scaledPattern;
        }
    }

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void molecularmanipulator$forgetScaledPattern(
            IPatternDetails patternDetails, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        molecularmanipulator$forgetTemporaryScaledPattern();
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderLogic;adapterAcceptsAll(Lappeng/helpers/patternprovider/PatternProviderTarget;[Lappeng/api/stacks/KeyCounter;)Z"))
    private boolean molecularmanipulator$prepareScaledExternalPush(PatternProviderLogic provider,
            PatternProviderTarget target, KeyCounter[] inputs, Operation<Boolean> original,
            @Local(argsOnly = true) IPatternDetails patternDetails,
            @Share("scaledExternalInputs") LocalRef<List<GenericStack>> prepared) {
        if (molecularmanipulator$queuedProbeDedicatedMachine) {
            return false;
        }
        if (!original.call(provider, target, inputs)) {
            return false;
        }
        if (molecularmanipulator$queuedProbeInputs != null) {
            molecularmanipulator$queuedProbeTarget = target;
        }
        if (patternDetails instanceof MolecularScaledPattern scaled) {
            try {
                prepared.set(scaled.prepareExternalInputs(inputs));
            } catch (RuntimeException invalidInputs) {
                RateLimitedLog.warn("Rejected scaled processing pattern for {}: {}",
                        scaled.getPrimaryOutput(), invalidInputs.getMessage());
                return false;
            }
        }
        return true;
    }

    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/IPatternDetails;pushInputsToExternalInventory([Lappeng/api/stacks/KeyCounter;Lappeng/api/crafting/IPatternDetails$PatternInputSink;)V"))
    private void molecularmanipulator$pushPreparedExternalInputs(IPatternDetails patternDetails,
            KeyCounter[] inputs, IPatternDetails.PatternInputSink inputSink, Operation<Void> original,
            @Share("scaledExternalInputs") LocalRef<List<GenericStack>> prepared) {
        if (molecularmanipulator$queuedProbeInputs != null) {
            original.call(patternDetails, inputs, (IPatternDetails.PatternInputSink) (key, amount) -> {});
            return;
        }
        var ordered = prepared.get();
        if (ordered == null) {
            original.call(patternDetails, inputs, inputSink);
            return;
        }
        for (var entry : ordered) {
            inputSink.pushInput(entry.what(), entry.amount());
        }
    }

    @WrapOperation(method = "lambda$pushPattern$2", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderTarget;insert(Lappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long molecularmanipulator$seedEveryBatchIngredient(PatternProviderTarget target,
            AEKey key, long amount, Actionable mode, Operation<Long> original) {
        long requested = amount;
        if (molecularmanipulator$balancingBatch) {
            long initialAmount = molecularmanipulator$initialBatchAmounts.getLong(key);
            if (initialAmount > 0) {
                requested = Math.min(requested, initialAmount);
            }
        }
        return original.call(target, key, requested, mode);
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void molecularmanipulator$loadAndMigrateLegacyBatch(CompoundTag tag,
            CallbackInfo callback) {
        molecularmanipulator$queuedBatchAmounts.clear();
        molecularmanipulator$legacyBatchRefund.clear();
        int smartQueueVersion = tag.getInt(MOLECULARMANIPULATOR_SMART_QUEUE_VERSION_TAG);
        molecularmanipulator$smartQueueVersion = !sendList.isEmpty()
                && smartQueueVersion >= MOLECULARMANIPULATOR_SMART_QUEUE_VERSION ? smartQueueVersion : 0;
        var savedBatchRecipe = tag.getList(
                MOLECULARMANIPULATOR_BATCH_RECIPE_TAG, Tag.TAG_COMPOUND);
        try {
            for (int index = 0; index < savedBatchRecipe.size(); index++) {
                var stack = GenericStack.readTag(savedBatchRecipe.getCompound(index));
                if (stack == null || stack.amount() <= 0) {
                    molecularmanipulator$queuedBatchAmounts.clear();
                    break;
                }
                molecularmanipulator$queuedBatchAmounts.put(stack.what(), Math.addExact(
                        molecularmanipulator$queuedBatchAmounts.getLong(stack.what()),
                        stack.amount()));
            }
        } catch (ArithmeticException exception) {
            molecularmanipulator$queuedBatchAmounts.clear();
        }

        var savedRefund = tag.getList(MOLECULARMANIPULATOR_LEGACY_REFUND_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < savedRefund.size(); index++) {
            var stack = GenericStack.readTag(savedRefund.getCompound(index));
            if (stack != null && stack.amount() > 0) {
                molecularmanipulator$legacyBatchRefund.add(stack);
            }
        }

        boolean recoverUnsafeAdaptiveBatch = !savedBatchRecipe.isEmpty()
                && !sendList.isEmpty()
                && molecularmanipulator$smartQueueVersion == 0
                && ((Object) this).getClass() == PatternProviderLogic.class;
        if (!recoverUnsafeAdaptiveBatch
                && (molecularmanipulator$smartQueueVersion != 0
                        || !molecularmanipulator$isLegacyBatchQueue())) {
            if (sendList.isEmpty()) {
                molecularmanipulator$queuedBatchAmounts.clear();
            } else {
                molecularmanipulator$ensureQueuedBatchAmounts();
            }
            return;
        }

        molecularmanipulator$legacyBatchRefund.addAll(sendList);
        sendList.clear();
        sendDirection = null;
        molecularmanipulator$smartQueueVersion = 0;
        molecularmanipulator$queuedBatchAmounts.clear();
        if (recoverUnsafeAdaptiveBatch) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                    "Recovered an unsafe adaptive pattern-provider batch queue with {} material types; "
                            + "its contents will be returned to ME storage",
                    molecularmanipulator$legacyBatchRefund.size());
        } else {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                    "Recovered a legacy oversized pattern-provider batch queue with {} material types; "
                            + "its contents will be returned to ME storage instead of being sent to a machine",
                    molecularmanipulator$legacyBatchRefund.size());
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void molecularmanipulator$saveLegacyBatchRefund(CompoundTag tag,
            CallbackInfo callback) {
        var savedBatchRecipe = new ListTag();
        if (!sendList.isEmpty() && molecularmanipulator$smartQueueVersion != 0) {
            molecularmanipulator$ensureQueuedBatchAmounts();
            for (var entry : molecularmanipulator$queuedBatchAmounts.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    savedBatchRecipe.add(GenericStack.writeTag(new GenericStack(entry.getKey(), entry.getLongValue())));
                }
            }
        }
        tag.put(MOLECULARMANIPULATOR_BATCH_RECIPE_TAG, savedBatchRecipe);
        if (!sendList.isEmpty() && molecularmanipulator$smartQueueVersion != 0) {
            tag.putInt(MOLECULARMANIPULATOR_SMART_QUEUE_VERSION_TAG,
                    molecularmanipulator$smartQueueVersion);
        } else {
            tag.remove(MOLECULARMANIPULATOR_SMART_QUEUE_VERSION_TAG);
        }

        var savedRefund = new ListTag();
        for (var stack : molecularmanipulator$legacyBatchRefund) {
            savedRefund.add(GenericStack.writeTag(stack));
        }
        tag.put(MOLECULARMANIPULATOR_LEGACY_REFUND_TAG, savedRefund);
    }

    @Inject(method = "hasWorkToDo", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$keepTickingForLegacyRefund(
            CallbackInfoReturnable<Boolean> callback) {
        if (!molecularmanipulator$legacyBatchRefund.isEmpty()) {
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "sendStacksOut", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$sendEveryIngredientFairly(CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$queuedProbeInputs != null || molecularmanipulator$committingProcessingBatch
                || molecularmanipulator$sendingSmartQueue) {
            callback.setReturnValue(false);
            return;
        }
        boolean refundedLegacyBatch = molecularmanipulator$refundLegacyBatch();
        boolean ownQueue = molecularmanipulator$smartQueueVersion != 0;
        if (!ownQueue && !molecularmanipulator$balancingBatch) {
            return;
        }
        molecularmanipulator$sendingSmartQueue = true;
        molecularmanipulator$smartQueueProgress = false;
        try {
            // Avoid short-circuiting transport when legacy refunds also made progress.
            callback.setReturnValue(molecularmanipulator$drainFairQueue() | refundedLegacyBatch);
        } finally {
            molecularmanipulator$sendingSmartQueue = false;
            if (sendList.isEmpty()) {
                sendDirection = null;
                molecularmanipulator$queuedBatchAmounts.clear();
                molecularmanipulator$smartQueueVersion = 0;
            }
            if (ownQueue && (molecularmanipulator$smartQueueProgress || sendList.isEmpty())) {
                // A later insertion may throw after earlier entries were delivered.
                saveChanges();
            }
        }
    }

    @Unique
    private boolean molecularmanipulator$drainFairQueue() {
        if (sendList.isEmpty()) {
            return false;
        }
        if (sendDirection == null) {
            throw new IllegalStateException("Invalid pattern provider state: queued inputs have no direction");
        }
        var target = findAdapter(sendDirection);
        if (target == null) {
            return false;
        }

        int seedResult = molecularmanipulator$seedEveryQueuedIngredient(target);
        boolean moved = (seedResult & MOLECULARMANIPULATOR_SEED_MOVED) != 0;
        boolean seedComplete = (seedResult & MOLECULARMANIPULATOR_SEED_COMPLETE) != 0;
        if (!seedComplete && molecularmanipulator$smartQueueVersion < MOLECULARMANIPULATOR_QUEUED_BATCH_VERSION) {
            return moved;
        }

        long deadline = System.nanoTime() + MOLECULARMANIPULATOR_FAIR_SEND_BUDGET_NANOS;
        while (!sendList.isEmpty() && molecularmanipulator$sendOneFairRound(target, seedComplete)) {
            moved = true;
            if (System.nanoTime() - deadline >= 0) {
                break;
            }
        }
        return moved;
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void molecularmanipulator$clearBalancedBatchState(CallbackInfo callback) {
        molecularmanipulator$legacyBatchRefund.clear();
        molecularmanipulator$initialBatchAmounts.clear();
        molecularmanipulator$queuedBatchAmounts.clear();
        molecularmanipulator$balancingBatch = false;
        molecularmanipulator$smartQueueVersion = 0;
        molecularmanipulator$forgetTemporaryScaledPattern();
    }

    @Unique
    private void molecularmanipulator$forgetTemporaryScaledPattern() {
        var temporary = molecularmanipulator$temporaryScaledPattern;
        if (temporary == null) {
            return;
        }
        patterns.remove(temporary);
        molecularmanipulator$temporaryScaledPattern = null;
    }

    @Unique
    private boolean molecularmanipulator$isLegacyBatchQueue() {
        long total = 0;
        for (var stack : sendList) {
            if (stack.amount() >= MOLECULARMANIPULATOR_LEGACY_BATCH_THRESHOLD) {
                return true;
            }
            if (stack.amount() > Long.MAX_VALUE - total) {
                return true;
            }
            total += stack.amount();
            if (total >= MOLECULARMANIPULATOR_LEGACY_BATCH_THRESHOLD) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean molecularmanipulator$refundLegacyBatch() {
        if (molecularmanipulator$legacyBatchRefund.isEmpty()) {
            return false;
        }

        var grid = mainNode.getGrid();
        if (grid == null) {
            return false;
        }

        boolean movedAny = false;
        var storage = grid.getStorageService().getInventory();
        var iterator = molecularmanipulator$legacyBatchRefund.listIterator();
        while (iterator.hasNext()) {
            var stack = iterator.next();
            long inserted = storage.insert(
                    stack.what(), stack.amount(), Actionable.MODULATE, actionSource);
            inserted = Math.max(0, Math.min(inserted, stack.amount()));
            if (inserted >= stack.amount()) {
                iterator.remove();
                movedAny = true;
            } else if (inserted > 0) {
                iterator.set(new GenericStack(stack.what(), stack.amount() - inserted));
                movedAny = true;
            }
        }
        if (movedAny) {
            // Persist partial refunds as well as completion. Reloading an older
            // refund list would otherwise duplicate material already returned.
            saveChanges();
        }
        return movedAny;
    }

    @Unique
    private void molecularmanipulator$ensureQueuedBatchAmounts() {
        for (var stack : sendList) {
            var key = stack.what();
            if (key != null && stack.amount() > 0
                    && molecularmanipulator$queuedBatchAmounts.getLong(key) <= 0) {
                molecularmanipulator$queuedBatchAmounts.put(
                        key, Math.max(1L, key.getAmountPerOperation()));
            }
        }
    }

    @Unique
    private int molecularmanipulator$seedEveryQueuedIngredient(PatternProviderTarget target) {
        if (sendList.isEmpty()) {
            return MOLECULARMANIPULATOR_SEED_COMPLETE;
        }

        molecularmanipulator$ensureQueuedBatchAmounts();
        var queuedAmounts = new Object2LongLinkedOpenHashMap<AEKey>();
        try {
            for (var stack : sendList) {
                if (stack.what() == null || stack.amount() <= 0) {
                    continue;
                }
                queuedAmounts.put(stack.what(), Math.addExact(
                        queuedAmounts.getLong(stack.what()), stack.amount()));
            }
        } catch (ArithmeticException exception) {
            return 0;
        }

        boolean moved = false;
        boolean complete = !queuedAmounts.isEmpty();
        for (var entry : queuedAmounts.object2LongEntrySet()) {
            var key = entry.getKey();
            long queuedAmount = entry.getLongValue();
            long recipeAmount = molecularmanipulator$balancingBatch
                    ? molecularmanipulator$initialBatchAmounts.getLong(key)
                    : molecularmanipulator$queuedBatchAmounts.getLong(key);
            if (recipeAmount <= 0) {
                recipeAmount = Math.max(1L, key.getAmountPerOperation());
            }

            long requested = Math.min(queuedAmount, recipeAmount);
            long inserted = molecularmanipulator$insertWithFallback(
                    target, key, requested, Actionable.MODULATE);
            inserted = Math.max(0, Math.min(inserted, requested));
            if (inserted > 0) {
                molecularmanipulator$consumeQueuedAmount(key, inserted);
                moved = true;
            }
            if (inserted < requested) {
                complete = false;
            }
        }

        int result = moved ? MOLECULARMANIPULATOR_SEED_MOVED : 0;
        if (complete) {
            result |= MOLECULARMANIPULATOR_SEED_COMPLETE;
        }
        return result;
    }

    @Unique
    private void molecularmanipulator$consumeQueuedAmount(AEKey key, long amount) {
        var iterator = sendList.listIterator();
        long remaining = amount;
        while (iterator.hasNext() && remaining > 0) {
            var stack = iterator.next();
            if (!key.equals(stack.what()) || stack.amount() <= 0) {
                continue;
            }
            long consumed = Math.min(remaining, stack.amount());
            remaining -= consumed;
            molecularmanipulator$smartQueueProgress = true;
            if (consumed >= stack.amount()) {
                iterator.remove();
            } else {
                iterator.set(new GenericStack(key, stack.amount() - consumed));
            }
        }
    }

    @Unique
    private boolean molecularmanipulator$sendOneFairRound(PatternProviderTarget target, boolean balanceAmounts) {
        if (sendList.isEmpty()) {
            return false;
        }

        double roundScale = 1.0;
        if (balanceAmounts) {
            for (var stack : sendList) {
                if (stack.amount() > 0) {
                    long limit = molecularmanipulator$fairTransportLimit(stack.what());
                    roundScale = Math.min(roundScale, (double) limit / (double) stack.amount());
                }
            }
        }

        boolean moved = false;
        var iterator = sendList.listIterator();
        while (iterator.hasNext()) {
            var stack = iterator.next();
            var key = stack.what();
            long remaining = stack.amount();
            if (remaining <= 0) {
                iterator.remove();
                continue;
            }

            long operation = Math.max(1L, key.getAmountPerOperation());
            long requested = !balanceAmounts ? Math.min(remaining, molecularmanipulator$fairTransportLimit(key))
                    : roundScale >= 1.0 ? remaining : Math.max(operation, (long) Math.floor(remaining * roundScale));
            requested = Math.min(remaining, Math.max(operation, requested - requested % operation));

            long inserted = molecularmanipulator$insertWithFallback(
                    target, key, requested, Actionable.MODULATE);
            inserted = Math.max(0, Math.min(inserted, remaining));
            if (inserted >= remaining) {
                iterator.remove();
                molecularmanipulator$smartQueueProgress = true;
                moved = true;
            } else if (inserted > 0) {
                iterator.set(new GenericStack(key, remaining - inserted));
                molecularmanipulator$smartQueueProgress = true;
                moved = true;
            }
        }
        return moved;
    }

    @Unique
    private static long molecularmanipulator$fairTransportLimit(AEKey key) {
        long operation = Math.max(1L, key.getAmountPerOperation());
        long transportOperations = MOLECULARMANIPULATOR_FAIR_SEND_OPERATIONS;
        if (operation > Long.MAX_VALUE / transportOperations) {
            return Long.MAX_VALUE;
        }
        return operation * transportOperations;
    }

    @Unique
    private static long molecularmanipulator$insertWithFallback(PatternProviderTarget target,
            AEKey key, long amount, Actionable mode) {
        long inserted = target.insert(key, amount, mode);
        if (inserted > 0 || amount <= 0) {
            return inserted;
        }

        long operation = Math.max(1L, key.getAmountPerOperation());
        long attempt = amount;
        while (attempt > operation) {
            attempt = Math.max(operation, attempt / 2L);
            inserted = target.insert(key, attempt, mode);
            if (inserted > 0) {
                return inserted;
            }
        }
        return 0;
    }
}
