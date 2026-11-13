package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.SimulationLanternContent;
import com.atir.molecularmanipulator.world.SimulationLanternLights;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** No inventory or ticking: the blockstate stores its flame, this BE indexes loaded light sources. */
public final class SimulationLanternBlockEntity extends BlockEntity {
    public SimulationLanternBlockEntity(BlockPos pos, BlockState state) {
        super(SimulationLanternContent.BLOCK_ENTITY.get(), pos, state);
    }
    @Override public void onLoad() {
        super.onLoad();
        if (level != null) SimulationLanternLights.add(level, worldPosition);
    }
    @Override public void setRemoved() {
        if (level != null) SimulationLanternLights.remove(level, worldPosition);
        super.setRemoved();
    }
    @Override public void onChunkUnloaded() {
        if (level != null) SimulationLanternLights.remove(level, worldPosition);
        super.onChunkUnloaded();
    }
}
