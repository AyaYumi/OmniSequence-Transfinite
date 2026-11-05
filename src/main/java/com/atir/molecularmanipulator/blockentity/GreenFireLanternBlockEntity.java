package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.GreenFireLanternContent;
import com.atir.molecularmanipulator.world.GreenFireLanternLights;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No inventory or ticking: the blockstate stores its flame, this BE indexes loaded light sources. */
public final class GreenFireLanternBlockEntity extends BlockEntity {
    public GreenFireLanternBlockEntity(BlockPos pos, BlockState state) {
        super(GreenFireLanternContent.BLOCK_ENTITY.get(), pos, state);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null) GreenFireLanternLights.add(level, worldPosition);
    }
    @Override public void setRemoved() {
        if (level != null) GreenFireLanternLights.remove(level, worldPosition);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (level != null) GreenFireLanternLights.remove(level, worldPosition);
        super.onChunkUnloaded();
    }
}
