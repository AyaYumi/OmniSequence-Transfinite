package com.atir.molecularmanipulator.item;

import appeng.api.stacks.GenericStack;
import appeng.items.storage.StorageCellTooltipComponent;
import com.atir.molecularmanipulator.ae2.NegativeEntropyInfoKey;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

public final class CreativeNegentropyInfoCellItem extends Item {
    public CreativeNegentropyInfoCellItem(Properties properties) {
        super(properties);
    }

    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new StorageCellTooltipComponent(
                List.of(),
                List.of(new GenericStack(NegativeEntropyInfoKey.INSTANCE, Integer.MAX_VALUE)),
                false,
                true));
    }
}
