package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class GravityPlayerRendererMixin {
    @Inject(method = "setupRotations", at = @At("HEAD"))
    private void omnisequence$body(LivingEntity entity, PoseStack pose, float bob, float yaw, float partialTick, float scale,
            CallbackInfo ci) {
        if (GravityController.rotated(entity)) pose.mulPose(GravityFrame.rotation(GravityController.direction(entity)));
    }
}
