package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import java.util.function.BooleanSupplier;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Accelerate chunk gameplay, not ticket expiry, generation scheduling, unloading or packet tracking. */
@Mixin(ServerChunkCache.class)
public abstract class DetachedSignalChunkTickMixin {
    @Shadow @Final public ServerLevel level;

    @Shadow private void tickChunks() { throw new AssertionError(); }

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void omnisequence$additionalChunkSimulation(BooleanSupplier hasTimeLeft, boolean tickChunks, CallbackInfo callback) {
        if (tickChunks && DetachedSignalRealm.isAdditionalTick(level)) {
            this.tickChunks();
            callback.cancel();
        }
    }
}
