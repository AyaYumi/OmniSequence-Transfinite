package com.atir.molecularmanipulator.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Resource regressions for the baked 3x3x3 engine model; no client or renderer is needed. */
class NegativeEntropyEngineAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final Path TAGS = Path.of("src/main/resources/data/minecraft/tags/block");
    private static final String[] PARTS = {
        "negative_entropy_engine_core",
        "negative_entropy_engine_casing",
        "negative_entropy_observation_port"
    };
    private static final String CORE_FORMED =
            "molecularmanipulator:block/negative_entropy_engine_core/core_formed";
    private static final String ENGINE_TEXTURES = "molecularmanipulator:block/negative_entropy_engine/";
    private static final String BASE_TEXTURE = ENGINE_TEXTURES + "controller_formed/controller_formed";
    private static final String LIGHT_TEXTURE = BASE_TEXTURE + "_light";

    @Test
    void observationPortHasOnlyTheNewCanonicalIdAndNoLegacyRegistrationOrResources() throws IOException {
        String key = "block.molecularmanipulator.negative_entropy_observation_port";
        var registration = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/registry/ModContent.java"));
        assertFalse(registration.contains("\"negative_entropy_observation_window\""),
                "The retired window ID must not be registered or aliased again");
        assertEquals("负熵观测端口", json(ASSETS.resolve("lang/zh_cn.json")).get(key).getAsString());
        assertEquals("Negative Entropy Observation Port", json(ASSETS.resolve("lang/en_us.json")).get(key).getAsString());
        for (var language : new String[] {"zh_cn", "en_us"}) {
            assertFalse(json(ASSETS.resolve("lang/" + language + ".json"))
                    .has("block.molecularmanipulator.negative_entropy_observation_window"));
        }
        for (var path : new String[] {"blockstates/", "models/block/", "models/item/"}) {
            assertTrue(Files.isRegularFile(ASSETS.resolve(path + "negative_entropy_observation_port.json")));
            assertFalse(Files.exists(ASSETS.resolve(path + "negative_entropy_observation_window.json")));
        }
    }

    @Test
    void allEightCoreAndSixteenMemberVariantsUseTheCorrectModelsAndRotations() throws IOException {
        var facings = new String[] {"north", "east", "south", "west"};
        for (var part : PARTS) {
            boolean core = part.equals(PARTS[0]);
            var variants = json(ASSETS.resolve("blockstates/" + part + ".json")).getAsJsonObject("variants");
            assertEquals(core ? 8 : 16, variants.size(), part + " must contain exactly the legal variants");
            var visibilityKeys = core ? new String[] {""}
                    : new String[] {",invisible=false", ",invisible=true"};
            for (int facing = 0; facing < facings.length; facing++) {
                for (boolean formed : new boolean[] {false, true}) {
                    var modelId = "molecularmanipulator:block/" + part;
                    if (formed) modelId += core ? "/core_formed" : "_formed";
                    for (var visibility : visibilityKeys) {
                        var key = "facing=" + facings[facing] + ",formed=" + formed + visibility;
                        var context = part + " [" + key + "]";
                        assertTrue(variants.has(key), "missing variant " + context);
                        var variant = variants.getAsJsonObject(key);
                        assertEquals(modelId, variant.get("model").getAsString(), context);
                        assertEquals(0, variant.has("x") ? variant.get("x").getAsInt() : 0, context);
                        assertEquals(facing * 90, variant.has("y") ? variant.get("y").getAsInt() : 0, context);
                        assertFalse(variant.has("uvlock") && variant.get("uvlock").getAsBoolean(),
                                context + " must rotate its textures with the model");
                        assertModel(modelId);
                    }
                }
            }
            assertModel("molecularmanipulator:item/" + part);
        }
    }

    @Test
    void coreParticleTexturesResolveInBothFormedAndUnformedStates() throws IOException {
        var models = new String[] {"molecularmanipulator:block/" + PARTS[0], CORE_FORMED};
        var expected = new String[] {"molecularmanipulator:block/" + PARTS[0], ENGINE_TEXTURES + "casing_formed"};
        for (int state = 0; state < models.length; state++) {
            var textures = assertModel(models[state]).getAsJsonObject("textures");
            assertTrue(textures.has("particle"), models[state] + " must define a particle texture");
            var particle = resolveTexture("#particle", textures);
            assertEquals(expected[state], particle, models[state]);
            image(particle, 16, 16);
        }
    }

    @Test
    void formedCoreInheritsTheWholeShellAndFiveUnculledFullbrightFaces() throws IOException {
        var core = json(asset(CORE_FORMED, "models", ".json"));
        assertEquals(CORE_FORMED + "_base", core.get("parent").getAsString());
        assertFalse(core.has("elements"), "the core must inherit the complete base geometry");
        var textures = core.getAsJsonObject("textures");
        assertEquals(BASE_TEXTURE, textures.get("base").getAsString());
        assertEquals(LIGHT_TEXTURE, textures.get("light").getAsString());
        assertEquals(ENGINE_TEXTURES + "casing_formed", textures.get("particle").getAsString());
        var base = json(asset(CORE_FORMED + "_base", "models", ".json"));
        assertEquals("minecraft:block/block", qualified(base.get("parent").getAsString()));

        var model = assertModel(CORE_FORMED);
        assertEquals("cutout", model.get("render_type").getAsString());
        assertFalse(model.get("ambientocclusion").getAsBoolean());
        assertVector(model.getAsJsonArray("texture_size"), 128, 128);
        var elements = model.getAsJsonArray("elements");
        assertEquals(2, elements.size(), "one base shell and one light overlay");
        for (int layer = 0; layer < elements.size(); layer++) {
            var element = elements.get(layer).getAsJsonObject();
            assertVector(element.getAsJsonArray("from"), -16, -16, -16);
            assertVector(element.getAsJsonArray("to"), 32, 32, 32);
            assertFalse(element.has("rotation"), "the shell must stay axis-aligned");
            if (layer == 1) assertFalse(element.get("shade").getAsBoolean());
            var faces = element.getAsJsonObject("faces");
            var directions = layer == 0 ? Set.of("north", "east", "south", "west", "up", "down")
                    : Set.of("north", "east", "south", "west", "up");
            assertEquals(directions, faces.keySet(), "shell layer " + layer);
            for (var direction : directions) {
                var face = faces.getAsJsonObject(direction);
                assertEquals(layer == 0 ? "#base" : "#light", face.get("texture").getAsString());
                assertFalse(face.has("cullface"), direction + " must not be culled by a neighboring member");
                switch (direction) {
                    case "north" -> assertVector(face.getAsJsonArray("uv"), 0, 0, 6, 6);
                    case "up" -> assertVector(face.getAsJsonArray("uv"), 0, 6, 6, 12);
                    case "down" -> assertVector(face.getAsJsonArray("uv"), 6, 6, 12, 12);
                    default -> assertVector(face.getAsJsonArray("uv"), 6, 0, 12, 6);
                }
                if (layer == 1) {
                    var light = face.getAsJsonObject("neoforge_data");
                    assertNotNull(light, direction + " must declare fullbright lighting");
                    assertEquals(15, light.get("block_light").getAsInt(), direction);
                    assertEquals(15, light.get("sky_light").getAsInt(), direction);
                }
            }
        }
    }

    @Test
    void formedModelDeclaresAllTieredConnectionAtlases() throws IOException {
        var model = json(asset(CORE_FORMED, "models", ".json"));
        assertEquals("molecularmanipulator:negative_entropy_engine_formed", model.get("loader").getAsString());
        var textures = model.getAsJsonObject("textures");
        for (var tier : new String[] {"base", "quantum", "terminal"}) {
            image(textures.get("light_" + tier).getAsString(), 128, 128 * 96);
            for (int mask = 0; mask < 16; mask++) {
                var key = "base_" + tier + "_connected_" + mask;
                image(textures.get(key).getAsString(), 128, 128);
                image(resolveTexture("#light_" + tier + "_connected_" + mask, textures), 128, 128 * 96);
            }
        }
    }
    @Test
    void tierBordersMatchActualAnchorBandsAndConnectedCornersHaveNoCaps() throws IOException {
        int[][] faces = {{0, 0}, {48, 0}, {0, 48}, {48, 48}};
        for (var tier : new String[] {"base", "quantum", "terminal"}) {
            var anchor = image("molecularmanipulator:block/entropy_anchor/" + tier + "/band_west_r0c2", 16, 16);
            int dark = anchor.getRGB(8, 0);
            int rim = anchor.getRGB(8, 1);
            int panel = image("molecularmanipulator:block/entropy_anchor/" + tier + "/panel", 16, 16).getRGB(8, 8);
            var legacy = NegativeEntropyEnginePanelPatternAssetsTest.baseline(tier, false);
            var bands = new BufferedImage[3];
            for (int row = 0; row < 3; row++) {
                bands[row] = image("molecularmanipulator:block/entropy_anchor/" + tier + "/band_west_r" + row + "c2", 16, 16);
            }
            var normal = image(BASE_TEXTURE + "_" + tier, 128, 128);
            for (int mask = 0; mask < 16; mask++) {
                var connected = image(BASE_TEXTURE + "_" + tier + "_connected_" + mask, 128, 128);
                for (var face : faces) {
                    for (int y = 0; y < 48; y++) {
                        for (int x = 0; x < 48; x++) {
                            int[] distances = {x, 47 - x, y, 47 - y};
                            int remaining = 48;
                            for (int edge = 0; edge < 4; edge++) {
                                if ((mask & (1 << edge)) == 0) remaining = Math.min(remaining, distances[edge]);
                            }
                            int sx = (mask & 1) != 0 && x < 4 ? 4 : (mask & 2) != 0 && x > 43 ? 43 : x;
                            int sy = (mask & 4) != 0 && y < 4 ? 4 : (mask & 8) != 0 && y > 43 ? 43 : y;
                            int cleanSource = legacy.getRGB(face[0] + sx, face[1] + sy);
                            int expected = remaining == 0 ? dark : remaining == 1 ? rim
                                    : cleanSource == panel
                                            ? NegativeEntropyEnginePanelPatternAssetsTest.patternedPanel(panel, bands, x, y)
                                            : cleanSource;
                            String context = tier + " mask=" + mask + " face=" + Arrays.toString(face) + " pixel=" + x + "," + y;
                            assertEquals(expected, connected.getRGB(face[0] + x, face[1] + y), context);
                            // Independently require a TWO-pixel frame, not the previous four-pixel frame.
                            int d = Math.min(Math.min(x, 47 - x), Math.min(y, 47 - y));
                            if (d < 4) {
                                int innerX = Math.clamp(x, 4, 43), innerY = Math.clamp(y, 4, 43);
                                int cleanInner = legacy.getRGB(face[0] + innerX, face[1] + innerY);
                                int framePixel = d == 0 ? dark : d == 1 ? rim
                                        : cleanInner == panel
                                                ? NegativeEntropyEnginePanelPatternAssetsTest.patternedPanel(panel, bands, x, y)
                                                : cleanInner;
                                assertEquals(framePixel, normal.getRGB(face[0] + x, face[1] + y),
                                        context + " normal frame/extended port");
                            }
                        }
                    }
                    // At a single joined edge, the opposite border must run through the seam:
                    // the previous 4x4 corner preserved a stray vertical dark/rim stub here.
                    if (mask == 2) {
                        for (int x = 44; x < 48; x++) {
                            assertEquals(dark, connected.getRGB(face[0] + x, face[1]));
                            assertEquals(rim, connected.getRGB(face[0] + x, face[1] + 1));
                            assertEquals(NegativeEntropyEnginePanelPatternAssetsTest.patternedPanel(panel, bands, x, 2),
                                    connected.getRGB(face[0] + x, face[1] + 2));
                            assertEquals(NegativeEntropyEnginePanelPatternAssetsTest.patternedPanel(panel, bands, x, 3),
                                    connected.getRGB(face[0] + x, face[1] + 3));
                        }
                    }
                }
            }
        }
    }

    @Test
    void tierPortGuardsAndAllAnimatedFramesReachTheInnerRimWithoutCoveringTheFrame() throws IOException {
        int[][] faces = {{0, 0}, {48, 0}, {0, 48}, {48, 48}};
        for (var tier : new String[] {"base", "quantum", "terminal"}) {
            var normal = image(BASE_TEXTURE + "_" + tier, 128, 128);
            var legacy = NegativeEntropyEnginePanelPatternAssetsTest.baseline(tier, false);
            int panel = image("molecularmanipulator:block/entropy_anchor/" + tier + "/panel", 16, 16).getRGB(8, 8);
            var light = image(BASE_TEXTURE + "_" + tier + "_light", 128, 128 * 96);
            // Actual port and its two metal guards at the front top, bottom, left and right.
            // Neither may stop at the old four-pixel frame when the new frame is only two.
            for (int cross : new int[] {20, 24, 27}) {
                for (int gap : new int[] {2, 3}) {
                    assertEquals(normal.getRGB(cross, 4), normal.getRGB(cross, gap), tier + " top");
                    assertEquals(normal.getRGB(cross, 43), normal.getRGB(cross, 47 - gap), tier + " bottom");
                    assertEquals(normal.getRGB(4, cross), normal.getRGB(gap, cross), tier + " left");
                    assertEquals(normal.getRGB(43, cross), normal.getRGB(47 - gap, cross), tier + " right");
                }
            }
            assertNotEquals(normal.getRGB(8, 8), normal.getRGB(24, 2), tier + " top port must not be panel");
            for (int frame = 0; frame < 96; frame++) {
                for (var face : faces) {
                    for (int y = 0; y < 48; y++) {
                        for (int x = 0; x < 48; x++) {
                            int d = Math.min(Math.min(x, 47 - x), Math.min(y, 47 - y));
                            if (d >= 4) continue;
                            int actual = light.getRGB(face[0] + x, frame * 128 + face[1] + y);
                            if (d < 2) {
                                assertEquals(0, actual >>> 24, tier + " animated light must not paint the border");
                            } else {
                                int sx = Math.clamp(x, 4, 43), sy = Math.clamp(y, 4, 43);
                                // A panel now carries a spatially varying anchor pattern; only the original
                                // hardware cross-section is required to extend unchanged to the inner rim.
                                if (legacy.getRGB(face[0] + sx, face[1] + sy) != panel) {
                                    assertEquals(light.getRGB(face[0] + sx, frame * 128 + face[1] + sy), actual,
                                            tier + " frame " + frame + " extended hardware light at " + x + "," + y);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void formedAtlasAndAllNinetySixLightFramesHaveTheExpectedPixelsAndTiming() throws IOException {
        var base = image(BASE_TEXTURE, 128, 128);
        assertTransparentAndBrightFrame(base, 0, "base atlas");
        var light = image(LIGHT_TEXTURE, 128, 128 * 96);
        for (int frame = 0; frame < 96; frame++) {
            assertTransparentAndBrightFrame(light, frame, "light frame " + frame);
        }
        var animation = json(Path.of(assertTexture(LIGHT_TEXTURE) + ".mcmeta")).getAsJsonObject("animation");
        assertEquals(2, animation.get("frametime").getAsInt());
        assertFalse(animation.get("interpolate").getAsBoolean());
    }

    @Test
    void visibleFormedMembersHaveRealSixteenPixelFallbackPanels() throws IOException {
        for (var part : new String[] {PARTS[1], PARTS[2]}) {
            var model = assertModel("molecularmanipulator:block/" + part + "_formed");
            assertEquals("minecraft:block/cube_all", qualified(model.get("parent").getAsString()));
            var texture = ENGINE_TEXTURES + (part.equals(PARTS[1]) ? "casing_formed" : "observation_port_formed");
            assertEquals(texture, model.getAsJsonObject("textures").get("all").getAsString());
            image(texture, 16, 16);
            // The formed observation port is a mechanical panel, not translucent glass.
            if (part.equals(PARTS[2])) assertEquals("cutout", model.get("render_type").getAsString());
        }
    }

    @Test
    void unformedObservationPortHasVisibleArtworkAndSupportsTranslucency() throws IOException {
        var model = assertModel("molecularmanipulator:block/" + PARTS[2]);
        assertEquals("translucent", model.get("render_type").getAsString());
        var texture = resolveTexture("#all", model.getAsJsonObject("textures"));
        var window = image(texture, 16, 16);
        // Artwork may be an opaque lens panel or translucent glass; neither
        // requires changing the model's transparency-capable render layer.
        assertTrue(Arrays.stream(framePixels(window, 0)).anyMatch(pixel -> (pixel >>> 24) > 0),
                "the unformed observation port must have visible pixels");
    }

    @Test
    void shellBlocksAreMineableWithAPickaxeAndDropThemselves() throws IOException {
        var pickaxe = tagValues("mineable/pickaxe.json");
        var diamond = tagValues("needs_diamond_tool.json");
        for (var part : PARTS) {
            var id = "molecularmanipulator:" + part;
            assertTrue(pickaxe.contains(id), id + " must be in mineable/pickaxe");
            assertTrue(diamond.contains(id), id + " must be in needs_diamond_tool");
            var drop = json(Path.of("src/main/resources/data/molecularmanipulator/loot_table/blocks")
                    .resolve(part + ".json")).getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("minecraft:item", drop.get("type").getAsString(), id);
            assertEquals(id, drop.get("name").getAsString(), id + " must drop itself");
        }
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static String qualified(String id) {
        return id.contains(":") ? id : "minecraft:" + id;
    }

    private static Path asset(String id, String directory, String suffix) {
        var parts = qualified(id).split(":", 2);
        return ASSETS.resolveSibling(parts[0]).resolve(directory).resolve(parts[1] + suffix);
    }

    private static JsonObject assertModel(String modelId) throws IOException {
        var model = inheritedModel(modelId, new LinkedHashSet<>());
        var textures = model.getAsJsonObject("textures");
        for (var key : textures.keySet()) {
            assertTexture(resolveTexture("#" + key, textures));
        }
        if (model.has("elements")) {
            for (var element : model.getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    var reference = face.getValue().getAsJsonObject().get("texture").getAsString();
                    assertTexture(resolveTexture(reference, textures));
                }
            }
        }
        return model;
    }

    // Merge first: the abstract base intentionally leaves #base/#light for its child to supply.
    private static JsonObject inheritedModel(String modelId, Set<String> parents) throws IOException {
        var id = qualified(modelId);
        assertTrue(parents.add(id), "model parent cycle: " + parents + " -> " + id);
        var file = asset(id, "models", ".json");
        assertTrue(Files.isRegularFile(file), "missing model " + id);
        var own = json(file);
        var model = new JsonObject();
        if (own.has("parent")) {
            var parent = qualified(own.get("parent").getAsString());
            // Vanilla parents (including unqualified block/block) come from Minecraft, not this mod.
            if (!parent.startsWith("minecraft:")) model = inheritedModel(parent, parents);
        }
        var textures = model.has("textures") ? model.getAsJsonObject("textures").deepCopy() : new JsonObject();
        if (own.has("textures")) {
            for (var entry : own.getAsJsonObject("textures").entrySet()) {
                textures.add(entry.getKey(), entry.getValue());
            }
        }
        for (var entry : own.entrySet()) {
            if (!entry.getKey().equals("textures")) model.add(entry.getKey(), entry.getValue());
        }
        model.add("textures", textures);
        parents.remove(id);
        return model;
    }

    private static String resolveTexture(String reference, JsonObject textures) {
        var seen = new LinkedHashSet<String>();
        while (reference.startsWith("#")) {
            var key = reference.substring(1);
            assertTrue(seen.add(key), "texture reference cycle: " + seen + " -> " + reference);
            assertTrue(textures.has(key), "unresolved texture " + reference);
            reference = textures.get(key).getAsString();
        }
        return reference;
    }

    private static Path assertTexture(String textureId) {
        var file = asset(textureId, "textures", ".png");
        assertTrue(Files.isRegularFile(file), "missing texture " + textureId);
        return file;
    }

    private static BufferedImage image(String textureId, int width, int height) throws IOException {
        var image = ImageIO.read(assertTexture(textureId).toFile());
        assertNotNull(image, textureId + " must load as an image");
        assertEquals(width, image.getWidth(), textureId + " width");
        assertEquals(height, image.getHeight(), textureId + " height");
        return image;
    }

    private static int[] framePixels(BufferedImage image, int frame) {
        int size = image.getWidth();
        return image.getRGB(0, frame * size, size, size, null, 0, size);
    }

    private static void assertTransparentAndBrightFrame(BufferedImage image, int frame, String context) {
        var pixels = framePixels(image, frame);
        assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) == 0),
                context + " must contain transparent pixels");
        assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) > 0
                        && ((pixel >>> 16) & 255) >= 192 && ((pixel >>> 8) & 255) >= 192 && (pixel & 255) >= 192),
                context + " must contain visible bright pixels");
    }

    private static void assertVector(JsonArray actual, int... expected) {
        assertNotNull(actual);
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals((double) expected[i], actual.get(i).getAsDouble(), "coordinate " + i);
        }
    }

    private static Set<String> tagValues(String name) throws IOException {
        var values = new LinkedHashSet<String>();
        for (var entry : json(TAGS.resolve(name)).getAsJsonArray("values")) {
            values.add(entry.getAsString());
        }
        return values;
    }
}
