package com.atir.molecularmanipulator.client;

import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Exact hardware/layout baselines; both pattern layers must copy the anchor's original RGBA. */
class NegativeEntropyEnginePanelPatternAssetsTest {
    private static final Path BLOCK = Path.of("src/main/resources/assets/molecularmanipulator/textures/block");
    private static final Path BASELINE = Path.of("src/test/resources/assets/molecularmanipulator/entropy_panel_baseline");
    private static final Path CORE = BLOCK.resolve("negative_entropy_engine/controller_formed");
    private static final String[] TIERS = {"base", "quantum", "terminal"};
    private static final int[][] FACES = {{0, 0}, {48, 0}, {0, 48}, {48, 48}};
    private static final int FRAME_PIXELS = 128 * 128;

    static BufferedImage baseline(String tier, boolean light) throws IOException {
        return read(BASELINE.resolve("controller_formed_" + tier + (light ? "_light" : "") + ".png"));
    }

    private static int legacyPreviewColor(int panel, int source) {
        int result = 0xFF000000;
        for (int shift : new int[] {16, 8, 0}) {
            int p = (panel >>> shift) & 255, c = (source >>> shift) & 255;
            result |= (int) Math.round(p + (c - p) * 0.52) << shift;
        }
        return result;
    }

    static int patternedPanel(int panel, BufferedImage[] bands, int x, int y) {
        if (centralBoard(x, y)) return panel;
        return bands[y / 16].getRGB(x % 16, y % 16);
    }

    @Test
    void allConnectedBaseAtlasesKeepHardwareAndOnlyPatternOriginalPanelPixels() throws IOException {
        for (var tier : TIERS) {
            var palette = palette(tier);
            int[] legacy = pixels(baseline(tier, false));
            for (int mask = 0; mask < 16; mask++) {
                int[] clean = connected(legacy, mask, palette);
                int[] expected = clean.clone();
                int changed = 0;
                for (var point : points(clean, palette.panel)) {
                    expected[point.index] = palette.bands[point.row].getRGB(point.u, point.v);
                    if (expected[point.index] != clean[point.index]) changed++;
                }
                int[] actual = pixels(read(texture(tier, mask, false)));
                assertPixels(expected, actual, tier + " mask " + mask + " static hardware/central board/pattern");
                assertTrue(changed > 0, tier + " mask " + mask + " must actually contain anchor artwork");
            }
        }
    }

    @Test
    void allConnectedLightAtlasesKeepOldGlowAndCopyExactAnchorRgbaWithoutPaintingRims() throws IOException {
        for (var tier : TIERS) {
            var palette = palette(tier);
            int[] legacyBase = pixels(baseline(tier, false));
            int[] legacyLight = pixels(baseline(tier, true));
            for (int mask = 0; mask < 16; mask++) {
                var points = points(connected(legacyBase, mask, palette), palette.panel);
                var light = read(texture(tier, mask, true));
                assertEquals(128, light.getWidth());
                assertEquals(128 * 96, light.getHeight());
                assertTiming(texture(tier, mask, true));
                int[] firstAdded = null;
                boolean animated = false;
                int totalAdded = 0;
                for (int frame = 0; frame < 96; frame++) {
                    int[] expected = Arrays.copyOfRange(legacyLight, frame * FRAME_PIXELS, (frame + 1) * FRAME_PIXELS);
                    int[] added = new int[FRAME_PIXELS];
                    for (var point : points) {
                        int source = palette.lights[point.row].getRGB(point.u, frame * 16 + point.v);
                        if ((source >>> 24) == 0 || (expected[point.index] >>> 24) != 0) continue;
                        // Fullbright RGB/alpha must be identical, NOT blended toward the panel.
                        expected[point.index] = added[point.index] = source;
                        totalAdded++;
                    }
                    int[] actual = light.getRGB(0, frame * 128, 128, 128, null, 0, 128);
                    // Includes every transparent pixel outside the four UV rectangles and every original glow pixel.
                    assertPixels(expected, actual, tier + " mask " + mask + " light frame " + frame);
                    if (frame == 0) firstAdded = added;
                    else if (!Arrays.equals(firstAdded, added)) animated = true;
                }
                assertTrue(totalAdded > 0, tier + " mask " + mask + " needs real emissive background pixels");
                assertTrue(animated, tier + " mask " + mask + " background light must animate, not bake frame 47");
            }
        }
    }

    @Test
    void frame47KeepsApprovedPatternLayoutButMatchesTheAnchorsUnattenuatedBrightness() throws IOException {
        for (var tier : TIERS) {
            var palette = palette(tier);
            int[] clean = connected(pixels(baseline(tier, false)), 1, palette);
            var oldHardwareLight = baseline(tier, true);
            var base = read(texture(tier, 1, false));
            var light = read(texture(tier, 1, true));
            // Keep the historical approved layout, not its subsequently rejected 52% brightness.
            var approved = read(BASELINE.resolve("concept_" + tier + "_front_48px.png"));
            assertEquals(48, approved.getWidth());
            assertEquals(48, approved.getHeight());
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 48; x++) {
                    int expected = clean[y * 128 + x];
                    int oldPreview = expected;
                    int hardwareGlow = oldHardwareLight.getRGB(x, 47 * 128 + y);
                    if ((hardwareGlow >>> 24) != 0) {
                        expected = oldPreview = hardwareGlow;
                    } else if (expected == palette.panel && !centralBoard(x, y)) {
                        int sample = palette.bands[y / 16].getRGB(x % 16, y % 16);
                        int anchorGlow = palette.lights[y / 16].getRGB(x % 16, 47 * 16 + y % 16);
                        if ((anchorGlow >>> 24) != 0) sample = anchorGlow;
                        expected = sample;
                        oldPreview = legacyPreviewColor(palette.panel, sample);
                    }
                    assertEquals(approved.getRGB(x, y), oldPreview, tier + " approved layout " + x + "," + y);
                    int glow = light.getRGB(x, 47 * 128 + y);
                    assertTrue((glow >>> 24) == 0 || (glow >>> 24) == 255);
                    int actual = (glow >>> 24) == 0 ? base.getRGB(x, y) : glow;
                    assertEquals(expected, actual, tier + " equal-brightness pixel " + x + "," + y);
                }
            }
        }
    }

    @Test
    void canonicalAtlasesAreTheMaskZeroVariantsWithMatchingNinetySixFrameTiming() throws IOException {
        for (var tier : TIERS) {
            for (boolean light : new boolean[] {false, true}) {
                var canonical = CORE.resolve("controller_formed_" + tier + (light ? "_light" : "") + ".png");
                assertPixels(pixels(read(texture(tier, 0, light))), pixels(read(canonical)), tier + " canonical light=" + light);
                if (light) assertTiming(canonical);
            }
        }
    }

    private static List<PanelPoint> points(int[] clean, int panel) {
        var result = new ArrayList<PanelPoint>();
        for (var face : FACES) {
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 48; x++) {
                    int index = (face[1] + y) * 128 + face[0] + x;
                    if (clean[index] == panel && !centralBoard(x, y)) {
                        result.add(new PanelPoint(index, x % 16, y % 16, y / 16));
                    }
                }
            }
        }
        return result;
    }

    private static int[] connected(int[] legacy, int mask, Palette p) {
        int[] clean = legacy.clone();
        for (var face : FACES) {
            for (int y = 0; y < 48; y++) {
                for (int x = 0; x < 48; x++) {
                    if (Math.min(Math.min(x, 47 - x), Math.min(y, 47 - y)) >= 4) continue;
                    int remaining = 48;
                    int[] distances = {x, 47 - x, y, 47 - y};
                    for (int edge = 0; edge < 4; edge++) {
                        if ((mask & (1 << edge)) == 0) remaining = Math.min(remaining, distances[edge]);
                    }
                    int sx = (mask & 1) != 0 && x < 4 ? 4 : (mask & 2) != 0 && x > 43 ? 43 : x;
                    int sy = (mask & 4) != 0 && y < 4 ? 4 : (mask & 8) != 0 && y > 43 ? 43 : y;
                    clean[(face[1] + y) * 128 + face[0] + x] = remaining == 0 ? p.dark : remaining == 1 ? p.rim
                            : legacy[(face[1] + sy) * 128 + face[0] + sx];
                }
            }
        }
        return clean;
    }

    private static Palette palette(String tier) throws IOException {
        var anchor = BLOCK.resolve("entropy_anchor/" + tier);
        var bands = new BufferedImage[3];
        var lights = new BufferedImage[3];
        for (int row = 0; row < 3; row++) {
            bands[row] = read(anchor.resolve("band_west_r" + row + "c2.png"));
            lights[row] = read(anchor.resolve("band_west_r" + row + "c2_light.png"));
            assertEquals(16, bands[row].getWidth());
            assertEquals(16, bands[row].getHeight());
            assertEquals(16, lights[row].getWidth());
            assertEquals(16 * 96, lights[row].getHeight());
        }
        return new Palette(read(anchor.resolve("panel.png")).getRGB(8, 8), bands[0].getRGB(8, 0),
                bands[0].getRGB(8, 1), bands, lights);
    }

    private static Path texture(String tier, int mask, boolean light) {
        // Top/bottom band borders carry no glow, so V-edge masks are pixel-identical.
        int spriteMask = light ? mask & 3 : mask;
        return CORE.resolve("controller_formed_" + tier + "_connected_" + spriteMask + (light ? "_light" : "") + ".png");
    }

    private static boolean centralBoard(int x, int y) {
        return 15 <= x && x <= 32 && 15 <= y && y <= 32;
    }

    private static BufferedImage read(Path path) throws IOException {
        var im = ImageIO.read(path.toFile());
        assertNotNull(im, path.toString());
        return im;
    }

    private static int[] pixels(BufferedImage image) {
        return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
    }

    private static void assertTiming(Path png) throws IOException {
        var json = JsonParser.parseString(Files.readString(Path.of(png + ".mcmeta"))).getAsJsonObject();
        var animation = json.getAsJsonObject("animation");
        assertEquals(2, animation.get("frametime").getAsInt(), png.toString());
        assertFalse(animation.get("interpolate").getAsBoolean(), png.toString());
        assertFalse(animation.has("frames"), "all 96 sequential frames must be used");
    }

    private static void assertPixels(int[] expected, int[] actual, String context) {
        assertEquals(expected.length, actual.length, context);
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) {
                fail(context + " pixel index=" + i + " expected=" + Integer.toHexString(expected[i])
                        + " actual=" + Integer.toHexString(actual[i]));
            }
        }
    }

    private record PanelPoint(int index, int u, int v, int row) {}
    private record Palette(int panel, int dark, int rim, BufferedImage[] bands, BufferedImage[] lights) {}
}
