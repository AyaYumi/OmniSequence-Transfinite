package com.atir.molecularmanipulator.starcomputation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Shared engine research costs, stable save bits and the actual recipes those bits permit. */
public final class StarComputationRecipes {
    public record Node(int index, String id, long entropy, long informationCost, int prerequisiteMask,
                       List<ResourceLocation> recipeIds) {
        public Node {
            recipeIds = List.copyOf(recipeIds);
        }

        public String translationKey() { return "gui.molecularmanipulator.star_computation.node." + id; }
        public boolean unlocked(int mask) { return (mask & (1 << index)) != 0; }
        public boolean prerequisitesMet(int mask) { return (mask & prerequisiteMask) == prerequisiteMask; }
    }

    /** Never reorder these nodes: their indices are permanent team and legacy core save bits. */
    public static final List<Node> NODES = List.of(
            node(0, "foundation", 90, 512, 0,
                    "molecular_auto_crafter", "molecular_center_casing", "molecular_center_coil",
                    "molecular_center_glass", "molecular_center_stabilizer"),
            // Base anchors cap at 900. The quantum anchor must unlock before requiring quantum entropy.
            node(1, "material", 900, 2048, 1,
                    "quantum_star_position_anchor", "molecular_manipulator", "molecular_center_core",
                    "molecular_center_controller", "assembler_matrix_molecular_core"),
            // Quantum anchors cap at 9000. Terminal anchors must be available at that threshold.
            node(2, "quantum", 9000, 8192, 1 << 1,
                    "terminal_star_position_anchor", "omni_computation_controller", "omni_computation_casing",
                    "omni_computation_glass", "computation_crystal_pylon", "computation_data_entangler",
                    "computation_energy_stabilizer", "computation_output_node"),
            node(3, "terminal", 18000, 16384, 1,
                    "infinite_crafting_storage", "infinite_parallel_matrix", "universal_pattern_matrix"),
            node(4, "transfinite", 90000, 65536, (1 << 2) | (1 << 3),
                    "transfinite_compute_nexus"));
    public static final int ALL_UNLOCKS = (1 << NODES.size()) - 1;
    private static final Map<ResourceLocation, Integer> RECIPE_MASKS = recipeMasks();

    private StarComputationRecipes() {}

    /** The same immutable IDs used by enforcement, suitable for recipe output/tooltips on either side. */
    public static List<ResourceLocation> recipeIds(int index) {
        return index >= 0 && index < NODES.size() ? NODES.get(index).recipeIds() : List.of();
    }

    /** Unlisted recipes have no engine requirement; existing matter research still applies separately. */
    public static int requiredMask(ResourceLocation recipeId) {
        return RECIPE_MASKS.getOrDefault(recipeId, 0);
    }

    public static boolean isUnlocked(ResourceLocation recipeId, int mask) {
        int required = requiredMask(recipeId);
        return (mask & required) == required;
    }

    private static Node node(int index, String id, long entropy, long informationCost, int prerequisites,
                             String... recipes) {
        return new Node(index, id, entropy, informationCost, prerequisites,
                java.util.Arrays.stream(recipes)
                        .map(path -> ResourceLocation.fromNamespaceAndPath("molecularmanipulator", path)).toList());
    }

    private static Map<ResourceLocation, Integer> recipeMasks() {
        var result = new LinkedHashMap<ResourceLocation, Integer>();
        for (var node : NODES) {
            for (var recipe : node.recipeIds()) {
                if (result.putIfAbsent(recipe, 1 << node.index()) != null) {
                    throw new IllegalStateException("Duplicate star computation recipe: " + recipe);
                }
            }
        }
        return Map.copyOf(result);
    }
}
