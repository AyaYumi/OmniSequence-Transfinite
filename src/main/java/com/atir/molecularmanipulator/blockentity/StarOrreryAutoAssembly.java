package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.MachineSource;
import appeng.me.helpers.PlayerSource;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntToLongFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/** A read-only preview and a reversible, server-thread transaction for fixed ring sockets. */
public final class StarOrreryAutoAssembly {
    public record Supply(int tier, int placed, int required, long available) {}
    public record Plan(List<Supply> supplies, @Nullable String blocked) {
        public Plan { supplies = List.copyOf(supplies); }
    }

    record Slot(BlockPos pos, int ring) { int tier() { return StarOrreryRings.tierOf(ring); } }
    enum Socket { MATCH, AIR, SPACE, CHUNKS, PERMISSION }
    record Inspection(Plan plan, List<Slot> missing) {
        Inspection { missing = List.copyOf(missing); }
    }

    private StarOrreryAutoAssembly() {}

    /** With no player, only the core's ME storage contributes to the material preview. */
    public static Plan plan(StarComputationEngineCoreBlockEntity core, int target,
            @Nullable ServerPlayer player) {
        if (!(core.getLevel() instanceof ServerLevel level) || target < 1 || target > StarOrreryRings.RINGS) {
            return new Plan(List.of(), "anchor_chunks");
        }
        var sources = sources(core, player);
        var facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        return inspect(slots(core.getBlockPos(), facing, target),
                slot -> socket(core, level, player, facing, slot),
                tier -> available(sources, anchorItem(tier))).plan();
    }

    /** Returns null only after every missing socket has been filled; the caller performs the normal ring scan. */
    @Nullable
    public static String assemble(StarComputationEngineCoreBlockEntity core, int target, ServerPlayer player) {
        if (!(core.getLevel() instanceof ServerLevel level) || player.level() != level
                || target < 1 || target > StarOrreryRings.RINGS) return "anchor_failed";
        var facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var sources = sources(core, player);
        var inspection = inspect(slots(core.getBlockPos(), facing, target),
                slot -> socket(core, level, player, facing, slot),
                tier -> available(sources, anchorItem(tier)));
        if (inspection.plan().blocked() != null) return inspection.plan().blocked();
        for (var supply : inspection.plan().supplies()) {
            if (supply.available() < supply.required() - supply.placed()) return "anchor_materials";
        }
        var taken = new ArrayList<Withdrawal>();
        for (var supply : inspection.plan().supplies()) {
            if (!withdraw(sources, anchorItem(supply.tier()), supply.required() - supply.placed(), taken)) {
                refund(taken, stack -> returnToPlayer(player, stack));
                return "anchor_materials";
            }
        }
        var changed = new ArrayList<Placed>();
        String failed = null;
        for (var slot : inspection.missing()) {
            var socket = socket(core, level, player, facing, slot);
            // A callback may have changed another socket since the preflight. Never replace it.
            if (socket != Socket.AIR) { failed = blocked(socket); break; }
            var previous = level.getBlockState(slot.pos());
            var snapshot = BlockSnapshot.create(level.dimension(), level, slot.pos());
            var expected = StarComputationEngineCoreBlockEntity.starOrreryAnchorFor(slot.ring())
                    .defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
            changed.add(new Placed(slot, snapshot));
            if (!level.setBlock(slot.pos(), expected, Block.UPDATE_CLIENTS)) {
                failed = "anchor_failed";
                break;
            }
            if (MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, previous, player))) {
                failed = "anchor_permission";
                break;
            }
            if (socket(core, level, player, facing, slot) != Socket.MATCH) {
                failed = "anchor_failed";
                break;
            }
        }
        if (failed == null) {
            for (var placed : changed) level.updateNeighborsAt(placed.slot().pos(),
                    StarComputationEngineCoreBlockEntity.starOrreryAnchorFor(placed.slot().ring()));
            for (var slot : inspection.missing()) {
                if (socket(core, level, player, facing, slot) != Socket.MATCH) {
                    failed = "anchor_failed";
                    break;
                }
            }
        }
        if (failed != null) {
            for (int index = changed.size() - 1; index >= 0; index--) changed.get(index).snapshot().restore(true, false);
            refund(taken, stack -> returnToPlayer(player, stack));
            return failed;
        }
        return null;
    }

    private record Placed(Slot slot, BlockSnapshot snapshot) {}

    static List<Slot> slots(BlockPos core, Direction facing, int target) {
        if (target < 1 || target > StarOrreryRings.RINGS) throw new IllegalArgumentException("Target ring must be 1..9");
        var result = new ArrayList<Slot>();
        for (int ring = 0; ring < target; ring++) for (int index = 0; index < StarOrreryRings.slotCount(ring); index++) {
            result.add(new Slot(StarOrreryRings.slot(core, facing, ring, index), ring));
        }
        return List.copyOf(result);
    }

    static Inspection inspect(List<Slot> slots, Function<Slot, Socket> reader, IntToLongFunction available) {
        int[] placed = new int[3], required = new int[3];
        var missing = new ArrayList<Slot>();
        String failure = null;
        for (var slot : slots) {
            required[slot.tier()]++;
            var socket = reader.apply(slot);
            if (socket == Socket.MATCH) placed[slot.tier()]++;
            else if (socket == Socket.AIR) missing.add(slot);
            else if (failure == null) failure = blocked(socket);
        }
        var supplies = new ArrayList<Supply>(3);
        for (int tier = 0; tier < 3; tier++) supplies.add(new Supply(tier, placed[tier], required[tier],
                required[tier] == 0 ? 0 : available.applyAsLong(tier)));
        return new Inspection(new Plan(supplies, failure), missing);
    }

    private static Socket socket(StarComputationEngineCoreBlockEntity core, ServerLevel level,
            @Nullable ServerPlayer player, Direction facing, Slot slot) {
        var pos = slot.pos();
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return Socket.SPACE;
        if (!level.hasChunkAt(pos)) return Socket.CHUNKS;
        if (BuildingTransfer.locked(level, pos) || player != null && (!player.mayBuild() || player.isSpectator()
                || !level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, ItemStack.EMPTY))) {
            return Socket.PERMISSION;
        }
        if (StarOrreryOwnership.ownedByOther(level, pos, core.getBlockPos())) return Socket.SPACE;
        var state = level.getBlockState(pos);
        if (StarComputationEngineCoreBlockEntity.starOrreryProbe(state,
                StarComputationEngineCoreBlockEntity.starOrreryAnchorFor(slot.ring()), facing, slot.ring(), false)
                == StarOrreryRings.Probe.MATCH) return Socket.MATCH;
        return state.isAir() && level.getBlockEntity(pos) == null ? Socket.AIR : Socket.SPACE;
    }

    private static String blocked(Socket socket) {
        return switch (socket) {
            case CHUNKS -> "anchor_chunks";
            case PERMISSION -> "anchor_permission";
            case SPACE -> "anchor_space";
            default -> "anchor_failed";
        };
    }

    private static Item anchorItem(int tier) {
        return StarComputationEngineCoreBlockEntity.starOrreryAnchorFor(tier * 3).asItem();
    }

    /** Small transaction boundary shared with inventory regression tests. Keys retain all item components. */
    interface MaterialSource {
        List<AEItemKey> keys();
        long extract(AEItemKey key, long amount, Actionable mode);
        long insert(AEItemKey key, long amount);
    }
    record Withdrawal(MaterialSource source, AEItemKey key, long amount) {}

    static long available(List<MaterialSource> sources, Item item) {
        long total = 0;
        for (var source : sources) for (var key : source.keys()) if (key.getItem() == item) {
            long amount = Math.max(0, source.extract(key, Long.MAX_VALUE, Actionable.SIMULATE));
            total = amount > Long.MAX_VALUE - total ? Long.MAX_VALUE : total + amount;
        }
        return total;
    }

    /** All demands are simulated by the caller before any source is modulated. A short extraction is recorded too. */
    static boolean withdraw(List<MaterialSource> sources, Item item, int demand, List<Withdrawal> taken) {
        long remaining = demand;
        for (var source : sources) {
            if (remaining <= 0) break;
            for (var key : source.keys()) {
                if (remaining <= 0) break;
                if (key.getItem() != item) continue;
                long amount = source.extract(key, remaining, Actionable.MODULATE);
                if (amount > 0) {
                    taken.add(new Withdrawal(source, key, amount));
                    remaining -= amount;
                }
            }
        }
        return remaining == 0;
    }

    static void refund(List<Withdrawal> taken, Consumer<ItemStack> remainder) {
        for (int index = taken.size() - 1; index >= 0; index--) {
            var entry = taken.get(index);
            long left = entry.amount() - entry.source().insert(entry.key(), entry.amount());
            while (left > 0) {
                int count = (int) Math.min(left, entry.key().toStack().getMaxStackSize());
                remainder.accept(entry.key().toStack(count));
                left -= count;
            }
        }
        taken.clear();
    }

    private static List<MaterialSource> sources(StarComputationEngineCoreBlockEntity core, @Nullable ServerPlayer player) {
        var result = new ArrayList<MaterialSource>(2);
        if (player != null) result.add(new InventorySource(player.getInventory()));
        var grid = core.getMainNode().isActive() ? core.getMainNode().getGrid() : null;
        if (grid != null) result.add(new NetworkSource(grid.getStorageService().getInventory(),
                player == null ? new MachineSource(core) : new PlayerSource(player)));
        return result;
    }

    private record NetworkSource(MEStorage storage, IActionSource source) implements MaterialSource {
        @Override public List<AEItemKey> keys() {
            var result = new ArrayList<AEItemKey>();
            var available = new KeyCounter();
            storage.getAvailableStacks(available);
            for (var entry : available) if (entry.getKey() instanceof AEItemKey key) result.add(key);
            return result;
        }
        @Override public long extract(AEItemKey key, long amount, Actionable mode) {
            return storage.extract(key, amount, mode, source);
        }
        @Override public long insert(AEItemKey key, long amount) {
            return storage.insert(key, amount, Actionable.MODULATE, source);
        }
    }

    private record InventorySource(Inventory inventory) implements MaterialSource {
        @Override public List<AEItemKey> keys() {
            var result = new LinkedHashSet<AEItemKey>();
            for (int index = 0; index < inventory.getContainerSize(); index++) {
                var key = AEItemKey.of(inventory.getItem(index));
                if (key != null) result.add(key);
            }
            return List.copyOf(result);
        }
        @Override public long extract(AEItemKey key, long amount, Actionable mode) {
            long taken = 0;
            for (int index = 0; index < inventory.getContainerSize() && taken < amount; index++) {
                var stack = inventory.getItem(index);
                if (!key.matches(stack)) continue;
                int count = (int) Math.min(amount - taken, stack.getCount());
                taken += count;
                if (mode == Actionable.MODULATE) inventory.removeItem(index, count);
            }
            if (mode == Actionable.MODULATE && taken > 0) inventory.setChanged();
            return taken;
        }
        @Override public long insert(AEItemKey key, long amount) {
            long remaining = amount;
            // Inventory.add discards a full-inventory remainder for creative players. These are paid
            // construction materials even in creative, so refund explicitly and never use that shortcut.
            for (var stack : inventory.items) {
                if (!key.matches(stack)) continue;
                int count = (int) Math.min(remaining, Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(count);
                remaining -= count;
                if (remaining == 0) break;
            }
            for (int index = 0; index < inventory.items.size() && remaining > 0; index++) {
                if (!inventory.items.get(index).isEmpty()) continue;
                int count = (int) Math.min(remaining, key.toStack().getMaxStackSize());
                inventory.items.set(index, key.toStack(count));
                remaining -= count;
            }
            inventory.setChanged();
            return amount - remaining;
        }
    }

    private static void returnToPlayer(ServerPlayer player, ItemStack stack) {
        var key = AEItemKey.of(stack);
        if (key == null) return;
        stack.shrink((int) new InventorySource(player.getInventory()).insert(key, stack.getCount()));
        if (!stack.isEmpty()) player.drop(stack, false);
    }
}
