package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.TaixuContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class TaixuCoreBlockEntity extends BlockEntity {
    public TaixuCoreBlockEntity(BlockPos pos, BlockState state) {
        super(TaixuContent.CORE_BE.get(), pos, state);
    }
}
