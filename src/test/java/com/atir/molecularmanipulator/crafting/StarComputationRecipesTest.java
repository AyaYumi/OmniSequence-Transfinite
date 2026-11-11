package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StarComputationRecipesTest {
    private static final String MOD = "molecularmanipulator:";
    private static final Path RECIPES = Path.of("src/main/resources/data/molecularmanipulator/recipe");
    private static final Map<String, String> TYPES = Map.of(
            "star_marrow_crystal_dust", MOD + "matter_fabrication",
            "star_chart_processor", MOD + "matter_fabrication",
            "star_marrow_alloy_ingot", MOD + "matter_fabrication",
            "star_computation_engine_casing", "minecraft:crafting_shaped",
            "star_computation_observation_port", "minecraft:crafting_shaped",
            "star_computation_engine_core", "minecraft:crafting_shaped",
            "reference_star_position_anchor", "minecraft:crafting_shaped",
            "quantum_star_position_anchor", MOD + "matter_fabrication",
            "terminal_star_position_anchor", MOD + "matter_fabrication");
    private static final Set<String> RESERVED = Set.of(
            MOD + "flawless_star_marrow_parent_rock",
            MOD + "creative_star_signal_disk", MOD + "compact_singularity_hub",
            MOD + "star_signal_flow");

    @Test
    void recipeFilesHaveExplicitTypesAndPositiveOutputs() throws IOException {
        for (var entry : TYPES.entrySet()) {
            var recipe = recipe(entry.getKey());
            assertEquals(entry.getValue(), recipe.get("type").getAsString(), entry.getKey());
            var result = result(recipe);
            assertEquals(MOD + entry.getKey(), result.get("id").getAsString());
            assertTrue(outputCount(recipe) > 0);
            assertFalse(inputs(recipe).isEmpty(), "Recipes must consume materials");
            assertFalse(recipe.has("neoforge:conditions"), "Star Marrow production no longer depends on AdvancedAE");
            for (var input : inputs(recipe).keySet()) {
                assertTrue(input.startsWith(MOD) || input.startsWith("ae2:") || input.startsWith("minecraft:"), input);
            }
        }
    }

    @Test
    void wellGrindsCrystalsAndAssemblesBoardsWithoutSelfCatalysts() throws IOException {
        assertEquals(Map.of(MOD + "star_marrow_crystal", 64), inputs(recipe("star_marrow_crystal_dust")));
        var board = recipe("star_chart_circuit_board");
        assertEquals(MOD + "matter_fabrication", board.get("type").getAsString());
        assertEquals(Map.of(MOD + "star_marrow_crystal", 16), inputs(board));
        assertEquals(16, outputCount(board));
        assertFalse(board.has("catalysts"));
        var processor = recipe("star_chart_processor");
        assertEquals(Map.of(MOD + "star_chart_circuit_board", 16,
                "minecraft:redstone", 16, "ae2:printed_silicon", 16), inputs(processor));
        assertEquals(16, outputCount(processor));
    }

    @Test
    void allWorkbenchRecipesUseValidThreeByThreePatterns() throws IOException {
        var quantities = Map.of("star_computation_engine_casing", 4, "star_computation_observation_port", 4,
                "star_computation_engine_core", 1, "reference_star_position_anchor", 9, "star_signal_storage_cell", 1);
        for (var entry : quantities.entrySet()) {
            var recipe = recipe(entry.getKey());
            assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            var keys = recipe.getAsJsonObject("key");
            var used = new HashSet<String>();
            assertEquals(3, recipe.getAsJsonArray("pattern").size());
            for (var row : recipe.getAsJsonArray("pattern")) {
                assertEquals(3, row.getAsString().length());
                for (char symbol : row.getAsString().toCharArray()) {
                    if (symbol == ' ') continue;
                    used.add(String.valueOf(symbol));
                    assertTrue(keys.has(String.valueOf(symbol)), "Every pattern symbol must be defined");
                }
            }
            assertEquals(keys.keySet(), used, "No unused pattern keys");
            assertEquals(9, inputs(recipe).values().stream().mapToInt(Integer::intValue).sum());
            assertEquals(entry.getValue().intValue(), outputCount(recipe));
        }
    }

    @Test
    void starSignalStorageCellUsesTheRequestedThreeByThreeAssembly() throws IOException {
        var cell = recipe("star_signal_storage_cell");
        assertEquals("minecraft:crafting_shaped", cell.get("type").getAsString());
        assertEquals("misc", cell.get("category").getAsString());
        String[][] expected = {
                {"ae2:quartz_vibrant_glass", MOD + "star_marrow_crystal_dust", "ae2:quartz_vibrant_glass"},
                {MOD + "star_marrow_crystal_dust", MOD + "star_signal_storage_component", MOD + "star_marrow_crystal_dust"},
                {MOD + "star_marrow_alloy_ingot", MOD + "star_chart_processor", MOD + "star_marrow_alloy_ingot"}
        };
        var pattern = cell.getAsJsonArray("pattern");
        var keys = cell.getAsJsonObject("key");
        assertEquals(3, pattern.size());
        for (int row = 0; row < 3; row++) {
            var symbols = pattern.get(row).getAsString();
            assertEquals(3, symbols.length());
            for (int column = 0; column < 3; column++) {
                assertEquals(expected[row][column], keys.getAsJsonObject(String.valueOf(symbols.charAt(column)))
                        .get("item").getAsString(), "Row " + row + ", column " + column);
            }
        }
        assertEquals(Map.of("ae2:quartz_vibrant_glass", 2, MOD + "star_marrow_crystal_dust", 3,
                MOD + "star_signal_storage_component", 1, MOD + "star_marrow_alloy_ingot", 2,
                MOD + "star_chart_processor", 1), inputs(cell));
        assertEquals(MOD + "star_signal_storage_cell", result(cell).get("id").getAsString());
        assertEquals(1, outputCount(cell));
        assertFalse(cell.has("ingredients"), "The old housing-plus-component shapeless recipe must be replaced");
    }

    @Test
    void theCoreAndBaseAnchorFitInNineOrdinaryCraftingSlots() throws IOException {
        assertEquals(Map.of(
                MOD + "star_computation_engine_casing", 4,
                MOD + "star_marrow_alloy_ingot", 2,
                MOD + "star_chart_processor", 1,
                MOD + "star_marrow_crystal", 1,
                "ae2:energy_acceptor", 1), inputs(recipe("star_computation_engine_core")));
        assertEquals(Map.of(
                MOD + "star_marrow_alloy_ingot", 4,
                MOD + "star_computation_engine_casing", 2,
                MOD + "star_marrow_crystal", 2,
                MOD + "star_chart_processor", 1), inputs(recipe("reference_star_position_anchor")));
    }

    @Test
    void alloyUsesNativeWellFormatAndKeepsOriginalMaterialRatios() throws IOException {
        var alloy = recipe("star_marrow_alloy_ingot");
        assertEquals(MOD + "matter_fabrication", alloy.get("type").getAsString());
        assertEquals(Map.of(MOD + "star_marrow_crystal_dust", 32, "minecraft:iron_ingot", 32,
                "ae2:fluix_dust", 8, "ae2:sky_dust", 8), inputs(alloy));
        assertEquals("minecraft:water", alloy.getAsJsonObject("fluid_input").get("id").getAsString());
        assertEquals(4000, alloy.getAsJsonObject("fluid_input").get("amount").getAsInt());
        assertEquals(1024, alloy.get("ae_per_tick").getAsInt());
        assertEquals(400, alloy.get("processing_time").getAsInt());
        assertEquals(32, outputCount(alloy));
        assertFalse(alloy.has("neoforge:conditions"));
    }

    @Test
    void convertedRecipesNoLongerHaveNativeFabricationFieldsOrFoundationLocks() throws IOException {
        var unlocks = recipe("research/ae_foundation").getAsJsonArray("unlocks");
        for (var id : Set.of("star_computation_engine_core", "reference_star_position_anchor")) {
            var recipe = recipe(id);
            for (var field : Set.of("requires_research", "processing_time", "ae_per_tick", "results")) {
                assertFalse(recipe.has(field), id + " retains an obsolete fabrication field: " + field);
            }
            for (var unlock : unlocks) assertNotEquals(MOD + id, unlock.getAsString());
        }
    }

    @Test
    void fabricationRecipesStayWithinMachineLimitsAndHaveReachableResearchOwners() throws IOException {
        var foundation = recipe("research/ae_foundation");
        assertEquals(0, foundation.getAsJsonArray("prerequisites").size());
        var material = recipe("research/star_marrow_materials");
        var unlocks = new HashSet<String>();
        for (var entry : foundation.getAsJsonArray("unlocks")) {
            assertTrue(unlocks.add(entry.getAsString()), "Duplicate research unlock");
        }
        for (var unlock : material.getAsJsonArray("unlocks")) unlocks.add(unlock.getAsString());
        for (var entry : TYPES.entrySet()) {
            if (!entry.getValue().equals(MOD + "matter_fabrication")) continue;
            var recipe = recipe(entry.getKey());
            assertTrue(recipe.get("requires_research").getAsBoolean());
            assertTrue(unlocks.contains(MOD + entry.getKey()), "A research-required native recipe must have an owner");
            assertTrue(recipe.getAsJsonArray("ingredients").size() <= 12);
            assertTrue(recipe.getAsJsonArray("results").size() <= 6);
            for (int count : inputs(recipe).values()) assertTrue(count > 0 && count <= 64);
            assertTrue(recipe.get("processing_time").getAsInt() > 0);
            assertTrue(recipe.get("ae_per_tick").getAsInt() > 0);
        }
        for (var cost : foundation.getAsJsonArray("ingredients")) {
            var item = cost.getAsJsonObject().getAsJsonObject("ingredient").get("item").getAsString();
            assertFalse(TYPES.containsKey(item.replace(MOD, "")), "Research must not consume its own locked products");
        }
    }

    @Test
    void anchorsAreCraftedAndUpgradedInCompleteNineBlockCrossSections() throws IOException {
        String previous = null;
        int energy = 0;
        for (var tier : new String[] {"reference_star_position_anchor", "quantum_star_position_anchor", "terminal_star_position_anchor"}) {
            var recipe = recipe(tier);
            assertEquals(9, outputCount(recipe));
            if (previous == null) {
                assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
            } else {
                assertEquals(MOD + "matter_fabrication", recipe.get("type").getAsString());
                assertEquals(9, inputs(recipe).get(MOD + previous).intValue());
                assertEquals(400, recipe.get("processing_time").getAsInt());
                assertTrue(recipe.get("ae_per_tick").getAsInt() > energy);
                energy = recipe.get("ae_per_tick").getAsInt();
            }
            previous = tier;
        }
    }

    @Test
    void theMaterialChainHasNoCyclesAndStartsFromCrystalsAndLaboratoryBoards() throws IOException {
        var pending = new HashMap<String, Set<String>>();
        for (var id : TYPES.keySet()) {
            var dependencies = new HashSet<String>();
            for (var input : inputs(recipe(id)).keySet()) {
                if (!input.startsWith(MOD)) continue;
                var path = input.substring(MOD.length());
                assertTrue(TYPES.containsKey(path) || path.equals("star_marrow_crystal")
                                || path.equals("star_chart_circuit_board"),
                        "Unexpected unavailable starting material: " + input);
                if (TYPES.containsKey(path)) dependencies.add(path);
            }
            pending.put(id, dependencies);
        }
        var available = new HashSet<String>();
        while (!pending.isEmpty()) {
            var ready = pending.entrySet().stream()
                    .filter(entry -> available.containsAll(entry.getValue()))
                    .map(Map.Entry::getKey).toList();
            assertFalse(ready.isEmpty(), "Circular production dependency: " + pending);
            available.addAll(ready);
            ready.forEach(pending::remove);
        }
        assertEquals(TYPES.keySet(), available);
    }

    @Test
    void reservedAcquisitionStaysUntouchedAndNewRecipesDoNotConsumeStarSignalFlow() throws IOException {
        try (var paths = Files.walk(RECIPES)) {
            for (var path : paths.filter(file -> file.toString().endsWith(".json")).toList()) {
                var recipe = json(path);
                if (recipe.has("result")) {
                    assertFalse(RESERVED.contains(recipe.getAsJsonObject("result").get("id").getAsString()));
                }
                if (recipe.has("results")) {
                    for (var output : recipe.getAsJsonArray("results")) {
                        assertFalse(RESERVED.contains(output.getAsJsonObject().get("id").getAsString()));
                    }
                }
                if (recipe.has("output")) {
                    assertFalse(RESERVED.contains(recipe.getAsJsonObject("output").get("id").getAsString()));
                }
            }
        }
        for (var id : TYPES.keySet()) {
            var recipe = recipe(id);
            assertFalse(recipe.has("ae_inputs"));
            assertFalse(recipe.has("ae_outputs"));
            assertEquals(id.equals("star_marrow_alloy_ingot"), recipe.has("fluid_input"));
            assertFalse(recipe.has("catalysts"));
            assertTrue(inputs(recipe).keySet().stream().noneMatch(RESERVED::contains));
        }
    }

    @Test
    void bothGuidesExposeEveryNewRecipe() throws IOException {
        for (var locale : new String[] {"", "_zh_cn/"}) {
            var guides = Path.of("src/main/resources/assets/molecularmanipulator/ae2guide")
                    .resolve(locale + "items-blocks-machines");
            var content = Files.readString(guides.resolve("star_computation_engine.md"))
                    + Files.readString(guides.resolve("star_marrow_crystals.md"));
            for (var id : TYPES.keySet()) {
                assertTrue(content.contains("<RecipeFor id=\"" + MOD + id + "\""), locale + id);
            }
        }
    }

    private static String slot(JsonObject recipe, String slot) {
        return recipe.getAsJsonObject("ingredients").getAsJsonObject(slot).get("item").getAsString();
    }

    private static JsonObject result(JsonObject recipe) {
        if (recipe.has("result")) return recipe.getAsJsonObject("result");
        if (recipe.has("output")) return recipe.getAsJsonObject("output");
        return recipe.getAsJsonArray("results").get(0).getAsJsonObject();
    }

    private static int outputCount(JsonObject recipe) {
        var output = result(recipe);
        return output.get(output.has("#") ? "#" : "count").getAsInt();
    }

    private static Map<String, Integer> inputs(JsonObject recipe) {
        var inputs = new LinkedHashMap<String, Integer>();
        if (recipe.get("type").getAsString().equals("minecraft:crafting_shaped")) {
            var keys = recipe.getAsJsonObject("key");
            for (var row : recipe.getAsJsonArray("pattern")) {
                for (char symbol : row.getAsString().toCharArray()) {
                    if (symbol != ' ') inputs.merge(keys.getAsJsonObject(String.valueOf(symbol)).get("item").getAsString(), 1, Integer::sum);
                }
            }
        } else if (recipe.has("input_items")) {
            for (var input : recipe.getAsJsonArray("input_items")) {
                var entry = input.getAsJsonObject();
                inputs.merge(entry.getAsJsonObject("ingredient").get("item").getAsString(), entry.get("amount").getAsInt(), Integer::sum);
            }
        } else if (recipe.get("ingredients").isJsonObject()) {
            for (var ingredient : recipe.getAsJsonObject("ingredients").entrySet()) {
                inputs.merge(ingredient.getValue().getAsJsonObject().get("item").getAsString(), 1, Integer::sum);
            }
        } else {
            for (JsonElement ingredient : recipe.getAsJsonArray("ingredients")) {
                var entry = ingredient.getAsJsonObject();
                inputs.merge(entry.getAsJsonObject("ingredient").get("item").getAsString(), entry.get("count").getAsInt(), Integer::sum);
            }
        }
        return inputs;
    }

    private static JsonObject recipe(String path) throws IOException {
        return json(RECIPES.resolve(path + ".json"));
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
