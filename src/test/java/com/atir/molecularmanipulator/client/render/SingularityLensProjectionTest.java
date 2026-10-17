package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityLensProjectionTest {
    @Test
    void keepsTheWorldCenterAtTheSameScreenPositionWithCameraBob() {
        Matrix4f pose = new Matrix4f().translation(4, 0, -30);
        Matrix4f cameraEffects = new Matrix4f().translation(.08F, -.12F, 0).rotateZ(.03F).rotateX(.04F);
        Matrix4f baseProjection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, .05F, 512F);
        Matrix4f renderedProjection = new Matrix4f(baseProjection).mul(cameraEffects);

        Vector3f unrotatedCenter = pose.transformPosition(0, 12.5F, 0, new Vector3f());
        Vector3f center = SingularityLensProjection.centerInViewSpace(pose, 12.5F, cameraEffects);
        Matrix4f lensProjection = SingularityLensProjection.lensProjection(renderedProjection, cameraEffects);
        Vector4f expectedClip = renderedProjection.transform(new Vector4f(unrotatedCenter, 1));
        Vector4f actualClip = lensProjection.transform(new Vector4f(center, 1));
        Vector4f restoredCenter = SingularityLensProjection.inverseProjection(lensProjection)
                .transform(new Vector4f(expectedClip));

        assertEquals(baseProjection.m00(), lensProjection.m00(), 1.0E-6F);
        assertEquals(baseProjection.m11(), lensProjection.m11(), 1.0E-6F);
        assertEquals(expectedClip.x / expectedClip.w, actualClip.x / actualClip.w, 1.0E-5F);
        assertEquals(expectedClip.y / expectedClip.w, actualClip.y / actualClip.w, 1.0E-5F);
        assertEquals(center.x, restoredCenter.x / restoredCenter.w, 1.0E-5F);
        assertEquals(center.y, restoredCenter.y / restoredCenter.w, 1.0E-5F);
        assertEquals(center.z, restoredCenter.z / restoredCenter.w, 1.0E-5F);
    }

    @Test
    void reconstructedCenterRayHitsTheWorldAnchorThroughoutTheWalkingCycle() {
        var projection = new Matrix4f().perspective((float) Math.toRadians(70), 3400F / 1440F, .05F, 512);
        for (float localY : new float[]{0, 12.5F}) for (float distance : new float[]{6, 20, 40, 90}) {
            var pose = new Matrix4f().translation(0, -localY, -distance);
            for (int phase = 0; phase < 32; phase++) {
                float stride = phase * (float) Math.PI / 16;
                var effects = new Matrix4f().translation((float) Math.sin(stride) * .06F,
                        -(float) Math.abs(Math.cos(stride)) * .12F, 0)
                        .rotateZ((float) Math.toRadians(Math.sin(stride) * .36))
                        .rotateX((float) Math.toRadians(Math.abs(Math.cos(stride - .2)) * .6));
                var renderedProjection = new Matrix4f(projection).mul(effects);
                var center = SingularityLensProjection.centerInViewSpace(pose, localY, effects);
                var lensProjection = SingularityLensProjection.lensProjection(renderedProjection, effects);
                var worldCenter = pose.transformPosition(0, localY, 0, new Vector3f());
                var clip = renderedProjection.transform(new Vector4f(worldCenter, 1));
                // Matches GLSL viewPoint(uv, 0.5), not just the forward projection identity.
                var point = SingularityLensProjection.inverseProjection(lensProjection)
                        .transform(new Vector4f(clip.x / clip.w, clip.y / clip.w, 0, 1));
                var ray = new Vector3f(point.x, point.y, point.z).div(point.w).normalize();
                float missDistance = ray.cross(center, new Vector3f()).length();
                assertTrue(missDistance < .0001F, "Shader ray misses anchor by " + missDistance
                        + " at distance=" + distance + " localY=" + localY + " phase=" + phase);
                assertTrue(projection.equals(lensProjection, .0001F),
                        "All affine bob translation/rotation must be removed from the ray projection");
            }
        }
    }

    @Test
    void leavesProjectionAndCenterUnchangedWhenCameraEffectsAreDisabled() {
        Matrix4f pose = new Matrix4f().translation(-2, 0, -20);
        Matrix4f cameraEffects = new Matrix4f();
        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(70), 16F / 9F, .05F, 512F);

        Vector3f center = SingularityLensProjection.centerInViewSpace(pose, 12.5F, cameraEffects);
        Matrix4f lensProjection = SingularityLensProjection.lensProjection(projection, cameraEffects);

        assertEquals(-2, center.x, 1.0E-6F);
        assertEquals(12.5F, center.y, 1.0E-6F);
        assertEquals(-20, center.z, 1.0E-6F);
        assertEquals(projection.m00(), lensProjection.m00(), 1.0E-6F);
        assertEquals(projection.m11(), lensProjection.m11(), 1.0E-6F);
    }
}
