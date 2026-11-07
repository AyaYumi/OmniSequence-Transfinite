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
    void basicAutoCrafterBelongsOnlyToFoundationAndRetainsItsEngineGate() throws IOException {
        var id = MOD + "molecular_auto_crafter";
        var owners = researches().entrySet().stream()
                .filter(entry -> strings(entry.getValue(), "unlocks").contains(id))
                .map(Map.Entry::getKey).sorted().toList();
        assertEquals(List.of("ae_foundation"), owners);
        var recipe = json(RECIPES.resolve("molecular_auto_crafter.json"));
        assertTrue(recipe.get("requires_research").getAsBoolean());
        assertEquals(1, StarComputationRecipes.requiredMask(ResourceLocation.parse(id)),
                "Moving well research must not bypass the permanent foundation node");
        assertEquals(List.of(), strings(researches().get("ae_foundation"), "required_mods"));
    }

    @Test
    void exoticMatterAppearsAfterFoundationWithoutBecomingAMandatoryTier() throws IOException {
        var researches = researches();
        var order = researches.entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getValue().get("sort_order").getAsInt()))
                .map(Map.Entry::getKey).toList();
        assertEquals(List.of("ae_foundation", "nomai_materials", "sequence_array", "omni_computation", "event_horizon"), order);
        var exotic = researches.get("nomai_materials");
        assertEquals(0, exotic.get("stage").getAsInt());
        assertEquals(1, exotic.getAsJsonArray("depths").size());
        assertEquals(List.of(MOD + "research/ae_foundation"), strings(exotic, "prerequisites"));
        assertEquals(List.of(MOD + "ghost_matter", MOD + "gravity_crystal"), strings(exotic, "unlocks"));
    }

    @Test
    void thirdPartyMaterialUnlocksStayInFoundationAndOutOfOtherBranches() throws IOException {
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
            assertEquals(entry.getKey().equals("ae_foundation") ? expected : Set.of(), new HashSet<>(linked), entry.getKey());
            assertEquals(new HashSet<>(linked).size(), linked.size(), "Linked materials must not be duplicated");
        }
    }

    @Test
    void existingPrerequisitesLoadingConditionsAndDeepResearchRemainUnchanged() throws IOException {
        var researches = researches();
        var parents = Map.of(
                "ae_foundation", List.<String>of(),
                "nomai_materials", List.of("ae_foundation"),
                "sequence_array", List.of("ae_foundation"),
                "omni_computation", List.of("ae_foundation"),
                "event_horizon", List.of("sequence_array", "omni_computation"));
        var mods = Map.of("sequence_array", List.of("extendedae"), "omni_computation", List.of("advanced_ae"),
                "event_horizon", List.of("extendedae", "advanced_ae"));
        var powers = Map.of("ae_foundation", 256, "nomai_materials", 256, "sequence_array", 512,
                "omni_computation", 1024, "event_horizon", 2048);
        for (var entry : researches.entrySet()) {
            var name = entry.getKey();
            var definition = entry.getValue();
            assertEquals(parents.get(name).stream().map(id -> MOD + "research/" + id).toList(),
                    strings(definition, "prerequisites"), name);
            assertEquals(mods.getOrDefault(name, List.of()), strings(definition, "required_mods"), name);
            assertEquals(600, definition.get("duration").getAsInt(), name);
            assertEquals(powers.get(name).intValue(), definition.get("ae_per_tick").getAsInt(), name);
            var conditions = definition.getAsJsonArray("neoforge:conditions");
            if (conditions == null) {
                assertFalse(mods.containsKey(name), name);
            } else {
                assertEquals(mods.get(name), conditions.asList().stream().map(condition -> {
                    assertEquals("neoforge:mod_loaded", condition.getAsJsonObject().get("type").getAsString());
                    return condition.getAsJsonObject().get("modid").getAsString();
                }).toList(), name);
            }
            var depths = definition.getAsJsonArray("depths");
            assertEquals(name.equals("nomai_materials") ? 1 : 9, depths.size(), name);
            for (int index = 0; index < depths.size(); index++) {
                var depth = depths.get(index).getAsJsonObject();
                assertEquals(1L << index, depth.get("material_multiplier").getAsLong(), name);
                assertEquals(index == 8 ? Long.MAX_VALUE : 1L << (index * 8), depth.get("parallel").getAsLong(), name);
            }
        }
    }

    private static void addMaterials(Set<String> target, String mod, List<String> paths) {
        paths.forEach(path -> target.add(MOD + "fabrication/research_materials/" + mod + "/" + path));
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
