package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.ForgeRenderSupport;
import com.atir.molecularmanipulator.client.render.GpuEffectSorting;
import com.atir.molecularmanipulator.client.render.SealedQuadSorting;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Forge's equivalent of the mesh seal: retain Oculus's original segment and sort domain. */
@Mixin(BufferBuilder.class)
public abstract class GpuEffectMeshDataMixin implements ForgeRenderSupport.BuilderAccess {
    @Shadow private VertexFormat format;
    @Shadow private VertexFormat.Mode mode;
    @Shadow private Vector3f[] sortingPoints;
    @Shadow private VertexSorting sorting;
    @Unique private SealedQuadSorting.Job omni$pendingSort;

    @Override public VertexFormat omni$vertexFormat() { return format; }

    @Inject(method = "putSortedQuadIndices", at = @At("HEAD"), cancellable = true)
    private void omni$sealEffectSort(VertexFormat.IndexType type, CallbackInfo callback) {
        omni$pendingSort = GpuEffectSorting.seal(format, mode, sortingPoints, sorting, type);
        if (omni$pendingSort != null) callback.cancel();
    }

    @WrapMethod(method = "storeRenderedBuffer")
    private BufferBuilder.RenderedBuffer omni$attachEffectSort(Operation<BufferBuilder.RenderedBuffer> original) {
        try {
            var buffer = original.call();
            GpuEffectSorting.attach(buffer, omni$pendingSort);
            omni$pendingSort = null;
            return buffer;
        } finally {
            if (omni$pendingSort != null) {
                omni$pendingSort.close();
                omni$pendingSort = null;
            }
        }
    }
}
