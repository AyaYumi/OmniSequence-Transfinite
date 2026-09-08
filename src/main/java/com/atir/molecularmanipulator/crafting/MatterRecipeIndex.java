package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.mixin.RecipeManagerAccessor;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;

/** Shared definition indexes; controller research completion is deliberately not cached here. */
public final class MatterRecipeIndex {
    private static final Map<RecipeManager, MatterRecipeIndex> CACHE = new WeakHashMap<>();
    private final Map<ResourceLocation, Recipe<?>> source;
    private final List<MatterResearchRecipe> research;
    private final Map<Map<AEKey, Long>, List<MatterFabricationRecipe>> byOutput = new HashMap<>();
    private final Map<ResourceLocation, List<MatterResearchRecipe>> researchByRecipe = new HashMap<>();

    private MatterRecipeIndex(RecipeManager manager, Map<ResourceLocation, Recipe<?>> source) {
        this.source = source;
        // Keep RecipeManager's candidate order when several recipes have the same complete output.
        for (var holder : manager.getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get())) {
            var output = Map.copyOf(outputAmounts(holder.value()));
            byOutput.computeIfAbsent(output, ignored -> new ArrayList<>()).add(holder);
        }
        byOutput.replaceAll((output, recipes) -> List.copyOf(recipes));
        research = manager.getAllRecipesFor(ModContent.MATTER_RESEARCH_RECIPE_TYPE.get()).stream()
                .filter(holder -> holder.value().available())
                .sorted(Comparator.<MatterResearchRecipe>comparingInt(holder -> holder.value().sortOrder())
                        .thenComparing(holder -> holder.id().toString())).toList();
        for (var holder : research) {
            for (var recipe : holder.value().unlocks()) {
                researchByRecipe.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(holder);
            }
        }
        researchByRecipe.replaceAll((recipe, owners) -> List.copyOf(owners));
    }

    public static MatterRecipeIndex get(Level level) {
        var manager = level.getRecipeManager();
        // Forge 1.20.1 getRecipes() allocates a new Set on every call. Observe the replaced
        // byName snapshot directly so reloads invalidate this index without rebuilding it per lookup.
        var source = ((RecipeManagerAccessor) manager).molecularmanipulator$getRecipesByName();
        synchronized (CACHE) {
            var index = CACHE.get(manager);
            if (index == null || index.source != source) {
                index = new MatterRecipeIndex(manager, source);
                CACHE.put(manager, index);
            }
            return index;
        }
    }

    public List<MatterFabricationRecipe> candidates(Map<AEKey, Long> output) {
        return byOutput.getOrDefault(output, List.of());
    }

    public List<MatterResearchRecipe> research() { return research; }

    public List<MatterResearchRecipe> researchFor(ResourceLocation recipe) {
        return researchByRecipe.getOrDefault(recipe, List.of());
    }

    public static Map<AEKey, Long> outputAmounts(MatterFabricationRecipe recipe) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var stack : recipe.results()) result.merge(AEItemKey.of(stack), (long) stack.getCount(), Math::addExact);
        if (!recipe.fluidResult().isEmpty()) result.merge(AEFluidKey.of(recipe.fluidResult()), (long) recipe.fluidResult().getAmount(), Math::addExact);
        return result;
    }
}
