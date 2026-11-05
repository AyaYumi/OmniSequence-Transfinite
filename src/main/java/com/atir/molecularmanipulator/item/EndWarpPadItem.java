package com.atir.molecularmanipulator.item;

import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/** The approved model's core sits one block above its physical base. */
public final class EndWarpPadItem extends BlockItem {
    public EndWarpPadItem(Block block, Properties properties) { super(block, properties); }

    @Override
    public BlockPlaceContext updatePlacementContext(BlockPlaceContext context) {
        return BlockPlaceContext.at(context, context.getClickedPos().above(), Direction.UP);
    }
}
