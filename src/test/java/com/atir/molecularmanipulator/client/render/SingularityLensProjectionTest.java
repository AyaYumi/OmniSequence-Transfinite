package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SingularityLensProjectionTest {
    @Test
    void projectsTheWorldCenterWithTheRenderedCameraProjection() {
        var pose = new Matrix4f().translation(4, 12.5F, -3);
        var view = new Matrix4f().rotationY(.4F).translate(-2, -1, 6);
        var walkBob = new Matrix4f().translation(.08F, -.12F, 0).rotateZ(.03F).rotateX(.04F);
        var renderedProjection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, .05F, 512F)
                .mul(walkBob);

        Vector3f center = SingularityLensProjection.centerInViewSpace(pose, 12.5F, view);
        var expectedCenter = view.transformPosition(pose.transformPosition(0, 12.5F, 0, new Vector3f()), new Vector3f());
        var clipCenter = renderedProjection.transform(new Vector4f(center, 1));
        var restoredCenter = SingularityLensProjection.inverseProjection(renderedProjection).transform(clipCenter);

        assertEquals(expectedCenter.x, center.x, 1.0E-6F);
        assertEquals(expectedCenter.y, center.y, 1.0E-6F);
        assertEquals(expectedCenter.z, center.z, 1.0E-6F);
        assertEquals(center.x, restoredCenter.x / restoredCenter.w, 1.0E-5F);
        assertEquals(center.y, restoredCenter.y / restoredCenter.w, 1.0E-5F);
        assertEquals(center.z, restoredCenter.z / restoredCenter.w, 1.0E-5F);
    }
}
