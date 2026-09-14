package com.atir.molecularmanipulator.client.render.ctm;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.IQuadTransformer;
import org.junit.jupiter.api.Test;

/** Real 1.20.1 sprites use texel coordinates (0..16), not the 1.21 normalized API. */
class MatterEmissiveQuadTest {
    @Test
    void connectedWorldModelKeepsEmissiveFacesAndSamplesTheNeutralFillerInterior() throws Exception {
        Class.forName(com.atir.molecularmanipulator.client.UiRenderRecorder.class.getName());
        try (var contents = contents("matter_fabrication_pattern_assembly");
                var fillerContents = contents("matter_fabrication_casing_top")) {
            var sprite = new Sprite(contents);
            var filler = new Sprite(fillerContents);
            var original = new net.minecraft.client.resources.model.SimpleBakedModel(List.of(),
                    java.util.Map.of(Direction.NORTH, List.of(face(sprite, 0, 0, 16, 16))),
                    true, true, true, sprite, net.minecraft.client.renderer.block.model.ItemTransforms.NO_TRANSFORMS,
                    net.minecraft.client.renderer.block.model.ItemOverrides.EMPTY);
            var model = new MatterConnectedModel(original, state -> original, id -> filler);
            // No block entity, ME connection, power or formed state is required for local emission.
            for (int mask : new int[] {0, 255}) {
                var faces = model.getQuads(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),
                        Direction.NORTH, net.minecraft.util.RandomSource.create(1),
                        MatterConnectedModel.modelData((long) mask << (Direction.NORTH.ordinal() * 8)), null);
                assertTrue(faces.stream().anyMatch(MatterGoldEmissive::isEmissive));
                if (mask == 0) continue;
                var neutral = faces.stream().filter(quad -> quad.getSprite() == filler).toList();
                assertFalse(neutral.isEmpty(), "Connected fasteners should use the plain casing fill");
                for (var quad : neutral) {
                    var bounds = bounds(quad);
                    assertTrue(bounds[0] >= 2.5F && bounds[1] >= 2.5F, "Filler must stay inside its frame");
                    assertTrue(bounds[2] <= 13.5F && bounds[3] <= 13.5F);
                    assertFalse(MatterGoldEmissive.isEmissive(quad));
                }
            }
        }
    }

    @Test
    void realAnimatedGoldBlueAndStaticPurpleSpritesProduceOnlyLocalFullBrightFaces() throws Exception {
        for (String name : List.of("matter_fabrication_coil", "matter_fabrication_fluid_input",
                "matter_fabrication_fluid_output", "molecular_manipulator")) {
            try (var contents = contents(name)) {
                var sprite = new Sprite(contents);
                assertEquals(16, sprite.getUOffset(sprite.getU(16)), 0.0001F);
                var input = face(sprite, 0, 0, 16, 16);
                var quads = new MatterGoldEmissive().apply(List.of(input));
                assertTrue(quads.stream().anyMatch(MatterGoldEmissive::isEmissive), name + " has no emissive faces");
                assertTrue(quads.stream().anyMatch(q -> !MatterGoldEmissive.isEmissive(q)), name + " must retain shading");
                for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
                    boolean expected = true;
                    int frames = contents.getOriginalImage().getHeight() / 16;
                    for (int frame = 0; frame < frames; frame++) {
                        expected &= MatterGoldEmissive.isEmissivePixel(contents.name(),
                                contents.getOriginalImage().getPixelRGBA(x, y + frame * 16));
                    }
                    int matches = 0;
                    for (var quad : quads) {
                        float[] bounds = bounds(quad);
                        if (x + 0.5F < bounds[0] || x + 0.5F >= bounds[2]
                                || y + 0.5F < bounds[1] || y + 0.5F >= bounds[3]) continue;
                        matches++;
                        assertEquals(expected, MatterGoldEmissive.isEmissive(quad), name + " pixel " + x + "," + y);
                        for (int vertex = 0; vertex < 4; vertex++) {
                            int offset = vertex * IQuadTransformer.STRIDE;
                            float u = sprite.getUOffset(Float.intBitsToFloat(quad.getVertices()[offset + IQuadTransformer.UV0]));
                            float v = sprite.getVOffset(Float.intBitsToFloat(quad.getVertices()[offset + IQuadTransformer.UV0 + 1]));
                            assertEquals(u / 16F, Float.intBitsToFloat(quad.getVertices()[offset]), 0.0001F);
                            assertEquals(v / 16F, Float.intBitsToFloat(quad.getVertices()[offset + 1]), 0.0001F);
                            assertEquals(expected ? LightTexture.FULL_BRIGHT : 0,
                                    quad.getVertices()[offset + IQuadTransformer.UV2]);
                        }
                    }
                    assertEquals(1, matches, name + " must cover each texel once");
                }
            }
        }
    }

    @Test
    void croppedAndMirroredFacesKeepTheirUvAreaAfterEmissiveSplitting() throws Exception {
        try (var contents = contents("molecular_manipulator")) {
            var sprite = new Sprite(contents);
            var input = face(sprite, 2, 1, 14, 15);
            var data = input.getVertices();
            for (int i = 0; i < 4; i++) {
                int index = i * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
                data[index] = Float.floatToRawIntBits(sprite.getU(16 - sprite.getUOffset(Float.intBitsToFloat(data[index]))));
            }
            var quads = new MatterGoldEmissive().apply(List.of(input));
            assertTrue(quads.stream().anyMatch(MatterGoldEmissive::isEmissive));
            double total = 0;
            for (var quad : quads) {
                var bounds = bounds(quad);
                assertTrue(bounds[0] >= 2 - 0.0001 && bounds[2] <= 14 + 0.0001);
                assertTrue(bounds[1] >= 1 - 0.0001 && bounds[3] <= 15 + 0.0001);
                total += (bounds[2] - bounds[0]) * (bounds[3] - bounds[1]);
            }
            assertEquals(12 * 14, total, 0.001);
        }
    }

    private static SpriteContents contents(String name) throws Exception {
        var path = Path.of("src/main/resources/assets/molecularmanipulator/textures/block", name + ".png");
        var metadata = AnimationMetadataSection.EMPTY;
        var sidecar = Path.of(path + ".mcmeta");
        if (Files.exists(sidecar)) {
            var json = JsonParser.parseString(Files.readString(sidecar)).getAsJsonObject().getAsJsonObject("animation");
            metadata = AnimationMetadataSection.SERIALIZER.fromJson(json);
        }
        try (var stream = Files.newInputStream(path)) {
            return new SpriteContents(new ResourceLocation("molecularmanipulator", "block/" + name),
                    new FrameSize(16, 16), NativeImage.read(stream), metadata, null);
        }
    }

    private static BakedQuad face(TextureAtlasSprite sprite, float u0, float v0, float u1, float v1) {
        int[] data = new int[IQuadTransformer.STRIDE * 4];
        for (int i = 0; i < 4; i++) {
            float u = i % 2 == 0 ? u0 : u1, v = i < 2 ? v0 : v1;
            int offset = i * IQuadTransformer.STRIDE;
            data[offset] = Float.floatToRawIntBits(u / 16F);
            data[offset + 1] = Float.floatToRawIntBits(v / 16F);
            data[offset + IQuadTransformer.COLOR] = -1;
            data[offset + IQuadTransformer.UV0] = Float.floatToRawIntBits(sprite.getU(u));
            data[offset + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(sprite.getV(v));
        }
        return new BakedQuad(data, -1, Direction.NORTH, sprite, true, true);
    }

    private static float[] bounds(BakedQuad quad) {
        float[] result = {16, 16, 0, 0};
        var sprite = quad.getSprite();
        for (int i = 0; i < 4; i++) {
            int offset = i * IQuadTransformer.STRIDE;
            float u = sprite.getUOffset(Float.intBitsToFloat(quad.getVertices()[offset + IQuadTransformer.UV0]));
            float v = sprite.getVOffset(Float.intBitsToFloat(quad.getVertices()[offset + IQuadTransformer.UV0 + 1]));
            result[0] = Math.min(result[0], u); result[2] = Math.max(result[2], u);
            result[1] = Math.min(result[1], v); result[3] = Math.max(result[3], v);
        }
        return result;
    }

    private static final class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents) {
            super(new ResourceLocation("minecraft", "textures/atlas/blocks.png"), contents, 256, 256, 32, 64);
        }
    }
}
