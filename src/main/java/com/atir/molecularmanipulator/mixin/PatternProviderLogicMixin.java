package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
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
public abstract class PatternProviderLogicMixin {
    @Unique
    private static final long MOLECULARMANIPULATOR_FAIR_SEND_BUDGET_NANOS = 2_000_000L;
    @Unique
    private static final long MOLECULARMANIPULATOR_LEGACY_BATCH_THRESHOLD = 1_000_000L;
    @Unique
    private static final String MOLECULARMANIPULATOR_LEGACY_REFUND_TAG =
            "molecularmanipulatorLegacyBatchRefund";

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

    @Unique
    private final List<GenericStack> molecularmanipulator$legacyBatchRefund = new ArrayList<>();

    @Shadow
    private PatternProviderTarget findAdapter(Direction direction) {
        throw new AssertionError();
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void molecularmanipulator$loadAndMigrateLegacyBatch(CompoundTag tag,
            HolderLookup.Provider registries, CallbackInfo callback) {
        var savedRefund = tag.getList(MOLECULARMANIPULATOR_LEGACY_REFUND_TAG, Tag.TAG_COMPOUND);
        for (int index = 0; index < savedRefund.size(); index++) {
            var stack = GenericStack.readTag(registries, savedRefund.getCompound(index));
            if (stack != null && stack.amount() > 0) {
                molecularmanipulator$legacyBatchRefund.add(stack);
            }
        }

        if (!molecularmanipulator$isLegacyBatchQueue()) {
            return;
        }

        molecularmanipulator$legacyBatchRefund.addAll(sendList);
        sendList.clear();
        sendDirection = null;
        com.atir.molecularmanipulator.MolecularManipulator.LOGGER.warn(
                "Recovered a legacy oversized pattern-provider batch queue with {} material types; "
                        + "its contents will be returned to ME storage instead of being sent to a machine",
                molecularmanipulator$legacyBatchRefund.size());
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void molecularmanipulator$saveLegacyBatchRefund(CompoundTag tag,
            HolderLookup.Provider registries, CallbackInfo callback) {
        var savedRefund = new ListTag();
        for (var stack : molecularmanipulator$legacyBatchRefund) {
            savedRefund.add(GenericStack.writeTag(registries, stack));
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
            callback.setReturnValue(refundedLegacyBatch);
            return;
        }

        var target = findAdapter(sendDirection);
        if (target == null) {
            callback.setReturnValue(refundedLegacyBatch);
            return;
        }

        boolean movedAny = refundedLegacyBatch;
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
        }
        callback.setReturnValue(movedAny);
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
        if (key instanceof AEItemKey itemKey) {
            return Math.max(operation, itemKey.getMaxStackSize());
        }
        if (operation > Long.MAX_VALUE / 64L) {
            return Long.MAX_VALUE;
        }
        return operation * 64L;
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
