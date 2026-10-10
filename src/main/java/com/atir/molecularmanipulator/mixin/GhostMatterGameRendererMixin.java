package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.GhostMatterExposureRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GhostMatterGameRendererMixin {
    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void omnisequence$exposure(float partialTick, long finishTimeNano, com.mojang.blaze3d.vertex.PoseStack pose, CallbackInfo ci) {
        GhostMatterExposureRenderer.render(partialTick);
    }
}
