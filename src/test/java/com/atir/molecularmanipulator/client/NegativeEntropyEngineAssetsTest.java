package com.atir.molecularmanipulator.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Asset regressions for the Negative Entropy Engine shell.
 *
 * <p>The formed shell is driven purely by blockstates/models, so a typo here
 * silently turns 26 blocks invisible in game. These checks need no client.</p>
 */
class NegativeEntropyEngineAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/molecularmanipulator");
    private static final Path TAGS = Path.of("src/main/resources/data/minecraft/tags/block");
    private static final String[] PARTS = {
        "negative_entropy_engine_core",
        "negative_entropy_engine_casing",
        "negative_entropy_observation_window"
    };

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<String> models(JsonObject variant) {
        var model = variant.get("model");
        var out = new ArrayList<String>();
        if (model.isJsonArray()) model.getAsJsonArray().forEach(entry -> out.add(entry.getAsString()));
        else out.add(model.getAsString());
        return out;
    }

    private static Set<String> tagValues(String name) throws IOException {
        var values = new LinkedHashSet<String>();
        for (var entry : json(TAGS.resolve(name)).getAsJsonArray("values")) {
            values.add(entry.getAsString());
        }
        return values;
    }

    @Test
    void everyFormedAndUnformedVariantResolvesToARealModelAndTexture() throws IOException {
        for (var part : PARTS) {
            var variants = json(ASSETS.resolve("blockstates").resolve(part + ".json")).getAsJsonObject("variants");
            for (var facing : new String[] {"north", "east", "south", "west"}) {
                for (var formed : new String[] {"false", "true"}) {
                    var key = "facing=" + facing + ",formed=" + formed;
                    assertTrue(variants.has(key), part + " has no variant " + key);
                    for (var model : models(variants.getAsJsonObject(key))) {
                        assertModel(model);
                    }
                }
            }
        }
    }

    @Test
    void formedModelsAreNotEmptyPlaceholders() throws IOException {
        for (var formed : new String[] {
            "negative_entropy_engine_casing_formed",
            "negative_entropy_observation_window_formed"
        }) {
            var model = json(ASSETS.resolve("models/block").resolve(formed + ".json"));
            assertTrue(model.has("textures"), formed + " must declare textures");
            var all = model.getAsJsonObject("textures").get("all").getAsString();
            assertTexture(all);
        }
        var core = json(ASSETS.resolve("models/block/negative_entropy_engine_core_formed.json"));
        assertTexture(core.getAsJsonObject("textures").get("front").getAsString());
        assertTexture(core.getAsJsonObject("textures").get("frame").getAsString());
    }

    @Test
    void shellBlocksAreMineableWithAPickaxeAndDropThemselves() throws IOException {
        var pickaxe = tagValues("mineable/pickaxe.json");
        var diamond = tagValues("needs_diamond_tool.json");
        for (var part : PARTS) {
            var id = "molecularmanipulator:" + part;
            assertTrue(pickaxe.contains(id), id + " must be in mineable/pickaxe");
            assertTrue(diamond.contains(id), id + " must be in needs_diamond_tool");
            assertTrue(json(Path.of("src/main/resources/data/molecularmanipulator/loot_table/blocks")
                    .resolve(part + ".json")).toString().contains(id), id + " must drop itself");
        }
    }

    @Test
    void observationWindowsKeepTranslucentGlass() throws IOException {
        for (var part : new String[] {
            "negative_entropy_observation_window",
            "negative_entropy_observation_window_formed"
        }) {
            BufferedImage image = ImageIO.read(
                    ASSETS.resolve("textures/block").resolve(part + ".png").toFile());
            assertNotNull(image, part + " texture must load");
            boolean translucent = false;
            for (int y = 0; y < image.getHeight() && !translucent; y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    if ((image.getRGB(x, y) >>> 24) < 255) {
                        translucent = true;
                        break;
                    }
                }
            }
            assertTrue(translucent, part + " must keep translucent glass pixels");
        }
    }

    @Test
    void formedShellTexturesExist() {
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/negative_entropy_engine_formed.png")),
                "the continuous formed shell texture must exist");
        assertTrue(Files.isRegularFile(ASSETS.resolve("textures/block/negative_entropy_engine_formed_side.png")),
                "the windowed side shell texture must exist");
    }

    @Test
    void formedShellSideTextureHasWindowCutout() throws IOException {
        BufferedImage full = ImageIO.read(
                ASSETS.resolve("textures/block/negative_entropy_engine_formed.png").toFile());
        BufferedImage side = ImageIO.read(
                ASSETS.resolve("textures/block/negative_entropy_engine_formed_side.png").toFile());
        assertNotNull(full, "full shell texture must load");
        assertNotNull(side, "side shell texture must load");
        assertEquals(full.getWidth(), side.getWidth());
        assertEquals(full.getHeight(), side.getHeight());

        boolean cutout = false;
        for (int y = 0; y < side.getHeight(); y++) {
            for (int x = 0; x < side.getWidth(); x++) {
                if ((side.getRGB(x, y) >>> 24) == 0) {
                    cutout = true;
                }
                assertEquals(255, full.getRGB(x, y) >>> 24,
                        "full shell texture must stay opaque");
            }
        }
        assertTrue(cutout, "side shell texture must cut out the window");
    }

    private static void assertModel(String modelId) throws IOException {
        var path = modelId.split(":", 2)[1];
        var file = ASSETS.resolve("models").resolve(path + ".json");
        assertTrue(Files.isRegularFile(file), "missing model " + modelId);
        var model = json(file);
        assertFalse(model.has("parent") && model.get("parent").getAsString().equals("minecraft:builtin/generated"),
                modelId + " must not use the empty builtin/generated parent");
        if (!model.has("textures")) return;
        for (var entry : model.getAsJsonObject("textures").entrySet()) {
            if (entry.getValue().getAsString().startsWith("#")) continue;
            assertTexture(entry.getValue().getAsString());
        }
    }

    private static void assertTexture(String textureId) {
        var parts = textureId.split(":", 2);
        assertEquals("molecularmanipulator", parts[0], "unexpected namespace in " + textureId);
        var file = ASSETS.resolve("textures").resolve(parts[1] + ".png");
        assertTrue(Files.isRegularFile(file), "missing texture " + textureId);
    }
}
