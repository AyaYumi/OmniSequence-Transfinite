package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.SingularityContent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class SingularityCoreBlockEntity extends BlockEntity {
    public SingularityCoreBlockEntity(BlockPos pos, BlockState state) {
        super(SingularityContent.CORE_BE.get(), pos, state);
    }
}
