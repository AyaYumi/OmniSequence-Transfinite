package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.junit.jupiter.api.Test;
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
    private static final class EntityRecorder implements VertexConsumer {
        int attributes, vertices;
        public VertexConsumer vertex(double x, double y, double z) {
            assertEquals(0, attributes); assertTrue(Double.isFinite(x + y + z));
            attributes = 1; return this;
        }
        public VertexConsumer color(int r, int g, int b, int a) { attributes |= 2; return this; }
        public VertexConsumer uv(float u, float v) {
            assertTrue((u == 0 || u == 1) && (v == 0 || v == 1)); attributes |= 4; return this;
        }
        public VertexConsumer overlayCoords(int u, int v) { attributes |= 8; return this; }
        public VertexConsumer uv2(int u, int v) { assertEquals(240, u); assertEquals(240, v); attributes |= 16; return this; }
        public VertexConsumer normal(float x, float y, float z) {
            assertEquals(31, attributes); assertEquals(1, x*x + y*y + z*z, 1e-5);
            return this;
        }
        public void endVertex() { assertEquals(31, attributes); attributes = 0; vertices++; }
        public void defaultColor(int r, int g, int b, int a) { }
        public void unsetDefaultColor() { }
    }
}
