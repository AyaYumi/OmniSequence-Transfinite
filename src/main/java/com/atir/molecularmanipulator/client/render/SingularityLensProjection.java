package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.mixin.GameRendererCameraBobAccessor;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Vector3f;

final class SingularityLensProjection {
    private SingularityLensProjection() {}

    static Matrix4f cameraEffects(float partialTick) {
        var poses = new PoseStack();
        var renderer = (GameRendererCameraBobAccessor) Minecraft.getInstance().gameRenderer;
        renderer.molecularmanipulator$bobHurt(poses, partialTick);
        if (Minecraft.getInstance().options.bobView().get()) {
            renderer.molecularmanipulator$bobView(poses, partialTick);
        }
        return new Matrix4f(poses.last().pose());
    }

    // 1.20.1 puts BOTH translation and rotation from bobbing/hurt into the projection.
    // Our GLSL traces from view-space zero, so move the entire affine effect into the
    // center instead. Keeping even a small translation in the inverse projection makes
    // normalize(viewPoint(uv, 0.5)) a ray from the wrong origin and amplifies drift with distance.
    static Vector3f centerInViewSpace(Matrix4f renderedPose, float localY, Matrix4f cameraEffects) {
        var center = renderedPose.transformPosition(0, localY, 0, new Vector3f());
        return cameraEffects.transformPosition(center);
    }

    static Matrix4f lensProjection(Matrix4f renderedProjection, Matrix4f cameraEffects) {
        return new Matrix4f(renderedProjection).mul(new Matrix4f(cameraEffects).invert());
    }

    static Matrix4f inverseProjection(Matrix4f renderedProjection) {
        return new Matrix4f(renderedProjection).invert();
    }
}
