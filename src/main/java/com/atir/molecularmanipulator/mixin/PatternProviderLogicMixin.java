package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.integration.ae2.MolecularBalancedBatchProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
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
public abstract class PatternProviderLogicMixin implements MolecularBalancedBatchProvider {
    @Unique
    private static final long MOLECULARMANIPULATOR_FAIR_SEND_BUDGET_NANOS = 2_000_000L;
    @Unique
    private static final long MOLECULARMANIPULATOR_LEGACY_BATCH_THRESHOLD = 1_000_000L;
    @Unique
    private static final String MOLECULARMANIPULATOR_LEGACY_REFUND_TAG =
            "molecularmanipulatorLegacyBatchRefund";
    @Unique
    private static final String MOLECULARMANIPULATOR_BATCH_RECIPE_TAG =
            "molecularmanipulatorBatchRecipe";
    @Unique
    private static final int MOLECULARMANIPULATOR_SEED_MOVED = 1;
    @Unique
    private static final int MOLECULARMANIPULATOR_SEED_COMPLETE = 2;

    @Shadow
    @Final
    private List<GenericStack> sendList;

    @Shadow
    @Final
    private IManagedGridNode mainNode;

    @Shadow
    @Final
    private IActionSource actionSource;

    @Shadow
    private Direction sendDirection;

    @Shadow
    public abstract void saveChanges();

    @Unique
    private final List<GenericStack> molecularmanipulator$legacyBatchRefund = new ArrayList<>();
    @Unique
    private final Object2LongOpenHashMap<AEKey> molecularmanipulator$initialBatchAmounts =
            new Object2LongOpenHashMap<>();
    @Unique
    private final Object2LongOpenHashMap<AEKey> molecularmanipulator$queuedBatchAmounts =
            new Object2LongOpenHashMap<>();
    @Unique
    private boolean molecularmanipulator$balancingBatch;

    @Shadow
    private PatternProviderTarget findAdapter(Direction direction) {
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
    public void molecularmanipulator$endBalancedBatch() {
        boolean saveBatchState = molecularmanipulator$balancingBatch;
        if (saveBatchState) {
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
    private void molecularmanipulator$loadAndMigrateLegacyBatch(CompoundTag tag, CallbackInfo callback) {
        molecularmanipulator$queuedBatchAmounts.clear();
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
            var stack = GenericStack.readTag( savedRefund.getCompound(index));
            if (stack != null && stack.amount() > 0) {
                molecularmanipulator$legacyBatchRefund.add(stack);
            }
        }

        if (!molecularmanipulator$isLegacyBatchQueue()) {
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
        molecularmanipulator$queuedBatchAmounts.clear();
        com.atir.molecularmanipulator.MolecularManipulator.LOGGER.warn(
                "Recovered a legacy oversized pattern-provider batch queue with {} material types; "
                        + "its contents will be returned to ME storage instead of being sent to a machine",
                molecularmanipulator$legacyBatchRefund.size());
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void molecularmanipulator$saveLegacyBatchRefund(CompoundTag tag, CallbackInfo callback) {
        var savedBatchRecipe = new ListTag();
        if (!sendList.isEmpty()) {
            molecularmanipulator$ensureQueuedBatchAmounts();
            for (var entry : molecularmanipulator$queuedBatchAmounts.object2LongEntrySet()) {
                if (entry.getLongValue() > 0) {
                    savedBatchRecipe.add(GenericStack.writeTag(
                            new GenericStack(entry.getKey(), entry.getLongValue())));
                }
            }
        }
        tag.put(MOLECULARMANIPULATOR_BATCH_RECIPE_TAG, savedBatchRecipe);

        var savedRefund = new ListTag();
        for (var stack : molecularmanipulator$legacyBatchRefund) {
            savedRefund.add(GenericStack.writeTag( stack));
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
        boolean refundedLegacyBatch = molecularmanipulator$refundLegacyBatch();
        if (sendDirection == null) {
            if (!sendList.isEmpty()) {
                throw new IllegalStateException("Invalid pattern provider state: queued inputs have no direction");
            }
            molecularmanipulator$queuedBatchAmounts.clear();
            callback.setReturnValue(refundedLegacyBatch);
            return;
        }

        var target = findAdapter(sendDirection);
        if (target == null) {
            callback.setReturnValue(refundedLegacyBatch);
            return;
        }

        int seedResult = molecularmanipulator$seedEveryQueuedIngredient(target);
        boolean movedAny = refundedLegacyBatch
                || (seedResult & MOLECULARMANIPULATOR_SEED_MOVED) != 0;
        if (sendList.isEmpty()) {
            sendDirection = null;
            molecularmanipulator$queuedBatchAmounts.clear();
            callback.setReturnValue(movedAny);
            return;
        }
        if ((seedResult & MOLECULARMANIPULATOR_SEED_COMPLETE) == 0) {
            callback.setReturnValue(movedAny);
            return;
        }

        long deadline = System.nanoTime() + MOLECULARMANIPULATOR_FAIR_SEND_BUDGET_NANOS;
        do {
            boolean movedThisRound = molecularmanipulator$sendOneFairRound(target);
            movedAny |= movedThisRound;
            if (!movedThisRound) {
                break;
            }
        } while (!sendList.isEmpty() && System.nanoTime() < deadline);

        if (sendList.isEmpty()) {
            sendDirection = null;
            molecularmanipulator$queuedBatchAmounts.clear();
        }
        callback.setReturnValue(movedAny);
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void molecularmanipulator$clearBalancedBatchState(CallbackInfo callback) {
        molecularmanipulator$initialBatchAmounts.clear();
        molecularmanipulator$queuedBatchAmounts.clear();
        molecularmanipulator$balancingBatch = false;
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
        var queuedAmounts = new Object2LongOpenHashMap<AEKey>();
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
            if (consumed >= stack.amount()) {
                iterator.remove();
            } else {
                iterator.set(new GenericStack(key, stack.amount() - consumed));
            }
        }
    }

    @Unique
    private boolean molecularmanipulator$sendOneFairRound(PatternProviderTarget target) {
        if (sendList.isEmpty()) {
            return false;
        }

        double roundScale = 1.0;
        for (var stack : sendList) {
            if (stack.amount() <= 0) {
                continue;
            }
            long limit = molecularmanipulator$fairTransportLimit(stack.what());
            roundScale = Math.min(roundScale, (double) limit / (double) stack.amount());
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
            long requested = roundScale >= 1.0
                    ? remaining
                    : Math.max(operation, (long) Math.floor(remaining * roundScale));
            requested = Math.min(remaining, Math.max(operation, requested - requested % operation));

            long inserted = molecularmanipulator$insertWithFallback(
                    target, key, requested, Actionable.MODULATE);
            inserted = Math.max(0, Math.min(inserted, remaining));
            if (inserted >= remaining) {
                iterator.remove();
                moved = true;
            } else if (inserted > 0) {
                iterator.set(new GenericStack(key, remaining - inserted));
                moved = true;
            }
        }
        return moved;
    }

    @Unique
    private static long molecularmanipulator$fairTransportLimit(AEKey key) {
        long operation = Math.max(1L, key.getAmountPerOperation());
        long transportOperations = ModConfig.OMNI_PROVIDER_SEND_OPERATIONS.get();
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
