package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * A budding parent rock that grows the negative-entropy quartz family on its
 * exposed faces.  The growth order mirrors AE2's certus quartz:
 * small bud -> medium bud -> large bud -> cluster.
 */
public final class NegativeEntropyBuddingQuartzBlock extends Block {
    private static final Direction[] DIRECTIONS = Direction.values();

    private final @Nullable Supplier<Block> nextStage;
    private final int decayChance;

    public NegativeEntropyBuddingQuartzBlock(
            Properties properties,
            @Nullable Supplier<Block> nextStage,
            int decayChance
    ) {
        super(properties);
        this.nextStage = nextStage;
        this.decayChance = decayChance;
    }

    @Override
    public PushReaction getPistonPushReaction(BlockState state) {
        return PushReaction.DESTROY;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(5) != 0) {
            return;
        }

        Direction direction = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos targetPos = pos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);
        BlockState grownState = null;

        if (canClusterGrowAtState(targetState)) {
            grownState = ModContent.NEGATIVE_ENTROPY_QUARTZ_BUD_SMALL.get().defaultBlockState();
        } else if (targetState.is(ModContent.NEGATIVE_ENTROPY_QUARTZ_BUD_SMALL.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            grownState = ModContent.NEGATIVE_ENTROPY_QUARTZ_BUD_MEDIUM.get().defaultBlockState();
        } else if (targetState.is(ModContent.NEGATIVE_ENTROPY_QUARTZ_BUD_MEDIUM.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            grownState = ModContent.NEGATIVE_ENTROPY_QUARTZ_BUD_LARGE.get().defaultBlockState();
        } else if (targetState.is(ModContent.NEGATIVE_ENTROPY_QUARTZ_BUD_LARGE.get())
                && targetState.getValue(AmethystClusterBlock.FACING) == direction) {
            grownState = ModContent.NEGATIVE_ENTROPY_QUARTZ_CLUSTER.get().defaultBlockState();
        }

        if (grownState == null) {
            return;
        }

        boolean waterlogged = targetState.getFluidState().getType() == Fluids.WATER
                && targetState.getFluidState().getAmount() == 8;
        grownState = grownState
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, waterlogged);
        level.setBlockAndUpdate(targetPos, grownState);

        // A successful growth can cost the parent rock one quality step.
        // Flawless -> flawed is rarer; flawed -> chipped -> damaged is faster.
        if (nextStage != null && decayChance > 0 && random.nextInt(decayChance) == 0) {
            level.setBlockAndUpdate(pos, nextStage.get().defaultBlockState());
        }
    }

    private static boolean canClusterGrowAtState(BlockState state) {
        return state.isAir() || state.is(Blocks.WATER) && state.getFluidState().getAmount() == 8;
    }
}
