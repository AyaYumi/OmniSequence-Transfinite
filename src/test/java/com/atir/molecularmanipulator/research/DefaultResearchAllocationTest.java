package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarComputationRecipes;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DefaultResearchAllocationTest {
    private static final String MOD = "molecularmanipulator:";
    private static final Path RECIPES = Path.of("src/main/resources/data/molecularmanipulator/recipe");

    @Test
    void basicAutoCrafterBelongsOnlyToWellFoundationWithoutADefaultRecipeLock() throws IOException {
        var id = MOD + "molecular_auto_crafter";
        var owners = researches().entrySet().stream()
                .filter(entry -> strings(entry.getValue(), "unlocks").contains(id))
                .map(Map.Entry::getKey).sorted().toList();
        assertEquals(List.of("ae_foundation"), owners);
        var recipe = json(RECIPES.resolve("molecular_auto_crafter.json"));
        assertTrue(recipe.get("requires_research").getAsBoolean());
        assertEquals(0, StarComputationRecipes.requiredMask(ResourceLocation.parse(id)),
                "Well research remains required without an additional default star research lock");
        assertEquals(List.of(), strings(researches().get("ae_foundation"), "required_mods"));
    }

    @Test
    void exoticMatterAppearsAfterFoundationWithoutBecomingAMandatoryTier() throws IOException {
        var researches = researches();
        var order = researches.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue().get("sort_order").getAsInt()))
                .map(Map.Entry::getKey).toList();
        assertEquals(List.of("ae_foundation", "nomai_materials", "star_marrow_materials", "star_marrow_crystallization",
                "eae_materials", "quantum_materials", "sequence_array", "omni_computation", "event_horizon",
                "stellar_fabrication", "transfinite_fabrication"), order);
        var exotic = researches.get("nomai_materials");
        assertEquals(0, exotic.get("stage").getAsInt());
        assertEquals(9, exotic.getAsJsonArray("depths").size());
        assertEquals(List.of(MOD + "research/ae_foundation"), strings(exotic, "prerequisites"));
        assertEquals(List.of(MOD + "ghost_matter", MOD + "gravity_crystal"), strings(exotic, "unlocks"));
    }

    @Test
    void curatedStartupMaterialsHaveSeparateOwnersAndCannotBypassEachOthersResearch() throws IOException {
        var expected = new HashSet<String>();
        addMaterials(expected, "advanced_ae", List.of("data_entangler", "quantum_accelerator", "quantum_alloy",
                "quantum_core", "quantum_crafter", "quantum_infused_dust", "quantum_infusion_source",
                "quantum_processor", "quantum_storage_128", "quantum_storage_256", "quantum_storage_component",
                "quantum_unit", "shattered_singularity"));
        addMaterials(expected, "extendedae", List.of("assembler_matrix_crafter", "assembler_matrix_frame",
                "assembler_matrix_pattern", "assembler_matrix_speed", "assembler_matrix_wall", "concurrent_processor",
                "entro_crystal", "entro_dust", "entro_ingot", "ex_molecular_assembler", "ex_pattern_provider",
                "machine_frame", "wireless_connect", "wireless_hub"));
        assertEquals(27, expected.size());
        for (var entry : researches().entrySet()) {
            var linked = strings(entry.getValue(), "unlocks").stream()
                    .filter(id -> id.startsWith(MOD + "fabrication/research_materials/")).toList();
            var owned = expected.stream().filter(id -> entry.getKey().equals("eae_materials")
                    ? id.contains("/extendedae/") : entry.getKey().equals("quantum_materials")
                    && id.contains("/advanced_ae/")).collect(java.util.stream.Collectors.toSet());
            assertEquals(owned, new HashSet<>(linked), entry.getKey());
            assertEquals(new HashSet<>(linked).size(), linked.size(), "Linked materials must not be duplicated");
        }
    }

    @Test
    void sixStageTreeHasMatchingLoadingConditionsAndNineParallelOnlyDepths() throws IOException {
        var researches = researches();
        var parents = Map.ofEntries(
                Map.entry("ae_foundation", List.<String>of()),
                Map.entry("nomai_materials", List.of("ae_foundation")),
                Map.entry("star_marrow_materials", List.of("ae_foundation")),
                Map.entry("star_marrow_crystallization", List.of("star_marrow_materials")),
                Map.entry("eae_materials", List.of("ae_foundation")),
                Map.entry("quantum_materials", List.of("eae_materials")),
                Map.entry("sequence_array", List.of("eae_materials", "star_marrow_materials")),
                Map.entry("omni_computation", List.of("quantum_materials", "star_marrow_materials")),
                Map.entry("event_horizon", List.of("sequence_array", "omni_computation")),
                Map.entry("stellar_fabrication", List.of("event_horizon")),
                Map.entry("transfinite_fabrication", List.of("stellar_fabrication")));
        assertEquals(parents.keySet(), researches.keySet());
        var powers = Map.ofEntries(Map.entry("ae_foundation", 256), Map.entry("nomai_materials", 256),
                Map.entry("star_marrow_materials", 512), Map.entry("star_marrow_crystallization", 512),
                Map.entry("eae_materials", 512), Map.entry("quantum_materials", 1024),
                Map.entry("sequence_array", 1024), Map.entry("omni_computation", 2048),
                Map.entry("event_horizon", 4096), Map.entry("stellar_fabrication", 8192),
                Map.entry("transfinite_fabrication", 16384));
        var durations = Map.ofEntries(Map.entry("ae_foundation", 600), Map.entry("nomai_materials", 600),
                Map.entry("star_marrow_materials", 900), Map.entry("star_marrow_crystallization", 1200),
                Map.entry("eae_materials", 900), Map.entry("quantum_materials", 1200),
                Map.entry("sequence_array", 1200), Map.entry("omni_computation", 1800),
                Map.entry("event_horizon", 1800), Map.entry("stellar_fabrication", 2400),
                Map.entry("transfinite_fabrication", 3600));
        for (var entry : researches.entrySet()) {
            var name = entry.getKey(); var definition = entry.getValue();
            assertEquals(parents.get(name).stream().map(id -> MOD + "research/" + id).toList(),
                    strings(definition, "prerequisites"), name);
            assertEquals(durations.get(name), definition.get("duration").getAsInt(), name);
            assertEquals(powers.get(name), definition.get("ae_per_tick").getAsInt(), name);
            var mods = strings(definition, "required_mods");
            var conditions = definition.getAsJsonArray("neoforge:conditions");
            assertEquals(mods, conditions == null ? List.of() : conditions.asList().stream()
                    .map(condition -> condition.getAsJsonObject().get("modid").getAsString()).toList(), name);
            var depths = definition.getAsJsonArray("depths");
            assertEquals(9, depths.size(), name);
            for (int index = 0; index < depths.size(); index++) {
                var depth = depths.get(index).getAsJsonObject();
                assertEquals(1L << index, depth.get("material_multiplier").getAsLong(), name);
                assertEquals(index == 8 ? Long.MAX_VALUE : 1L << (index * 8), depth.get("parallel").getAsLong(), name);
                if (index > 0) assertTrue(depth.has("ingredients"), "Expansion must have sustainable explicit costs: " + name);
            }
        }
        assertFalse(Files.exists(RECIPES.resolve("research/terminal_fabrication.json")));
        var owners = new java.util.HashMap<String, String>();
        researches.forEach((name, definition) -> strings(definition, "unlocks").forEach(id ->
                assertNull(owners.put(id, name), "Default unlocks must have exactly one owner: " + id)));
        assertEquals("sequence_array", owners.get(MOD + "infinite_crafting_storage"));
        assertEquals("sequence_array", owners.get(MOD + "infinite_parallel_matrix"));
        assertEquals("omni_computation", owners.get(MOD + "universal_pattern_matrix"));
        for (var matrix : List.of("infinite_crafting_storage", "infinite_parallel_matrix", "universal_pattern_matrix")) {
            assertEquals(0, StarComputationRecipes.requiredMask(ResourceLocation.parse(MOD + matrix)),
                    "Matrices retain their well research owners without a default terminal lock");
        }
        assertEquals("stellar_fabrication", owners.get(MOD + "miniature_supernova"));
        assertEquals("transfinite_fabrication", owners.get(MOD + "transfinite_compute_nexus"));
        for (var id : List.of("star_chart_circuit_board", "star_chart_processor", "star_marrow_crystal_dust",
                "star_marrow_alloy_ingot", "star_signal_storage_component", "quantum_star_position_anchor", "terminal_star_position_anchor")) {
            assertEquals("star_marrow_materials", owners.get(MOD + id));
        }
    }

    private static void addMaterials(Set<String> target, String mod, List<String> paths) {
        paths.forEach(path -> target.add(MOD + "fabrication/research_materials/" + mod + "/" + path));
    }

    @Test
    void researchGraphHasNoCyclesAndFirstRoundHasIndependentBootstrapMaterials() throws IOException {
        var definitions = researches();
        var pending = new java.util.HashMap<>(definitions);
        var completed = new HashSet<String>();
        while (!pending.isEmpty()) {
            var ready = pending.entrySet().stream().filter(entry -> strings(entry.getValue(), "prerequisites").stream()
                    .allMatch(id -> completed.contains(id.substring((MOD + "research/").length())))).toList();
            assertFalse(ready.isEmpty(), "Cyclic or missing research prerequisites: " + pending.keySet());
            for (var entry : ready) {
                completed.add(entry.getKey()); pending.remove(entry.getKey());
            }
        }
        // These foundation inputs have independent AE2 inscriber/in-world recipes before any well research.
        var nativeBootstrap = Set.of("ae2:fluix_crystal", "ae2:logic_processor",
                "ae2:calculation_processor", "ae2:engineering_processor");
        for (var entry : definitions.entrySet()) {
            var ownProducts = new HashSet<String>();
            for (var unlock : strings(entry.getValue(), "unlocks")) {
                var path = RECIPES.resolve(unlock.substring(MOD.length()) + ".json");
                assertTrue(Files.isRegularFile(path), "Every default unlock must resolve: " + unlock);
                var production = json(path);
                if (production.has("results")) production.getAsJsonArray("results").forEach(value ->
                        ownProducts.add(value.getAsJsonObject().get("id").getAsString()));
            }
            for (var raw : entry.getValue().getAsJsonArray("ingredients")) {
                var cost = raw.getAsJsonObject().getAsJsonObject("ingredient");
                if (cost.has("item")) {
                    var item = cost.get("item").getAsString();
                    assertTrue(!ownProducts.contains(item) || nativeBootstrap.contains(item),
                            "First round needs an independent source for " + item + ": " + entry.getKey());
                }
            }
        }
    }

    private static Map<String, JsonObject> researches() throws IOException {
        var result = new java.util.HashMap<String, JsonObject>();
        try (var paths = Files.list(RECIPES.resolve("research"))) {
            for (var path : paths.filter(path -> path.toString().endsWith(".json")).toList()) {
                result.put(path.getFileName().toString().replace(".json", ""), json(path));
            }
        }
        return result;
    }

    private static List<String> strings(JsonObject json, String field) {
        var values = json.getAsJsonArray(field);
        return values == null ? List.of() : values.asList().stream().map(value -> value.getAsString()).toList();
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }
}
