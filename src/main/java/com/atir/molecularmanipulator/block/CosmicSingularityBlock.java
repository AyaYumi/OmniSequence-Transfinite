package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.CosmicSingularityBlockEntity;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.world.WhiteHoleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** A placeable black hole or white hole rendered against the world scene. */
public final class CosmicSingularityBlock extends Block implements EntityBlock {
    public enum Kind { BLACK_HOLE, WHITE_HOLE }

    private final Kind kind;

    public CosmicSingularityBlock(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
            LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof ServerPlayer player
                && level.getBlockEntity(pos) instanceof CosmicSingularityBlockEntity singularity) {
            singularity.bindToPlayerTeam(player);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CosmicSingularityBlockEntity(pos, state);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState replacement, boolean moved) {
        if (!state.is(replacement.getBlock()) && level instanceof ServerLevel server) {
            if (kind == Kind.WHITE_HOLE) WhiteHoleRegistry.unregister(server, pos);
            MultiblockChunkLoading.release(server, pos);
        }
        super.onRemove(state, level, pos, replacement, moved);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide() ? null : (world, pos, current, entity) -> {
            if (entity instanceof CosmicSingularityBlockEntity singularity) singularity.serverTick();
        };
    }
}
