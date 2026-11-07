package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SingularityShaderVerticesTest {
    @Test void everyEffectQuadSuppliesFiniteEntityAttributes() {
        for (boolean detailed : new boolean[]{false, true}) for (boolean suspended : new boolean[]{false, true}) {
            var recorder = new EntityRecorder();
            var out = new SingularityShaderVertices(recorder);
            var poses = new PoseStack();
            var frame = SingularityStructureEffects.Frame.steady(240);
            SingularityStructureEffects.renderCrystal(poses, out, frame, detailed, null, suspended);
            SingularityStructureEffects.render(poses, out, frame, detailed, null, suspended);
            SingularityCoreEffects.render(poses, out, 240, 0, detailed, false);
            SingularityCoreEffects.render(poses, out, 240, 0, detailed, true);
            SingularityStellarCore.renderStar(poses, out, frame, detailed);
            SingularityStellarCore.renderFlux(poses, out, frame, detailed);
            assertEquals(0, recorder.attributes);
            assertTrue(recorder.vertices > 1000);
            assertEquals(0, recorder.vertices % 4);
        }
    }

    @Test void bulkQuadEmissionKeepsEveryCornerAttribute() {
        var recorder = new AttributeRecorder();
        var out = new SingularityShaderVertices(recorder);
        int color = 0x7F336699;
        addQuad(out, color, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0);

        assertEquals(4, recorder.vertices.size());
        assertEquals(4, recorder.bulkVertices, "A completed quad should use the whole-vertex overload");
        float[][] positions = {{0, 0, 0}, {1, 0, 0}, {1, 1, 0}, {0, 1, 0}};
        float[][] uvs = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int i = 0; i < 4; i++) {
            var v = recorder.vertices.get(i);
            assertEquals(positions[i][0], v.x(), 0F);
            assertEquals(positions[i][1], v.y(), 0F);
            assertEquals(positions[i][2], v.z(), 0F);
            assertEquals(color, v.color());
            assertEquals(uvs[i][0], v.u(), 0F);
            assertEquals(uvs[i][1], v.v(), 0F);
            assertEquals(OverlayTexture.NO_OVERLAY, v.overlay());
            assertEquals(LightTexture.FULL_BRIGHT, v.light());
            assertNormal(v, 0, 0, 1);
        }
    }

    @Test void reverseWindingReversesTheNormalAndStartsAFreshQuad() {
        var recorder = new AttributeRecorder();
        var out = new SingularityShaderVertices(recorder);
        addQuad(out, 0xFF112233, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 1, 0);
        addQuad(out, 0xFF445566, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, 0);

        assertEquals(8, recorder.vertices.size());
        for (int i = 0; i < 4; i++) assertNormal(recorder.vertices.get(i), 0, 0, 1);
        for (int i = 4; i < 8; i++) assertNormal(recorder.vertices.get(i), 0, 0, -1);
        assertEquals(0xFF112233, recorder.vertices.get(0).color());
        assertEquals(0xFF445566, recorder.vertices.get(4).color());
    }

    @Test void transformedQuadKeepsWindingAndNormalizesItsNormal() {
        var poses = new PoseStack();
        poses.translate(5F, -2F, 7F);
        poses.mulPose(new Quaternionf().rotationY((float) (Math.PI / 2D)));
        poses.scale(2F, 3F, 4F);
        var pose = poses.last();
        var recorder = new AttributeRecorder();
        var out = new SingularityShaderVertices(recorder);
        int color = 78 << 24 | 12 << 16 | 34 << 8 | 56;
        out.addVertex(pose, 0, 0, 0).setColor(12, 34, 56, 78);
        out.addVertex(pose, 1, 0, 0).setColor(12, 34, 56, 78);
        out.addVertex(pose, 1, 1, 0).setColor(12, 34, 56, 78);
        out.addVertex(pose, 0, 1, 0).setColor(12, 34, 56, 78);

        assertEquals(4, recorder.bulkVertices);
        float[][] positions = {{5, -2, 7}, {5, -2, 5}, {5, 1, 5}, {5, 1, 7}};
        for (int i = 0; i < 4; i++) {
            var v = recorder.vertices.get(i);
            assertEquals(positions[i][0], v.x(), 1.0E-5F);
            assertEquals(positions[i][1], v.y(), 1.0E-5F);
            assertEquals(positions[i][2], v.z(), 1.0E-5F);
            assertEquals(color, v.color());
            assertNormal(v, 1, 0, 0);
        }
    }

    @Test void degenerateQuadsUseTheSecondTriangleThenUpAsFallback() {
        var recorder = new AttributeRecorder();
        var out = new SingularityShaderVertices(recorder);
        // The first triangle is collinear, but the second triangle still defines a plane.
        addQuad(out, 0xFF010203, 0, 0, 0, 1, 0, 0, 2, 0, 0, 0, 1, 0);
        // The fallback keeps the second triangle's winding on the other side.
        addQuad(out, 0xFF010203, 0, 0, 0, 1, 0, 0, 2, 0, 0, 0, -1, 0);
        // Every corner is collinear, so the only stable fallback is up.
        addQuad(out, 0xFF010203, 0, 0, 0, 1, 0, 0, 2, 0, 0, 3, 0, 0);

        assertEquals(12, recorder.vertices.size());
        for (int i = 0; i < 4; i++) assertNormal(recorder.vertices.get(i), 0, 0, 1);
        for (int i = 4; i < 8; i++) assertNormal(recorder.vertices.get(i), 0, 0, -1);
        for (int i = 8; i < 12; i++) assertNormal(recorder.vertices.get(i), 0, 1, 0);
    }

    @Test
    void fallingCodeKeepsEveryTrailCharacterAndItsBrightnessThroughTheEmissiveAdapter() {
        for (int quality : new int[] {1, 2}) for (double distance : new double[] {0, 113D * 113D}) {
            for (boolean glow : new boolean[] {false, true}) {
                var poses = new PoseStack();
                var expected = new RecordingColorConsumer();
                StarOrreryCoreEffects.renderRain(poses, expected, 9, quality, distance, 3.5F, glow);
                StarOrreryCoreEffects.renderBuildingRain(poses, expected, 9, quality, distance, 3.5F, 4F, glow);

                var actual = new RecordingColorConsumer();
                var attributes = new EntityRecorder(actual);
                var out = new SingularityShaderVertices(attributes);
                StarOrreryCoreEffects.renderRain(poses, out, 9, quality, distance, 3.5F, glow);
                StarOrreryCoreEffects.renderBuildingRain(poses, out, 9, quality, distance, 3.5F, 4F, glow);

                assertEquals(0, attributes.attributes);
                assertFalse(actual.vertices().isEmpty());
                assertEquals(expected.vertices(), actual.vertices(),
                        "The emissive path must retain all glyphs, their colors and their authored alpha");
                assertTrue(actual.vertices().stream().anyMatch(v -> v.y() < -30));
                assertTrue(actual.vertices().stream().anyMatch(v -> Math.hypot(v.x(), v.z()) > 29));
            }
        }
    }

    private static void addQuad(VertexConsumer out, int color,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        out.addVertex(x0, y0, z0).setColor(color);
        out.addVertex(x1, y1, z1).setColor(color);
        out.addVertex(x2, y2, z2).setColor(color);
        out.addVertex(x3, y3, z3).setColor(color);
    }

    private static void assertNormal(AttributeRecorder.Vertex v, float x, float y, float z) {
        assertEquals(x, v.nx(), 1.0E-5F);
        assertEquals(y, v.ny(), 1.0E-5F);
        assertEquals(z, v.nz(), 1.0E-5F);
        assertEquals(1F, v.nx() * v.nx() + v.ny() * v.ny() + v.nz() * v.nz(), 1.0E-5F,
                "Emitted normals must stay normalized");
    }

    /** Records both complete whole-vertex calls and the per-attribute chain. */
    private static final class AttributeRecorder implements VertexConsumer {
        private final List<Vertex> vertices = new ArrayList<>();
        int bulkVertices;
        private float x, y, z, u, v, nx, ny, nz;
        private int color, overlay, light, mask;

        @Override public void addVertex(float x, float y, float z, int color, float u, float v,
                int packedOverlay, int packedLight, float nx, float ny, float nz) {
            bulkVertices++;
            vertices.add(new Vertex(x, y, z, color, u, v, packedOverlay, packedLight, nx, ny, nz));
        }

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            assertEquals(0, mask, "The previous vertex is incomplete");
            this.x = x; this.y = y; this.z = z; mask = 1;
            return this;
        }

        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            assertEquals(0, mask & 2, "Color supplied twice");
            color = a << 24 | r << 16 | g << 8 | b; mask |= 2;
            return this;
        }

        @Override public VertexConsumer setUv(float u, float v) {
            assertEquals(0, mask & 4, "UV supplied twice");
            this.u = u; this.v = v; mask |= 4;
            return this;
        }

        @Override public VertexConsumer setUv1(int u, int v) {
            assertEquals(0, mask & 8, "Overlay supplied twice");
            overlay = u | v << 16; mask |= 8;
            return this;
        }

        @Override public VertexConsumer setUv2(int u, int v) {
            assertEquals(0, mask & 16, "Light supplied twice");
            light = u | v << 16; mask |= 16;
            return this;
        }

        @Override public VertexConsumer setNormal(float nx, float ny, float nz) {
            assertEquals(31, mask, "A complete vertex must reach the normal last");
            vertices.add(new Vertex(x, y, z, color, u, v, overlay, light, nx, ny, nz));
            mask = 0;
            return this;
        }

        record Vertex(float x, float y, float z, int color, float u, float v,
                int overlay, int light, float nx, float ny, float nz) {}
    }

    private static final class EntityRecorder implements VertexConsumer {
        private final RecordingColorConsumer colors;
        int attributes, vertices;
        EntityRecorder() { this(null); }
        EntityRecorder(RecordingColorConsumer colors) { this.colors = colors; }
        public VertexConsumer addVertex(float x, float y, float z) {
            assertEquals(0, attributes); assertTrue(Float.isFinite(x + y + z));
            if (colors != null) colors.addVertex(x, y, z);
            attributes = 1; return this;
        }
        public VertexConsumer setColor(int r, int g, int b, int a) {
            if (colors != null) colors.setColor(r, g, b, a);
            attributes |= 2; return this;
        }
        public VertexConsumer setUv(float u, float v) {
            assertTrue((u == 0 || u == 1) && (v == 0 || v == 1)); attributes |= 4; return this;
        }
        public VertexConsumer setUv1(int u, int v) { attributes |= 8; return this; }
        public VertexConsumer setUv2(int u, int v) { assertEquals(240, u); assertEquals(240, v); attributes |= 16; return this; }
        public VertexConsumer setNormal(float x, float y, float z) {
            assertEquals(31, attributes); assertEquals(1, x*x + y*y + z*z, 1e-5);
            attributes = 0; vertices++; return this;
        }
    }
}
