package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Server-side 3x3x3 structure owner for the Negative Entropy Engine.
 *
 * <p>The formed flag is synced to the client, where the block entity renderer
 * replaces the loose shell with one continuous 3x3x3 panel.</p>
 */
public final class NegativeEntropyEngineCoreBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 10;

    private boolean formed;
    private int tickCounter;

    public NegativeEntropyEngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.NEGATIVE_ENTROPY_ENGINE_CORE_BE.get(), pos, state);
    }

    public boolean isFormed() {
        return formed;
    }

    public void serverTick() {
        if (++tickCounter < CHECK_INTERVAL) {
            return;
        }
        tickCounter = 0;
        refreshStructure();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            refreshStructure();
        }
    }

    private void refreshStructure() {
        var level = getLevel();
        if (level == null || level.isClientSide) {
            return;
        }

        boolean complete = true;
        for (int dx = -1; dx <= 1 && complete; dx++) {
            for (int dy = -1; dy <= 1 && complete; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    var state = level.getBlockState(getBlockPos().offset(dx, dy, dz));
                    boolean sideWindow = dy == 0 && Math.abs(dx) + Math.abs(dz) == 1;
                    boolean valid = sideWindow
                            ? state.is(ModContent.NEGATIVE_ENTROPY_OBSERVATION_WINDOW.get())
                            : state.is(ModContent.NEGATIVE_ENTROPY_ENGINE_CASING.get());
                    if (!valid) {
                        complete = false;
                        break;
                    }
                }
            }
        }

        if (complete == formed) {
            return;
        }
        formed = complete;
        setChanged();
        level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("formed", formed);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        formed = tag.getBoolean("formed");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
