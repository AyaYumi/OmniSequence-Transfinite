package com.atir.molecularmanipulator.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class TaixuBlockItem extends BlockItem {
    private final boolean controller;

    public TaixuBlockItem(Block block, Properties properties, boolean controller) {
        super(block, properties);
        this.controller = controller;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (controller) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.taixu.lore").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.taixu.palette").withStyle(ChatFormatting.GRAY));
    }
}
