package com.atir.molecularmanipulator.mixin;

import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.blockentity.grid.AENetworkInvBlockEntity;
import appeng.blockentity.grid.AENetworkPowerBlockEntity;
import appeng.me.helpers.IGridConnectedBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2's first-tick queue is independent of vanilla block-entity tickers. */
@Mixin({AENetworkBlockEntity.class, AENetworkInvBlockEntity.class, AENetworkPowerBlockEntity.class})
public abstract class BuildingNodePauseMixin {
    @Unique private CompoundTag folding$pendingProxy;

    @Inject(method = "loadTag", at = @At("RETURN"), remap = false)
    private void folding$rememberPendingOwner(CompoundTag data, CallbackInfo callback) {
        folding$pendingProxy = data.contains("proxy", Tag.TAG_COMPOUND) ? data.getCompound("proxy").copy() : null;
    }

    @Inject(method = "saveAdditional", at = @At("RETURN"))
    private void folding$savePendingOwner(CompoundTag data, CallbackInfo callback) {
        // ManagedGridNode.saveToNBT writes nothing until create(). A transfer must be durable BEFORE create().
        if (((IGridConnectedBlockEntity) (Object) this).getMainNode().getNode() == null && folding$pendingProxy != null)
            data.put("proxy", folding$pendingProxy.copy());
    }

    @Inject(method = "onReady", at = @At("HEAD"), cancellable = true, remap = false)
    private void folding$deferNode(CallbackInfo callback) {
        if (BuildingTransfer.restoring((BlockEntity) (Object) this)) callback.cancel();
    }

    /** Queue initialization once, after all archived contents have been restored. */
    @Mixin(appeng.blockentity.AEBaseBlockEntity.class)
    public abstract static class InitQueue {
        @Inject(method = "scheduleInit", at = @At("HEAD"), cancellable = true, remap = false)
        private void folding$deferQueue(CallbackInfo callback) {
            if (BuildingTransfer.restoring((appeng.blockentity.AEBaseBlockEntity) (Object) this)) callback.cancel();
        }
    }
}
