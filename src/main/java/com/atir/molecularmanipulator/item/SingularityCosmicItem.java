package com.atir.molecularmanipulator.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

/** A compact inventory representation of one of the building's two singularities. */
public final class SingularityCosmicItem extends BlockItem {
    private final String tooltipKey;
    private final ChatFormatting tooltipColor;

    public SingularityCosmicItem(Block block, String tooltipKey, ChatFormatting tooltipColor, Properties properties) {
        super(block, properties);
        this.tooltipKey = tooltipKey;
        this.tooltipColor = tooltipColor;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.molecularmanipulator." + tooltipKey)
                .withStyle(tooltipColor));
    }
}
