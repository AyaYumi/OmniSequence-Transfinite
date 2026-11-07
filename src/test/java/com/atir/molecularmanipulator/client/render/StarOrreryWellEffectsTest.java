package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarOrreryWellEffectsTest {
    @Test
    void inactiveOrDisabledWellsRemainOpenWhileEnabledInkUsesFogAtEveryDistance() {
        for (int lit : new int[] {-1, 0, 1, 3, 6, 9}) for (int quality : new int[] {0, 1, 2}) {
            boolean expected = lit > 0 && quality > 0;
            assertEquals(expected, StarOrreryWellEffects.visible(lit, quality, 0));
            assertEquals(expected ? 4 : 0, draw(lit, quality, 0).vertices.size());
            for (double distance : new double[] {160, 224, 257, 384, 512}) {
                assertEquals(expected, StarOrreryWellEffects.visible(lit, quality, distance * distance));
                assertEquals(expected ? 4 : 0, draw(lit, quality, distance * distance).vertices.size());
            }
        }
        assertEquals(4, draw(9, 1, 159D * 159D).vertices.size());
    }

    @Test
    void shaderSurfaceUsesOneUvQuadInsteadOfThousandsOfConcentricRailVertices() {
        var vertices = draw(9, 2, 0).vertices;
        assertEquals(4, vertices.size());
        assertEquals(Set.of("0.0,0.0", "0.0,1.0", "1.0,1.0", "1.0,0.0"), vertices.stream()
                .map(v -> v.u + "," + v.v).collect(Collectors.toSet()));
        assertTrue(vertices.stream().allMatch(v -> Float.isFinite(v.x) && Float.isFinite(v.y) && Float.isFinite(v.z)));
    }

    @Test
    void fixedWellUvsMapToTheOriginalInnerLipAndNeverTheMovingDial() {
        float radius = StarOrreryWellEffects.MAX_RADIUS;
        assertTrue(radius < 4.99F && radius > 4.9F);
        for (var v : draw(9, 2, 0).vertices) {
            assertEquals((v.u * 2F - 1F) * radius, v.x);
            assertEquals((v.v * 2F - 1F) * radius, v.z);
            assertEquals(StarOrreryWellEffects.SURFACE_Y, v.y, 1E-5);
            assertTrue(v.y < -34.67F);
        }
    }

    @Test
    void reducedQualityAndDistanceSelectTwoOctavesWithoutAddingGeometryOrChangingOpacityUniforms() {
        var full = draw(9, 2, 0).vertices;
        var reduced = draw(9, 1, 0).vertices;
        assertEquals(4, full.size());
        assertEquals(4, reduced.size());
        assertTrue(full.stream().allMatch(v -> v.alpha == 255));
        assertTrue(reduced.stream().allMatch(v -> v.alpha == 128));
        assertEquals(reduced, draw(9, 2, 113D * 113D).vertices);
        for (int i = 0; i < full.size(); i++) {
            assertEquals(full.get(i).x, reduced.get(i).x);
            assertEquals(full.get(i).y, reduced.get(i).y);
            assertEquals(full.get(i).z, reduced.get(i).z);
            assertEquals(full.get(i).rgb, reduced.get(i).rgb);
        }
    }

    @Test
    void eachMachineCarriesItsOwnTierHueInsteadOfOverwritingASharedShaderColor() {
        for (int lit : new int[] {1, 3, 4, 6, 7, 9}) {
            int tier = StarOrreryEffects.tierColor(lit - 1);
            assertTrue(draw(lit, 2, 0).vertices.stream().allMatch(v -> v.rgb == tier));
        }
    }

    @Test
    void surfaceRespectsEveryFixedMachineFacingAndRestoresTheCallerPose() {
        for (int facing = 0; facing < 4; facing++) {
            var poses = new PoseStack();
            poses.translate(7, 20, -8);
            poses.mulPose(Axis.YP.rotationDegrees(facing * -90));
            var before = new Matrix4f(poses.last().pose());
            var actual = new InkConsumer();
            StarOrreryWellEffects.surface(poses, actual, 9, 2, 0);
            var local = draw(9, 2, 0).vertices;
            assertEquals(before, poses.last().pose());
            for (int i = 0; i < local.size(); i++) {
                var v = local.get(i);
                var expected = before.transformPosition(new Vector3f(v.x, v.y, v.z));
                var result = actual.vertices.get(i);
                assertEquals(expected.x, result.x, 1E-5);
                assertEquals(expected.y, result.y, 1E-5);
                assertEquals(expected.z, result.z, 1E-5);
                assertEquals(v.u, result.u);
                assertEquals(v.v, result.v);
                assertEquals(v.rgb, result.rgb);
            }
        }
    }

    @Test
    void shaderUsesLocalUvsAndVanillaTimeWithoutTexturesOrPerMachineMutableUniforms() throws Exception {
        var json = JsonParser.parseString(resource("json")).getAsJsonObject();
        assertEquals("molecularmanipulator:star_orrery_well_ink", json.get("vertex").getAsString());
        assertEquals("molecularmanipulator:star_orrery_well_ink", json.get("fragment").getAsString());
        assertTrue(json.getAsJsonArray("samplers").isEmpty());
        assertEquals(Set.of("ModelViewMat", "ProjMat", "ColorModulator", "GameTime", "FogStart", "FogEnd", "FogShape"),
                json.getAsJsonArray("uniforms").asList().stream()
                        .map(e -> e.getAsJsonObject().get("name").getAsString()).collect(Collectors.toSet()));
        String vertex = resource("vsh").replaceAll("\\s+", "");
        assertTrue(vertex.contains("wellUV=UV0;"));
        assertTrue(vertex.contains("ProjMat*ModelViewMat*vec4(Position,1.0)"));
        assertTrue(vertex.contains("fog_distance(Position,FogShape)"));
        assertTrue(resource("fsh").contains("linear_fog_fade(vertexDistance, FogStart, FogEnd)"));
        assertFalse(vertex.contains("InverseView"), "Not a camera-facing billboard");
    }

    @Test
    void inkLayerKeepsDepthTestingButNeverWritesAnOpaqueDiskIntoSceneDepth() throws Exception {
        String layers = Files.readString(Path.of(
                "src/main/java/com/atir/molecularmanipulator/client/render/OmniRenderLayers.java"));
        String ink = layers.substring(layers.indexOf("private static final RenderType STAR_ORRERY_WELL_INK"),
                layers.indexOf("private static final RenderType PLACEMENT_LINES"));
        assertTrue(ink.contains("OmniShaders::starOrreryWellInk"));
        assertTrue(ink.contains("POSITION_TEX_COLOR"));
        assertTrue(ink.contains("TRANSLUCENT_TRANSPARENCY"));
        assertTrue(ink.contains("LEQUAL_DEPTH_TEST"));
        assertTrue(ink.contains("NO_CULL"));
        assertTrue(ink.contains("setWriteMaskState(COLOR_WRITE)"));
        assertFalse(ink.contains("COLOR_DEPTH_WRITE"));
    }

    @Test
    void organicDiffusionHasOutwardAdvectionMottledBleedingFibresAndBoundedNoiseWork() throws Exception {
        String shader = resource("fsh").replaceAll("\\s+", "");
        assertTrue(shader.contains("radius*1.90-time/8.0"), "Constant fronts move to larger radius");
        assertTrue(shader.contains("(warp-0.5)*0.95"));
        assertTrue(shader.contains("floatbleed="));
        assertTrue(shader.contains("floatfray="));
        assertTrue(shader.contains("floatgrain="));
        assertTrue(shader.contains("vertexColor.a>0.75?4:2"));
        assertTrue(shader.contains("for(inti=0;i<4;i++)"));
        assertFalse(shader.contains("sampler2D"));
        // Both phase periods divide vanilla's 1200-second GameTime wrap.
        assertEquals(0D, 1200D % 8D);
        assertEquals(0D, 1200D % 120D);
    }

    @Test
    void centralApertureFeatheredOuterEdgeAndInkRemainTransparentRatherThanAReplacementSolidCap() throws Exception {
        String shader = resource("fsh").replaceAll("\\s+", "");
        assertTrue(shader.contains("if(radius>=1.0||radius<=0.09)discard;"));
        assertTrue(shader.contains("1.0-smoothstep(0.93,1.0,radius)"));
        assertTrue(shader.contains("clamp(pigment*0.70+fray*0.32+wet*0.085,0.0,0.76)"));
        assertTrue(shader.contains("if(alpha<0.003)discard;"));
        assertTrue(shader.contains("alpha*ColorModulator.a"));
        assertFalse(shader.contains("alpha*vertexColor.a"), "Vertex alpha encodes detail, not film opacity");
    }

    private static InkConsumer draw(int lit, int quality, double distance) {
        var out = new InkConsumer();
        StarOrreryWellEffects.surface(new PoseStack(), out, lit, quality, distance);
        assertFalse(out.pending);
        return out;
    }

    private static String resource(String extension) throws Exception {
        try (var stream = StarOrreryWellEffectsTest.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/star_orrery_well_ink." + extension)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record InkVertex(float x, float y, float z, float u, float v, int rgb, int alpha) {}

    /** Enforces the actual POSITION_TEX_COLOR contract without starting a GL context. */
    private static final class InkConsumer implements VertexConsumer {
        private final List<InkVertex> vertices = new ArrayList<>();
        private float x, y, z, u, v;
        private boolean pending, hasUv;

        @Override public VertexConsumer addVertex(float x, float y, float z) {
            assertFalse(pending);
            this.x = x; this.y = y; this.z = z;
            pending = true; hasUv = false;
            return this;
        }
        @Override public VertexConsumer setUv(float u, float v) {
            assertTrue(pending);
            this.u = u; this.v = v; hasUv = true;
            return this;
        }
        @Override public VertexConsumer setColor(int r, int g, int b, int a) {
            assertTrue(pending && hasUv);
            vertices.add(new InkVertex(x, y, z, u, v, r << 16 | g << 8 | b, a));
            pending = false;
            return this;
        }
        @Override public VertexConsumer setUv1(int u, int v) { throw new AssertionError("No overlay"); }
        @Override public VertexConsumer setUv2(int u, int v) { throw new AssertionError("No lightmap"); }
        @Override public VertexConsumer setNormal(float x, float y, float z) { throw new AssertionError("No normal"); }
    }
}
