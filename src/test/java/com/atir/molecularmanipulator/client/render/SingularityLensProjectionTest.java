package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingularityLensProjectionTest {
    @Test
    void appliesWalkingCameraMotionBeforeProjectingTheWorldCenter() {
        var pose = new Matrix4f().translation(4, 12.5F, -3);
        var view = new Matrix4f().rotationY(.4F).translate(-2, -1, 6);
        var cameraEffects = new Matrix4f().translation(.08F, -.12F, 0).rotateZ(.03F).rotateX(.04F);
        var baseProjection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, .05F, 512F);
        var renderedProjection = new Matrix4f(baseProjection).mul(cameraEffects);
        var lensProjection = SingularityLensProjection.baseProjection(renderedProjection, cameraEffects);

        Vector3f center = SingularityLensProjection.centerInProjectionSpace(pose, 12.5F, view, cameraEffects);
        var worldCenterInView = view.transformPosition(pose.transformPosition(0, 12.5F, 0, new Vector3f()), new Vector3f());
        var expected = renderedProjection.transform(new Vector4f(worldCenterInView, 1));
        var actual = lensProjection.transform(new Vector4f(center, 1));

        assertEquals(expected.x / expected.w, actual.x / actual.w, 1.0E-6F);
        assertEquals(expected.y / expected.w, actual.y / actual.w, 1.0E-6F);
        assertEquals(expected.z / expected.w, actual.z / actual.w, 1.0E-6F);
    }
}
