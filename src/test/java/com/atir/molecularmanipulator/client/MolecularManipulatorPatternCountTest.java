package com.atir.molecularmanipulator.client;

import appeng.menu.guisync.GuiSync;
import appeng.menu.slot.AppEngSlot;
import appeng.util.inv.AppEngInternalInventory;
import com.atir.molecularmanipulator.menu.MolecularManipulatorMenu;
import java.lang.reflect.Method;
import java.util.stream.IntStream;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MolecularManipulatorPatternCountTest {
    private static MolecularManipulatorMenu menu(AppEngInternalInventory inventory) throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var menu = ResponsiveScreenTestFixture.allocate(MolecularManipulatorMenu.class);
        ResponsiveScreenTestFixture.set(menu, "patternInventory", inventory);
        ResponsiveScreenTestFixture.set(menu, "patternSlots", IntStream.range(0, inventory.size())
                .mapToObj(slot -> new AppEngSlot(inventory, slot)).toList());
        ResponsiveScreenTestFixture.set(menu, "countedPatternRevision", -1);
        return menu;
    }

    private static void refresh(MolecularManipulatorMenu menu, int revision) throws Exception {
        Method method = MolecularManipulatorMenu.class.getDeclaredMethod("refreshPatternSlotCount", int.class);
        method.setAccessible(true);
        method.invoke(menu, revision);
    }

    private static void page(MolecularManipulatorMenu menu, int page) throws Exception {
        Method method = MolecularManipulatorMenu.class.getDeclaredMethod("applyPage", int.class);
        method.setAccessible(true);
        method.invoke(menu, page);
    }

    @Test
    void switchingToAnEmptyPageKeepsTheWholeMachineTotal() throws Exception {
        // Use real AE2 slots: disabled slots hide their stacks even if the inventory is populated.
        Class.forName(UiRenderRecorder.class.getName());
        var inventory = new AppEngInternalInventory(360);
        inventory.setItemDirect(0, new ItemStack(Items.IRON_INGOT));
        inventory.setItemDirect(1, new ItemStack(Items.COPPER_INGOT, 6));
        var menu = menu(inventory);

        page(menu, 0);
        refresh(menu, 0);
        assertEquals(2, menu.usedPatternSlots, "Count occupied slots, not stack amounts");
        assertTrue(menu.getPatternSlots().get(0).isSlotEnabled());
        for (int targetPage : new int[] {1, 9, 0}) {
            page(menu, targetPage);
            refresh(menu, 0);
            assertEquals(2, menu.usedPatternSlots);
            assertEquals(10, menu.getPageCount());
            assertEquals(targetPage == 0, menu.getPatternSlots().get(0).isSlotEnabled());
            if (targetPage != 0) {
                // Enabled getItem() needs NeoForge's Slot access transformer, absent in unit tests.
                // Disabled real AE2 slots take the early EMPTY branch without that transformer.
                assertFalse(menu.getPatternSlots().get(0).hasItem());
                assertFalse(menu.getPatternSlots().get(1).hasItem());
            }
        }
    }

    @Test
    void hiddenSlotsAreCountedOnOpeningAndAfterInventoryChanges() throws Exception {
        Class.forName(UiRenderRecorder.class.getName());
        var inventory = new AppEngInternalInventory(360);
        inventory.setItemDirect(0, new ItemStack(Items.IRON_INGOT));
        inventory.setItemDirect(36, new ItemStack(Items.COPPER_INGOT));
        inventory.setItemDirect(359, new ItemStack(Items.GOLD_INGOT));
        var menu = menu(inventory);
        page(menu, 1);
        refresh(menu, 0);
        assertEquals(3, menu.usedPatternSlots, "Initial revision zero must count every page");

        inventory.setItemDirect(359, ItemStack.EMPTY);
        refresh(menu, 1);
        assertEquals(2, menu.usedPatternSlots);
        inventory.setItemDirect(358, new ItemStack(Items.GOLD_INGOT));
        refresh(menu, 2);
        assertEquals(3, menu.usedPatternSlots);

        // A search view enables only matching slots, possibly none, but must not affect the total.
        for (var slot : menu.getPatternSlots()) slot.setSlotEnabled(false);
        menu.patternSearchActive = true;
        menu.patternSearchResultCount = 0;
        refresh(menu, 3);
        assertEquals(3, menu.usedPatternSlots);
        assertEquals(0, menu.getPatternSlots().stream().filter(AppEngSlot::hasItem).count());

        inventory.setItemDirect(0, ItemStack.EMPTY);
        inventory.setItemDirect(36, ItemStack.EMPTY);
        inventory.setItemDirect(358, ItemStack.EMPTY);
        refresh(menu, 4);
        assertEquals(0, menu.usedPatternSlots);
        assertEquals(4, menu.patternRevision);
    }

    @Test
    void wholeMachineCountIsSyncedIndependentlyOfSearchResults() throws Exception {
        var totalSync = MolecularManipulatorMenu.class.getField("usedPatternSlots").getAnnotation(GuiSync.class);
        var searchSync = MolecularManipulatorMenu.class.getField("patternSearchResultCount").getAnnotation(GuiSync.class);
        assertNotNull(totalSync);
        assertNotEquals(searchSync.value(), totalSync.value());
    }
}
