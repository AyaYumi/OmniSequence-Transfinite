package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin implements OmniCapacityTarget {
    // Cap the generic adapter's work independently of the CPU's exact-job size.
    @Unique private static final long MAX_ADMISSION_ITEMS = 4096;
    @Unique private static final long MAX_ADMISSION_FLUID = 1024L * AEFluidKey.AMOUNT_BUCKET;
    @Unique private static final String LEGACY_REFUND_TAG = "molecularmanipulatorLegacyBatchRefund";

    @Shadow @Final private List<GenericStack> sendList;
    @Shadow @Final private List<IPatternDetails> patterns;
    @Shadow @Final private IManagedGridNode mainNode;
    @Shadow @Final private IActionSource actionSource;
    @Shadow @Final private PatternProviderLogicHost host;
    @Shadow private Direction sendDirection;
    @Shadow public abstract void saveChanges();

    @Unique private List<GenericStack> molecularmanipulator$legacyRefund = List.of();
    @Unique private long molecularmanipulator$sendQueueRevision;

    @Shadow
    private PatternProviderTarget findAdapter(Direction direction) {
        throw new AssertionError();
    }

    @Shadow
    private Set<Direction> getActiveSides() {
        throw new AssertionError();
    }

    @Override
    public @Nullable OmniCapacityAdmission omniDispatch$prepareTarget(OmniCapacityRequest request) {
        var provider = (PatternProviderLogic) (Object) this;
        if (provider.getClass() != PatternProviderLogic.class || request.provider() != provider) return null;
        var machine = host.getBlockEntity();
        if (!(machine.getLevel() instanceof ServerLevel level) || request.level() != level || machine.isRemoved()) {
            return OmniCapacityAdmission.UNAVAILABLE;
        }
        if (!molecularmanipulator$ready(provider, request.pattern())) return OmniCapacityAdmission.UNAVAILABLE;
        if (!molecularmanipulator$canBatch(provider) || !request.unitRemainders().isEmpty()
                || !(request.pattern() instanceof AEProcessingPattern processing)
                || processing.getClass() != AEProcessingPattern.class
                || !processing.supportsPushInputsToExternalInventory()) return null;

        // AE2 condenses repeated slots of the same key. Verify the exact reservation's
        // prototype matches that single-key input and cannot feed one copy with another's output.
        var inputs = processing.getInputs();
        if (inputs.length != 1) return null;
        var key = inputs[0].getPossibleInputs()[0].what();
        long unitAmount = inputs[0].getMultiplier();
        long limit = key instanceof AEItemKey ? MAX_ADMISSION_ITEMS
                : key instanceof AEFluidKey ? MAX_ADMISSION_FLUID : 0;
        if (unitAmount <= 0 || unitAmount > limit || request.unitOutputs().get(key) != 0
                || !molecularmanipulator$matchesUnit(request, key, unitAmount)) return null;

        if (!molecularmanipulator$allowedPattern(machine, processing, level)) return OmniCapacityAdmission.UNAVAILABLE;
        var requestedCopies = request.maximumCrafts();
        if (requestedCopies.signum() <= 0) return OmniCapacityAdmission.UNAVAILABLE;
        long boundedCopies = limit / unitAmount;
        if (requestedCopies.bitLength() <= 63) boundedCopies = Math.min(boundedCopies, requestedCopies.longValue());

        long bestCopies = 0;
        Direction bestSide = null;
        BlockPos bestPosition = null;
        boolean hasDedicatedTarget = false;
        try (var scope = RecipeExecutionContext.open(machine)) {
            for (var side : getActiveSides()) {
                var position = machine.getBlockPos().relative(side);
                if (!level.hasChunkAt(position)) continue;
                if (molecularmanipulator$acceptsPlans(level, position, side)) {
                    hasDedicatedTarget = true;
                    continue;
                }
                var target = findAdapter(side);
                if (target == null) continue;

                long copies = molecularmanipulator$simulateCopies(target, key, unitAmount, boundedCopies);
                if (copies > bestCopies) {
                    bestCopies = copies;
                    bestSide = side;
                    bestPosition = position;
                    if (bestCopies == boundedCopies) break;
                }
            }
        }
        if (bestSide == null) return hasDedicatedTarget ? null : OmniCapacityAdmission.UNAVAILABLE;

        var side = bestSide;
        var position = bestPosition;
        var targetState = level.getBlockState(position);
        var targetEntity = level.getBlockEntity(position);
        var maximum = BigInteger.valueOf(bestCopies);
        return new OmniCapacityAdmission() {
            private boolean submitted;

            @Override public BigInteger maximumCrafts() { return maximum; }

            @Override public boolean acceptsPartial() { return false; }

            @Override
            public BigInteger submit(BigInteger crafts) {
                if (submitted) return BigInteger.ZERO;
                submitted = true;
                if (crafts.signum() <= 0 || crafts.compareTo(maximum) > 0) return BigInteger.ZERO;
                return molecularmanipulator$enqueue(provider, processing, machine, level, side, position,
                        targetState, targetEntity, key, unitAmount, crafts.longValue()) ? crafts : BigInteger.ZERO;
            }
        };
    }

    @Unique
    private boolean molecularmanipulator$enqueue(PatternProviderLogic provider, IPatternDetails pattern,
            BlockEntity machine, ServerLevel level, Direction side, BlockPos position, BlockState targetState,
            @Nullable BlockEntity targetEntity, AEKey key, long unitAmount, long crafts) {
        if (machine.isRemoved() || machine.getLevel() != level || !molecularmanipulator$ready(provider, pattern)
                || !molecularmanipulator$canBatch(provider) || !molecularmanipulator$allowedPattern(machine, pattern, level)
                || !getActiveSides().contains(side) || !level.hasChunkAt(position)
                || level.getBlockState(position) != targetState || level.getBlockEntity(position) != targetEntity) return false;

        try (var scope = RecipeExecutionContext.open(machine)) {
            if (molecularmanipulator$acceptsPlans(level, position, side)) return false;
            var target = findAdapter(side);
            if (target == null || molecularmanipulator$simulateCopies(target, key, unitAmount, crafts) != crafts) return false;
        }
        if (!molecularmanipulator$ready(provider, pattern) || !molecularmanipulator$canBatch(provider)) return false;

        // Transfer the entire reservation to AE2's durable queue before waking any grid work.
        // A later partial insertion leaves its remainder in this queue for the same side.
        sendList.add(new GenericStack(key, unitAmount * crafts));
        sendDirection = side;
        saveChanges();
        mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        return true;
    }

    @Unique
    private boolean molecularmanipulator$ready(PatternProviderLogic provider, IPatternDetails pattern) {
        return sendList.isEmpty() && molecularmanipulator$legacyRefund.isEmpty() && mainNode.isActive()
                && patterns.contains(pattern) && provider.getCraftingLockedReason() == LockCraftingMode.NONE;
    }

    @Unique
    private static boolean molecularmanipulator$canBatch(PatternProviderLogic provider) {
        return !provider.isBlocking()
                && provider.getConfigManager().getSetting(Settings.LOCK_CRAFTING_MODE) == LockCraftingMode.NONE;
    }

    @Unique
    private static boolean molecularmanipulator$allowedPattern(BlockEntity machine, IPatternDetails pattern,
            ServerLevel level) {
        return !RecipeLocks.hasRules(level) || RecipeLocks.allowedPattern(machine, pattern);
    }

    @Unique
    private static boolean molecularmanipulator$matchesUnit(OmniCapacityRequest request, AEKey key, long unitAmount) {
        for (var counter : request.unitInputs()) {
            if (counter == null) return false;
            for (var entry : counter) {
                long amount = entry.getLongValue();
                if (!key.equals(entry.getKey()) || amount <= 0 || amount > unitAmount) return false;
                unitAmount -= amount;
            }
        }
        return unitAmount == 0;
    }

    @Unique
    private static boolean molecularmanipulator$acceptsPlans(ServerLevel level, BlockPos position, Direction side) {
        var target = ICraftingMachine.of(level, position, side.getOpposite());
        return target != null && target.acceptsPlans();
    }

    @Unique
    private static long molecularmanipulator$simulateCopies(PatternProviderTarget target, AEKey key,
            long unitAmount, long copies) {
        long requested = unitAmount * copies;
        long inserted = target.insert(key, requested, Actionable.SIMULATE);
        return Math.max(0, Math.min(requested, inserted)) / unitAmount;
    }

    @Inject(method = "sendStacksOut", at = {
            @At(value = "INVOKE", target = "Ljava/util/ListIterator;remove()V", shift = At.Shift.AFTER),
            @At(value = "INVOKE", target = "Ljava/util/ListIterator;set(Ljava/lang/Object;)V", shift = At.Shift.AFTER)
    })
    private void molecularmanipulator$observeQueueReduction(CallbackInfoReturnable<Boolean> callback) {
        ++molecularmanipulator$sendQueueRevision;
    }

    @WrapMethod(method = "sendStacksOut")
    private boolean molecularmanipulator$persistQueueReduction(Operation<Boolean> original) {
        if (sendList.isEmpty()) return original.call();
        long revision = molecularmanipulator$sendQueueRevision;
        try (var scope = RecipeExecutionContext.open(host.getBlockEntity())) {
            return original.call();
        } finally {
            // Mark partial progress even if a later entry's target throws.
            if (molecularmanipulator$sendQueueRevision != revision) saveChanges();
        }
    }

    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void molecularmanipulator$loadLegacyQueue(CompoundTag tag, HolderLookup.Provider registries,
            CallbackInfo callback) {
        // The old recipe list stores per-craft metadata. Only AE2's sendList and the
        // separate refund list contain owned materials. Version 2 committed its sendList.
        boolean oldAdaptiveQueue = ((Object) this).getClass() == PatternProviderLogic.class
                && tag.getInt("molecularmanipulatorSmartQueueVersion") < 2
                && !tag.getList("molecularmanipulatorBatchRecipe", Tag.TAG_COMPOUND).isEmpty();
        boolean recoverQueue = !sendList.isEmpty() && (oldAdaptiveQueue || sendDirection == null);
        var savedRefund = tag.getList(LEGACY_REFUND_TAG, Tag.TAG_COMPOUND);
        if (savedRefund.isEmpty() && !recoverQueue) {
            molecularmanipulator$legacyRefund = List.of();
            return;
        }

        var refund = new ArrayList<GenericStack>(savedRefund.size() + (recoverQueue ? sendList.size() : 0));
        for (int index = 0; index < savedRefund.size(); index++) {
            var stack = GenericStack.readTag(registries, savedRefund.getCompound(index));
            if (stack != null && stack.amount() > 0) refund.add(stack);
        }
        if (recoverQueue) {
            refund.addAll(sendList);
            sendList.clear();
            sendDirection = null;
        }
        if (refund.isEmpty()) {
            molecularmanipulator$legacyRefund = List.of();
        } else {
            molecularmanipulator$legacyRefund = refund;
            mainNode.ifPresent((grid, node) -> grid.getTickManager().alertDevice(node));
        }
    }

    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void molecularmanipulator$saveLegacyRefund(CompoundTag tag, HolderLookup.Provider registries,
            CallbackInfo callback) {
        if (molecularmanipulator$legacyRefund.isEmpty()) {
            tag.remove(LEGACY_REFUND_TAG);
            return;
        }
        var refund = new ListTag();
        for (var stack : molecularmanipulator$legacyRefund) refund.add(GenericStack.writeTag(registries, stack));
        tag.put(LEGACY_REFUND_TAG, refund);
    }

    @Inject(method = "hasWorkToDo", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$keepTickingForRefund(CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && !molecularmanipulator$legacyRefund.isEmpty()) callback.setReturnValue(true);
    }

    @Inject(method = "doWork", at = @At("RETURN"), cancellable = true)
    private void molecularmanipulator$refundLegacyStock(CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$legacyRefund.isEmpty()) return;
        var grid = mainNode.getGrid();
        if (grid == null) return;
        var storage = grid.getStorageService().getInventory();
        boolean moved = false;
        try {
            for (var it = molecularmanipulator$legacyRefund.listIterator(); it.hasNext();) {
                var stack = it.next();
                long amount = storage.insert(stack.what(), stack.amount(), Actionable.MODULATE, actionSource);
                if (amount >= stack.amount()) {
                    it.remove();
                    moved = true;
                } else if (amount > 0) {
                    it.set(new GenericStack(stack.what(), stack.amount() - amount));
                    moved = true;
                }
            }
        } finally {
            if (moved) saveChanges();
        }
        if (molecularmanipulator$legacyRefund.isEmpty()) molecularmanipulator$legacyRefund = List.of();
        if (moved && !callback.getReturnValueZ()) callback.setReturnValue(true);
    }

    @Inject(method = "addDrops", at = @At("TAIL"))
    private void molecularmanipulator$dropLegacyStock(List<ItemStack> drops, CallbackInfo callback) {
        if (molecularmanipulator$legacyRefund.isEmpty()) return;
        var machine = host.getBlockEntity();
        for (var stack : molecularmanipulator$legacyRefund) {
            stack.what().addDrops(stack.amount(), drops, machine.getLevel(), machine.getBlockPos());
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void molecularmanipulator$clearLegacyStock(CallbackInfo callback) {
        molecularmanipulator$legacyRefund = List.of();
    }
}
