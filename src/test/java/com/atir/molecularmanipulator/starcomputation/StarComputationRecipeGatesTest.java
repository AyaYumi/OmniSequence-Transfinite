package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class StarComputationRecipeGatesTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/molecularmanipulator/recipe");

    @Test
    void savedIndicesCostsAndPrerequisitesKeepTheExistingMenuContract() {
        var ids = List.of("foundation", "material", "quantum", "terminal", "transfinite");
        long[] entropy = {90, 900, 9000, 18000, 90000};
        long[] information = {512, 2048, 8192, 16384, 65536};
        int[] prerequisites = {0, 1, 2, 1, 12};
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
    void everyDisplayedRecipeExistsAndUsesTheEnforcedGateAndOriginalResearch() throws IOException {
        var researchUnlocks = new HashSet<String>();
        try (var paths = Files.list(RECIPES.resolve("research"))) {
            for (var path : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                for (var unlock : json(path).getAsJsonArray("unlocks")) researchUnlocks.add(unlock.getAsString());
            }
        }
        var seen = new HashSet<ResourceLocation>();
        for (var node : StarComputationRecipes.NODES) {
            assertFalse(node.recipeIds().isEmpty());
            for (var id : node.recipeIds()) {
                assertTrue(seen.add(id), "A recipe has exactly one engine node: " + id);
                assertEquals("molecularmanipulator", id.getNamespace());
                var recipe = json(RECIPES.resolve(id.getPath() + ".json"));
                assertEquals("molecularmanipulator:matter_fabrication", recipe.get("type").getAsString());
                assertTrue(recipe.get("requires_research").getAsBoolean());
                assertTrue(researchUnlocks.contains(id.toString()), "Engine unlock must retain a reachable research owner: " + id);
                assertEquals(1 << node.index(), StarComputationRecipes.requiredMask(id));
                for (int mask = 0; mask <= StarComputationRecipes.ALL_UNLOCKS; mask++) {
                    assertEquals(node.unlocked(mask), StarComputationRecipes.isUnlocked(id, mask), id + " mask=" + mask);
                }
            }
        }
    }

    @Test
    void bootstrapAndThirdPartyRecipesRemainOutsideTheEngineTree() {
        for (var path : List.of("reference_star_position_anchor", "star_computation_engine_core", "star_computation_engine_casing",
                "star_computation_observation_port", "star_marrow_crystal_dust", "star_marrow_alloy_ingot",
                "star_chart_circuit_board", "star_chart_processor", "star_marrow_seed",
                "star_signal_storage_cell", "creative_star_signal_disk", "matter_fabrication_controller",
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
    void anchorsAndAdvancedOwnMachinesHaveRealRecipeRequirements() {
        assertEquals(1 << 1, StarComputationRecipes.requiredMask(id("quantum_star_position_anchor")));
        assertEquals(1 << 2, StarComputationRecipes.requiredMask(id("terminal_star_position_anchor")));
        assertEquals(1 << 1, StarComputationRecipes.requiredMask(id("molecular_center_core")));
        assertEquals(1 << 1, StarComputationRecipes.requiredMask(id("assembler_matrix_molecular_core")));
        assertEquals(1 << 2, StarComputationRecipes.requiredMask(id("omni_computation_controller")));
        assertEquals(1 << 3, StarComputationRecipes.requiredMask(id("infinite_crafting_storage")));
        assertEquals(1 << 3, StarComputationRecipes.requiredMask(id("infinite_parallel_matrix")));
        assertEquals(1 << 4, StarComputationRecipes.requiredMask(id("transfinite_compute_nexus")));
    }

    @Test
    void entropyProgressionCanReachEveryNodeWithoutAnAnchorUnlockingItself() {
        int mask = 0;
        while (mask != StarComputationRecipes.ALL_UNLOCKS) {
            long maximum = maximumReachableEntropy(mask);
            int previous = mask;
            for (var node : StarComputationRecipes.NODES) {
                if (node.entropy() <= maximum && node.prerequisitesMet(mask)) mask |= 1 << node.index();
            }
            assertNotEquals(previous, mask, "Unreachable engine tree at mask " + mask + " and entropy " + maximum);
        }
    }

    @Test
    void gatedMaterialDependenciesNeverRequireALaterNodeAndHaveNoProductionCycle() throws IOException {
        var recipesByProduct = new HashMap<String, ResourceLocation>();
        var definitions = new HashMap<ResourceLocation, JsonObject>();
        for (var node : StarComputationRecipes.NODES) {
            for (var id : node.recipeIds()) {
                var recipe = json(RECIPES.resolve(id.getPath() + ".json"));
                definitions.put(id, recipe);
                for (var output : recipe.getAsJsonArray("results")) {
                    recipesByProduct.put(output.getAsJsonObject().get("id").getAsString(), id);
                }
            }
        }
        var pending = new HashMap<ResourceLocation, Set<ResourceLocation>>();
        for (var entry : definitions.entrySet()) {
            var dependencies = new HashSet<ResourceLocation>();
            for (var cost : entry.getValue().getAsJsonArray("ingredients")) {
                var ingredient = cost.getAsJsonObject().getAsJsonObject("ingredient");
                if (!ingredient.has("item")) continue;
                var dependency = recipesByProduct.get(ingredient.get("item").getAsString());
                if (dependency == null) continue;
                assertTrue(StarComputationRecipes.requiredMask(dependency) <= StarComputationRecipes.requiredMask(entry.getKey()),
                        entry.getKey() + " consumes a later node's product: " + dependency);
                dependencies.add(dependency);
            }
            pending.put(entry.getKey(), dependencies);
        }
        var available = new HashSet<ResourceLocation>();
        while (!pending.isEmpty()) {
            var ready = pending.entrySet().stream().filter(entry -> available.containsAll(entry.getValue()))
                    .map(Map.Entry::getKey).toList();
            assertFalse(ready.isEmpty(), "Circular gated material recipes: " + pending);
            available.addAll(ready);
            ready.forEach(pending::remove);
        }
    }

    private static long maximumReachableEntropy(int mask) {
        if (StarComputationRecipes.isUnlocked(id("terminal_star_position_anchor"), mask)) return StarComputationBenefits.entropy(0, 0, 90);
        if (StarComputationRecipes.isUnlocked(id("quantum_star_position_anchor"), mask)) return StarComputationBenefits.entropy(0, 90, 0);
        return StarComputationBenefits.entropy(90, 0, 0);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
