package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StarComputationRecipeGatesTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/molecularmanipulator/recipe");
    private static final ResourceLocation CONTROLLER = id("matter_fabrication_controller");
    private static final List<ResourceLocation> DEFAULT_TARGETS = List.of(
            id("molecular_manipulator"), id("transfinite_compute_nexus"));
    private static final List<String> WELL_RESEARCH_TARGETS = List.of(
            "molecular_auto_crafter", "molecular_center_casing", "molecular_center_coil",
            "molecular_center_glass", "molecular_center_stabilizer", "quantum_star_position_anchor",
            "molecular_manipulator", "molecular_center_core", "molecular_center_controller",
            "assembler_matrix_molecular_core", "terminal_star_position_anchor", "omni_computation_controller",
            "omni_computation_casing", "omni_computation_glass", "computation_crystal_pylon",
            "computation_data_entangler", "computation_energy_stabilizer", "computation_output_node",
            "infinite_crafting_storage", "infinite_parallel_matrix", "universal_pattern_matrix",
            "transfinite_compute_nexus");

    @Test
    void fivePermanentSaveBitsRetainTheirOrderWhileMaterialBecomesFreeAndIndependent() {
        var ids = List.of("foundation", "material", "quantum", "terminal", "transfinite");
        long[] entropy = {90, 900, 9000, 18000, 90000};
        long[] information = {512, 0, 8192, 16384, 65536};
        int[] prerequisites = {0, 0, 2, 1, 12};
        assertEquals(5, StarComputationRecipes.NODES.size());
        assertEquals(31, StarComputationRecipes.ALL_UNLOCKS);
        for (int index = 0; index < ids.size(); index++) {
            var node = StarComputationRecipes.NODES.get(index);
            assertEquals(index, node.index());
            assertEquals(ids.get(index), node.id());
            assertEquals(entropy[index], node.entropy());
            assertEquals(information[index], node.informationCost());
            assertEquals(prerequisites[index], node.prerequisiteMask());
            assertEquals(node.recipeIds(), StarComputationRecipes.recipeIds(index));
            assertTrue(node.prerequisitesMet(node.prerequisiteMask()));
            for (int bit = 0; bit < index; bit++) {
                if ((node.prerequisiteMask() & (1 << bit)) != 0) {
                    assertFalse(node.prerequisitesMet(node.prerequisiteMask() & ~(1 << bit)));
                }
            }
        }
    }

    @Test
    void onlyBundledLockAndLegacyDisplayListTargetExactlyTheTwoSingleBlocksThroughMaterial() throws IOException {
        var locks = new ArrayList<Path>();
        try (var paths = Files.walk(RECIPES)) {
            for (var path : paths.filter(path -> path.toString().endsWith(".json")).sorted().toList()) {
                var recipe = json(path);
                if ("molecularmanipulator:recipe_lock".equals(recipe.get("type").getAsString())) locks.add(path);
            }
        }
        assertEquals(List.of(RECIPES.resolve("recipe_locks/material.json")), locks,
                "Exactly one bundled declaration retains the recipe_locks/material holder ID");
        var lock = json(locks.getFirst());
        assertEquals(List.of("molecularmanipulator:material"), strings(lock, "requires"));
        assertEquals("all", lock.has("mode") ? lock.get("mode").getAsString() : "all");
        assertFalse(lock.has("neoforge:conditions"));
        var match = lock.getAsJsonObject("match");
        assertEquals(Set.of("recipe_ids"), match.keySet(), "No broad selectors or exclusions in the default lock");
        var targets = strings(match, "recipe_ids").stream().map(ResourceLocation::parse).toList();
        assertEquals(DEFAULT_TARGETS, targets);

        var displayed = new ArrayList<ResourceLocation>();
        for (var node : StarComputationRecipes.NODES) {
            assertEquals(node.id().equals("material") ? targets : List.of(), node.recipeIds(), node.id());
            for (var id : node.recipeIds()) {
                displayed.add(id);
                assertEquals(1 << node.index(), StarComputationRecipes.requiredMask(id));
                for (int mask = 0; mask <= StarComputationRecipes.ALL_UNLOCKS; mask++) {
                    assertEquals(node.unlocked(mask), StarComputationRecipes.isUnlocked(id, mask), id + " mask=" + mask);
                }
            }
        }
        assertEquals(targets, displayed, "Default GUI targets and the lock declaration must agree exactly");
        for (var target : DEFAULT_TARGETS) {
            assertEquals(1 << 1, StarComputationRecipes.requiredMask(target));
            assertFalse(StarComputationRecipes.isUnlocked(target, 1), "Foundation never aliases the visible material slot");
            assertTrue(StarComputationRecipes.isUnlocked(target, 2), "Material's permanent bit opens both single blocks");
        }
    }

    @Test
    void nativeTargetsRetainTheirWellResearchWithDefaultRecipeLocksOnlyOnTheTwoSingleBlocks() throws IOException {
        var researchUnlocks = new HashSet<String>();
        try (var paths = Files.list(RECIPES.resolve("research"))) {
            for (var path : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                researchUnlocks.addAll(strings(json(path), "unlocks"));
            }
        }
        assertEquals(22, new HashSet<>(WELL_RESEARCH_TARGETS).size());
        for (var path : WELL_RESEARCH_TARGETS) {
            var id = id(path);
            boolean locked = DEFAULT_TARGETS.contains(id);
            assertEquals(locked ? 1 << 1 : 0, StarComputationRecipes.requiredMask(id), path);
            assertEquals(!locked, StarComputationRecipes.isUnlocked(id, 0), path);
            var recipe = json(RECIPES.resolve(path + ".json"));
            assertEquals("molecularmanipulator:matter_fabrication", recipe.get("type").getAsString(), path);
            assertTrue(recipe.get("requires_research").getAsBoolean(), path);
            assertTrue(researchUnlocks.contains(id.toString()), "Well research remains reachable: " + id);
        }
    }

    @Test
    void lockedSingleBlocksKeepTheirOriginalWellRecipeCostsOutputsAndLoadingConditions() throws IOException {
        var expectedIngredients = Map.of(
                id("molecular_manipulator"), Map.of(
                        "extendedae:ex_pattern_provider", 4,
                        "extendedae:concurrent_processor", 4,
                        "extendedae:ex_molecular_assembler", 1),
                id("transfinite_compute_nexus"), Map.of(
                        "molecularmanipulator:omni_computation_controller", 1,
                        "molecularmanipulator:infinite_parallel_matrix", 64,
                        "molecularmanipulator:infinite_crafting_storage", 64,
                        "molecularmanipulator:universal_pattern_matrix", 32,
                        "molecularmanipulator:computation_energy_stabilizer", 32,
                        "advanced_ae:quantum_processor", 64));
        for (var target : DEFAULT_TARGETS) {
            var recipe = json(RECIPES.resolve(target.getPath() + ".json"));
            assertEquals("molecularmanipulator:matter_fabrication", recipe.get("type").getAsString());
            assertTrue(recipe.get("requires_research").getAsBoolean(), "Star research does not replace well research: " + target);
            var ingredients = recipe.getAsJsonArray("ingredients").asList().stream()
                    .map(value -> value.getAsJsonObject()).collect(java.util.stream.Collectors.toMap(
                            value -> value.getAsJsonObject("ingredient").get("item").getAsString(),
                            value -> value.get("count").getAsInt()));
            assertEquals(expectedIngredients.get(target), ingredients, target.toString());
            assertEquals(400, recipe.get("processing_time").getAsInt());
            boolean nexus = target.getPath().equals("transfinite_compute_nexus");
            assertEquals(nexus ? 4096 : 512, recipe.get("ae_per_tick").getAsInt());
            var results = recipe.getAsJsonArray("results");
            assertEquals(1, results.size());
            assertEquals(target.toString(), results.get(0).getAsJsonObject().get("id").getAsString());
            assertEquals(1, results.get(0).getAsJsonObject().get("count").getAsInt());
            for (var field : List.of("catalysts", "fluid_input", "fluid_result", "ae_inputs", "ae_outputs")) {
                assertFalse(recipe.has(field), "No new recipe costs or outputs: " + target + " " + field);
            }
            if (nexus) {
                assertEquals(JsonParser.parseString("""
                        [{"type":"neoforge:mod_loaded","modid":"advanced_ae"}]
                        """), recipe.get("neoforge:conditions"));
            } else {
                assertFalse(recipe.has("neoforge:conditions"));
            }
        }
    }

    @Test
    void threeMultiblockControllersHaveNoDefaultStarResearchLock() {
        for (var target : List.of(CONTROLLER, id("molecular_center_controller"), id("omni_computation_controller"))) {
            assertEquals(0, StarComputationRecipes.requiredMask(target), target.toString());
            assertTrue(StarComputationRecipes.isUnlocked(target, 0), target.toString());
        }
    }

    @Test
    void bootstrapAndThirdPartyRecipesRemainOutsideTheEngineTree() {
        for (var path : List.of("reference_star_position_anchor", "star_computation_engine_core", "star_computation_engine_casing",
                "star_computation_observation_port", "star_marrow_crystal_dust", "star_marrow_alloy_ingot",
                "star_chart_circuit_board", "star_chart_processor", "star_marrow_seed",
                "star_signal_storage_cell", "creative_star_signal_disk",
                "fabrication/research_materials/advanced_ae/quantum_processor",
                "fabrication/research_materials/extendedae/concurrent_processor")) {
            assertEquals(0, StarComputationRecipes.requiredMask(id(path)), path);
            assertTrue(StarComputationRecipes.isUnlocked(id(path), 0));
        }
        assertEquals(0, StarComputationRecipes.requiredMask(ResourceLocation.parse("ae2:crafting_unit")));
        assertEquals(0, StarComputationRecipes.requiredMask(ResourceLocation.parse("advanced_ae:quantum_core")));
        assertEquals(List.of(), StarComputationRecipes.recipeIds(-1));
        assertEquals(List.of(), StarComputationRecipes.recipeIds(32));
    }

    @Test
    void formerControllerTargetUsesOrdinaryCraftingWithoutAWellResearchGateOrLockedInputs() throws IOException {
        var recipe = json(RECIPES.resolve(CONTROLLER.getPath() + ".json"));
        assertEquals("minecraft:crafting_shaped", recipe.get("type").getAsString());
        assertFalse(recipe.has("requires_research"));
        assertFalse(recipe.has("results"));
        assertEquals(CONTROLLER.toString(), recipe.getAsJsonObject("result").get("id").getAsString());
        assertEquals(1, recipe.getAsJsonObject("result").get("count").getAsInt());
        assertFalse(recipe.getAsJsonObject("key").entrySet().isEmpty());
        for (var entry : recipe.getAsJsonObject("key").entrySet()) {
            var ingredient = ResourceLocation.parse(entry.getValue().getAsJsonObject().get("item").getAsString());
            assertEquals(0, StarComputationRecipes.requiredMask(ingredient), ingredient.toString());
            assertNotEquals(CONTROLLER, ingredient, "The controller must not consume its own output");
        }
    }

    @Test
    void defaultIntegrationGateIsReachableAtL3WithoutBuyingHiddenLegacyResearchOrItsOwnOutput() {
        var directory = StarResearchNodes.build(java.util.Map.of());
        var material = id("material");
        assertEquals(Set.of(material), directory.nodes().keySet());
        assertEquals(3, directory.nodes().get(material).requiredLevel());
        assertEquals(0, directory.nodes().get(material).informationCost());
        assertEquals(List.of(), directory.nodes().get(material).prerequisites());
        assertFalse(directory.canUnlock(material, 2, Set.of()));
        assertTrue(directory.canUnlock(material, 3, Set.of()));
        for (var target : DEFAULT_TARGETS) {
            assertFalse(StarComputationRecipes.isUnlocked(target, 0));
            assertTrue(StarComputationRecipes.isUnlocked(target, 2));
        }
        for (var anchor : List.of("reference_star_position_anchor", "quantum_star_position_anchor", "terminal_star_position_anchor")) {
            assertTrue(StarComputationRecipes.isUnlocked(id(anchor), 0), "The L3 anchor path cannot depend on its own material grant");
        }
    }

    private static List<String> strings(JsonObject json, String field) {
        return json.getAsJsonArray(field).asList().stream().map(value -> value.getAsString()).toList();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
