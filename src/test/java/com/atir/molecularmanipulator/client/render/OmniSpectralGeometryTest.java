package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class OmniSpectralGeometryTest {
    @Test
    void torusClosesBothSeamsAndPreservesRoundTubeRadius() {
        var recorder = new RecordingColorConsumer();
        OmniRenderGeometry.torus(new PoseStack().last(), recorder, Vec3.ZERO,
                14.25F, 0.2F, 96, 8, 0xDCAABBCC);
        var vertices = recorder.vertices();
        assertEquals(96 * 8 * 8, vertices.size());
        var occurrences = new HashMap<RecordingColorConsumer.Vertex, Integer>();
        for (var vertex : vertices) {
            double offset = Math.hypot(vertex.x(), vertex.z()) - 14.25D;
            assertEquals(0.04D, offset * offset + vertex.y() * vertex.y(), 0.00001D);
            assertEquals(0xDCAABBCC, vertex.argb());
            occurrences.merge(vertex, 1, Integer::sum);
        }
        assertEquals(96 * 8, occurrences.size(), "Wrapped indices must reuse seam vertices exactly");
        assertTrue(occurrences.values().stream().allMatch(n -> n == 8));
        for (int i = 0; i < vertices.size(); i += 8) {
            for (int j = 0; j < 4; j++) assertEquals(vertices.get(i + j), vertices.get(i + 7 - j));
        }
    }

    @Test
    void sharedSphereSubmitsOnlyPositionAndColor() {
        var recorder = new RecordingColorConsumer();
        OmniRenderGeometry.sphere(new PoseStack().last(), recorder, Vec3.ZERO,
                6.35F, 18, 36, 0x2A123456);
        assertEquals(18 * 36 * 8, recorder.vertices().size());
        for (var vertex : recorder.vertices()) {
            assertEquals(6.35D, vertex.radius(), 0.00001D);
            assertEquals(0x2A123456, vertex.argb());
        }
    }

    @Test
    void builtInSpectralResourcesMatchThePositionColorContract() throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/molecular_spectral.json")) {
            assertNotNull(stream);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("molecularmanipulator:omni_effect", json.get("vertex").getAsString());
            assertEquals("molecularmanipulator:molecular_spectral", json.get("fragment").getAsString());
            assertTrue(json.getAsJsonArray("samplers").isEmpty());
        }
        String vertex = resource("omni_effect.vsh");
        String fragment = resource("molecular_spectral.fsh");
        assertTrue(vertex.contains("in vec3 Position;"));
        assertTrue(vertex.contains("in vec4 Color;"));
        assertFalse(vertex.contains("UV0"));
        for (String varying : new String[] { "vertexColor", "viewPosition", "effectPosition" }) {
            assertTrue(vertex.contains(varying));
            assertTrue(fragment.contains(varying));
        }
        assertTrue(fragment.contains("uniform float GameTime;"));
        assertTrue(fragment.contains("dFdx(viewPosition)"));
        assertTrue(fragment.contains("dFdy(viewPosition)"));
        assertFalse(fragment.contains("sampler2D"));
    }

    private String resource(String file) throws Exception {
        try (var stream = getClass().getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/" + file)) {
            assertNotNull(stream);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
