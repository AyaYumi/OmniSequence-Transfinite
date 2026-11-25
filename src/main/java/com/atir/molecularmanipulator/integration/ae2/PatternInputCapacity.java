package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.SupplierStorage;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.items.storage.BasicStorageCell;
import appeng.me.cells.BasicCellInventory;
import com.atir.molecularmanipulator.integration.mekanism.MekanismBatchSupport;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;

/** Proves whole processing-pattern batches against the capacity on one provider face. */
public final class PatternInputCapacity {
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

    // AE2's target keeps the exact capability selected by its sided cache. A second capability
    // lookup can return a different wrapper, especially for ME chests and third-party redirects.
    private static final ClassValue<Field> TARGET_STORAGE = new ClassValue<>() {
        @Override protected @Nullable Field computeValue(Class<?> type) {
            if (!type.getName().equals("appeng.helpers.patternprovider.PatternProviderTargetCache$1")
                    && !type.getName().equals("appeng.helpers.patternprovider.PatternProviderTarget$1")) return null;
            try {
                var field = type.getDeclaredField("val$storage");
                return field.getType() == MEStorage.class && field.trySetAccessible() ? field : null;
            } catch (ReflectiveOperationException | SecurityException unavailable) {
                return null;
            }
        }
    };
    private record CellAccess(MethodHandle delegate, MethodHandle voidUpgrade) {
        private static final @Nullable CellAccess INSTANCE = bind();

        private static @Nullable CellAccess bind() {
            try {
                var lookup = MethodHandles.lookup();
                return new CellAccess(MethodHandles.privateLookupIn(SupplierStorage.class, lookup)
                        .findVirtual(SupplierStorage.class, "getDelegate", MethodType.methodType(MEStorage.class)),
                        MethodHandles.privateLookupIn(BasicCellInventory.class, lookup)
                                .findGetter(BasicCellInventory.class, "hasVoidUpgrade", boolean.class));
            } catch (ReflectiveOperationException | SecurityException unavailable) {
                return null;
            }
        }
    }

    public static @Nullable MEStorage targetStorage(PatternProviderTarget target) {
        var field = TARGET_STORAGE.get(target.getClass());
        try {
            return field == null ? null : (MEStorage) field.get(target);
        } catch (IllegalAccessException unavailable) {
            return null;
        }
    }

    private PatternInputCapacity() {}

    /** Returns a proved number of crafts, or {@code -1} when capacity is unknown. */
    public static long limit(ServerLevel level, BlockPos pos, Direction inputSide, @Nullable BlockEntity target,
            PatternProviderTarget adapter, List<GenericStack> unitInputs, long requestedCrafts) {
        if (requestedCrafts <= 0) return 0;
        if (unitInputs.isEmpty()) return -1;
        var single = unitInputs.size() == 1 ? unitInputs.getFirst() : null;
        if (single != null && single.amount() <= 0) return -1;

        // AE2 prefers the ME capability over the platform wrappers on this face.
        var state = target == null ? level.getBlockState(pos) : target.getBlockState();
        if (level.getCapability(AECapabilities.ME_STORAGE, pos, state, target, inputSide) != null) {
            var storage = targetStorage(adapter);
            InterfaceLogicHost host = target instanceof InterfaceLogicHost block ? block : null;
            if (host == null && target instanceof CableBusBlockEntity cable && cable.getPart(inputSide) instanceof InterfaceLogicHost part) {
                host = part;
            }
            if (host != null) {
                var logic = host.getInterfaceLogic();
                if (logic.getClass() == InterfaceLogic.class && !logic.getConfig().isEmpty() && storage != null
                        && storage == logic.getInventory()) {
                    return limitInterface(logic.getStorage(), unitInputs, requestedCrafts);
                }
            }
            if (target != null && target.getClass() == MEChestBlockEntity.class
                    && ((MEChestBlockEntity) target).getCell().getItem().getClass() == BasicStorageCell.class) {
                var chest = (MEChestBlockEntity) target;
                if (!(storage instanceof SupplierStorage supplier) || CellAccess.INSTANCE == null) return -1;
                try {
                    var delegate = (MEStorage) CellAccess.INSTANCE.delegate.invokeExact(supplier);
                    if (delegate != null && delegate == chest.getCellInventory(0)
                            && chest.getOriginalCellInventory(0) instanceof BasicCellInventory cell) {
                        // AE2's void upgrade can acknowledge stock it discards, even for a single key.
                        if ((boolean) CellAccess.INSTANCE.voidUpgrade.invokeExact(cell)
                                || cell.getUpgradesInventory().isInstalled(AEItems.VOID_CARD)) return -1;
                        return single == null ? limitCell(cell, adapter, unitInputs, requestedCrafts)
                                : probe(adapter, single, requestedCrafts);
                    }
                    return -1;
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable unavailable) {
                    return -1;
                }
            }
            return single == null ? -1 : probe(adapter, single, requestedCrafts);
        }
        if (single != null && !(single.what() instanceof AEItemKey || single.what() instanceof AEFluidKey)) {
            return probe(adapter, single, requestedCrafts);
        }

        boolean hasItems = false;
        boolean hasFluids = false;
        boolean hasOther = false;
        for (var unit : unitInputs) {
            if (unit.amount() <= 0) return -1;
            switch (unit.what()) {
                case AEItemKey ignored -> hasItems = true;
                case AEFluidKey ignored -> hasFluids = true;
                default -> hasOther = true;
            }
        }
        var itemHandler = hasItems ? level.getCapability(Capabilities.ItemHandler.BLOCK, pos, state, target, inputSide)
                : null;
        if (hasOther) return MekanismBatchSupport.limit(target, itemHandler, adapter, unitInputs, requestedCrafts);
        if (hasItems && !knownItems(level, pos, target, itemHandler)) {
            return single == null ? -1 : probe(adapter, single, requestedCrafts);
        }

        long upper = requestedCrafts;
        FluidFill fluidFill = null;
        if (hasFluids) {
            var handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, state, target, inputSide);
            if (handler == null || handler.getClass() != FluidTank.class) {
                return single == null ? -1 : probe(adapter, single, requestedCrafts);
            }
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

        if (itemHandler != null && upper > 0) {
            upper = limitItems(itemHandler, unitInputs, upper);
            if (upper < 0) return single == null ? -1 : probe(adapter, single, requestedCrafts);
        }
        if (upper == 0 || fluidFill == null || fluidFill.fit(unitInputs, upper)) return upper;
        // A custom FluidTank validator can depend on the offered amount. Its rejection
        // at the physical maximum says nothing about other positive batch sizes.
        return upper == 1 ? 0 : -1;
    }

    private static long limitCell(BasicCellInventory cell, PatternProviderTarget adapter, List<GenericStack> inputs, long maximum) {
        long room = cell.getRemainingItemCount();
        long total = 0;
        var amounts = new Object2LongOpenHashMap<AEKey>();
        for (var input : inputs) {
            if (input.amount() <= 0) return -1;
            if (input.amount() > room - total) return 0;
            total += input.amount();
            amounts.addTo(input.what(), input.amount());
        }
        var present = cell.getAvailableStacks();
        long newTypes = 0;
        var type = inputs.getFirst().what().getType();
        for (var key : amounts.keySet()) {
            if (key.getType() != type) return 0;
            if (present.get(key) == 0) newTypes++;
        }
        if (newTypes > 0) {
            long bytesPerType = cell.getBytesPerType();
            long unitsPerByte = type.getAmountPerByte();
            if (bytesPerType <= 0 || unitsPerByte <= 0) return -1;
            if (newTypes > cell.getRemainingItemTypes() || newTypes > room / unitsPerByte / bytesPerType) return 0;
            room -= newTypes * bytesPerType * unitsPerByte;
        }
        maximum = Math.min(maximum, room / total);
        // Filters, nested cells and equal-distribution limits still use the actual chest and action source.
        for (var entry : amounts.object2LongEntrySet()) {
            if (maximum == 0) break;
            long perCraft = entry.getLongValue();
            maximum = adapter.insert(entry.getKey(), maximum * perCraft, Actionable.SIMULATE) / perCraft;
        }
        return maximum;
    }

    static long limitInterface(GenericStackInv storage, List<GenericStack> inputs, long maximum) {
        int slots = storage.size();
        var keys = new AEKey[slots];
        var counts = new long[slots];
        var rates = new long[slots];
        // Follow GenericStackInv's first-fit order. Between slot claims and fill boundaries, each
        // resident count and outstanding offer changes linearly with the number of copies.
        nextSize: while (maximum > 0) {
            for (int slot = 0; slot < slots; slot++) {
                var stack = storage.getStack(slot);
                keys[slot] = stack == null ? null : stack.what();
                counts[slot] = stack == null ? 0 : stack.amount();
                rates[slot] = 0;
            }
            long next = 0;
            for (var input : inputs) {
                var key = input.what();
                long rate = input.amount();
                if (rate <= 0 || maximum > Long.MAX_VALUE / rate) return -1;
                long remaining = maximum * rate;
                long limit = storage.getMaxAmount(key);
                for (int slot = 0; slot < slots && remaining > 0; slot++) {
                    if ((keys[slot] != null && !key.equals(keys[slot])) || !storage.isAllowedIn(slot, key)) continue;
                    long count = counts[slot];
                    if (count >= limit) continue;
                    long combinedRate = rate + rates[slot];
                    if (remaining > Long.MAX_VALUE - count) {
                        // AE2 adds the offer to the resident before clamping. Overflow skips this slot.
                        if (combinedRate > 0) next = Math.max(next,
                                maximum - Math.ceilDiv(remaining - (Long.MAX_VALUE - count), combinedRate));
                        continue;
                    }
                    if (keys[slot] == null) {
                        if (rate > 0) next = Math.max(next, maximum - Math.ceilDiv(remaining, rate));
                        keys[slot] = key;
                    }
                    long room = limit - count;
                    if (remaining >= room) {
                        remaining -= room;
                        if (combinedRate > 0) next = Math.max(next, maximum - remaining / combinedRate - 1);
                        counts[slot] = limit;
                        rates[slot] = 0;
                        rate = combinedRate;
                    } else {
                        counts[slot] = count + remaining;
                        rates[slot] = combinedRate;
                        remaining = 0;
                    }
                }
                if (remaining > 0) {
                    if (rate > 0) next = Math.max(next, maximum - Math.ceilDiv(remaining, rate));
                    maximum = next;
                    continue nextSize;
                }
            }
            return maximum;
        }
        return 0;
    }

    static long probe(PatternProviderTarget adapter, GenericStack unit, long requestedCrafts) {
        long amount = unit.amount();
        if (amount <= 0) return -1;
        long copies = Math.min(requestedCrafts, Long.MAX_VALUE / amount);
        if (copies <= 0) return 0;
        long requested = copies * amount;
        long inserted = adapter.insert(unit.what(), requested, Actionable.SIMULATE);
        if (inserted == requested) return copies;
        if (inserted < 0 || inserted > requested) return -1;
        long whole = inserted / amount;
        if (whole == 0) return copies == 1 ? 0 : -1;
        // Recheck the exact offer that will be queued, including its whole-craft rounding.
        return adapter.insert(unit.what(), whole * amount, Actionable.SIMULATE) == whole * amount ? whole : -1;
    }

    public static boolean hasConstantStackLimit(AEItemKey key) {
        return COMPONENT_STACK_LIMIT.get(key.getItem().getClass());
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

    static long limitItems(IItemHandler handler, List<GenericStack> unitInputs, long maximum) {
        int slotCount = handler.getSlots();
        if (slotCount <= 0 || maximum <= 0) return 0;
        var needs = new HashMap<AEItemKey, ItemNeed>();
        var ordered = new ItemNeed[unitInputs.size()];
        int slotLimit = handler.getSlotLimit(0);
        for (int i = 0; i < ordered.length; i++) {
            var unit = unitInputs.get(i);
            if (!(unit.what() instanceof AEItemKey key)) continue;
            var need = needs.get(key);
            if (need == null) {
                if (!hasConstantStackLimit(key)) return -1;
                int limit = Math.min(slotLimit, key.getMaxStackSize());
                if (limit <= 0) return 0;
                need = new ItemNeed(key, limit);
                needs.put(key, need);
            }
            if (unit.amount() <= 0) return -1;
            if (unit.amount() > Long.MAX_VALUE - need.perCraft) return 0;
            need.perCraft += unit.amount();
            ordered[i] = need;
        }

        var distinct = needs.values().toArray(ItemNeed[]::new);
        // Once the prefix alone fits every key in initially empty slots, later slots cannot matter.
        long emptySlotsNeeded = 0;
        for (var need : distinct) {
            maximum = Math.min(maximum, (long) slotCount * need.slotLimit / need.perCraft);
        }
        if (maximum == 0) return 0;
        for (var need : distinct) {
            emptySlotsNeeded += Math.ceilDiv(maximum * need.perCraft, need.slotLimit);
        }
        IdentityHashMap<ItemStack, Boolean> seen = null;
        int emptySlots = 0;
        for (int slot = 0; slot < slotCount; slot++) {
            var stack = handler.getStackInSlot(slot);
            if (stack.isEmpty()) {
                if (++emptySlots >= emptySlotsNeeded) return maximum;
                continue;
            }
            int count = stack.getCount();
            for (var need : distinct) {
                if (count >= need.slotLimit || !need.key.matches(stack)) continue;
                if (handler.getClass() == ItemStackHandler.class) {
                    // Growing one mutable resident also fills every alias of it. Only its
                    // first slot supplies room in AE2's scan. Full and unrelated stacks need no entry.
                    if (seen == null) seen = new IdentityHashMap<>();
                    if (seen.put(stack, Boolean.TRUE) != null) break;
                }
                need.addRoom(emptySlots, need.slotLimit - count);
                break;
            }
        }
        return new ItemSlots(ordered, distinct, emptySlots).largest(unitInputs, maximum);
    }

    private static final class ItemNeed {
        private final AEItemKey key;
        private final int slotLimit;
        // Cumulative compatible room before each change in the original empty-slot prefix.
        private final IntArrayList emptyPrefixes = new IntArrayList();
        private final LongArrayList rooms = new LongArrayList();
        private long perCraft;
        private long occupiedRoom;

        // Per-probe state. All previously claimed empties precede the next unclaimed one.
        private int claimed;
        private long demand;
        private long variablePerCraft;

        private ItemNeed(AEItemKey key, int slotLimit) {
            this.key = key;
            this.slotLimit = slotLimit;
        }

        private void addRoom(int emptySlots, int room) {
            occupiedRoom += room;
            int last = emptyPrefixes.size() - 1;
            if (last >= 0 && emptyPrefixes.getInt(last) == emptySlots) {
                rooms.set(last, occupiedRoom);
            } else {
                emptyPrefixes.add(emptySlots);
                rooms.add(occupiedRoom);
            }
        }

        private long roomAt(int emptyPrefix, int alreadyClaimed) {
            long emptyRoom = (long) (emptyPrefix - alreadyClaimed + claimed) * slotLimit;
            int low = 0;
            int high = emptyPrefixes.size();
            while (low < high) {
                int mid = low + (high - low) / 2;
                if (emptyPrefixes.getInt(mid) <= emptyPrefix) low = mid + 1;
                else high = mid;
            }
            return emptyRoom + (low == 0 ? 0 : rooms.getLong(low - 1));
        }

        // While the same facade chunks and preceding slots are used, only final chunks
        // vary with copies. Find the largest smaller offer that can avoid this slot.
        private long fitsAtMost(long copies, long room) {
            return variablePerCraft == 0 ? 0 : copies - Math.ceilDiv(demand - room, variablePerCraft);
        }
    }

    private record ItemSlots(ItemNeed[] ordered, ItemNeed[] distinct, int emptySlots) {
        private long largest(List<GenericStack> inputs, long maximum) {
            for (var need : distinct) {
                long room = need.occupiedRoom + (long) emptySlots * need.slotLimit;
                maximum = Math.min(maximum, room / need.perCraft);
            }
            // All empty slots are available to a lone key on every native int-sized pass.
            if (distinct.length == 1) return maximum;
            long low = 0;
            long high = maximum;
            while (low < high) {
                long middle = low + Math.ceilDiv(high - low, 2L);
                long free = emptySlots;
                for (var need : distinct) {
                    long shortage = need.perCraft * middle - need.occupiedRoom;
                    if (shortage > 0) free -= Math.ceilDiv(shortage, need.slotLimit);
                    if (free < 0) break;
                }
                if (free >= 0) low = middle;
                else high = middle - 1;
            }

            boolean monotone = true;
            int runs = 0;
            ItemNeed previous = null;
            for (int i = 0; i < ordered.length; i++) {
                var need = ordered[i];
                if (need == null) continue;
                if (need != previous) runs++;
                previous = need;
                if (inputs.get(i).amount() * high > Integer.MAX_VALUE) monotone = false;
            }
            monotone &= runs == distinct.length;
            low = 0;
            long copies = high;
            while (copies > low) {
                long next = fitOrLower(inputs, copies);
                if (next == copies) {
                    if (!monotone || copies == high) return copies;
                    low = copies;
                } else {
                    high = next;
                }
                // With one run per key, larger preceding offers can only move the next
                // empty slot forward. Interleaved keys can reclaim earlier slack.
                copies = monotone ? low + Math.ceilDiv(high - low, 2L) : high;
            }
            return low;
        }

        private long fitOrLower(List<GenericStack> inputs, long copies) {
            for (var need : distinct) {
                need.claimed = 0;
                need.demand = 0;
                need.variablePerCraft = 0;
            }
            long largestOffer = 0;
            long next = 0;
            for (int i = 0; i < ordered.length; i++) {
                if (ordered[i] == null) continue;
                long unit = inputs.get(i).amount();
                long total = unit * copies;
                largestOffer = Math.max(largestOffer, total);
                if (total > Integer.MAX_VALUE) {
                    long earlierChunks = (total - 1) / Integer.MAX_VALUE;
                    next = Math.max(next, earlierChunks * Integer.MAX_VALUE / unit);
                }
            }

            int used = 0;
            // AE2 attempts each sendList entry once per pass and clips each item offer to int.
            // A subsequent pass requires a full int of physical slot room in the preceding one.
            for (long previous = 0; previous < largestOffer; previous += Integer.MAX_VALUE) {
                for (int i = 0; i < ordered.length; i++) {
                    var need = ordered[i];
                    if (need == null) continue;
                    long unit = inputs.get(i).amount();
                    long remaining = unit * copies - previous;
                    if (remaining <= 0) continue;
                    need.demand += Math.min(remaining, Integer.MAX_VALUE);
                    if (remaining <= Integer.MAX_VALUE) need.variablePerCraft += unit;

                    long available = need.occupiedRoom + (long) (emptySlots - used + need.claimed) * need.slotLimit;
                    if (need.demand > available) return Math.max(next, need.fitsAtMost(copies, available));
                    int low = used;
                    int high = emptySlots;
                    while (low < high) {
                        int mid = low + (high - low) / 2;
                        if (need.demand > need.roomAt(mid, used)) low = mid + 1;
                        else high = mid;
                    }
                    if (low > used) {
                        // Until an earlier emission can release its last claimed empty, the
                        // same ordered prefix is unavailable to every later key. All sizes
                        // skipped after a failure therefore share that failure.
                        next = Math.max(next, need.fitsAtMost(copies, need.roomAt(low - 1, used)));
                        need.claimed += low - used;
                        used = low;
                    }
                }
            }
            return copies;
        }
    }
}
