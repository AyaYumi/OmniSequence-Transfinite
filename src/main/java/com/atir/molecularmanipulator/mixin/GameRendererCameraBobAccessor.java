package com.atir.molecularmanipulator.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererCameraBobAccessor {
    @Invoker("bobHurt")
    void molecularmanipulator$bobHurt(PoseStack poses, float partialTick);

    @Invoker("bobView")
    void molecularmanipulator$bobView(PoseStack poses, float partialTick);
}
