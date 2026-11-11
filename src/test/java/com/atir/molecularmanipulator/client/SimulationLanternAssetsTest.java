package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SimulationLanternAssetsTest {
    private static final Path RESOURCES = Path.of("src/main/resources");
    private static final Path ASSETS = RESOURCES.resolve("assets/molecularmanipulator");
    private static final Path DATA = RESOURCES.resolve("data/molecularmanipulator");
    private static final String ID = "molecularmanipulator:simulation_lantern";

    @ParameterizedTest
    @CsvSource({"en_us,Simulation Lantern", "zh_cn,模拟灯笼"})
    void displayNameTooltipAndControlsUseTheNewName(String language, String name) throws Exception {
        var translations = json(ASSETS.resolve("lang/" + language + ".json"));
        assertEquals(name, translations.get("block.molecularmanipulator.simulation_lantern").getAsString());
        assertTrue(translations.get("key.categories.molecularmanipulator.lantern").getAsString().endsWith(name));
        assertTrue(translations.has("tooltip.molecularmanipulator.simulation_lantern"));
        assertTrue(translations.has("tooltip.molecularmanipulator.simulation_lantern_capture"));
        assertFalse(translations.has("block.molecularmanipulator.green_fire_lantern"));
        assertFalse(translations.has("tooltip.molecularmanipulator.green_fire_lantern"));
        assertFalse(translations.has("tooltip.molecularmanipulator.green_fire_lantern_capture"));
    }

    @Test
    void bothFlameModelsAndTheTextureUseTheNewResourcePath() throws Exception {
        var item = json(ASSETS.resolve("models/item/simulation_lantern.json"));
        assertEquals("molecularmanipulator:block/simulation_lantern", item.get("parent").getAsString());
        var override = item.getAsJsonArray("overrides").get(0).getAsJsonObject();
        assertEquals(1, override.getAsJsonObject("predicate").get("molecularmanipulator:lit").getAsInt());
        assertEquals("molecularmanipulator:block/simulation_lantern_lit", override.get("model").getAsString());
        var variants = json(ASSETS.resolve("blockstates/simulation_lantern.json")).getAsJsonObject("variants");
        for (boolean lit : new boolean[] {false, true}) {
            String model = "simulation_lantern" + (lit ? "_lit" : "");
            assertEquals("molecularmanipulator:block/" + model,
                    variants.getAsJsonObject("lit=" + lit).get("model").getAsString());
            assertEquals("molecularmanipulator:item/simulation_lantern",
                    json(ASSETS.resolve("models/block/" + model + ".json")).getAsJsonObject("textures").get("atlas").getAsString());
        }
        assertNotNull(ImageIO.read(ASSETS.resolve("textures/item/simulation_lantern.png").toFile()));
        assertFalse(Files.exists(ASSETS.resolve("textures/item/green_fire_lantern.png")));
    }

    @Test
    void recipeAndLootUseTheNewItemWithoutChangingTheAssembly() throws Exception {
        var recipe = json(DATA.resolve("recipe/simulation_lantern.json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertEquals(JsonParser.parseString("[\" NS\",\"ACA\",\"ANA\"]"), recipe.get("pattern"));
        var key = recipe.getAsJsonObject("key");
        assertEquals("minecraft:iron_nugget", key.getAsJsonObject("N").get("item").getAsString());
        assertEquals("minecraft:stick", key.getAsJsonObject("S").get("item").getAsString());
        assertEquals("molecularmanipulator:star_marrow_alloy_ingot", key.getAsJsonObject("A").get("item").getAsString());
        assertEquals("molecularmanipulator:star_marrow_crystal", key.getAsJsonObject("C").get("item").getAsString());
        assertEquals(ID, recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        var pool = json(DATA.resolve("loot_table/blocks/simulation_lantern.json")).getAsJsonArray("pools").get(0).getAsJsonObject();
        assertEquals(ID, pool.getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        assertFalse(Files.exists(DATA.resolve("recipe/green_fire_lantern.json")));
        assertFalse(Files.exists(DATA.resolve("loot_table/blocks/green_fire_lantern.json")));
    }

    private static JsonObject json(Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
