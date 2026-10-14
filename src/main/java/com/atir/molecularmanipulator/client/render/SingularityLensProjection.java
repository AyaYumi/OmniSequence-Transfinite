package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.mixin.GameRendererCameraBobAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class SingularityLensProjection {
    private SingularityLensProjection() {}

    static Matrix4f cameraEffects(Camera camera) {
        var poses = new PoseStack();
        var renderer = (GameRendererCameraBobAccessor) Minecraft.getInstance().gameRenderer;
        float partialTick = camera.getPartialTickTime();
        renderer.molecularmanipulator$bobHurt(poses, partialTick);
        if (Minecraft.getInstance().options.bobView().get()) {
            renderer.molecularmanipulator$bobView(poses, partialTick);
        }
        return new Matrix4f(poses.last().pose());
    }

    static Vector3f centerInProjectionSpace(Matrix4f pose, float localY, Matrix4f view, Matrix4f cameraEffects) {
        var center = pose.transformPosition(0, localY, 0, new Vector3f());
        view.transformPosition(center);
        cameraEffects.transformPosition(center);
        return center;
    }

    static Matrix4f baseProjection(Matrix4f renderedProjection, Matrix4f cameraEffects) {
        return new Matrix4f(renderedProjection).mul(new Matrix4f(cameraEffects).invert());
    }
}
