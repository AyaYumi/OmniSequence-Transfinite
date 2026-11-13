package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.ForgeRenderSupport;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Oculus 1.8 uploads segments directly, so custom GPU draws must run at this same draw point. */
@Pseudo
@Mixin(targets = "net.irisshaders.batchedentityrendering.impl.BufferSegmentRenderer", remap = false)
public abstract class GpuEffectOculusSegmentMixin {
    @Inject(method = "drawInner", at = @At("HEAD"), cancellable = true, require = 0)
    private void omni$drawGpuSegment(@Coerce Object segment, CallbackInfo callback) {
        if (ForgeRenderSupport.drawOculusSegment(segment)) callback.cancel();
    }
}
