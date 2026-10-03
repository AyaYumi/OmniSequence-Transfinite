package com.atir.molecularmanipulator.block;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Six-way placement supports crowns, hanging finials and horizontal ring ornaments. */
public final class SingularitySpireBlock extends Block {
    private static final Map<Direction, VoxelShape> SHAPES = shapes();

    public SingularitySpireBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DirectionalBlock.FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(DirectionalBlock.FACING, context.getClickedFace());
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(DirectionalBlock.FACING, rotation.rotate(state.getValue(DirectionalBlock.FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(DirectionalBlock.FACING, mirror.mirror(state.getValue(DirectionalBlock.FACING)));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(DirectionalBlock.FACING));
    }

    private static Map<Direction, VoxelShape> shapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        // The same five stepped sections are used by the native JSON model.
        double[][] sections = {{2, 0, 14, 3}, {4, 3, 12, 5}, {5, 5, 11, 10}, {6, 10, 10, 14}, {7, 14, 9, 16}};
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] s : sections) {
                double lo = s[0], a = s[1], hi = s[2], b = s[3];
                VoxelShape section = switch (facing) {
                    case UP -> box(lo, a, lo, hi, b, hi);
                    case DOWN -> box(lo, 16 - b, lo, hi, 16 - a, hi);
                    case NORTH -> box(lo, lo, 16 - b, hi, hi, 16 - a);
                    case SOUTH -> box(lo, lo, a, hi, hi, b);
                    case WEST -> box(16 - b, lo, lo, 16 - a, hi, hi);
                    case EAST -> box(a, lo, lo, b, hi, hi);
                };
                shape = Shapes.or(shape, section);
            }
            shapes.put(facing, shape);
        }
        return shapes;
    }
}
