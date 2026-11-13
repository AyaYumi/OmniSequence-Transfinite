package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.GpuEffectSorting;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Deferred indices have exactly the lifetime of their Forge rendered buffer. */
@Mixin(BufferBuilder.RenderedBuffer.class)
public abstract class GpuEffectRenderedBufferMixin {
    @Inject(method = "indexBuffer", at = @At("HEAD"), cancellable = true)
    private void omni$publishIndices(CallbackInfoReturnable<ByteBuffer> callback) {
        var indices = GpuEffectSorting.indices((BufferBuilder.RenderedBuffer) (Object) this);
        if (indices != null) callback.setReturnValue(indices);
    }

    @WrapMethod(method = "release")
    private void omni$releaseIndices(Operation<Void> original) {
        try { GpuEffectSorting.release((BufferBuilder.RenderedBuffer) (Object) this); }
        finally { original.call(); }
    }
}
