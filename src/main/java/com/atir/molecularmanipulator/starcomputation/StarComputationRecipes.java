package com.atir.molecularmanipulator.starcomputation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Shared legacy research costs, stable save bits and default recipe display lists. */
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
            node(0, "foundation", 90, 512, 0),
            // The sole built-in research: free at L3, no prerequisite; keep its permanent legacy bit 1.
            node(1, "material", 900, 0, 0, "molecular_manipulator", "transfinite_compute_nexus"),
            node(2, "quantum", 9000, 8192, 1 << 1),
            node(3, "terminal", 18000, 16384, 1),
            node(4, "transfinite", 90000, 65536, (1 << 2) | (1 << 3)));
    public static final int ALL_UNLOCKS = (1 << NODES.size()) - 1;
    private static final Map<ResourceLocation, Integer> RECIPE_MASKS = recipeMasks();

    private StarComputationRecipes() {}

    /** Immutable default recipe IDs for the research menu; data-pack lock rules are resolved separately. */
    public static List<ResourceLocation> recipeIds(int index) {
        return index >= 0 && index < NODES.size() ? NODES.get(index).recipeIds() : List.of();
    }

    /** Default gate mask for menu and legacy callers; data-pack lock rules and matter research apply separately. */
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
                        .map(path -> new ResourceLocation("molecularmanipulator", path)).toList());
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
