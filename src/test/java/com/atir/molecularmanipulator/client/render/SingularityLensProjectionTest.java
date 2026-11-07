package com.atir.molecularmanipulator.client.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SingularityLensProjectionTest {
    @Test
    void projectsTheBerCenterWithTheActualRenderedCameraProjection() {
        var localPose = new Matrix4f().translation(4, 0, -30);
        var view = new Matrix4f().rotationY(.4F).translate(-2, -1, 6);
        // Forge 1.20.1 passes the view rotation inside the BER pose, unlike the
        // separate model-view matrix exposed by the newer RenderLevelStageEvent.
        var renderedPose = new Matrix4f(view).mul(localPose);
        var savedPose = new Matrix4f(renderedPose);
        var renderedProjection = perspective(70, 16F / 9F).mul(walkBob(.7F));
        var savedProjection = new Matrix4f(renderedProjection);

        var center = SingularityLensProjection.centerInViewSpace(renderedPose, 12.5F);
        var expectedCenter = view.transformPosition(localPose.transformPosition(0, 12.5F, 0, new Vector3f()), new Vector3f());
        var clipCenter = renderedProjection.transform(new Vector4f(center, 1));
        var restoredCenter = SingularityLensProjection.inverseProjection(renderedProjection).transform(clipCenter);

        assertEquals(expectedCenter.x, center.x, 1.0E-6F);
        assertEquals(expectedCenter.y, center.y, 1.0E-6F);
        assertEquals(expectedCenter.z, center.z, 1.0E-6F);
        // Inverting the full bobbed float projection loses sub-millimeter precision at this distance.
        assertEquals(center.x, restoredCenter.x / restoredCenter.w, 1.0E-3F);
        assertEquals(center.y, restoredCenter.y / restoredCenter.w, 1.0E-3F);
        assertEquals(center.z, restoredCenter.z / restoredCenter.w, 1.0E-3F);
        assertEquals(savedPose, renderedPose, "Center lookup must not change the captured BER pose");
        assertEquals(savedProjection, renderedProjection, "Projection inversion must not mutate the render event matrix");
    }

    @Test
    void reconstructedCenterRayHitsBothWorldAnchorsThroughoutTheWalkingCycle() {
        var projection = perspective(70, 3400F / 1440F);
        for (float localY : new float[]{0, 12.5F}) for (float distance : new float[]{6, 20, 40, 90}) {
            var pose = new Matrix4f().translation(0, -localY, -distance);
            for (int phase = 0; phase < 32; phase++) {
                var renderedProjection = new Matrix4f(projection).mul(walkBob(phase * (float) Math.PI / 16));
                var inverse = SingularityLensProjection.inverseProjection(renderedProjection);
                var origin = SingularityLensProjection.viewOrigin(inverse);
                var center = SingularityLensProjection.centerInViewSpace(pose, localY);
                var clip = renderedProjection.transform(new Vector4f(center, 1));
                var ray = unproject(inverse, clip.x / clip.w, clip.y / clip.w, .5F).sub(origin).normalize();
                float missDistance = new Vector3f(center).sub(origin).cross(ray).length();
                assertTrue(missDistance < .001F, "Shader ray misses anchor by " + missDistance
                        + " at distance=" + distance + " localY=" + localY + " phase=" + phase);
            }
        }
    }

    @Test
    void recoversTranslatedEyeAcrossWalkingPhasesFovsAndAspectRatios() {
        for (float fov : new float[]{50, 70, 110}) for (float aspect : new float[]{1, 16F / 9F, 3400F / 1440F}) {
            for (int phase = 0; phase < 32; phase++) {
                var bob = walkBob(phase * (float) Math.PI / 16);
                var projection = perspective(fov, aspect).mul(bob);
                var inverse = SingularityLensProjection.inverseProjection(projection);
                var savedInverse = new Matrix4f(inverse);
                var origin = SingularityLensProjection.viewOrigin(inverse);
                var expected = new Matrix4f(bob).invert().transformPosition(new Vector3f());
                assertEquals(expected.x, origin.x, 1.0E-6F);
                assertEquals(expected.y, origin.y, 1.0E-6F);
                assertEquals(expected.z, origin.z, 1.0E-6F);
                assertEquals(savedInverse, inverse, "Origin lookup must not mutate the shared inverse matrix");
            }
        }
    }

    @Test
    void everyProjectedCoreUsesTheSameRayRegardlessOfUnprojectionDepth() {
        for (int phase = 0; phase < 32; phase++) {
            var projection = perspective(70, 3400F / 1440F).mul(walkBob(phase * (float) Math.PI / 16));
            var inverse = SingularityLensProjection.inverseProjection(projection);
            var origin = SingularityLensProjection.viewOrigin(inverse);
            for (var center : new Vector3f[]{new Vector3f(0, 0, -24), new Vector3f(3, 4, -32), new Vector3f(1, -.5F, -6)}) {
                var clip = projection.transform(new Vector4f(center, 1));
                var toCenter = new Vector3f(center).sub(origin);
                for (float depth : new float[]{.1F, .5F, .9F}) {
                    var point = unproject(inverse, clip.x / clip.w, clip.y / clip.w, depth);
                    var ray = new Vector3f(point).sub(origin).normalize();
                    assertTrue(ray.dot(toCenter) > 0);
                    assertEquals(0, new Vector3f(toCenter).cross(ray).length(), 1.0E-3F,
                            "A projected world center must remain on its lens ray at every walking phase");
                    if (phase == 0 && center.x == 0 && depth == .5F) {
                        float oldMiss = new Vector3f(center).cross(new Vector3f(point).normalize()).length();
                        assertTrue(oldMiss > 12.5F, "The old zero-origin ray misses even the Hub's entire influence volume");
                    }
                }
            }
        }
    }

    @Test
    void reconstructedSceneDepthUsesTheSameEyeAsTheSphereIntersection() {
        for (int phase = 0; phase < 16; phase++) {
            var projection = perspective(70, 16F / 9F).mul(walkBob(phase * (float) Math.PI / 8));
            var inverse = SingularityLensProjection.inverseProjection(projection);
            var origin = SingularityLensProjection.viewOrigin(inverse);
            var center = new Vector3f(0, 0, -24);
            var ray = new Vector3f(center).sub(origin).normalize();
            float coreNear = center.distance(origin) - 3.8F;
            for (float distance : new float[]{coreNear - .1F, coreNear + .1F, 40}) {
                var worldPoint = new Vector3f(ray).mul(distance).add(origin);
                var clip = projection.transform(new Vector4f(worldPoint, 1));
                var reconstructed = unproject(inverse, clip.x / clip.w, clip.y / clip.w, clip.z / clip.w * .5F + .5F);
                float sceneDistance = reconstructed.distance(origin);
                // Perspective depth is float-quantized; allow one centimeter at forty blocks.
                assertEquals(distance, sceneDistance, .01F);
                assertEquals(distance < coreNear, sceneDistance < coreNear,
                        "Foreground/background classification must use the ray origin, not the unbobbed zero");
            }
        }
    }

    @Test
    void disabledBobbingAndPureHurtRotationRetainTheZeroEye() {
        var pose = new Matrix4f().translation(-2, 0, -20);
        var center = SingularityLensProjection.centerInViewSpace(pose, 12.5F);
        assertEquals(-2, center.x, 1.0E-6F);
        assertEquals(12.5F, center.y, 1.0E-6F);
        assertEquals(-20, center.z, 1.0E-6F);
        for (var transform : new Matrix4f[]{new Matrix4f(), new Matrix4f().rotateY(.3F).rotateZ(.2F).rotateY(-.3F)}) {
            // Zoom changes clip-space framing, not the perspective center in view space.
            var projection = new Matrix4f().translation(.1F, -.05F, 0).scale(1.4F, 1.4F, 1)
                    .mul(perspective(70, 16F / 9F)).mul(transform);
            var origin = SingularityLensProjection.viewOrigin(SingularityLensProjection.inverseProjection(projection));
            assertEquals(0, origin.length(), 1.0E-6F);
        }
    }

    private static Matrix4f perspective(float fov, float aspect) {
        return new Matrix4f().perspective((float) Math.toRadians(fov), aspect, .05F, 512);
    }

    private static Matrix4f walkBob(float phase) {
        float bob = .1F;
        return new Matrix4f().translation((float) Math.sin(phase) * bob * .5F, -(float) Math.abs(Math.cos(phase)) * bob, 0)
                .rotateZ((float) Math.toRadians(Math.sin(phase) * bob * 3))
                .rotateX((float) Math.toRadians(Math.abs(Math.cos(phase - .2F) * bob) * 5));
    }

    private static Vector3f unproject(Matrix4f inverse, float x, float y, float depth) {
        var point = inverse.transform(new Vector4f(x, y, depth * 2 - 1, 1));
        return new Vector3f(point.x, point.y, point.z).div(point.w);
    }
}
