package com.atir.molecularmanipulator.ae2;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.world.item.ItemStack;

public final class CreativeStarSignalDiskHandler implements ICellHandler {
    public static final CreativeStarSignalDiskHandler INSTANCE =
            new CreativeStarSignalDiskHandler();

    private CreativeStarSignalDiskHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModContent.CREATIVE_STAR_SIGNAL_DISK.get());
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider saveProvider) {
        return isCell(stack) ? new CreativeStarSignalDiskInventory(stack) : null;
    }
}
