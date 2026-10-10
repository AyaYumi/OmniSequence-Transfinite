package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.GravityCrystalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.*;
import org.joml.Vector3f;

import java.util.EnumMap;
import java.util.Map;

/** FACING points out from the mounting surface; attraction works in the opposite direction. */
public final class GravityCrystalBlock extends Block implements EntityBlock {
    public static final BooleanProperty REPEL = BooleanProperty.create("repel");
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    private static final Map<Direction, VoxelShape> SHAPES = shapes();
    private static final DustParticleOptions FIELD_PARTICLE =
            new DustParticleOptions(new Vector3f(.43F, .38F, 1F), .65F);

    public GravityCrystalBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(DirectionalBlock.FACING, Direction.UP)
                .setValue(REPEL, false).setValue(POWERED, false));
    }

    @Override
    public void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(DirectionalBlock.FACING, REPEL, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(DirectionalBlock.FACING, context.getClickedFace())
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighbor,
            BlockPos neighborPos, boolean moved) {
        if (!level.isClientSide()) {
            boolean powered = level.hasNeighborSignal(pos);
            if (state.getValue(POWERED) != powered) level.setBlock(pos, state.setValue(POWERED, powered), 3);
        }
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos,
            Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!player.getItemInHand(hand).isEmpty() || !player.mayBuild()) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            boolean repel = !state.getValue(REPEL);
            level.setBlock(pos, state.setValue(REPEL, repel), 3);
            player.displayClientMessage(Component.translatable("message.molecularmanipulator.gravity_crystal."
                    + (repel ? "repel" : "attract")), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GravityCrystalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : (world, pos, current, entity) -> {
            if (entity instanceof GravityCrystalBlockEntity crystal) crystal.serverTick();
        };
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

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(POWERED)) return;
        var direction = state.getValue(DirectionalBlock.FACING);
        var axis = new org.joml.Vector3f(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        var start = pos.getCenter().add(direction.getStepX() * 1.1, direction.getStepY() * 1.1, direction.getStepZ() * 1.1);
        float sign = state.getValue(REPEL) ? 1 : -1;
        level.addParticle(FIELD_PARTICLE, start.x + (random.nextDouble() - .5) * .4,
                start.y + (random.nextDouble() - .5) * .4, start.z + (random.nextDouble() - .5) * .4,
                axis.x * sign * .08, axis.y * sign * .08, axis.z * sign * .08);
    }

    private static Map<Direction, VoxelShape> shapes() {
        var shapes = new EnumMap<Direction, VoxelShape>(Direction.class);
        double[][] sections = {{4, 0, 12, 2}, {5, 2, 11, 13}, {7, 13, 9, 15}};
        for (var facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (var s : sections) {
                double lo = s[0], a = s[1], hi = s[2], b = s[3];
                shape = Shapes.or(shape, switch (facing) {
                    case UP -> box(lo, a, lo, hi, b, hi);
                    case DOWN -> box(lo, 16 - b, lo, hi, 16 - a, hi);
                    case NORTH -> box(lo, lo, 16 - b, hi, hi, 16 - a);
                    case SOUTH -> box(lo, lo, a, hi, hi, b);
                    case WEST -> box(16 - b, lo, lo, 16 - a, hi, hi);
                    case EAST -> box(a, lo, lo, b, hi, hi);
                });
            }
            shapes.put(facing, shape);
        }
        return shapes;
    }
}
