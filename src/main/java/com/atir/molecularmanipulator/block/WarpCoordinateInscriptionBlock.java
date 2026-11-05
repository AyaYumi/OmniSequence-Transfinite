package com.atir.molecularmanipulator.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

/** Fixed, carved coordinate symbols in the ruin masonry. No text or ticking entity. */
public final class WarpCoordinateInscriptionBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<WarpCoordinateInscriptionBlock> CODEC = simpleCodec(WarpCoordinateInscriptionBlock::new);
    public static final IntegerProperty GLYPH = IntegerProperty.create("glyph", 0, 2);

    public WarpCoordinateInscriptionBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(GLYPH, 0));
    }

    @Override
    protected MapCodec<WarpCoordinateInscriptionBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, GLYPH);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        var stack = super.getCloneItemStack(level, pos, state);
        stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(GLYPH, state));
        return stack;
    }
}
