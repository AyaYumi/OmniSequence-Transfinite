package com.atir.molecularmanipulator.entropy;

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

class NegativeEntropyRecipeGatesTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/molecularmanipulator/recipe");

    @Test
    void savedIndicesCostsAndPrerequisitesKeepTheExistingMenuContract() {
        var ids = List.of("foundation", "material", "quantum", "terminal", "transfinite");
        long[] entropy = {90, 900, 9000, 18000, 90000};
        long[] information = {512, 2048, 8192, 16384, 65536};
        int[] prerequisites = {0, 1, 2, 1, 12};
        assertEquals(5, NegativeEntropyRecipes.NODES.size());
        assertEquals(31, NegativeEntropyRecipes.ALL_UNLOCKS);
        for (int index = 0; index < ids.size(); index++) {
            var node = NegativeEntropyRecipes.NODES.get(index);
            assertEquals(index, node.index());
            assertEquals(ids.get(index), node.id());
            assertEquals(entropy[index], node.entropy());
            assertEquals(information[index], node.informationCost());
            assertEquals(prerequisites[index], node.prerequisiteMask());
            assertEquals(node.recipeIds(), NegativeEntropyRecipes.recipeIds(index));
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
        for (var node : NegativeEntropyRecipes.NODES) {
            assertFalse(node.recipeIds().isEmpty());
            for (var id : node.recipeIds()) {
                assertTrue(seen.add(id), "A recipe has exactly one engine node: " + id);
                assertEquals("molecularmanipulator", id.getNamespace());
                var recipe = json(RECIPES.resolve(id.getPath() + ".json"));
                assertEquals("molecularmanipulator:matter_fabrication", recipe.get("type").getAsString());
                assertTrue(recipe.get("requires_research").getAsBoolean());
                assertTrue(researchUnlocks.contains(id.toString()), "Engine unlock must retain a reachable research owner: " + id);
                assertEquals(1 << node.index(), NegativeEntropyRecipes.requiredMask(id));
                for (int mask = 0; mask <= NegativeEntropyRecipes.ALL_UNLOCKS; mask++) {
                    assertEquals(node.unlocked(mask), NegativeEntropyRecipes.isUnlocked(id, mask), id + " mask=" + mask);
                }
            }
        }
    }

    @Test
    void bootstrapAndThirdPartyRecipesRemainOutsideTheEngineTree() {
        for (var path : List.of("base_entropy_anchor", "negative_entropy_engine_core", "negative_entropy_engine_casing",
                "negative_entropy_observation_port", "negative_entropy_crystal_dust", "negative_entropy_alloy_ingot",
                "printed_negentropy_information_processor", "negentropy_information_processor", "negentropy_seed",
                "negentropy_information_cell", "creative_negentropy_information_cell", "matter_fabrication_controller",
                "fabrication/research_materials/advanced_ae/quantum_processor",
                "fabrication/research_materials/extendedae/concurrent_processor")) {
            assertEquals(0, NegativeEntropyRecipes.requiredMask(id(path)), path);
            assertTrue(NegativeEntropyRecipes.isUnlocked(id(path), 0));
        }
        assertEquals(0, NegativeEntropyRecipes.requiredMask(ResourceLocation.parse("ae2:crafting_unit")));
        assertEquals(0, NegativeEntropyRecipes.requiredMask(ResourceLocation.parse("advanced_ae:quantum_core")));
        assertEquals(List.of(), NegativeEntropyRecipes.recipeIds(-1));
        assertEquals(List.of(), NegativeEntropyRecipes.recipeIds(32));
    }

    @Test
    void anchorsAndAdvancedOwnMachinesHaveRealRecipeRequirements() {
        assertEquals(1 << 1, NegativeEntropyRecipes.requiredMask(id("quantum_entropy_anchor")));
        assertEquals(1 << 2, NegativeEntropyRecipes.requiredMask(id("terminal_entropy_anchor")));
        assertEquals(1 << 1, NegativeEntropyRecipes.requiredMask(id("molecular_center_core")));
        assertEquals(1 << 1, NegativeEntropyRecipes.requiredMask(id("assembler_matrix_molecular_core")));
        assertEquals(1 << 2, NegativeEntropyRecipes.requiredMask(id("omni_computation_controller")));
        assertEquals(1 << 3, NegativeEntropyRecipes.requiredMask(id("infinite_crafting_storage")));
        assertEquals(1 << 3, NegativeEntropyRecipes.requiredMask(id("infinite_parallel_matrix")));
        assertEquals(1 << 4, NegativeEntropyRecipes.requiredMask(id("transfinite_compute_nexus")));
    }

    @Test
    void entropyProgressionCanReachEveryNodeWithoutAnAnchorUnlockingItself() {
        int mask = 0;
        while (mask != NegativeEntropyRecipes.ALL_UNLOCKS) {
            long maximum = maximumReachableEntropy(mask);
            int previous = mask;
            for (var node : NegativeEntropyRecipes.NODES) {
                if (node.entropy() <= maximum && node.prerequisitesMet(mask)) mask |= 1 << node.index();
            }
            assertNotEquals(previous, mask, "Unreachable engine tree at mask " + mask + " and entropy " + maximum);
        }
    }

    @Test
    void gatedMaterialDependenciesNeverRequireALaterNodeAndHaveNoProductionCycle() throws IOException {
        var recipesByProduct = new HashMap<String, ResourceLocation>();
        var definitions = new HashMap<ResourceLocation, JsonObject>();
        for (var node : NegativeEntropyRecipes.NODES) {
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
                assertTrue(NegativeEntropyRecipes.requiredMask(dependency) <= NegativeEntropyRecipes.requiredMask(entry.getKey()),
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
        if (NegativeEntropyRecipes.isUnlocked(id("terminal_entropy_anchor"), mask)) return NegativeEntropyBenefits.entropy(0, 0, 90);
        if (NegativeEntropyRecipes.isUnlocked(id("quantum_entropy_anchor"), mask)) return NegativeEntropyBenefits.entropy(0, 90, 0);
        return NegativeEntropyBenefits.entropy(90, 0, 0);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
