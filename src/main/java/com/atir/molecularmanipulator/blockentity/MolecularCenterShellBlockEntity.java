package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.util.AECableType;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class MolecularCenterShellBlockEntity extends BlockEntity implements IInWorldGridNodeHost {
    private static final String CONTROLLER_POS_TAG = "molecular_center_controller_pos";
    private BlockPos controllerPos;

    public MolecularCenterShellBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.MOLECULAR_CENTER_SHELL_BE.get(), pos, state);
    }

    public void setControllerPos(BlockPos controllerPos) {
        if (controllerPos != null && controllerPos.equals(this.controllerPos)
                || controllerPos == null && this.controllerPos == null) {
            return;
        }
        this.controllerPos = controllerPos;
        setChanged();
        Level level = getLevel();
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public IGridNode getGridNode(Direction direction) {
        if (controllerPos == null || getLevel() == null) {
            return null;
        }
        var controller = getLevel().getBlockEntity(controllerPos);
        if (controller instanceof MolecularCenterBlockEntity center && center.isFormed()) {
            return center.getGridNode(direction);
        }
        return null;
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.SMART;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (controllerPos != null) {
            tag.putLong(CONTROLLER_POS_TAG, controllerPos.asLong());
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        controllerPos = tag.contains(CONTROLLER_POS_TAG, CompoundTag.TAG_LONG)
                ? BlockPos.of(tag.getLong(CONTROLLER_POS_TAG))
                : null;
    }
}
