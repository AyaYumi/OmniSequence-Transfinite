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
    @Mixin(value = {
            net.minecraftforge.common.capabilities.CapabilityProvider.class,
            appeng.blockentity.AEBaseInvBlockEntity.class,
            com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity.class,
            com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.class
    }, remap = false)
    public abstract static class Capabilities {
        @Inject(method = "getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
                at = @At("HEAD"), cancellable = true, remap = false)
        private <T> void folding$hideUntilCommitted(net.minecraftforge.common.capabilities.Capability<T> capability,
                net.minecraft.core.Direction side,
                CallbackInfoReturnable<net.minecraftforge.common.util.LazyOptional<T>> callback) {
            if ((Object) this instanceof BlockEntity entity && BuildingTransfer.restoring(entity)) {
                callback.setReturnValue(net.minecraftforge.common.util.LazyOptional.empty());
            }
        }
    }

    /** AE2's powered base answers both capability overloads without delegating to its inventory base. */
    @Mixin(value = appeng.blockentity.powersink.AEBasePoweredBlockEntity.class, remap = false)
    public abstract static class PowerCapabilities {
        @Inject(method = {
                "getCapability(Lnet/minecraftforge/common/capabilities/Capability;)Lnet/minecraftforge/common/util/LazyOptional;",
                "getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;"
        }, at = @At("HEAD"), cancellable = true, remap = false)
        private <T> void folding$hidePowerUntilCommitted(
                CallbackInfoReturnable<net.minecraftforge.common.util.LazyOptional<T>> callback) {
            if (BuildingTransfer.restoring((BlockEntity) (Object) this)) {
                callback.setReturnValue(net.minecraftforge.common.util.LazyOptional.empty());
            }
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
