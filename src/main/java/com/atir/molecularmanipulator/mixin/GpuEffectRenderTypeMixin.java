package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.render.ForgeRenderSupport;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(RenderType.class)
public abstract class GpuEffectRenderTypeMixin implements ForgeRenderSupport.LayerAccess {
    @Shadow @Final private boolean sortOnUpload;
    @Override public boolean omni$sortOnUpload() { return sortOnUpload; }
}
