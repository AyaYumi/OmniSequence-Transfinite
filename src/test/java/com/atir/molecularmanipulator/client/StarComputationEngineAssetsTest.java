package com.atir.molecularmanipulator.client;

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

/** Central-part resource regressions; formed visuals belong to the orrery renderer. */
class StarComputationEngineAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final Path TAGS = Path.of("src/main/resources/data/minecraft/tags/block");
    private static final String[] PARTS = {
        "star_computation_engine_core",
        "star_computation_engine_casing",
        "star_computation_observation_port"
    };

    @Test
    void observationPortHasOnlyTheNewCanonicalIdAndNoLegacyRegistrationOrResources() throws IOException {
        String key = "block.molecularmanipulator.star_computation_observation_port";
        var registration = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/registry/ModContent.java"));
        assertFalse(registration.contains("\"star_computation_observation_window\""),
                "The retired window ID must not be registered or aliased again");
        assertEquals("星算观测端口", json(ASSETS.resolve("lang/zh_cn.json")).get(key).getAsString());
        assertEquals("Star Computation Observation Port", json(ASSETS.resolve("lang/en_us.json")).get(key).getAsString());
        for (var language : new String[] {"zh_cn", "en_us"}) {
            assertFalse(json(ASSETS.resolve("lang/" + language + ".json"))
                    .has("block.molecularmanipulator.star_computation_observation_window"));
        }
        for (var path : new String[] {"blockstates/", "models/block/", "models/item/"}) {
            assertTrue(Files.isRegularFile(ASSETS.resolve(path + "star_computation_observation_port.json")));
            assertFalse(Files.exists(ASSETS.resolve(path + "star_computation_observation_window.json")));
        }
    }

    @Test
    void allEightCoreAndSixteenMemberVariantsUseOrdinaryModelsAndCorrectRotations() throws IOException {
        var facings = new String[] {"north", "east", "south", "west"};
        for (var part : PARTS) {
            boolean core = part.equals(PARTS[0]);
            var variants = json(ASSETS.resolve("blockstates/" + part + ".json")).getAsJsonObject("variants");
            assertEquals(core ? 8 : 16, variants.size(), part + " must contain exactly the legal variants");
            var visibilityKeys = core ? new String[] {""}
                    : new String[] {",invisible=false", ",invisible=true"};
            var modelId = "molecularmanipulator:block/" + part;
            for (int facing = 0; facing < facings.length; facing++) {
                for (boolean formed : new boolean[] {false, true}) {
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
        }
    }

    @Test
    void ordinaryPartsAndItemsKeepTheirOriginalTexturesAndValidParticles() throws IOException {
        for (var part : PARTS) {
            var modelId = "molecularmanipulator:block/" + part;
            var model = assertModel(modelId);
            var textures = model.getAsJsonObject("textures");
            assertTrue(textures.has("particle"), modelId + " must define a particle texture");
            assertEquals(modelId, resolveTexture("#particle", textures), modelId);
            image(modelId, 16, 16);
            var item = assertModel("molecularmanipulator:item/" + part);
            assertEquals(modelId, item.get("parent").getAsString(), part + " item must keep its ordinary model");
        }
    }

    @Test
    void retiredWholeBodyModelsAtlasesAndFormedFallbacksAreAbsent() {
        assertFalse(Files.exists(ASSETS.resolve("models/block/star_computation_engine_core")),
                "The old whole-body geometry must be deleted, not replaced by a compatibility stub");
        assertFalse(Files.exists(ASSETS.resolve("textures/block/star_computation_engine")),
                "The old shell atlases, animated overlays and formed fallback textures must be deleted");
        for (var part : new String[] {PARTS[1], PARTS[2]}) {
            assertFalse(Files.exists(ASSETS.resolve("models/block/" + part + "_formed.json")));
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
        var pixels = window.getRGB(0, 0, 16, 16, null, 0, 16);
        assertTrue(Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) > 0),
                "the unformed observation port must have visible pixels");
    }

    @Test
    void shellBlocksAreMineableWithAPickaxeAndDropThemselves() throws IOException {
        var pickaxe = tagValues("mineable/pickaxe.json");
        var diamond = tagValues("needs_diamond_tool.json");
        for (var part : PARTS) {
            var id = "molecularmanipulator:block/" + part;
            assertModel(id);
            var blockId = "molecularmanipulator:" + part;
            assertTrue(pickaxe.contains(blockId), blockId + " must be in mineable/pickaxe");
            assertTrue(diamond.contains(blockId), blockId + " must be in needs_diamond_tool");
            var drop = json(Path.of("src/main/resources/data/molecularmanipulator/loot_table/blocks")
                    .resolve(part + ".json")).getAsJsonArray("pools").get(0).getAsJsonObject()
                    .getAsJsonArray("entries").get(0).getAsJsonObject();
            assertEquals("minecraft:item", drop.get("type").getAsString(), blockId);
            assertEquals(blockId, drop.get("name").getAsString(), blockId + " must drop itself");
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
        for (var key : textures.keySet()) assertTexture(resolveTexture("#" + key, textures));
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

    private static JsonObject inheritedModel(String modelId, Set<String> parents) throws IOException {
        var id = qualified(modelId);
        assertTrue(parents.add(id), "model parent cycle: " + parents + " -> " + id);
        var file = asset(id, "models", ".json");
        assertTrue(Files.isRegularFile(file), "missing model " + id);
        var own = json(file);
        assertFalse(own.has("loader"), id + " must no longer need a custom whole-body loader");
        var model = new JsonObject();
        if (own.has("parent")) {
            var parent = qualified(own.get("parent").getAsString());
            if (!parent.startsWith("minecraft:")) {
                model = inheritedModel(parent, parents);
            } else if (parent.equals("minecraft:block/cube_all")) {
                // Vanilla parents live in Minecraft, not this source tree.
                var defaults = new JsonObject();
                defaults.addProperty("particle", "#all");
                model.add("textures", defaults);
            }
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

    private static Set<String> tagValues(String name) throws IOException {
        var values = new LinkedHashSet<String>();
        for (var entry : json(TAGS.resolve(name)).getAsJsonArray("values")) {
            values.add(entry.getAsString());
        }
        return values;
    }
}
