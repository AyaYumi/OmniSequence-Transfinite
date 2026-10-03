package com.atir.molecularmanipulator.block;

import com.atir.molecularmanipulator.blockentity.SingularityCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The only palette part needing a renderer; deliberately has no server ticker. */
public final class SingularityCoreBlock extends Block implements EntityBlock {
    public SingularityCoreBlock(Properties properties) { super(properties); }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SingularityCoreBlockEntity(pos, state);
    }
}
