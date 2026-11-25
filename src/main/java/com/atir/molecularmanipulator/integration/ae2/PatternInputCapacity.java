package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.atir.molecularmanipulator.integration.mekanism.MekanismBatchSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.extensions.IItemExtension;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.VanillaHopperItemHandler;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/** Proves whole processing-pattern batches against the capacity on one provider face. */
public final class PatternInputCapacity {
    private static final int MAX_ITEM_SLOTS = 1024;
    private static final int LINEAR_SEARCH_SIZE = 8;

    // NeoForge's default limit depends only on the key's components. A custom
    // limit can depend on the offered stack count as AE2 walks the slots.
    private static final ClassValue<Boolean> COMPONENT_STACK_LIMIT = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getMaxStackSize", ItemStack.class).getDeclaringClass() == IItemExtension.class;
            } catch (NoSuchMethodException ignored) {
                return false;
            }
        }
    };

    private PatternInputCapacity() {}

    /** Returns a proved number of crafts, or {@code -1} when capacity is unknown. */
    public static long limit(ServerLevel level, BlockPos pos, Direction inputSide, @Nullable BlockEntity target,
            PatternProviderTarget adapter, List<GenericStack> unitInputs, long requestedCrafts) {
        if (requestedCrafts <= 0) return 0;
        if (unitInputs.size() == 1) {
            var unit = unitInputs.getFirst();
            if (unit.what() instanceof AEItemKey || unit.what() instanceof AEFluidKey) {
                long requested = unit.amount() * requestedCrafts;
                long inserted = adapter.insert(unit.what(), requested, Actionable.SIMULATE);
                return Math.max(0, Math.min(requested, inserted)) / unit.amount();
            }
        }

        // AE2 prefers the ME capability over the platform wrappers on this face.
        var state = target == null ? level.getBlockState(pos) : target.getBlockState();
        if (level.getCapability(AECapabilities.ME_STORAGE, pos, state, target, inputSide) != null) return -1;

        boolean hasItems = false;
        boolean hasFluids = false;
        boolean hasOther = false;
        for (var unit : unitInputs) {
            switch (unit.what()) {
                case AEItemKey ignored -> hasItems = true;
                case AEFluidKey ignored -> hasFluids = true;
                default -> hasOther = true;
            }
        }
        if (hasOther && hasFluids) return -1;
        var itemHandler = hasItems ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, target, inputSide)
                : null;
        if (hasOther) return MekanismBatchSupport.limit(target, itemHandler, adapter, unitInputs, requestedCrafts);
        if (hasItems && !knownItems(level, pos, target, itemHandler)) return -1;

        long upper = requestedCrafts;
        FluidFill fluidFill = null;
        if (hasFluids) {
            var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, target, inputSide);
            if (handler == null || handler.getClass() != FluidTank.class) return -1;
            // These exact implementations own separate backing and have no change hooks.
            if (itemHandler != null && itemHandler.getClass() != ItemStackHandler.class) return -1;
            var tank = (FluidTank) handler;
            var stored = tank.getFluid();
            long room = Math.max(0L, (long) tank.getCapacity() - stored.getAmount());
            long perCraft = 0;
            AEFluidKey fluid = null;
            for (var unit : unitInputs) {
                if (unit.what() instanceof AEFluidKey key) {
                    if (fluid == null) fluid = key;
                    if (!fluid.equals(key) || unit.amount() > room - perCraft) return 0;
                    perCraft += unit.amount();
                }
            }
            if (!stored.isEmpty() && !fluid.matches(stored)) return 0;
            upper = Math.min(upper, room / perCraft);
            fluidFill = new FluidFill(tank, fluid.toStack(1));
        }

        ItemSlots slots = null;
        if (itemHandler != null) {
            int slotCount = itemHandler.getSlots();
            if (slotCount == 0) return 0;
            if (slotCount > MAX_ITEM_SLOTS) return -1;
            slots = ItemSlots.capture(itemHandler, slotCount, unitInputs);
            if (slots == null) return -1;
            upper = slots.upperBound(upper);
        }
        return search(unitInputs, slots, fluidFill, upper);
    }

    private static boolean knownItems(ServerLevel level, BlockPos pos, @Nullable BlockEntity target,
            @Nullable IItemHandler handler) {
        if (handler == null) return false;
        if (handler.getClass() == ItemStackHandler.class) return true;
        if (!(target instanceof RandomizableContainerBlockEntity container) || container.getLootTable() != null) {
            return false;
        }
        if (handler.getClass() == VanillaHopperItemHandler.class) {
            return target.getClass() == HopperBlockEntity.class && ((InvWrapper) handler).getInv() == target;
        }
        if (handler.getClass() != InvWrapper.class) return false;
        var backing = ((InvWrapper) handler).getInv();
        var type = target.getClass();
        if (backing == target) {
            return type == ChestBlockEntity.class || type == TrappedChestBlockEntity.class
                    || type == BarrelBlockEntity.class || type == DispenserBlockEntity.class
                    || type == DropperBlockEntity.class;
        }
        if (backing == null || backing.getClass() != CompoundContainer.class
                || type != ChestBlockEntity.class && type != TrappedChestBlockEntity.class) return false;

        var state = target.getBlockState();
        if (!state.hasProperty(ChestBlock.TYPE) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return false;
        var otherPos = pos.relative(ChestBlock.getConnectedDirection(state));
        if (!level.hasChunkAt(otherPos)) return false;
        var other = level.getBlockEntity(otherPos);
        var combined = (CompoundContainer) backing;
        return other != target && other instanceof ChestBlockEntity chest && other.getClass() == type
                && chest.getLootTable() == null && combined.contains(container) && combined.contains(chest);
    }

    private static long search(List<GenericStack> unitInputs, @Nullable ItemSlots slots,
            @Nullable FluidFill fluidFill, long upper) {
        if (upper == 0 || fits(unitInputs, slots, fluidFill, upper)) return upper;
        long best = 0;
        long high = upper - 1;
        boolean skipped = false;
        // Greedy slot order can make fit nonmonotonic when earlier inputs claim
        // empty slots ahead of partially filled stacks. Every result is tested.
        while (high - best > LINEAR_SEARCH_SIZE) {
            long middle = best + (high - best) / 2;
            if (fits(unitInputs, slots, fluidFill, middle)) {
                best = middle;
            } else {
                skipped = true;
                high = middle - 1;
            }
        }
        for (long copies = high; copies > best; copies--) {
            if (fits(unitInputs, slots, fluidFill, copies)) return copies;
        }
        return best == 0 && skipped ? -1 : best;
    }

    private static boolean fits(List<GenericStack> unitInputs, @Nullable ItemSlots slots,
            @Nullable FluidFill fluidFill, long copies) {
        if (slots != null && !slots.fit(unitInputs, copies)) return false;
        return fluidFill == null || fluidFill.fit(unitInputs, copies);
    }

    private record FluidFill(FluidTank tank, FluidStack stack) {
        private boolean fit(List<GenericStack> unitInputs, long copies) {
            // The room bound reserves space for all emissions of this key. Test
            // the tank's validator with the amount AE2 will offer each time.
            for (int i = 0; i < unitInputs.size(); i++) {
                var unit = unitInputs.get(i);
                if (unit.what() instanceof AEFluidKey) {
                    stack.setAmount((int) (unit.amount() * copies));
                    if (!tank.isFluidValid(0, stack)) return false;
                }
            }
            return true;
        }
    }

    private static final class ItemNeed {
        private final AEItemKey key;
        private final int slotLimit;
        private long perCraft;
        private long occupiedRoom;

        private ItemNeed(AEItemKey key, int slotLimit) {
            this.key = key;
            this.slotLimit = slotLimit;
        }
    }

    private static final class ItemSlots {
        private final ItemNeed[] ordered;
        private final ItemNeed[] distinct;
        private final ItemNeed[] originalOwners;
        private final int[] originalCounts;
        private final ItemNeed[] owners;
        private final int[] counts;
        private int emptySlots;

        private ItemSlots(ItemNeed[] ordered, ItemNeed[] distinct, int slotCount) {
            this.ordered = ordered;
            this.distinct = distinct;
            this.originalOwners = new ItemNeed[slotCount];
            this.originalCounts = new int[slotCount];
            this.owners = new ItemNeed[slotCount];
            this.counts = new int[slotCount];
        }

        private static @Nullable ItemSlots capture(IItemHandler handler, int slotCount, List<GenericStack> unitInputs) {
            var ordered = new ItemNeed[unitInputs.size()];
            var needs = new HashMap<AEItemKey, ItemNeed>();
            // The verified handlers have the same slot limit and validity rules at each index.
            int slotLimit = handler.getSlotLimit(0);
            for (int i = 0; i < ordered.length; i++) {
                var unit = unitInputs.get(i);
                if (unit.what() instanceof AEItemKey key) {
                    var need = needs.get(key);
                    if (need == null) {
                        if (!COMPONENT_STACK_LIMIT.get(key.getReadOnlyStack().getItem().getClass())) return null;
                        need = new ItemNeed(key, Math.min(slotLimit, key.getMaxStackSize()));
                        needs.put(key, need);
                    }
                    // A clipped emission still exceeds all physical item capacity on this path.
                    need.perCraft += Math.min(unit.amount(), Integer.MAX_VALUE);
                    ordered[i] = need;
                }
            }
            var result = new ItemSlots(ordered, needs.values().toArray(ItemNeed[]::new), slotCount);
            IdentityHashMap<ItemStack, Boolean> seen = null;
            for (int slot = 0; slot < slotCount; slot++) {
                var stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    result.emptySlots++;
                    continue;
                }
                if (handler.getClass() == ItemStackHandler.class) {
                    // ItemStackHandler grows resident stacks in place.
                    if (seen == null) seen = new IdentityHashMap<>();
                    if (seen.put(stack, Boolean.TRUE) != null) return null;
                }
                int count = stack.getCount();
                result.originalCounts[slot] = count;
                for (var need : result.distinct) {
                    if (count < need.slotLimit && need.key.matches(stack)) {
                        result.originalOwners[slot] = need;
                        need.occupiedRoom += need.slotLimit - count;
                        break;
                    }
                }
            }
            return result;
        }

        private long upperBound(long high) {
            for (var need : distinct) {
                if (need.slotLimit <= 0) return 0;
                long room = need.occupiedRoom + (long) emptySlots * need.slotLimit;
                high = Math.min(high, room / need.perCraft);
            }
            // Even with a perfect slot assignment, every key has to fund its
            // remaining demand with separate empty slots.
            long low = 0;
            while (low < high) {
                long middle = low + (high - low + 1) / 2;
                int free = emptySlots;
                for (var need : distinct) {
                    long shortage = need.perCraft * middle - need.occupiedRoom;
                    if (shortage > 0) free -= (int) Math.ceilDiv(shortage, need.slotLimit);
                    if (free < 0) break;
                }
                if (free >= 0) low = middle;
                else high = middle - 1;
            }
            return low;
        }

        private boolean fit(List<GenericStack> unitInputs, long copies) {
            System.arraycopy(originalOwners, 0, owners, 0, owners.length);
            System.arraycopy(originalCounts, 0, counts, 0, counts.length);
            for (int i = 0; i < ordered.length; i++) {
                var need = ordered[i];
                if (need == null) continue;
                long remaining = unitInputs.get(i).amount() * copies;
                // AE2's item facade starts each emission at slot zero.
                for (int slot = 0; slot < counts.length && remaining > 0; slot++) {
                    int count = counts[slot];
                    if (count != 0 && owners[slot] != need) continue;
                    int inserted = (int) Math.min(remaining, need.slotLimit - count);
                    if (inserted <= 0) continue;
                    owners[slot] = need;
                    counts[slot] = count + inserted;
                    remaining -= inserted;
                }
                if (remaining > 0) return false;
            }
            return true;
        }
    }
}
