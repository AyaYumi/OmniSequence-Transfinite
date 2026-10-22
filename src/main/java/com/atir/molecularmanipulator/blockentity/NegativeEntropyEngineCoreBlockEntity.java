package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.block.NegativeEntropyEngineCasingBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyEngineCoreBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyObservationWindowBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side 3x3x3 structure owner for the Negative Entropy Engine.
 *
 * <p>The formed flag controls all 27 blocks' FORMED blockstate property.
 * The client renders a continuous shell over the casing while leaving the
 * four observation windows visible.</p>
 */
public final class NegativeEntropyEngineCoreBlockEntity extends BlockEntity {
    private static final int CHECK_INTERVAL = 10;

    private boolean formed;
    private boolean needsStateSync = true;
    private int tickCounter;

    public NegativeEntropyEngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.NEGATIVE_ENTROPY_ENGINE_CORE_BE.get(), pos, state);
    }

    /**
     * @return whether the blockstate currently shows a formed engine. Reads the
     *         blockstate rather than the saved flag so callers always see the
     *         same state as the shell models.
     */
    public boolean isFormed() {
        return getBlockState().getValue(NegativeEntropyEngineCoreBlock.FORMED);
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
            // Existing worlds may still have the pre-FORMED blockstate defaults
            // (formed=false) while the block entity NBT says the engine is formed.
            // Do not rewrite blockstates while the chunk is still loading; run
            // the forced pass on the next server tick instead.
            needsStateSync = true;
            tickCounter = CHECK_INTERVAL - 1;
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

        boolean changed = complete != formed;
        if (!changed && !needsStateSync) {
            return;
        }
        needsStateSync = false;
        formed = complete;
        updateAllBlockStates();
        if (changed) {
            setChanged();
        }
    }

    /**
     * Updates the FORMED blockstate property on all 27 blocks in the structure.
     */
    private void updateAllBlockStates() {
        var level = getLevel();
        if (level == null) {
            return;
        }

        applyFormed(level, getBlockPos(), formed);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    applyFormed(level, getBlockPos().offset(dx, dy, dz), formed);
                }
            }
        }
    }

    /**
     * Drops the shell back to its unformed look when the core disappears, so no
     * shell block keeps a stale FORMED state after the engine is taken apart.
     */
    public static void resetShell(Level level, BlockPos corePos) {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) {
                        continue;
                    }
                    applyFormed(level, corePos.offset(dx, dy, dz), false);
                }
            }
        }
    }

    private static void applyFormed(Level level, BlockPos pos, boolean formed) {
        var state = level.getBlockState(pos);
        BlockState updated = withFormed(state, formed);
        if (updated != null) {
            level.setBlock(pos, updated, 3);
        }
    }

    @Nullable
    private static BlockState withFormed(BlockState state, boolean formed) {
        if (state.hasProperty(NegativeEntropyEngineCoreBlock.FORMED)) {
            return state.getValue(NegativeEntropyEngineCoreBlock.FORMED) == formed
                    ? null
                    : state.setValue(NegativeEntropyEngineCoreBlock.FORMED, formed);
        }
        if (state.hasProperty(NegativeEntropyEngineCasingBlock.FORMED)) {
            return state.getValue(NegativeEntropyEngineCasingBlock.FORMED) == formed
                    ? null
                    : state.setValue(NegativeEntropyEngineCasingBlock.FORMED, formed);
        }
        if (state.hasProperty(NegativeEntropyObservationWindowBlock.FORMED)) {
            return state.getValue(NegativeEntropyObservationWindowBlock.FORMED) == formed
                    ? null
                    : state.setValue(NegativeEntropyObservationWindowBlock.FORMED, formed);
        }
        return null;
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
}
