package com.atir.molecularmanipulator.recipelock;

import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Immutable recipe-ID index. Selectors run at reload, not at every machine tick. No team authorization cache. */
final class RecipeLockIndex {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeLockIndex.class);
    private final Map<ResourceLocation, List<Requirement>> requirements;
    private final Map<Recipe<?>, ResourceLocation> identities;

    record Requirement(Set<ResourceLocation> nodes, boolean any, boolean valid) {
        Requirement { nodes = Set.copyOf(nodes); }

        boolean satisfied(Set<ResourceLocation> grants) {
            return valid && (any ? nodes.stream().anyMatch(grants::contains) : grants.containsAll(nodes));
        }
    }

    private RecipeLockIndex(Map<ResourceLocation, List<Requirement>> requirements,
                            Map<Recipe<?>, ResourceLocation> identities) {
        var immutable = new HashMap<ResourceLocation, List<Requirement>>();
        requirements.forEach((id, rules) -> immutable.put(id, List.copyOf(rules)));
        this.requirements = Map.copyOf(immutable);
        this.identities = Collections.unmodifiableMap(new IdentityHashMap<>(identities));
    }

    boolean hasRules() { return !requirements.isEmpty(); }
    boolean locked(ResourceLocation id) { return requirements.containsKey(id); }
    @Nullable ResourceLocation idOf(Recipe<?> recipe) { return identities.get(recipe); }

    List<RecipeLocks.ResearchRequirement> requirements(ResourceLocation id) {
        return requirements.getOrDefault(id, List.of()).stream()
                .map(rule -> new RecipeLocks.ResearchRequirement(rule.nodes(), rule.any(), rule.valid())).toList();
    }

    boolean allowed(ResourceLocation id, Set<ResourceLocation> grants) {
        for (var rule : requirements.getOrDefault(id, List.of())) {
            if (!rule.satisfied(grants)) return false;
        }
        return true;
    }

    static RecipeLockIndex compile(Collection<RecipeHolder<?>> recipes, StarResearchNodes.Catalog nodes) {
        var types = new LinkedHashMap<ResourceLocation, ResourceLocation>();
        var declarations = new ArrayList<RecipeHolder<RecipeLockRecipe>>();
        var identities = new IdentityHashMap<Recipe<?>, ResourceLocation>();
        for (var holder : recipes) {
            if (holder.value() instanceof RecipeLockRecipe lock) {
                declarations.add(new RecipeHolder<>(holder.id(), lock));
            } else if (!(holder.value() instanceof StarResearchNodeRecipe)) {
                types.put(holder.id(), BuiltInRegistries.RECIPE_TYPE.getKey(holder.value().getType()));
                identities.put(holder.value(), holder.id());
            }
        }
        // The name press has no registered holder, but namespace/type/exact-ID locks must cover it too.
        types.put(RecipeLocks.NAME_PRESS_RECIPE, ResourceLocation.parse("ae2:inscriber"));
        var valid = new HashSet<>(nodes.nodes().keySet());
        valid.removeAll(nodes.errors().keySet());
        var built = build(types, declarations, valid);
        return new RecipeLockIndex(built.requirements, identities);
    }

    /** Pure expansion boundary for rule/selector/permission regression tests. */
    static RecipeLockIndex build(Map<ResourceLocation, ResourceLocation> recipes,
                                 List<RecipeHolder<RecipeLockRecipe>> declarations,
                                 Set<ResourceLocation> validNodes) {
        var result = new LinkedHashMap<ResourceLocation, List<Requirement>>();
        for (var holder : declarations) {
            var rule = holder.value();
            String error = rule.error();
            if (error != null) throw new IllegalArgumentException("Invalid recipe lock " + holder.id() + ": " + error);
            boolean valid = validNodes.containsAll(rule.requires());
            if (!valid) LOGGER.warn("Recipe lock {} references undefined/invalid research nodes; matching recipes stay locked: {}",
                    holder.id(), rule.requires());
            var requirement = new Requirement(new HashSet<>(rule.requires()), "any".equals(rule.mode()), valid);
            var selector = rule.match();
            var patterns = selector.recipeIdRegex().stream().map(Pattern::compile).toList();
            // Exact IDs remain protected even when a bridge produces their holder outside RecipeManager.
            var candidates = new HashSet<>(recipes.keySet());
            candidates.addAll(selector.recipeIds());
            int matched = 0;
            for (var id : candidates) {
                if (matches(selector, patterns, id, recipes.get(id))) {
                    result.computeIfAbsent(id, ignored -> new ArrayList<>()).add(requirement);
                    matched++;
                }
            }
            if (matched == 0) LOGGER.warn("Recipe lock {} matches no loaded recipes", holder.id());
        }
        return new RecipeLockIndex(result, Map.of());
    }

    /** Within a selector list: OR; between nonempty selector fields: AND; exclusions always win. */
    private static boolean matches(RecipeLockRecipe.Selector selector, List<Pattern> patterns,
                                   ResourceLocation id, @Nullable ResourceLocation type) {
        return !selector.excludeIds().contains(id)
                && (selector.recipeIds().isEmpty() || selector.recipeIds().contains(id))
                && (patterns.isEmpty() || patterns.stream().anyMatch(pattern -> pattern.matcher(id.toString()).matches()))
                && (selector.recipeTypes().isEmpty() || type != null && selector.recipeTypes().contains(type))
                && (selector.namespaces().isEmpty() || selector.namespaces().contains(id.getNamespace()));
    }
}
