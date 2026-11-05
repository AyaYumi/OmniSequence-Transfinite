package com.atir.molecularmanipulator.mixin;

import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** AE2's first-tick queue is independent of vanilla block-entity tickers. */
@Mixin(AENetworkedBlockEntity.class)
public abstract class BuildingNodePauseMixin {
    @Inject(method = "onReady", at = @At("HEAD"), cancellable = true)
    private void folding$deferNode(CallbackInfo callback) {
        if (BuildingTransfer.restoring((AENetworkedBlockEntity) (Object) this)) callback.cancel();
    }

    /** Queue initialization once, after all archived contents have been restored. */
    @Mixin(appeng.blockentity.AEBaseBlockEntity.class)
    public abstract static class InitQueue {
        @Inject(method = "scheduleInit", at = @At("HEAD"), cancellable = true)
        private void folding$deferQueue(CallbackInfo callback) {
            if (BuildingTransfer.restoring((appeng.blockentity.AEBaseBlockEntity) (Object) this)) callback.cancel();
        }
    }
}
