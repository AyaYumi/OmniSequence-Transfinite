package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.ForgeRenderSupport;
import com.mojang.blaze3d.vertex.VertexBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(VertexBuffer.class)
public abstract class GpuEffectVertexBufferMixin implements ForgeRenderSupport.BufferAccess {
    @Shadow private int vertexBufferId;
    @Override public int omni$vertexBufferId() { return vertexBufferId; }
}
