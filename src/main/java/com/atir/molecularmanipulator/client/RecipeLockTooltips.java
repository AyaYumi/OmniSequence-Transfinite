package com.atir.molecularmanipulator.client;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipe;
import com.atir.molecularmanipulator.crafting.MatterRecipeIndex;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.atir.molecularmanipulator.recipelock.RecipeLocks.ResearchRequirement;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodeRecipe;
import com.atir.molecularmanipulator.starcomputation.StarResearchNodes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.jetbrains.annotations.Nullable;

/** Research requirements describe recipes, not ownership of an item or the viewer's current grants. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class RecipeLockTooltips {
    private static final String KEY = "tooltip.molecularmanipulator.recipe_lock.";
    private static final ThreadLocal<List<Component>> CURRENT_RECIPE = new ThreadLocal<>();
    @Nullable private static OutputIndex outputs;

    private record OutputLock(ItemStack output, List<ResearchRequirement> requirements) {}
    private record OutputIndex(RecipeManager manager, Collection<RecipeHolder<?>> source, long generation,
                               Map<Item, List<OutputLock>> byItem) {}

    private RecipeLockTooltips() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void tooltip(ItemTooltipEvent event) {
        // JEI supplies the actual displayed recipe separately, including custom and non-item renderers.
        if (CURRENT_RECIPE.get() != null) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        var item = event.getItemStack();
        var wrapped = GenericStack.unwrapItemStack(item);
        if (wrapped != null) {
            if (!(wrapped.what() instanceof AEItemKey key)) return;
            item = key.toStack();
        }
        event.getToolTip().addAll(itemLines(level, item));
    }

    static List<Component> itemLines(Level level, ItemStack stack) {
        var groups = new LinkedHashSet<List<ResearchRequirement>>();
        for (var entry : outputIndex(level).getOrDefault(stack.getItem(), List.of())) {
            if (ItemStack.isSameItemSameComponents(stack, entry.output())) groups.add(entry.requirements());
        }
        boolean partial = groups.size() > 1;
        var result = new ArrayList<Component>();
        for (var group : groups) {
            for (var line : lines(group, StarResearchNodes.nodes(level))) {
                result.add(partial ? Component.translatable(KEY + "some_recipes", line)
                        .withStyle(ChatFormatting.GOLD) : line);
            }
        }
        return List.copyOf(result);
    }

    private static Map<Item, List<OutputLock>> outputIndex(Level level) {
        var manager = level.getRecipeManager();
        var source = manager.getRecipes();
        long generation = PatternRecipeReloads.generation();
        if (outputs != null && outputs.manager() == manager && outputs.source() == source
                && outputs.generation() == generation) return outputs.byItem();
        var recipes = new ArrayList<RecipeHolder<?>>(source);
        recipes.addAll(MatterRecipeIndex.get(level).fabrication());
        var result = new HashMap<Item, List<OutputLock>>();
        for (var holder : recipes) {
            var requirements = RecipeLocks.requirements(level, holder);
            if (holder.value() instanceof MatterFabricationRecipe matter) {
                for (var stack : matter.results()) addOutput(result, stack, requirements);
                for (var output : matter.aeOutputs()) {
                    if (output.what() instanceof AEItemKey key) addOutput(result, key.toStack(), requirements);
                }
            } else {
                addOutput(result, holder.value().getResultItem(level.registryAccess()), requirements);
            }
        }
        // Retain open alternatives only for items that also have a locked recipe.
        result.entrySet().removeIf(entry -> entry.getValue().stream().allMatch(value -> value.requirements().isEmpty()));
        result.replaceAll((item, entries) -> List.copyOf(entries));
        outputs = new OutputIndex(manager, source, generation, Map.copyOf(result));
        return outputs.byItem();
    }

    private static void addOutput(Map<Item, List<OutputLock>> index, ItemStack stack,
                                  List<ResearchRequirement> requirements) {
        if (!stack.isEmpty()) index.computeIfAbsent(stack.getItem(), ignored -> new ArrayList<>())
                .add(new OutputLock(stack.copyWithCount(1), requirements));
    }

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

    /** Scope covers JEI's real slot tooltip and prevents an ingredient's other recipes leaking into it. */
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
