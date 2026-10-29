package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.stacks.AEItemKey;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MolecularItemCapacityTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void longMaxRequestVisitsEachSlotOnceAndDoesNotChangeContents() {
        var inventory = new CountingInventory(21);
        assertEquals(21 * 64, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE));
        assertEquals(21, inventory.probes);
        for (int slot = 0; slot < inventory.size(); slot++) assertTrue(inventory.getStackInSlot(slot).isEmpty());
    }

    @Test
    void requestStopsAsSoonAsEnoughSpaceHasBeenFound() {
        var inventory = new CountingInventory(21);
        assertEquals(80, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.OAK_PLANKS), 80));
        assertEquals(2, inventory.probes);
    }

    @Test
    void combinesPartialMatchingStacksAndEmptySlotsOnly() {
        var inventory = new CountingInventory(3);
        inventory.setItemDirect(0, new ItemStack(Items.OAK_PLANKS, 60));
        inventory.setItemDirect(1, new ItemStack(Items.STONE, 1));
        assertEquals(68, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.OAK_PLANKS), 1000));
        assertEquals(60, inventory.getStackInSlot(0).getCount());
        assertEquals(1, inventory.getStackInSlot(1).getCount());
        assertTrue(inventory.getStackInSlot(2).isEmpty());
    }

    @Test
    void obeysExistingSlotFiltersAndLimits() {
        var inventory = new CountingInventory(3);
        inventory.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
            @Override
            public boolean allowInsert(appeng.api.inventories.InternalInventory inv, int slot, ItemStack stack) {
                return slot == 1;
            }
        });
        inventory.setMaxStackSize(1, 10);
        assertEquals(10, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE));
    }

    @Test
    void respectsComponentIdentityAndUnstackableItems() {
        var inventory = new CountingInventory(2);
        var damaged = new ItemStack(Items.DIAMOND_PICKAXE);
        damaged.set(DataComponents.DAMAGE, 7);
        inventory.setItemDirect(0, damaged);
        assertEquals(1, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.DIAMOND_PICKAXE), 1000));
        assertEquals(7, inventory.getStackInSlot(0).get(DataComponents.DAMAGE));
    }

    @Test
    void delegatesUpgradedCapacityToTheActualSlotImplementation() {
        var inventory = new CountingInventory(3) {
            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                assertTrue(simulate);
                probes++;
                int accepted = Math.min(stack.getCount(), 1_000_000 - getStackInSlot(slot).getCount());
                return stack.copyWithCount(stack.getCount() - accepted);
            }
        };
        inventory.setItemDirect(0, new ItemStack(Items.OAK_PLANKS, 999_999));
        assertEquals(2_000_001, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.OAK_PLANKS), Long.MAX_VALUE));
        assertEquals(3, inventory.probes);
        assertEquals(999_999, inventory.getStackInSlot(0).getCount());
    }

    @Test
    void zeroRequestDoesNotTouchAnySlot() {
        var inventory = new CountingInventory(3);
        assertEquals(0, MolecularItemCapacity.simulate(inventory, AEItemKey.of(Items.OAK_PLANKS), 0));
        assertEquals(0, inventory.probes);
    }

    private static class CountingInventory extends AppEngInternalInventory {
        int probes;

        CountingInventory(int slots) { super(null, slots); }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            assertTrue(simulate);
            probes++;
            return super.insertItem(slot, stack, simulate);
        }
    }
}
