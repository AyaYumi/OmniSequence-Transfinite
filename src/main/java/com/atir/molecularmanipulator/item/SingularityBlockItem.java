package com.atir.molecularmanipulator.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

public final class SingularityBlockItem extends BlockItem {
    private final boolean controller;

    public SingularityBlockItem(Block block, Properties properties, boolean controller) {
        super(block, properties);
        this.controller = controller;
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (getBlock() == com.atir.molecularmanipulator.registry.SingularityContent.COMPACT.get()) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.singularity.compact").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.singularity.compact_network").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (controller) {
            tooltip.add(Component.translatable("tooltip.molecularmanipulator.singularity.lore").withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.singularity.palette").withStyle(ChatFormatting.GRAY));
    }
}
