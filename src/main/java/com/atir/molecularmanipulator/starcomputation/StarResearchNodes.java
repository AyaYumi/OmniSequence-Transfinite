package com.atir.molecularmanipulator.starcomputation;

import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.mixin.RecipeManagerAccessor;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Reload-aware node directory, shared by purchases and recipe-lock rule validation. Never contains team progress. */
public final class StarResearchNodes {
    private static final Logger LOGGER = LoggerFactory.getLogger(StarResearchNodes.class);
    private static final Map<ResourceLocation, StarResearchNodeRecipe> FALLBACKS = fallbacks();
    private static final Map<RecipeManager, Cached> CACHE = new WeakHashMap<>();

    private StarResearchNodes() {}

    /** Includes the material fallback, overridden by a data-pack recipe with the exact same holder ID. */
    public static Map<ResourceLocation, StarResearchNodeRecipe> nodes(Level level) {
        return catalog(level).nodes();
    }

    public static Catalog catalog(Level level) {
        return catalog(level.getRecipeManager(), PatternRecipeReloads.generation());
    }

    /** Unknown IDs and invalid graphs fail closed, even when an absent prerequisite was previously purchased. */
    @Nullable
    public static String validationError(Level level, ResourceLocation id) {
        return catalog(level).error(id);
    }

    public record Catalog(Map<ResourceLocation, StarResearchNodeRecipe> nodes, Map<ResourceLocation, String> errors) {
        public Catalog {
            nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
            errors = Collections.unmodifiableMap(new LinkedHashMap<>(errors));
        }

        @Nullable public String error(ResourceLocation id) {
            return nodes.containsKey(id) ? errors.get(id) : "Unknown research node: " + id;
        }

        /** The supplied set MUST be the owning team's permanent progress, not network-borrowed authorization. */
        public boolean canUnlock(ResourceLocation id, int actualLevel, Set<ResourceLocation> permanentUnlocks) {
            if (error(id) != null || permanentUnlocks.contains(id)) return false;
            var recipe = nodes.get(id);
            return actualLevel >= recipe.requiredLevel() && permanentUnlocks.containsAll(recipe.prerequisites());
        }
    }

    private record Cached(long generation, Map<ResourceLocation, net.minecraft.world.item.crafting.Recipe<?>> source,
                          Catalog catalog) {}

    // Package-private boundary lets tests model same-manager reloads without constructing a Level or a mod event bus.
    static synchronized Catalog catalog(RecipeManager manager, long generation) {
        var source = ((RecipeManagerAccessor) manager).molecularmanipulator$getRecipesByName();
        var cached = CACHE.get(manager);
        if (cached != null && cached.generation() == generation && cached.source() == source) return cached.catalog();
        var overrides = new LinkedHashMap<ResourceLocation, StarResearchNodeRecipe>();
        for (var candidate : source.values()) {
            if (candidate instanceof StarResearchNodeRecipe recipe) overrides.put(recipe.id(), recipe);
        }
        var catalog = build(overrides);
        catalog.errors().forEach((id, error) -> LOGGER.warn("Cannot purchase star research node {}: {}", id, error));
        CACHE.put(manager, new Cached(generation, source, catalog));
        return catalog;
    }

    /** Pure validation. Iterative topological traversal has no arbitrary five/32-node limit or recursion-depth cap. */
    static Catalog build(Map<ResourceLocation, StarResearchNodeRecipe> overrides) {
        var nodes = new LinkedHashMap<>(FALLBACKS);
        overrides.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> nodes.put(entry.getKey(), entry.getValue()));
        var errors = new LinkedHashMap<ResourceLocation, String>();
        var remaining = new HashMap<ResourceLocation, Integer>();
        var dependents = new HashMap<ResourceLocation, List<ResourceLocation>>();
        var ready = new ArrayDeque<ResourceLocation>();
        nodes.forEach((id, recipe) -> {
            String error = recipe.error();
            if (error != null) errors.put(id, error);
            int count = 0;
            for (var prerequisite : new LinkedHashSet<>(recipe.prerequisites())) {
                if (!nodes.containsKey(prerequisite)) {
                    errors.putIfAbsent(id, "Missing prerequisite: " + prerequisite);
                } else {
                    count++;
                    dependents.computeIfAbsent(prerequisite, ignored -> new ArrayList<>()).add(id);
                }
            }
            remaining.put(id, count);
            if (count == 0) ready.addLast(id);
        });
        while (!ready.isEmpty()) {
            var id = ready.removeFirst();
            for (var dependent : dependents.getOrDefault(id, List.of())) {
                if (errors.containsKey(id)) errors.putIfAbsent(dependent, "Invalid prerequisite: " + id);
                int count = remaining.compute(dependent, (ignored, previous) -> previous - 1);
                if (count == 0) ready.addLast(dependent);
            }
        }
        remaining.forEach((id, count) -> {
            if (count > 0) errors.putIfAbsent(id, "Cyclic prerequisites (or depends on a cycle)");
        });
        return new Catalog(nodes, errors);
    }

    private static Map<ResourceLocation, StarResearchNodeRecipe> fallbacks() {
        // Material remains permanent legacy bit 1, even though it is now the first (and only default) menu entry.
        // The other four IDs/bits remain save compatibility data, not registered research definitions.
        var material = StarComputationRecipes.NODES.get(1);
        return Map.of(StarComputationResearchData.LEGACY_NODE_IDS.get(1),
                new StarResearchNodeRecipe(material.translationKey(), 3, material.informationCost(), List.of())
                        .withId(StarComputationResearchData.LEGACY_NODE_IDS.get(1)));
    }
}
