package com.atir.molecularmanipulator.client.render;

import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

    @Test
    void unmodifiedPerspectiveKeepsTheRayOriginAtZero() {
        var projection = perspective(70);
        var inverse = SingularityLensProjection.inverseProjection(projection);
        assertVectorEquals(new Vector3f(), SingularityLensProjection.rayOriginInViewSpace(inverse), 1.0E-6F);
    }

    @Test
    void raysStayOnBothLargeAndMiniatureCoresThroughoutTheWalkingCycle() {
        var centers = new Vector3f[] {new Vector3f(4, 12.5F, -35), new Vector3f(.4F, .2F, -3)};
        for (float fov : new float[] {40, 70, 110}) {
            for (int step = 0; step < 32; step++) {
                float phase = (float) (step * Math.PI / 16);
                var bob = new Matrix4f().translation(.08F * (float) Math.sin(phase),
                                -.12F * Math.abs((float) Math.cos(phase)), 0)
                        .rotateZ(.03F * (float) Math.sin(phase)).rotateX(.04F * (float) Math.cos(phase));
                var projection = perspective(fov).mul(bob);
                var inverse = SingularityLensProjection.inverseProjection(projection);
                var origin = SingularityLensProjection.rayOriginInViewSpace(inverse);
                var expectedOrigin = new Matrix4f(bob).invert().transformPosition(new Vector3f());
                assertVectorEquals(expectedOrigin, origin, 1.0E-6F);

                for (var center : centers) {
                    var screen = project(projection, center);
                    var expectedRay = new Vector3f(center).sub(origin).normalize();
                    // All points on a pixel's ray must give the same direction, even
                    // close to the near plane where the old zero-origin ray went astray.
                    for (float depth : new float[] {.1F, .5F, .95F}) {
                        var ray = unproject(inverse, screen.x, screen.y, depth).sub(origin).normalize();
                        assertVectorEquals(expectedRay, ray, 2.0E-6F);
                        assertTrue(new Vector3f(center).sub(origin).cross(ray).length() < 1.0E-4F);
                    }
                }
            }
        }
    }

    @Test
    void sceneDepthAndSphereIntersectionsUseTheSameTranslatedEye() {
        var bob = new Matrix4f().translation(.08F, -.12F, .02F).rotateZ(.03F).rotateX(.04F);
        var projection = perspective(70).mul(bob);
        var inverse = SingularityLensProjection.inverseProjection(projection);
        var origin = SingularityLensProjection.rayOriginInViewSpace(inverse);
        var ray = new Vector3f(.2F, .1F, -1).normalize();
        for (float radius : new float[] {.3F, 3.8F}) {
            var relativeCenter = new Vector3f(ray).mul(radius * 3);
            float along = ray.dot(relativeCenter);
            float impact2 = Math.max(0, relativeCenter.lengthSquared() - along * along);
            float coreNear = along - (float) Math.sqrt(Math.max(0, radius * radius - impact2));
            assertEquals(radius * 2, coreNear, 1.0E-4F);
            for (float distance : new float[] {radius, radius * 4}) {
                var point = new Vector3f(ray).mul(distance).add(origin);
                var screen = project(projection, point);
                float sceneDistance = unproject(inverse, screen.x, screen.y, screen.z).distance(origin);
                assertEquals(distance, sceneDistance, 1.0E-3F);
                assertEquals(distance < coreNear, sceneDistance < coreNear);
            }
        }
    }

    @Test
    void bothLensShadersUseTheUploadedOriginForRaysCentersAndSceneDepth() throws Exception {
        for (String name : new String[] {"singularity_lens", "cosmic_lens"}) {
            var json = JsonParser.parseString(resource(name + ".json")).getAsJsonObject();
            var origin = json.getAsJsonArray("uniforms").asList().stream()
                    .map(element -> element.getAsJsonObject())
                    .filter(uniform -> uniform.get("name").getAsString().equals("RayOrigin"))
                    .findFirst().orElseThrow();
            assertEquals("float", origin.get("type").getAsString());
            assertEquals(3, origin.get("count").getAsInt());
            assertEquals(3, origin.getAsJsonArray("values").size());
            origin.getAsJsonArray("values").forEach(value -> assertEquals(0, value.getAsFloat()));
            var fragment = resource(name + ".fsh").replaceAll("\\s+", "");
            assertTrue(fragment.contains("uniformvec3RayOrigin;"), name);
            assertTrue(fragment.contains("normalize(viewPoint(uv,0.5)-RayOrigin)"), name);
            assertTrue(fragment.contains("relativeCenter=CoreCenter-RayOrigin;"), name);
            assertTrue(fragment.contains("dot(ray,relativeCenter)"), name);
            assertTrue(fragment.contains("dot(relativeCenter,relativeCenter)"), name);
            assertTrue(fragment.contains("length(viewPoint(uv,depth)-RayOrigin)"), name);
            // The screen-space refraction center still uses the original view-space point.
            assertTrue(fragment.contains("LensProjection*vec4(CoreCenter,1.0)"), name);
        }
    }

    private static Matrix4f perspective(float fov) {
        return new Matrix4f().perspective((float) Math.toRadians(fov), 16F / 9F, .05F, 512F);
    }

    private static Vector3f project(Matrix4f projection, Vector3f point) {
        var clip = projection.transform(new Vector4f(point, 1));
        return new Vector3f(clip.x, clip.y, clip.z).div(clip.w).mul(.5F).add(.5F, .5F, .5F);
    }

    private static Vector3f unproject(Matrix4f inverse, float u, float v, float depth) {
        var point = inverse.transform(new Vector4f(u * 2 - 1, v * 2 - 1, depth * 2 - 1, 1));
        return new Vector3f(point.x, point.y, point.z).div(point.w);
    }

    private static void assertVectorEquals(Vector3f expected, Vector3f actual, float tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }

    private String resource(String file) throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/" + file)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
