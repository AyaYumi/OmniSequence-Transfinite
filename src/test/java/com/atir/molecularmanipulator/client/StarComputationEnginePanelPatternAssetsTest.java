package com.atir.molecularmanipulator.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Semantic checks for the v3 three-tier, sixteen-mask engine connection atlases. */
class StarComputationEnginePanelPatternAssetsTest {
    private static final Path BLOCK = Path.of("src/main/resources/assets/molecularmanipulator/textures/block");
    private static final Path CORE = BLOCK.resolve("star_computation_engine/controller_formed");
    private static final Path ANCHORS = BLOCK.resolve("star_position_anchor");
    private static final Path MODEL = Path.of(
            "src/main/resources/assets/molecularmanipulator/models/block/star_computation_engine_core/core_formed.json");
    private static final String TEXTURE_ID_PREFIX =
            "molecularmanipulator:block/star_computation_engine/controller_formed/controller_formed_";
    private static final String[] TIERS = {"base", "quantum", "terminal"};
    private static final int[][] FACES = {{0, 0}, {48, 0}, {0, 48}, {48, 48}};
    private static final int FRAMES = 96;
    private static final int BOARD_MIN = 15;
    private static final int BOARD_MAX = 32;
    private static final int BOARD_INNER_MIN = 16;
    private static final int BOARD_INNER_MAX = 31;

    @Test
    void openEdgesKeepTheTwoPixelAnchorBorderAndConnectedEdgesDrawTheTierChannel() throws IOException {
        for (var tier : TIERS) {
            var palette = palette(tier);
            for (int mask = 0; mask < 16; mask++) {
                var atlas = read(baseAtlas(tier, mask));
                assertEquals(128, atlas.getWidth(), tier + " mask " + mask + " width");
                assertEquals(128, atlas.getHeight(), tier + " mask " + mask + " height");
                for (var face : FACES) {
                    for (int edge = 0; edge < 4; edge++) {
                        String context = tier + " mask=" + mask + " face=" + Arrays.toString(face) + " edge=" + edge;
                        if ((mask & (1 << edge)) == 0) {
                            assertOpenEdge(atlas, face[0], face[1], edge, palette, context);
                        } else {
                            assertChannel(atlas, face[0], face[1], edge, palette.accent(), context);
                        }
                    }
                }
            }
        }
    }

    @Test
    void everyVariantKeepsTheCentralBoardAndTheFourBaseUvFacesWithTransparentGutters() throws IOException {
        for (var tier : TIERS) {
            var palette = palette(tier);
            var canonical = read(baseAtlas(tier, 0));
            for (int mask = 0; mask < 16; mask++) {
                var atlas = read(baseAtlas(tier, mask));
                String context = tier + " mask=" + mask;
                assertBaseUvLayout(atlas, context);
                for (var face : FACES) {
                    for (int y = BOARD_INNER_MIN; y <= BOARD_INNER_MAX; y++) {
                        for (int x = BOARD_INNER_MIN; x <= BOARD_INNER_MAX; x++) {
                            assertEquals(canonical.getRGB(face[0] + x, face[1] + y),
                                    atlas.getRGB(face[0] + x, face[1] + y),
                                    context + " board interior " + x + "," + y);
                        }
                    }
                    // v3 paints only the shared one-pixel accent frame around the board when a
                    // connection exists; no mask-specific artwork may enter the 15..32 board.
                    int frame = mask == 0 ? palette.innerBorder() : palette.accent();
                    for (int y = BOARD_MIN; y <= BOARD_MAX; y++) {
                        for (int x = BOARD_MIN; x <= BOARD_MAX; x++) {
                            if (x != BOARD_MIN && x != BOARD_MAX && y != BOARD_MIN && y != BOARD_MAX) continue;
                            assertEquals(frame, atlas.getRGB(face[0] + x, face[1] + y),
                                    context + " board frame " + x + "," + y);
                        }
                    }
                }
            }
        }
    }

    @Test
    void allFourLightVariantsAnimateNinetySixFramesWithoutBlankFramesOrOpaqueGutters() throws IOException {
        for (var tier : TIERS) {
            for (int mask = 0; mask < 4; mask++) {
                var path = lightAtlas(tier, mask);
                var atlas = read(path);
                assertEquals(128, atlas.getWidth(), tier + " mask " + mask + " light width");
                assertEquals(128 * FRAMES, atlas.getHeight(), tier + " mask " + mask + " light height");
                assertTiming(path);
                int[] firstFrame = null;
                boolean animated = false;
                for (int frame = 0; frame < FRAMES; frame++) {
                    int[] pixels = atlas.getRGB(0, frame * 128, 128, 128, null, 0, 128);
                    if (firstFrame == null) firstFrame = pixels;
                    else if (!animated && !Arrays.equals(firstFrame, pixels)) animated = true;

                    String context = tier + " mask=" + mask + " light frame=" + frame;
                    for (int y = 0; y < 128; y++) {
                        for (int x = 0; x < 128; x++) {
                            if (!insideUv(x, y)) {
                                assertEquals(0, pixels[y * 128 + x] >>> 24,
                                        context + " opaque gutter at " + x + "," + y);
                            }
                        }
                    }
                    for (var face : FACES) {
                        int transparent = 0;
                        int opaque = 0;
                        for (int y = 0; y < 48; y++) {
                            for (int x = 0; x < 48; x++) {
                                if ((pixels[(face[1] + y) * 128 + face[0] + x] >>> 24) == 0) transparent++;
                                else opaque++;
                            }
                        }
                        assertTrue(transparent > 0,
                                context + " face " + Arrays.toString(face) + " must keep transparency");
                        assertTrue(opaque > 0,
                                context + " face " + Arrays.toString(face) + " must contain artwork");
                    }
                }
                assertTrue(animated, tier + " mask " + mask + " light animation must not be static");
            }
        }
    }

    @Test
    void lightAtlasesExistOnlyForMasksZeroThroughThreeAndTheModelAliasesHigherMasks() throws IOException {
        var textures = json(MODEL).getAsJsonObject("textures");
        for (var tier : TIERS) {
            for (int mask = 0; mask < 4; mask++) {
                String key = "light_" + tier + "_connected_" + mask;
                var value = textures.get(key);
                assertNotNull(value, "missing model texture " + key);
                assertEquals(TEXTURE_ID_PREFIX + tier + "_connected_" + mask + "_light",
                        value.getAsString(), key);
                assertTrue(Files.isRegularFile(lightAtlas(tier, mask)), "missing light atlas " + key);
            }
            for (int mask = 4; mask < 16; mask++) {
                String key = "light_" + tier + "_connected_" + mask;
                var value = textures.get(key);
                assertNotNull(value, "missing model texture " + key);
                assertEquals("#light_" + tier + "_connected_" + (mask & 3), value.getAsString(), key);
                var unexpected = lightAtlas(tier, mask);
                assertFalse(Files.exists(unexpected), "light masks 4..15 must alias, not add " + unexpected);
                assertFalse(Files.exists(Path.of(unexpected + ".mcmeta")),
                        "light masks 4..15 must not add animation metadata " + unexpected);
            }
        }
    }

    @Test
    void modelUsesConnectedBaseAtlasesAndCanonicalLightsKeepMaskZeroTiming() throws IOException {
        var textures = json(MODEL).getAsJsonObject("textures");
        for (var tier : TIERS) {
            for (int mask = 0; mask < 16; mask++) {
                assertEquals(TEXTURE_ID_PREFIX + tier + "_connected_" + mask,
                        textures.get("base_" + tier + "_connected_" + mask).getAsString());
            }
            var canonical = canonicalLightAtlas(tier);
            assertArrayEquals(pixels(read(lightAtlas(tier, 0))), pixels(read(canonical)), tier);
            assertTiming(canonical);
        }
    }

    private static void assertOpenEdge(BufferedImage atlas, int ox, int oy, int edge, Palette palette,
            String context) {
        for (int along = 0; along < 48; along++) {
            assertEquals(palette.outerBorder(),
                    atlas.getRGB(edgeX(ox, edge, along, 0), edgeY(oy, edge, along, 0)),
                    context + " outer border " + along);
            if (along > 0 && along < 47) {
                assertEquals(palette.innerBorder(),
                        atlas.getRGB(edgeX(ox, edge, along, 1), edgeY(oy, edge, along, 1)),
                        context + " inner border " + along);
            }
        }
    }

    private static void assertChannel(BufferedImage atlas, int ox, int oy, int edge, int accent,
            String context) {
        for (int along : new int[] {23, 24}) {
            for (int depth = 0; depth < 5; depth++) {
                assertEquals(accent, atlas.getRGB(edgeX(ox, edge, along, depth), edgeY(oy, edge, along, depth)),
                        context + " channel " + along + "/" + depth);
            }
        }
    }

    private static void assertBaseUvLayout(BufferedImage atlas, String context) {
        assertEquals(128, atlas.getWidth(), context + " width");
        assertEquals(128, atlas.getHeight(), context + " height");
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                if (!insideUv(x, y)) {
                    assertEquals(0, atlas.getRGB(x, y) >>> 24, context + " outside UV at " + x + "," + y);
                }
            }
        }
        for (var face : FACES) {
            int opaque = 0;
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 48; x++) {
                    if ((atlas.getRGB(face[0] + x, face[1] + y) >>> 24) != 0) opaque++;
                }
            }
            assertTrue(opaque > 0, context + " UV face " + Arrays.toString(face) + " must contain artwork");
        }
    }

    private static int edgeX(int ox, int edge, int along, int depth) {
        return ox + switch (edge) {
            case 0 -> depth;
            case 1 -> 47 - depth;
            default -> along;
        };
    }

    private static int edgeY(int oy, int edge, int along, int depth) {
        return oy + switch (edge) {
            case 2 -> depth;
            case 3 -> 47 - depth;
            default -> along;
        };
    }

    private static boolean insideUv(int x, int y) {
        for (var face : FACES) {
            if (x >= face[0] && x < face[0] + 48 && y >= face[1] && y < face[1] + 48) return true;
        }
        return false;
    }

    private static Palette palette(String tier) throws IOException {
        var band = read(ANCHORS.resolve(tier.equals("base") ? "reference" : tier).resolve("band_west_r0c2.png"));
        assertEquals(16, band.getWidth(), tier + " anchor band width");
        assertEquals(16, band.getHeight(), tier + " anchor band height");
        int outer = band.getRGB(0, 0);
        int inner = band.getRGB(0, 1);
        int panel = band.getRGB(0, 2);
        var colors = new LinkedHashSet<Integer>();
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) colors.add(band.getRGB(x, y));
        }
        colors.remove(outer);
        colors.remove(inner);
        colors.remove(panel);
        assertEquals(1, colors.size(), tier + " anchor band must expose one tier accent color");
        return new Palette(outer, inner, colors.iterator().next());
    }

    private static Path baseAtlas(String tier, int mask) {
        return CORE.resolve("controller_formed_" + tier + "_connected_" + mask + ".png");
    }

    private static Path lightAtlas(String tier, int mask) {
        return CORE.resolve("controller_formed_" + tier + "_connected_" + mask + "_light.png");
    }

    private static Path canonicalLightAtlas(String tier) {
        return CORE.resolve("controller_formed_" + tier + "_light.png");
    }

    private static BufferedImage read(Path path) throws IOException {
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, "unreadable image " + path);
        return image;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void assertTiming(Path png) throws IOException {
        var animation = json(Path.of(png + ".mcmeta")).getAsJsonObject("animation");
        assertNotNull(animation, png + " needs an animation block");
        assertEquals(2, animation.get("frametime").getAsInt(), png.toString());
        assertFalse(animation.get("interpolate").getAsBoolean(), png.toString());
        assertFalse(animation.has("frames"), png + " must use all 96 sequential frames");
    }

    private record Palette(int outerBorder, int innerBorder, int accent) {}
}
