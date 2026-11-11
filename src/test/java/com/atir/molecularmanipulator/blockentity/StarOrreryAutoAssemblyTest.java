package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class StarOrreryAutoAssemblyTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test void plansUseTheExactUniquePrefixAndCumulativeTierCountsInEveryFacing() {
        int[] total = {24, 56, 96, 144, 200, 264, 336, 416, 504};
        for (var facing : Direction.Plane.HORIZONTAL) for (int target = 1; target <= 9; target++) {
            var slots = StarOrreryAutoAssembly.slots(new BlockPos(7, 100, -9), facing, target);
            assertEquals(total[target - 1], slots.size());
            assertEquals(slots.size(), new HashSet<>(slots.stream().map(StarOrreryAutoAssembly.Slot::pos).toList()).size());
            var inspection = StarOrreryAutoAssembly.inspect(slots, ignored -> StarOrreryAutoAssembly.Socket.AIR, tier -> 0);
            assertNull(inspection.plan().blocked(), "Insufficient materials are not a geometry failure");
            assertEquals(slots.size(), inspection.missing().size());
            assertEquals(slots.size(), inspection.plan().supplies().stream()
                    .mapToInt(StarOrreryAutoAssembly.Supply::required).sum());
            for (var supply : inspection.plan().supplies()) {
                int count = 0;
                for (int ring = 0; ring < target; ring++) if (StarOrreryRings.tierOf(ring) == supply.tier()) {
                    count += StarOrreryRings.slotCount(ring);
                }
                assertEquals(count, supply.required());
            }
        }
        var all = StarOrreryAutoAssembly.inspect(StarOrreryAutoAssembly.slots(BlockPos.ZERO, Direction.NORTH, 9),
                ignored -> StarOrreryAutoAssembly.Socket.AIR, tier -> 0).plan();
        assertEquals(List.of(96, 168, 240), all.supplies().stream().map(StarOrreryAutoAssembly.Supply::required).toList());
        assertThrows(IllegalArgumentException.class, () -> StarOrreryAutoAssembly.slots(BlockPos.ZERO, Direction.NORTH, 0));
        assertThrows(IllegalArgumentException.class, () -> StarOrreryAutoAssembly.slots(BlockPos.ZERO, Direction.NORTH, 10));
    }

    @Test void correctExistingSocketsAreCreditedButNeverAddedToTheConstructionList() {
        var slots = StarOrreryAutoAssembly.slots(BlockPos.ZERO, Direction.WEST, 4);
        var inspection = StarOrreryAutoAssembly.inspect(slots,
                slot -> slot.ring() < 3 ? StarOrreryAutoAssembly.Socket.MATCH : StarOrreryAutoAssembly.Socket.AIR,
                tier -> tier == 1 ? 48 : 0);
        assertNull(inspection.plan().blocked());
        assertEquals(new StarOrreryAutoAssembly.Supply(0, 96, 96, 0), inspection.plan().supplies().get(0));
        assertEquals(new StarOrreryAutoAssembly.Supply(1, 0, 48, 48), inspection.plan().supplies().get(1));
        assertEquals(48, inspection.missing().size());
        assertTrue(inspection.missing().stream().allMatch(slot -> slot.ring() == 3));
        assertThrows(UnsupportedOperationException.class, () -> inspection.missing().clear());
        assertThrows(UnsupportedOperationException.class, () -> inspection.plan().supplies().clear());
    }

    @Test void occupiedUnloadedAndProtectedSocketsRemainFailuresEvenWithUnlimitedMaterials() {
        var reasons = Map.of(StarOrreryAutoAssembly.Socket.SPACE, "anchor_space",
                StarOrreryAutoAssembly.Socket.CHUNKS, "anchor_chunks",
                StarOrreryAutoAssembly.Socket.PERMISSION, "anchor_permission");
        var slots = StarOrreryAutoAssembly.slots(BlockPos.ZERO, Direction.SOUTH, 1);
        for (var entry : reasons.entrySet()) {
            var first = slots.getFirst();
            var inspection = StarOrreryAutoAssembly.inspect(slots,
                    slot -> slot.equals(first) ? entry.getKey() : StarOrreryAutoAssembly.Socket.MATCH,
                    tier -> Long.MAX_VALUE);
            assertEquals(entry.getValue(), inspection.plan().blocked());
            assertEquals(23, inspection.plan().supplies().getFirst().placed());
            assertTrue(inspection.missing().isEmpty(), "A blocked socket is never buildable air");
        }
    }

    @Test void simulationIsReadOnlyAndBackpackIsSpentBeforeTheNetwork() {
        var key = AEItemKey.of(new ItemStack(Items.DIAMOND));
        var backpack = new Source(key, 3);
        var network = new Source(key, 9);
        var sources = List.<StarOrreryAutoAssembly.MaterialSource>of(backpack, network);
        assertEquals(12, StarOrreryAutoAssembly.available(sources, Items.DIAMOND));
        assertEquals(3, backpack.count(key));
        assertEquals(9, network.count(key));
        assertEquals(0, backpack.modulations + network.modulations);
        var taken = new ArrayList<StarOrreryAutoAssembly.Withdrawal>();
        assertTrue(StarOrreryAutoAssembly.withdraw(sources, Items.DIAMOND, 5, taken));
        assertEquals(0, backpack.count(key));
        assertEquals(7, network.count(key));
        assertEquals(5, taken.stream().mapToLong(StarOrreryAutoAssembly.Withdrawal::amount).sum());
    }

    @Test void aShortRealExtractionCanRefundEveryTierAndRetainsItemComponents() {
        var plain = AEItemKey.of(new ItemStack(Items.DIAMOND));
        var namedStack = new ItemStack(Items.GOLD_INGOT);
        namedStack.set(DataComponents.CUSTOM_NAME, Component.literal("Original anchor component"));
        var named = AEItemKey.of(namedStack);
        var first = new Source(plain, 8);
        var second = new Source(named, 12);
        second.modulationLimit = 2;
        var sources = List.<StarOrreryAutoAssembly.MaterialSource>of(first, second);
        assertEquals(12, StarOrreryAutoAssembly.available(sources, Items.GOLD_INGOT));
        var taken = new ArrayList<StarOrreryAutoAssembly.Withdrawal>();
        assertTrue(StarOrreryAutoAssembly.withdraw(sources, Items.DIAMOND, 6, taken));
        assertFalse(StarOrreryAutoAssembly.withdraw(sources, Items.GOLD_INGOT, 7, taken));
        assertEquals(1, second.modulations);
        assertEquals(10, second.count(named));
        StarOrreryAutoAssembly.refund(taken, ignored -> fail("Original stores accept the complete refund"));
        assertEquals(8, first.count(plain));
        assertEquals(12, second.count(named));
        assertTrue(taken.isEmpty());
        assertEquals(Component.literal("Original anchor component"), named.toStack().get(DataComponents.CUSTOM_NAME));
        StarOrreryAutoAssembly.refund(taken, ignored -> fail("A cleared transaction cannot refund twice"));
        assertEquals(12, second.count(named));
    }

    @Test void rejectedRefundsReturnOnlyTheUnacceptedRemainderAndDoNotLoseOrCopyItems() {
        var key = AEItemKey.of(new ItemStack(Items.DIAMOND));
        var source = new Source(key, 20);
        var taken = new ArrayList<StarOrreryAutoAssembly.Withdrawal>();
        assertTrue(StarOrreryAutoAssembly.withdraw(List.of(source), Items.DIAMOND, 11, taken));
        source.insertionLimit = 4;
        var leftovers = new ArrayList<ItemStack>();
        StarOrreryAutoAssembly.refund(taken, leftovers::add);
        assertEquals(13, source.count(key));
        assertEquals(7, leftovers.stream().mapToInt(ItemStack::getCount).sum());
        assertEquals(20, source.count(key) + leftovers.stream().mapToInt(ItemStack::getCount).sum());
        assertTrue(leftovers.stream().allMatch(key::matches));
    }

    private static final class Source implements StarOrreryAutoAssembly.MaterialSource {
        private final Map<AEItemKey, Long> items = new LinkedHashMap<>();
        long modulationLimit = Long.MAX_VALUE;
        long insertionLimit = Long.MAX_VALUE;
        int modulations;
        Source(AEItemKey key, long count) { items.put(key, count); }
        long count(AEItemKey key) { return items.getOrDefault(key, 0L); }
        @Override public List<AEItemKey> keys() { return List.copyOf(items.keySet()); }
        @Override public long extract(AEItemKey key, long amount, Actionable mode) {
            long taken = Math.min(amount, count(key));
            if (mode == Actionable.MODULATE) {
                modulations++;
                taken = Math.min(taken, modulationLimit);
                items.put(key, count(key) - taken);
            }
            return taken;
        }
        @Override public long insert(AEItemKey key, long amount) {
            long accepted = Math.min(amount, insertionLimit);
            items.merge(key, accepted, Long::sum);
            return accepted;
        }
    }
}
