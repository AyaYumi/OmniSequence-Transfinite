package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.building.BuildingTransfer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

/** Incomplete folded buildings cannot tick or be edited while their journal owns the site. */
@Mixin(LevelChunk.class)
public abstract class BuildingChunkLockMixin {
    @Shadow public abstract Level getLevel();
    @Inject(method = "setBlockState", at = @At("HEAD"), cancellable = true)
    private void folding$lock(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> callback) {
        if (BuildingTransfer.locked(getLevel(), pos) && !BuildingTransfer.mayMutate(pos)
                && !(state.isAir() && BuildingTransfer.mayClearObstruction(getLevel(), pos))) callback.setReturnValue(null);
    }
    /** Neighbouring pipes must not extract partly restored inventories and then receive them again on journal replay. */
    @Mixin(net.neoforged.neoforge.capabilities.BlockCapability.class)
    public abstract static class Capabilities {
        @Inject(method = "getCapability", at = @At("HEAD"), cancellable = true, remap = false)
        private void folding$hideUntilCommitted(Level level, BlockPos pos, BlockState state, BlockEntity entity,
                Object context, CallbackInfoReturnable<Object> callback) {
            if (BuildingTransfer.locked(level, pos)) callback.setReturnValue(null);
        }
    }

    @Mixin(targets = "net.minecraft.world.level.chunk.LevelChunk$BoundTickingBlockEntity")
    public abstract static class Ticker {
        @Shadow @Final private BlockEntity blockEntity;
        @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
        private void folding$pause(CallbackInfo callback) {
            if (BuildingTransfer.restoring(blockEntity)) callback.cancel();
        }
    }
}
