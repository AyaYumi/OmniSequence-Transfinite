package com.atir.molecularmanipulator.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.me.storage.ExternalStorageFacade;
import com.appliedenhancements.Config;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class Ae2CompatibilityGameTests {
    @GameTest(template = "multiblock_dismantle_empty", timeoutTicks = 40)
    public static void indexedExtractionRetainsAe2TypeRecheck(GameTestHelper helper) {
        boolean previousIndex = Config.ENABLE_STORAGE_BUS_SLOT_INDEX.get();
        boolean previousObserved = Config.ENABLE_IO_BUS_OPTIMIZATION.get();
        var requested = AEItemKey.of(Items.DIAMOND);
        try {
            for (boolean index : new boolean[] {false, true}) {
                for (boolean observed : new boolean[] {false, true}) {
                    Config.ENABLE_STORAGE_BUS_SLOT_INDEX.set(index);
                    Config.ENABLE_IO_BUS_OPTIMIZATION.set(observed);
                    var handler = new SwitchingHandler();
                    var facade = ExternalStorageFacade.of(handler);
                    var listed = new KeyCounter();
                    facade.getAvailableStacks(listed);
                    helper.assertTrue(listed.get(requested) == 64, "Index must list the original key");
                    facade.getStackInSlot(0);
                    helper.assertTrue(facade.extract(requested, 128, Actionable.SIMULATE, IActionSource.empty()) == 64
                                    && handler.diamonds == 64 && handler.gold == 64,
                            "Simulation must leave both types intact");
                    facade.getStackInSlot(0);
                    helper.assertTrue(facade.extract(requested, 128, Actionable.MODULATE, IActionSource.empty()) == 64
                                    && handler.diamonds == 0 && handler.gold == 64,
                            "Extraction must stop when the slot switches type: index=" + index + ", observed=" + observed);
                    helper.assertTrue(facade.extract(requested, 1, Actionable.MODULATE, IActionSource.empty()) == 0
                                    && handler.gold == 64, "Stale candidate cannot extract the replacement key");
                }
            }
        } finally {
            Config.ENABLE_STORAGE_BUS_SLOT_INDEX.set(previousIndex);
            Config.ENABLE_IO_BUS_OPTIMIZATION.set(previousObserved);
        }
        System.out.println("AE2_COMPAT_EXTRACTION_PASS indexed=true observed=true fallback=true typeSwitch=true simulation=true");
        helper.succeed();
    }

    private static final class SwitchingHandler implements IItemHandler {
        private int diamonds = 64;
        private int gold = 64;

        @Override public int getSlots() { return 1; }
        @Override public ItemStack getStackInSlot(int slot) {
            return diamonds > 0 ? new ItemStack(Items.DIAMOND, diamonds) : new ItemStack(Items.GOLD_INGOT, gold);
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            var available = getStackInSlot(slot);
            int taken = Math.min(Math.max(amount, 0), available.getCount());
            if (!simulate) {
                if (diamonds > 0) diamonds -= taken;
                else gold -= taken;
            }
            return available.copyWithCount(taken);
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
