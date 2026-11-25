package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderTarget;
import appeng.me.storage.ExternalStorageFacade;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternInputCapacityTest {
    static {
        if (LoadingModList.get() == null) {
            LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void findsTheLastFittingBatchPastAHoleInOrderedInsertion() {
        var first = AEItemKey.of(Items.DIAMOND);
        var repeated = AEItemKey.of(Items.COAL);
        var other = AEItemKey.of(Items.GOLD_INGOT);
        var inputs = List.of(new GenericStack(first, 1), new GenericStack(repeated, 2),
                new GenericStack(other, 1), new GenericStack(repeated, 1));
        var slots = new ItemStackHandler(5);
        slots.setStackInSlot(0, first.toStack(24));
        slots.setStackInSlot(3, other.toStack(1));
        slots.setStackInSlot(4, first.toStack(62));

        // At 22–32 copies gold claims the empty slot needed by the second coal offer.
        // At 33–40 copies the first coal offer claims both empties and gold uses slot 3.
        assertEquals(21, PatternInputCapacity.limitItems(slots, inputs, 32));
        assertEquals(33, PatternInputCapacity.limitItems(slots, inputs, 33));
        assertEquals(40, PatternInputCapacity.limitItems(slots, inputs, Long.MAX_VALUE));
        assertEquals(24, slots.getStackInSlot(0).getCount());
    }

    @Test
    void anIntSizedNativePassLetsAnotherKeyClaimTheLastEmptySlot() {
        var coal = AEItemKey.of(Items.COAL);
        var gold = AEItemKey.of(Items.GOLD_INGOT);
        long coalUnits = (long) Integer.MAX_VALUE + 2;
        int empties = Math.toIntExact(Math.ceilDiv(coalUnits, 64L));
        var goldStack = gold.toStack(1);
        // The inventory has more than an int of actual item room without an array of empty stacks.
        var slots = new ItemStackHandler(0) {
            @Override public int getSlots() { return empties + 1; }
            @Override public ItemStack getStackInSlot(int slot) { return slot == empties ? goldStack : ItemStack.EMPTY; }
        };
        assertEquals(coalUnits, PatternInputCapacity.limitItems(slots, List.of(new GenericStack(coal, 1)), coalUnits));
        assertEquals(0, PatternInputCapacity.limitItems(slots,
                List.of(new GenericStack(coal, coalUnits), new GenericStack(gold, 1)), 1));
    }

    @Test
    void agreesWithNativeInsertionForMixedStackLimitsAndRepeatedKeys() {
        var random = new Random(0x2EA21L);
        var keys = List.of(AEItemKey.of(Items.COAL), AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.ENDER_PEARL));
        for (int trial = 0; trial < 60; trial++) {
            var original = new ItemStackHandler(random.nextInt(2, 12));
            for (int slot = 0; slot < original.getSlots(); slot++) {
                if (random.nextBoolean()) {
                    var key = keys.get(random.nextInt(keys.size()));
                    original.setStackInSlot(slot, key.toStack(random.nextInt(key.getMaxStackSize()) + 1));
                }
            }
            var inputs = new ArrayList<GenericStack>();
            int entries = random.nextInt(2, 7);
            for (int i = 0; i < entries; i++) {
                inputs.add(new GenericStack(keys.get(random.nextInt(keys.size())), random.nextInt(1, 8)));
            }
            int expected = 0;
            for (int copies = 1; copies <= 40; copies++) {
                if (nativeFits(original, inputs, copies)) expected = copies;
            }
            assertEquals(expected, PatternInputCapacity.limitItems(original, inputs, 40), "Inventory " + trial);
        }
    }

    private static boolean nativeFits(ItemStackHandler original, List<GenericStack> inputs, int copies) {
        var inventory = new ItemStackHandler(original.getSlots());
        for (int slot = 0; slot < original.getSlots(); slot++) {
            inventory.setStackInSlot(slot, original.getStackInSlot(slot).copy());
        }
        var target = ExternalStorageFacade.of(inventory);
        for (var input : inputs) {
            long amount = input.amount() * copies;
            if (target.insert(input.what(), amount, Actionable.MODULATE, IActionSource.empty()) != amount) return false;
        }
        return true;
    }

    @Test
    void roundsALongProbeToACompleteRepresentableOfferAndRechecksIt() {
        var probes = new ArrayList<Long>();
        var adapter = new PatternProviderTarget() {
            @Override public long insert(AEKey what, long amount, Actionable mode) {
                assertEquals(Actionable.SIMULATE, mode);
                probes.add(amount);
                return Math.min(amount, Long.MAX_VALUE - 2);
            }
            @Override public boolean containsPatternInput(Set<AEKey> keys) { throw new AssertionError(); }
        };
        long whole = (Long.MAX_VALUE - 2) / 3;
        assertEquals(whole, PatternInputCapacity.probe(adapter, new GenericStack(AEItemKey.of(Items.COAL), 3),
                Long.MAX_VALUE));
        assertEquals(List.of(Long.MAX_VALUE - 1, whole * 3), probes);
    }
}
