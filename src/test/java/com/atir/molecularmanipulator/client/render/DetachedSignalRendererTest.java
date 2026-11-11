package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DetachedSignalRendererTest {
    @Test
    void orreryGlyphsRiseInsteadOfFalling() {
        var before = new RecordingColorConsumer();
        var after = new RecordingColorConsumer();
        var camera = new Vec3(0D, 65D, 0D);
        DetachedSignalRenderer.renderCell(new PoseStack(), before, camera, 0, 1, 0, 10.1D, 1);
        DetachedSignalRenderer.renderCell(new PoseStack(), after, camera, 0, 1, 0, 10.2D, 1);
        assertFalse(before.vertices().isEmpty());
        assertEquals(before.vertices().size(), after.vertices().size());
        for (int vertex = 0; vertex < before.vertices().size(); vertex++) {
            assertTrue(after.vertices().get(vertex).y() > before.vertices().get(vertex).y(), "Every code stroke moves upward");
        }
        assertEquals(0, before.vertices().size() % 4);
    }

    @Test
    void shaderMeshRetainsEveryGlyphColorAndSuppliesFullBrightEntityAttributes() {
        var camera = new Vec3(0D, 65D, 0D);
        var expected = new RecordingColorConsumer();
        DetachedSignalRenderer.renderCell(new PoseStack(), expected, camera, 0, 1, 0, 10.1D, 2);
        assertFalse(expected.vertices().isEmpty());
        var format = DefaultVertexFormat.NEW_ENTITY;
        try (var storage = new ByteBufferBuilder(4096)) {
            var builder = new BufferBuilder(storage, VertexFormat.Mode.QUADS, format);
            DetachedSignalRenderer.renderCell(new PoseStack(), new SingularityShaderVertices(builder),
                    camera, 0, 1, 0, 10.1D, 2);
            try (var mesh = builder.buildOrThrow()) {
                assertSame(format, mesh.drawState().format());
                assertEquals(expected.vertices().size(), mesh.drawState().vertexCount());
                var bytes = mesh.vertexBuffer();
                for (int i = 0; i < expected.vertices().size(); i++) {
                    int base = i * format.getVertexSize();
                    var vertex = expected.vertices().get(i);
                    assertEquals(vertex.x(), bytes.getFloat(base), 0F);
                    assertEquals(vertex.y(), bytes.getFloat(base + 4), 0F);
                    assertEquals(vertex.z(), bytes.getFloat(base + 8), 0F);
                    int color = base + format.getOffset(VertexFormatElement.COLOR);
                    assertEquals(vertex.argb() >> 16 & 255, Byte.toUnsignedInt(bytes.get(color)));
                    assertEquals(vertex.argb() >> 8 & 255, Byte.toUnsignedInt(bytes.get(color + 1)));
                    assertEquals(vertex.argb() & 255, Byte.toUnsignedInt(bytes.get(color + 2)));
                    assertEquals(vertex.alpha(), Byte.toUnsignedInt(bytes.get(color + 3)));
                    assertEquals(OverlayTexture.NO_OVERLAY, bytes.getInt(base + format.getOffset(VertexFormatElement.UV1)));
                    assertEquals(LightTexture.FULL_BRIGHT, bytes.getInt(base + format.getOffset(VertexFormatElement.UV2)));
                    int uv = base + format.getOffset(VertexFormatElement.UV0);
                    assertEquals(i % 4 == 1 || i % 4 == 2 ? 1F : 0F, bytes.getFloat(uv));
                    assertEquals(i % 4 >= 2 ? 1F : 0F, bytes.getFloat(uv + 4));
                    int normal = base + format.getOffset(VertexFormatElement.NORMAL);
                    assertTrue(bytes.get(normal) != 0 || bytes.get(normal + 1) != 0 || bytes.get(normal + 2) != 0);
                }
            }
        }
    }

    @Test
    void shaderRenderingUsesTheEmissiveOrreryLayerAndDoesNotDependOnIrisEntityBatchFlushes() throws Exception {
        var root = Path.of("src/main/java/com/atir/molecularmanipulator/client/render");
        String layers = Files.readString(root.resolve("OmniRenderLayers.java"));
        String selector = layers.substring(layers.indexOf("public static RenderType detachedSignalStreams()"),
                layers.indexOf("public static RenderType starOrreryWellInk()"));
        assertTrue(selector.contains("SingularityEffectLayers.shadersActive() ? starOrreryRainShader(true) : DETACHED_SIGNAL_STREAMS"));
        String rain = layers.substring(layers.indexOf("private static RenderType createStarOrreryRain("),
                layers.indexOf("private static RenderType createStarOrreryColor("));
        assertTrue(rain.contains("DefaultVertexFormat.NEW_ENTITY"));
        assertTrue(rain.contains("RENDERTYPE_BEACON_BEAM_SHADER"));
        assertTrue(rain.contains("LEQUAL_DEPTH_TEST"));
        assertTrue(rain.contains("NO_CULL"));
        assertTrue(rain.contains("writeDepth ? COLOR_DEPTH_WRITE : COLOR_WRITE"));
        String renderer = Files.readString(root.resolve("DetachedSignalRenderer.java"));
        assertTrue(renderer.contains("Tesselator.getInstance().begin(layer.mode(), layer.format())"));
        assertTrue(renderer.contains("new SingularityShaderVertices(mesh)"));
        assertTrue(renderer.contains("layer.draw(data)"));
        assertFalse(renderer.contains("mc.renderBuffers().bufferSource()"));
    }

    @Test
    void streamsReuseTheOrreryPaletteAndAreDeterministicAndBounded() {
        var colors = new HashSet<Integer>();
        var camera = new Vec3(0D, 65D, 0D);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) {
            var first = new RecordingColorConsumer();
            var second = new RecordingColorConsumer();
            DetachedSignalRenderer.renderCell(new PoseStack(), first, camera, x, 1, z, 10.1D, 2);
            DetachedSignalRenderer.renderCell(new PoseStack(), second, camera, x, 1, z, 10.1D, 2);
            assertEquals(first.vertices(), second.vertices(), "World cells must not reshuffle between frames");
            assertTrue(first.vertices().size() <= 2 * 7 * 10 * 4, "At most ten merged strokes per 3x5 glyph");
            first.vertices().forEach(vertex -> {
                assertTrue(vertex.alpha() > 0 && vertex.alpha() <= 225);
                colors.add(vertex.rgb());
            });
        }
        assertTrue(colors.contains(0xBF91FF));
        assertTrue(colors.contains(0x71BFFF));
        assertTrue(colors.size() >= 5, "The void is not a single cyan particle field");
    }
}
