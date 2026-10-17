package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.GravityCameraView;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GravityGameRendererMixin {
    @Shadow @Final private Camera mainCamera;

    @Inject(method = "renderLevel", at = @At(value = "NEW", target = "org/joml/Matrix3f"))
    private void omnisequence$worldView(float partialTick, long finishTimeNano, PoseStack poses, CallbackInfo ci) {
        // 1.20.1 builds the actual view from Euler angles, not Camera.rotation(). Append the
        // inverse gravity frame after Forge's yaw/pitch/roll and before inverse-view lighting,
        // frustum construction and world rendering. Bobbing remains in the projection matrix.
        poses.mulPose(((GravityCameraView) mainCamera).omnisequence$inverseViewFrame());
    }
}
