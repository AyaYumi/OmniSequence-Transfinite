package com.atir.molecularmanipulator.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** JSON/PNG regressions only: no Minecraft classes, registries or game startup. */
class EntropyAnchorAssetsTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/molecularmanipulator");
    private static final String[] TIERS = {"base", "quantum", "terminal"};
    private static final String[] FACINGS = {"north", "east", "south", "west"};
    private static final Set<String> FACES = Set.of("north", "east", "south", "west", "up", "down");

    @Test
    void eachTierCoversAll144LegalStatesAndExactly18FormedModels() throws IOException {
        for (var tier : TIERS) {
            var block = tier + "_entropy_anchor";
            var variants = json(ASSETS.resolve("blockstates/" + block + ".json")).getAsJsonObject("variants");
            assertEquals(144, variants.size(), block);
            // Variant property order is not significant to Minecraft.
            var states = new HashMap<Map<String, String>, JsonObject>();
            for (var entry : variants.entrySet()) {
                var properties = new HashMap<String, String>();
                for (var property : entry.getKey().split(",")) {
                    var pair = property.split("=", -1);
                    assertEquals(2, pair.length, entry.getKey());
                    assertNull(properties.put(pair[0], pair[1]), "duplicate property " + entry.getKey());
                }
                assertNull(states.put(properties, entry.getValue().getAsJsonObject()), "duplicate state " + properties);
            }
            var expectedFiles = new HashSet<String>();
            for (int facing = 0; facing < FACINGS.length; facing++) {
                for (boolean formed : new boolean[] {false, true}) {
                    for (int pos = 0; pos < 9; pos++) {
                        for (boolean end : new boolean[] {false, true}) {
                            var state = Map.of("facing", FACINGS[facing], "formed", String.valueOf(formed),
                                    "anchor_pos", String.valueOf(pos), "anchor_end", String.valueOf(end));
                            var context = block + " " + state;
                            var variant = states.get(state);
                            assertNotNull(variant, context);
                            var model = "molecularmanipulator:block/" + block;
                            if (formed) {
                                var file = formedName(pos, end);
                                model += "/" + file;
                                expectedFiles.add(file + ".json");
                            }
                            assertEquals(model, variant.get("model").getAsString(), context);
                            assertEquals(0, variant.has("x") ? variant.get("x").getAsInt() : 0, context);
                            assertEquals(facing * 90, variant.has("y") ? variant.get("y").getAsInt() : 0, context);
                            assertFalse(variant.has("uvlock") && variant.get("uvlock").getAsBoolean(), context);
                        }
                    }
                }
            }
            try (var files = Files.list(ASSETS.resolve("models/block/" + block))) {
                assertEquals(expectedFiles, files.map(path -> path.getFileName().toString())
                        .collect(Collectors.toSet()), block);
            }
        }
    }

    @Test
    void formedModelsHaveSixBaseFacesAndOnlyTheirExposedFullbrightFaces() throws IOException {
        for (var tier : TIERS) {
            var textureRoot = "molecularmanipulator:block/entropy_anchor/" + tier + "/";
            for (int pos = 0; pos < 9; pos++) {
                for (boolean end : new boolean[] {false, true}) {
                    var id = "molecularmanipulator:block/" + tier + "_entropy_anchor/" + formedName(pos, end);
                    var model = assertModel(id);
                    assertEquals("minecraft:block/block", qualified(model.get("parent").getAsString()), id);
                    assertEquals("minecraft:cutout", qualified(model.get("render_type").getAsString()), id);
                    var textures = model.getAsJsonObject("textures");
                    var exposed = exposedTextures(pos, end);
                    var elements = model.getAsJsonArray("elements");
                    assertEquals(exposed.isEmpty() ? 1 : 2, elements.size(), id);
                    for (int layer = 0; layer < elements.size(); layer++) {
                        var element = elements.get(layer).getAsJsonObject();
                        assertVector(element.getAsJsonArray("from"), 0, 0, 0);
                        assertVector(element.getAsJsonArray("to"), 16, 16, 16);
                        var faces = element.getAsJsonObject("faces");
                        assertEquals(layer == 0 ? FACES : exposed.keySet(), faces.keySet(), id);
                        if (layer == 1) assertFalse(element.get("shade").getAsBoolean(), id);
                        for (var direction : faces.keySet()) {
                            var face = faces.getAsJsonObject(direction);
                            var texture = exposed.getOrDefault(direction, "panel") + (layer == 1 ? "_light" : "");
                            assertEquals(textureRoot + texture,
                                    resolveTexture(face.get("texture").getAsString(), textures), id + " " + direction);
                            if (layer == 1) {
                                var light = face.getAsJsonObject("neoforge_data");
                                assertNotNull(light, id + " " + direction);
                                assertEquals(15, light.get("block_light").getAsInt(), id);
                                assertEquals(15, light.get("sky_light").getAsInt(), id);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void staticTexturesAre16SquareAndAnimationsHave96FramesWithCorrectTiming() throws IOException {
        for (var tier : TIERS) {
            try (var files = Files.list(ASSETS.resolve("textures/block/entropy_anchor/" + tier))) {
                var paths = files.toList();
                assertFalse(paths.isEmpty(), tier);
                for (var path : paths) {
                    var name = path.getFileName().toString();
                    if (name.endsWith(".mcmeta")) {
                        assertTrue(Files.isRegularFile(path.resolveSibling(name.replaceFirst("\\.mcmeta$", ""))),
                                "orphan animation metadata " + path);
                        continue;
                    }
                    assertTrue(name.endsWith(".png"), path.toString());
                    boolean animated = name.endsWith("_light.png");
                    assertImage(path, 16, animated ? 16 * 96 : 16);
                    var metadata = Path.of(path + ".mcmeta");
                    assertEquals(animated, Files.isRegularFile(metadata), path.toString());
                    if (!animated) continue;
                    var animation = json(metadata).getAsJsonObject("animation");
                    assertEquals(2, animation.get("frametime").getAsInt(), path.toString());
                    assertFalse(animation.get("interpolate").getAsBoolean(), path.toString());
                    for (var dimension : new String[] {"width", "height"}) {
                        if (animation.has(dimension)) assertEquals(16, animation.get(dimension).getAsInt());
                    }
                    // Omitted frames means all 96; an explicit list must still play every frame once.
                    if (animation.has("frames")) {
                        var frames = animation.getAsJsonArray("frames");
                        assertEquals(96, frames.size(), path.toString());
                        var indices = new HashSet<Integer>();
                        for (var frame : frames) {
                            int index = frame.isJsonPrimitive() ? frame.getAsInt()
                                    : frame.getAsJsonObject().get("index").getAsInt();
                            assertTrue(index >= 0 && index < 96 && indices.add(index), path.toString());
                            if (frame.isJsonObject() && frame.getAsJsonObject().has("time")) {
                                assertEquals(2, frame.getAsJsonObject().get("time").getAsInt());
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void unformedBlocksAndItemsKeepTheirOriginalModelsAndValidTextures() throws IOException {
        for (var tier : TIERS) {
            var blockId = "molecularmanipulator:block/" + tier + "_entropy_anchor";
            var block = assertModel(blockId);
            assertEquals("minecraft:block/cube_all", qualified(block.get("parent").getAsString()));
            assertEquals(blockId, resolveTexture("#all", block.getAsJsonObject("textures")));
            assertImage(assertTexture(blockId), 16, 16);
            var itemId = "molecularmanipulator:item/" + tier + "_entropy_anchor";
            var item = assertModel(itemId);
            assertEquals(blockId, qualified(item.get("parent").getAsString()));
        }
    }

    @Test
    void existingMiningTagsAndLootTablesStillAllowMiningAndSelfDrops() throws IOException {
        for (var tag : new String[] {"mineable/pickaxe", "needs_diamond_tool"}) {
            var path = RESOURCES.resolve("data/minecraft/tags/block/" + tag + ".json");
            if (!Files.isRegularFile(path)) continue;
            var values = new HashSet<String>();
            for (var value : json(path).getAsJsonArray("values")) {
                values.add(value.isJsonPrimitive() ? value.getAsString() : value.getAsJsonObject().get("id").getAsString());
            }
            for (var tier : TIERS) assertTrue(values.contains("molecularmanipulator:" + tier + "_entropy_anchor"), tag);
        }
        for (var tier : TIERS) {
            var name = tier + "_entropy_anchor";
            var path = RESOURCES.resolve("data/molecularmanipulator/loot_table/blocks/" + name + ".json");
            if (!Files.isRegularFile(path)) continue;
            var loot = json(path);
            assertEquals("minecraft:block", loot.get("type").getAsString());
            var entry = loot.getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("minecraft:item", entry.get("type").getAsString());
            assertEquals("molecularmanipulator:" + name, entry.get("name").getAsString());
        }
    }

    private static String formedName(int pos, boolean end) {
        return "formed_r" + pos / 3 + "c" + pos % 3 + (end ? "_end" : "");
    }

    private static Map<String, String> exposedTextures(int pos, boolean end) {
        int row = pos / 3, column = pos % 3;
        var cell = "_r" + row + "c" + column;
        var suffix = cell + (end ? "_end" : "");
        var faces = new HashMap<String, String>();
        if (end) faces.put("north", "cap" + cell);
        if (row == 0) faces.put("up", "band_up" + suffix);
        if (row == 2) faces.put("down", "band_down" + suffix);
        if (column == 0) faces.put("east", "band_east" + suffix);
        if (column == 2) faces.put("west", "band_west" + suffix);
        return faces;
    }

    private static JsonObject assertModel(String id) throws IOException {
        var model = inheritedModel(id, new HashSet<>());
        var textures = model.getAsJsonObject("textures");
        assertTrue(textures.has("particle"), id + " needs a particle texture");
        for (var key : textures.keySet()) assertTexture(resolveTexture("#" + key, textures));
        if (model.has("elements")) {
            for (var element : model.getAsJsonArray("elements")) {
                for (var face : element.getAsJsonObject().getAsJsonObject("faces").entrySet()) {
                    assertTexture(resolveTexture(face.getValue().getAsJsonObject().get("texture").getAsString(), textures));
                }
            }
        }
        return model;
    }

    private static JsonObject inheritedModel(String id, Set<String> parents) throws IOException {
        id = qualified(id);
        assertTrue(parents.add(id), "model parent cycle " + id);
        var own = json(asset(id, "models", ".json"));
        assertFalse(own.has("loader"), id + " must use vanilla model baking");
        var model = new JsonObject();
        if (own.has("parent")) {
            var parent = qualified(own.get("parent").getAsString());
            if (!parent.startsWith("minecraft:")) {
                model = inheritedModel(parent, parents);
            } else {
                assertTrue(Set.of("minecraft:block/block", "minecraft:block/cube_all").contains(parent), parent);
                if (parent.equals("minecraft:block/cube_all")) {
                    // Vanilla cube_all supplies particle = #all; vanilla assets are not in this source tree.
                    var defaults = new JsonObject();
                    defaults.addProperty("particle", "#all");
                    model.add("textures", defaults);
                }
            }
        }
        var textures = model.has("textures") ? model.getAsJsonObject("textures").deepCopy() : new JsonObject();
        if (own.has("textures")) own.getAsJsonObject("textures").entrySet()
                .forEach(entry -> textures.add(entry.getKey(), entry.getValue()));
        for (var entry : own.entrySet()) {
            if (!entry.getKey().equals("textures")) model.add(entry.getKey(), entry.getValue());
        }
        model.add("textures", textures);
        parents.remove(id);
        return model;
    }

    private static String resolveTexture(String reference, JsonObject textures) {
        var seen = new HashSet<String>();
        while (reference.startsWith("#")) {
            var key = reference.substring(1);
            assertTrue(seen.add(key), "texture cycle " + reference);
            assertTrue(textures.has(key), "unresolved texture " + reference);
            reference = textures.get(key).getAsString();
        }
        return reference;
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

    private static Path assertTexture(String id) {
        var path = asset(id, "textures", ".png");
        assertTrue(Files.isRegularFile(path), "missing texture " + id);
        return path;
    }

    private static void assertImage(Path path, int width, int height) throws IOException {
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, path.toString());
        assertEquals(width, image.getWidth(), path.toString());
        assertEquals(height, image.getHeight(), path.toString());
    }

    private static void assertVector(JsonArray actual, int... expected) {
        assertNotNull(actual);
        assertEquals(expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) assertEquals((double) expected[i], actual.get(i).getAsDouble());
    }
}
