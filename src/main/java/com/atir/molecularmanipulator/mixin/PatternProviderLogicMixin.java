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
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityAdmission;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityRequest;
import com.atir.molecularmanipulator.crafting.dispatch.OmniCapacityTarget;
import com.atir.molecularmanipulator.integration.ae2.PatternInputCapacity;
import com.atir.molecularmanipulator.integration.ae2.PatternProviderSendQueue;
import com.atir.molecularmanipulator.integration.mekanism.MekanismBatchSupport;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
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
public abstract class PatternProviderLogicMixin implements OmniCapacityTarget, PatternProviderSendQueue {
    // Cap the generic adapter's work independently of the CPU's exact-job size.
    @Unique private static final long MAX_ADMISSION_ITEMS = 4096;
    @Unique private static final long MAX_ADMISSION_FLUID = 1024L * AEFluidKey.AMOUNT_BUCKET;
    @Unique private static final long MAX_ADMISSION_CHEMICAL = 1_024_000;
    @Unique private static final Direction[] NEIGHBOR_SIDES = Direction.values();
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
    public boolean omniDispatch$hasPendingSend(Direction direction) {
        return sendDirection == direction && !sendList.isEmpty();
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
        if (!molecularmanipulator$canBatch(provider) || request.unitRemainders() == null
                || !request.unitRemainders().isEmpty()
                || !(request.pattern() instanceof AEProcessingPattern processing)
                || processing.getClass() != AEProcessingPattern.class
                || !processing.supportsPushInputsToExternalInventory()) return null;

        var unitInputs = molecularmanipulator$unitInputs(processing, request);
        if (unitInputs == null) return null;
        if (!molecularmanipulator$allowedPattern(machine, processing, level)) return OmniCapacityAdmission.UNAVAILABLE;
        var requestedCopies = request.maximumCrafts();
        if (requestedCopies.signum() <= 0) return OmniCapacityAdmission.UNAVAILABLE;
        long boundedCopies = molecularmanipulator$boundCopies(unitInputs, requestedCopies);
        if (boundedCopies == 0) return null;

        long bestCopies = 0;
        Direction bestSide = null;
        BlockPos bestPosition = null;
        BlockState bestState = null;
        BlockEntity bestEntity = null;
        boolean unproven = false;
        var origin = machine.getBlockPos();
        var activeSides = getActiveSides();
        var cursor = new BlockPos.MutableBlockPos();
        try (var scope = RecipeExecutionContext.open(machine)) {
            // Dedicated machines keep AE2's native priority across all active faces.
            if (molecularmanipulator$hasDedicatedTarget(level, origin, activeSides)) return null;
            for (var side : activeSides) {
                cursor.setWithOffset(origin, side);
                if (!level.hasChunkAt(cursor)) continue;
                var state = level.getBlockState(cursor);
                var partner = molecularmanipulator$chestPartner(cursor, state);
                if (partner != null && !level.hasChunkAt(partner)) {
                    unproven = true;
                    continue;
                }
                var target = findAdapter(side);
                if (target == null) continue;
                var position = cursor.immutable();
                if (molecularmanipulator$destinationBusy(level, position, state, partner, cursor)) continue;

                var entity = level.getBlockEntity(position);
                long copies = Math.min(boundedCopies, PatternInputCapacity.limit(level, position, side.getOpposite(),
                        entity, target, unitInputs, boundedCopies));
                if (copies < 0) {
                    unproven = true;
                } else if (copies > bestCopies) {
                    bestCopies = copies;
                    bestSide = side;
                    bestPosition = position;
                    bestState = state;
                    bestEntity = entity;
                    if (bestCopies == boundedCopies) break;
                }
            }
        }
        if (bestSide == null) return unproven ? null : OmniCapacityAdmission.UNAVAILABLE;

        var side = bestSide;
        var position = bestPosition;
        var targetState = bestState;
        var targetEntity = bestEntity;
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
                        targetState, targetEntity, unitInputs, crafts.longValue()) ? crafts : BigInteger.ZERO;
            }
        };
    }

    @Unique
    private boolean molecularmanipulator$enqueue(PatternProviderLogic provider, IPatternDetails pattern,
            BlockEntity machine, ServerLevel level, Direction side, BlockPos position, BlockState targetState,
            @Nullable BlockEntity targetEntity, List<GenericStack> unitInputs, long crafts) {
        if (machine.isRemoved() || machine.getLevel() != level || !molecularmanipulator$ready(provider, pattern)
                || !molecularmanipulator$canBatch(provider) || !molecularmanipulator$allowedPattern(machine, pattern, level)
                || !molecularmanipulator$sameTarget(level, position, targetState, targetEntity)) return false;

        var activeSides = getActiveSides();
        if (!activeSides.contains(side)) return false;
        var partner = molecularmanipulator$chestPartner(position, targetState);
        var cursor = new BlockPos.MutableBlockPos();
        if (molecularmanipulator$destinationBusy(level, position, targetState, partner, cursor)) return false;
        try (var scope = RecipeExecutionContext.open(machine)) {
            if (molecularmanipulator$hasDedicatedTarget(level, machine.getBlockPos(), activeSides)) return false;
            var target = findAdapter(side);
            if (target == null || PatternInputCapacity.limit(level, position, side.getOpposite(),
                    targetEntity, target, unitInputs, crafts) != crafts) return false;
        }
        // Capability callbacks can run code from other mods before the queue transfer.
        if (!molecularmanipulator$ready(provider, pattern) || !molecularmanipulator$canBatch(provider)
                || !molecularmanipulator$sameTarget(level, position, targetState, targetEntity)
                || molecularmanipulator$destinationBusy(level, position, targetState, partner, cursor)) return false;

        // Publish the whole reservation to AE2's durable queue before invoking save or tick callbacks.
        for (int i = 0; i < unitInputs.size(); i++) {
            var stack = unitInputs.get(i);
            sendList.add(crafts == 1 ? stack : new GenericStack(stack.what(), stack.amount() * crafts));
        }
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
    private static @Nullable List<GenericStack> molecularmanipulator$unitInputs(AEProcessingPattern pattern,
            OmniCapacityRequest request) {
        var holders = request.unitInputs();
        var inputs = pattern.getInputs();
        var outputs = request.unitOutputs();
        if (holders == null || holders.length == 0 || holders.length != inputs.length || outputs == null) return null;
        for (var output : pattern.getOutputs()) {
            if (outputs.get(output.what()) <= 0) return null;
        }

        var sparse = pattern.getSparseInputs();
        if (holders.length == 1) {
            var key = molecularmanipulator$matchHolder(inputs[0], holders[0], outputs);
            if (key == null) return null;
            long amount = inputs[0].getMultiplier();
            long remaining = amount;
            GenericStack first = null;
            for (var stack : sparse) {
                if (stack == null) continue;
                if (!key.equals(stack.what()) || stack.amount() <= 0 || stack.amount() > remaining) return null;
                if (first == null) first = stack;
                remaining -= stack.amount();
            }
            if (first == null || remaining != 0) return null;
            return List.of(first.amount() == amount ? first : new GenericStack(key, amount));
        }

        // AE2 uses the holder order when there are no holes or repeated keys and the sparse slots otherwise.
        var remaining = sparse.size() == holders.length ? null : new Object2LongOpenHashMap<AEKey>(holders.length);
        var ordered = new ArrayList<GenericStack>(sparse.size());
        for (int i = 0; i < holders.length; i++) {
            var key = molecularmanipulator$matchHolder(inputs[i], holders[i], outputs);
            if (key == null) return null;
            long amount = inputs[i].getMultiplier();
            if (remaining == null) {
                var stack = sparse.get(i);
                if (stack == null || !key.equals(stack.what()) || stack.amount() != amount) return null;
                ordered.add(stack);
            } else if (remaining.put(key, amount) != 0) {
                return null;
            }
        }
        if (remaining == null) return ordered;
        for (var stack : sparse) {
            if (stack == null) continue;
            var key = stack.what();
            long available = remaining.getLong(key);
            long amount = stack.amount();
            if (amount <= 0 || amount > available) return null;
            if (amount == available) remaining.removeLong(key);
            else remaining.put(key, available - amount);
            ordered.add(stack);
        }
        return remaining.isEmpty() ? ordered : null;
    }

    @Unique
    private static @Nullable AEKey molecularmanipulator$matchHolder(@Nullable IPatternDetails.IInput input,
            @Nullable KeyCounter holder, KeyCounter outputs) {
        if (input == null || holder == null || holder.size() != 1) return null;
        var possible = input.getPossibleInputs();
        if (possible == null || possible.length != 1 || possible[0] == null || possible[0].amount() != 1) return null;
        var actual = holder.getFirstEntry();
        if (actual == null || actual.getLongValue() <= 0 || actual.getLongValue() != input.getMultiplier()) return null;
        var key = actual.getKey();
        return possible[0].what().equals(key) && outputs.get(key) == 0 && input.getRemainingKey(key) == null
                ? key : null;
    }

    @Unique
    private static long molecularmanipulator$boundCopies(List<GenericStack> inputs, BigInteger requested) {
        long items = 0;
        long fluid = 0;
        long chemical = 0;
        for (int i = 0; i < inputs.size(); i++) {
            var stack = inputs.get(i);
            var key = stack.what();
            long amount = stack.amount();
            if (key instanceof AEItemKey) {
                if (amount > MAX_ADMISSION_ITEMS - items) return 0;
                items += amount;
            } else if (key instanceof AEFluidKey) {
                if (amount > MAX_ADMISSION_FLUID - fluid) return 0;
                fluid += amount;
            } else if (MekanismBatchSupport.isChemicalKey(key)) {
                if (amount > MAX_ADMISSION_CHEMICAL - chemical) return 0;
                chemical += amount;
            } else {
                return 0;
            }
        }
        long limit = requested.bitLength() <= 63 ? requested.longValue() : Long.MAX_VALUE;
        if (items > 0) limit = Math.min(limit, MAX_ADMISSION_ITEMS / items);
        if (fluid > 0) limit = Math.min(limit, MAX_ADMISSION_FLUID / fluid);
        if (chemical > 0) limit = Math.min(limit, MAX_ADMISSION_CHEMICAL / chemical);
        return limit;
    }

    @Unique
    private static boolean molecularmanipulator$hasDedicatedTarget(ServerLevel level, BlockPos origin,
            Set<Direction> sides) {
        for (var side : sides) {
            var position = origin.relative(side);
            if (!level.hasChunkAt(position)) continue;
            var target = ICraftingMachine.of(level, position, side.getOpposite());
            if (target != null && target.acceptsPlans()) return true;
        }
        return false;
    }

    @Unique
    private static boolean molecularmanipulator$sameTarget(ServerLevel level, BlockPos position, BlockState state,
            @Nullable BlockEntity entity) {
        return level.hasChunkAt(position) && level.getBlockState(position) == state
                && level.getBlockEntity(position) == entity;
    }

    @Unique
    private static @Nullable BlockPos molecularmanipulator$chestPartner(BlockPos position, BlockState state) {
        return (state.is(Blocks.CHEST) || state.is(Blocks.TRAPPED_CHEST))
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                        ? position.relative(ChestBlock.getConnectedDirection(state)) : null;
    }

    @Unique
    private boolean molecularmanipulator$destinationBusy(ServerLevel level, BlockPos position, BlockState state,
            @Nullable BlockPos partner, BlockPos.MutableBlockPos cursor) {
        if (molecularmanipulator$neighborQueue(level, position, cursor)) return true;
        if (partner == null) return false;
        if (!level.hasChunkAt(partner)) return true;
        var other = level.getBlockState(partner);
        return other.is(state.getBlock()) && other.getValue(ChestBlock.TYPE) == state.getValue(ChestBlock.TYPE).getOpposite()
                && other.getValue(ChestBlock.FACING) == state.getValue(ChestBlock.FACING)
                && molecularmanipulator$neighborQueue(level, partner, cursor);
    }

    @Unique
    private boolean molecularmanipulator$neighborQueue(ServerLevel level, BlockPos destination,
            BlockPos.MutableBlockPos cursor) {
        for (var outward : NEIGHBOR_SIDES) {
            cursor.setWithOffset(destination, outward);
            if (!level.hasChunkAt(cursor)) continue;
            var neighbor = level.getBlockEntity(cursor);
            if (neighbor == null || neighbor.isRemoved()) continue;
            var toward = outward.getOpposite();
            if (neighbor instanceof PatternProviderLogicHost peer && molecularmanipulator$peerSending(peer, toward)) {
                return true;
            }
            if (neighbor instanceof CableBusBlockEntity cable
                    && cable.getPart(toward) instanceof PatternProviderLogicHost peer
                    && molecularmanipulator$peerSending(peer, toward)) return true;
        }
        return false;
    }

    @Unique
    private boolean molecularmanipulator$peerSending(PatternProviderLogicHost peer, Direction toward) {
        return peer != host && peer.getLogic() instanceof PatternProviderSendQueue queue
                && queue.omniDispatch$hasPendingSend(toward);
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
