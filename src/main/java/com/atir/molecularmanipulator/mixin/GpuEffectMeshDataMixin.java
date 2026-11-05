package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.GpuEffectSorting;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.nio.ByteBuffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional CLIENT registration. Every ordinary/foreign MeshData call passes through unchanged.
 * Iris 1.8.14's SegmentedBufferBuilder.getSegments builds each domain, calls sortQuads with
 * RenderSystem.getVertexSorting(), discards the SortState and later draws this same mesh.
 * Hooking that existing seal, rather than requesting extra buffers, preserves all Iris domains.
 */
@Mixin(MeshData.class)
public abstract class GpuEffectMeshDataMixin {
    @Shadow @Final private MeshData.DrawState drawState;
    @Shadow private ByteBufferBuilder.Result indexBuffer;
    @Unique private GpuEffectSorting.MeshState omni$effectSorting;

    @WrapMethod(method = "sortQuads")
    private MeshData.SortState omni$sealEffectSort(ByteBufferBuilder destination, VertexSorting sorting,
            Operation<MeshData.SortState> original) {
        if (!GpuEffectSorting.owns(drawState))
            return original.call(destination, sorting);
        if (omni$effectSorting == null) omni$effectSorting = new GpuEffectSorting.MeshState(indexBuffer);
        var state = omni$effectSorting.sort(((MeshData) (Object) this).vertexBuffer(), drawState, destination, sorting);
        indexBuffer = omni$effectSorting.currentResult();
        return state;
    }

    @Inject(method = "indexBuffer", at = @At("HEAD"))
    private void omni$publishEffectIndices(CallbackInfoReturnable<ByteBuffer> callback) {
        // All readers see the real sorted Result, not a null/sequential placeholder. Assignment
        // and ByteBufferBuilder.Result lifecycle operations remain on the mesh owner thread.
        if (omni$effectSorting != null) indexBuffer = omni$effectSorting.result();
    }

    @WrapMethod(method = "close")
    private void omni$closeEffectSort(Operation<Void> original) {
        var sorting = omni$effectSorting;
        try {
            if (sorting != null) indexBuffer = sorting.result(); // Join before the original vertex storage can be reused/freed.
        } finally {
            try { original.call(); } // A failed sort must still release the original mesh.
            finally {
                try { if (sorting != null) sorting.close(); }
                finally { omni$effectSorting = null; }
            }
        }
    }
}
