package com.atir.molecularmanipulator.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Dedicated placement key leaves normal right-click available to the other hand's tool. */
public final class SimulationLanternItem extends BlockItem {
    public SimulationLanternItem(Block block, Properties properties) { super(block, properties); }
    @Override public InteractionResult useOn(UseOnContext context) { return InteractionResult.PASS; }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.simulation_lantern").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.molecularmanipulator.simulation_lantern_capture").withStyle(ChatFormatting.DARK_GREEN));
    }
}
