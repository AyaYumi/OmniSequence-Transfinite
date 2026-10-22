package com.atir.molecularmanipulator.ae2;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.world.item.ItemStack;

public final class CreativeNegentropyInfoCellHandler implements ICellHandler {
    public static final CreativeNegentropyInfoCellHandler INSTANCE =
            new CreativeNegentropyInfoCellHandler();

    private CreativeNegentropyInfoCellHandler() {
    }

    @Override
    public boolean isCell(ItemStack stack) {
        return !stack.isEmpty()
                && stack.is(ModContent.CREATIVE_NEGENTROPY_INFORMATION_CELL.get());
    }

    @Override
    public StorageCell getCellInventory(ItemStack stack, ISaveProvider saveProvider) {
        return isCell(stack) ? new CreativeNegentropyInfoCellInventory(stack) : null;
    }
}
