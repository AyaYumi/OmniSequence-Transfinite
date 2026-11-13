package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.recipelock.RecipeLocks.ResearchRequirement;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

/** Research requirements for JEI's displayed recipe, independent of the viewer's current grants. */
public final class RecipeLockTooltips {
    private static final String KEY = "tooltip.molecularmanipulator.recipe_lock.";
    private static final ThreadLocal<List<Component>> CURRENT_RECIPE = new ThreadLocal<>();

    private RecipeLockTooltips() {}

    /** A single expression preserves (A or B) and C when several lock declarations overlap. */
    static List<Component> lines(List<ResearchRequirement> requirements,
                                 Map<ResourceLocation, StarResearchNodeRecipe> nodes) {
        if (requirements.isEmpty()) return List.of();
        var clauses = new ArrayList<Component>();
        for (var rule : requirements) {
            var names = rule.nodes().stream().sorted().map(id -> {
                var node = nodes.get(id);
                return node == null || node.title().isBlank() ? Component.literal(id.toString())
                        : Component.translatable(node.title());
            }).map(Component.class::cast).toList();
            Component clause = join(names, rule.any() ? "or" : "and");
            if (requirements.size() > 1 && names.size() > 1) {
                clause = Component.translatable(KEY + "group", clause);
            }
            clauses.add(clause);
        }
        var result = new ArrayList<Component>();
        result.add(Component.translatable(KEY + "requires", join(clauses, "and")).withStyle(ChatFormatting.GOLD));
        if (requirements.stream().anyMatch(rule -> !rule.valid())) {
            result.add(Component.translatable(KEY + "invalid").withStyle(ChatFormatting.RED));
        }
        return List.copyOf(result);
    }

    private static Component join(List<Component> values, String conjunction) {
        var result = Component.empty();
        for (var value : values) {
            if (!result.getSiblings().isEmpty()) result.append(Component.translatable(KEY + conjunction));
            result.append(value);
        }
        return result;
    }

    /** Binds output-slot and recipe-background tooltips to the recipe JEI is currently drawing. */
    public static Scope recipeContext(@Nullable ResourceLocation id, Object recipe) {
        var previous = CURRENT_RECIPE.get();
        var level = Minecraft.getInstance().level;
        var requirements = new ArrayList<ResearchRequirement>();
        if (level != null) {
            if (id != null) requirements.addAll(RecipeLocks.requirements(level, id));
            if (recipe instanceof RecipeHolder<?> holder) requirements.addAll(RecipeLocks.requirements(level, holder));
            else if (recipe instanceof Recipe<?> value) requirements.addAll(RecipeLocks.requirements(level, value));
        }
        CURRENT_RECIPE.set(level == null ? List.of() : lines(requirements.stream().distinct().toList(), StarResearchNodes.nodes(level)));
        return new Scope(previous);
    }

    public static void appendCurrentRecipe(Consumer<Component> tooltip) {
        var lines = CURRENT_RECIPE.get();
        if (lines != null) lines.forEach(tooltip);
    }

    public record Scope(@Nullable List<Component> previous) implements AutoCloseable {
        @Override public void close() {
            if (previous == null) CURRENT_RECIPE.remove();
            else CURRENT_RECIPE.set(previous);
        }
    }
}
