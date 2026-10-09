package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.GhostMatterExposureRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GhostMatterGameRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void omnisequence$exposure(DeltaTracker delta, CallbackInfo ci) {
        GhostMatterExposureRenderer.render(delta.getGameTimeDeltaPartialTick(true));
    }
}
