package com.atir.molecularmanipulator.item;

import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;

/** Keeps vanilla item consumption, sounds and NeoForge's multi-place protection events. */
public final class StarSeaCalculationMainframeItem extends BlockItem {
    public StarSeaCalculationMainframeItem(StarSeaCalculationMainframeBlock block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        return ((StarSeaCalculationMainframeBlock) getBlock()).placeBuilding(context, state);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        super.appendHoverText(stack, context, lines, flag);
        lines.add(Component.translatable("tooltip.molecularmanipulator.star_sea_calculation_mainframe.place").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.molecularmanipulator.star_sea_calculation_mainframe.operation").withStyle(ChatFormatting.DARK_AQUA));
        lines.add(Component.translatable("tooltip.molecularmanipulator.star_sea_calculation_mainframe.network").withStyle(ChatFormatting.GRAY));
    }
}
