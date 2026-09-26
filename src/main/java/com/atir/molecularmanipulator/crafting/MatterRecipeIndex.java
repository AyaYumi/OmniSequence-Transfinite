package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.research.MatterResearchRecipe;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Shared definition indexes; controller research completion is deliberately not cached here. */
public final class MatterRecipeIndex {
    private static final Map<RecipeManager, MatterRecipeIndex> CACHE = new WeakHashMap<>();
    private final Collection<RecipeHolder<?>> source;
    private final List<RecipeHolder<MatterFabricationRecipe>> fabrication;
    private final Map<ResourceLocation, RecipeHolder<MatterFabricationRecipe>> fabricationById = new HashMap<>();
    private final List<RecipeHolder<MatterResearchRecipe>> research;
    private final Map<Map<AEKey, Long>, List<RecipeHolder<MatterFabricationRecipe>>> byOutput = new HashMap<>();
    private final Map<ResourceLocation, List<RecipeHolder<MatterResearchRecipe>>> researchByRecipe = new HashMap<>();
    private final Map<ResourceLocation, List<RecipeHolder<MatterResearchRecipe>>> dynamicResearchByRecipe = new HashMap<>();
    private final Map<ResourceLocation, Integer> branchCounts = new HashMap<>();
    private final Map<ResourceLocation, Map<String, Integer>> researchOutputBranchCounts = new HashMap<>();

    private MatterRecipeIndex(Level level, RecipeManager manager, Collection<RecipeHolder<?>> source) {
        this.source = source;
        var allFabrication = new ArrayList<>(manager.getAllRecipesFor(ModContent.MATTER_FABRICATION_RECIPE_TYPE.get()));
        var imported = MatterRecipeBridge.importRecipes(level, source);
        for (var value : imported) {
            var holder = value.holder();
            if (allFabrication.stream().noneMatch(existing -> existing.id().equals(holder.id()))) {
                allFabrication.add(holder);
                branchCounts.put(holder.id(), value.branchCount());
            }
        }
        fabrication = List.copyOf(allFabrication);
        // Keep RecipeManager's candidate order when several recipes have the same complete output.
        // Imported machine recipes are part of the same candidate pool.  Omitting them here
        // makes them visible in the research/JEI screens but impossible for the fabrication
        // matcher to select.
        for (var holder : fabrication) {
            var output = Map.copyOf(outputAmounts(holder.value()));
            byOutput.computeIfAbsent(output, ignored -> new ArrayList<>()).add(holder);
        }
        byOutput.replaceAll((output, recipes) -> List.copyOf(recipes));
        var definitions = new ArrayList<>(manager.getAllRecipesFor(ModContent.MATTER_RESEARCH_RECIPE_TYPE.get()).stream()
                .filter(holder -> holder.value().available())
                .sorted(Comparator.<RecipeHolder<MatterResearchRecipe>>comparingInt(holder -> holder.value().sortOrder())
                        .thenComparing(holder -> holder.id().toString())).toList());
        var importedByMachine = new LinkedHashMap<String, List<RecipeHolder<MatterFabricationRecipe>>>();
        for (var value : imported) importedByMachine.computeIfAbsent(value.machineKey(), ignored -> new ArrayList<>()).add(value.holder());
        for (var machine : MatterRecipeBridge.machines()) {
            var recipes = importedByMachine.getOrDefault(machine.key(), List.of());
            if (recipes.isEmpty() || !ModList.get().isLoaded(machine.modId())) continue;
            var item = BuiltInRegistries.ITEM.get(machine.machineItem());
            if (item == null || !BuiltInRegistries.ITEM.containsKey(machine.machineItem())) continue;
            var unlocks = recipes.stream().map(RecipeHolder::id).toList();
            var id = MolecularManipulator.id("research/machine/" + machine.key());
            var parent = ResourceLocation.parse("molecularmanipulator:research/ae_foundation");
            var definition = new MatterResearchRecipe("research.molecularmanipulator.machine." + machine.key(),
                    manager.byKey(parent).isPresent() ? List.of(parent) : List.of(),
                    List.of(new MatterResearchRecipe.Cost(Ingredient.of(item), 1)), 600, 512,
                    unlocks, List.of(machine.modId()), 1000 + definitions.size(), 2);
            var holder = new RecipeHolder<>(id, definition);
            definitions.add(holder);
            var counts = new HashMap<String, Integer>();
            for (var recipe : recipes) {
                counts.merge(outputKey(recipe.value()), 1, Integer::sum);
                dynamicResearchByRecipe.computeIfAbsent(recipe.id(), ignored -> new ArrayList<>()).add(holder);
            }
            researchOutputBranchCounts.put(id, counts);
        }
        research = definitions.stream().sorted(Comparator.<RecipeHolder<MatterResearchRecipe>>comparingInt(holder -> holder.value().sortOrder())
                .thenComparing(holder -> holder.id().toString())).toList();
        for (var holder : research) {
            for (var recipe : holder.value().unlocks()) {
                researchByRecipe.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(holder);
            }
        }
        dynamicResearchByRecipe.replaceAll((recipe, owners) -> List.copyOf(owners));
        for (var holder : fabrication) fabricationById.put(holder.id(), holder);
        researchByRecipe.replaceAll((recipe, owners) -> List.copyOf(owners));
    }

    public static MatterRecipeIndex get(Level level) {
        var manager = level.getRecipeManager();
        // In 1.21.1 this is the cached values view of RecipeManager's immutable byName map.
        // Both data-pack apply and replaceRecipes (also client sync) replace that map, even for identical IDs/counts.
        var source = manager.getRecipes();
        synchronized (CACHE) {
            var index = CACHE.get(manager);
            if (index == null || index.source != source) {
                index = new MatterRecipeIndex(level, manager, source);
                CACHE.put(manager, index);
            }
            return index;
        }
    }

    public List<RecipeHolder<MatterFabricationRecipe>> candidates(Map<AEKey, Long> output) {
        return byOutput.getOrDefault(output, List.of());
    }

    public List<RecipeHolder<MatterFabricationRecipe>> fabrication() { return fabrication; }

    public RecipeHolder<MatterFabricationRecipe> fabrication(ResourceLocation id) { return fabricationById.get(id); }

    public List<RecipeHolder<MatterResearchRecipe>> research() { return research; }

    public List<RecipeHolder<MatterResearchRecipe>> researchFor(ResourceLocation recipe) {
        var result = new ArrayList<RecipeHolder<MatterResearchRecipe>>();
        result.addAll(researchByRecipe.getOrDefault(recipe, List.of()));
        result.addAll(dynamicResearchByRecipe.getOrDefault(recipe, List.of()));
        return List.copyOf(result);
    }

    public List<ResourceLocation> unlocksForResearch(RecipeHolder<MatterResearchRecipe> holder) {
        if (!researchOutputBranchCounts.containsKey(holder.id())) return holder.value().unlocks();
        var result = new ArrayList<ResourceLocation>();
        var seenOutputs = new java.util.HashSet<String>();
        for (var id : holder.value().unlocks()) {
            var recipe = fabricationById.get(id);
            if (recipe == null) {
                result.add(id);
                continue;
            }
            // A machine can have several input branches for one product.  The panel shows
            // one product row and the branch count beside it; the permission index still
            // retains every recipe id internally.
            if (seenOutputs.add(outputKey(recipe.value()))) result.add(id);
        }
        for (var entry : dynamicResearchByRecipe.entrySet()) {
            if (research.stream().filter(value -> value.id().equals(holder.id())).findFirst().isPresent()
                    && entry.getValue().stream().anyMatch(value -> value.id().equals(holder.id()))) {
                var recipe = fabricationById.get(entry.getKey());
                if (recipe != null && seenOutputs.add(outputKey(recipe.value()))) result.add(entry.getKey());
            }
        }
        return List.copyOf(result);
    }

    public int branchCountForResearch(ResourceLocation researchId, MatterFabricationRecipe recipe) {
        return Math.max(1, researchOutputBranchCounts.getOrDefault(researchId, Map.of()).getOrDefault(outputKey(recipe), 1));
    }

    private static String outputKey(ItemStack stack) {
        return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()) + "@" + stack.getComponents().hashCode();
    }

    private static String outputKey(MatterFabricationRecipe recipe) {
        var items = recipe.results().stream().map(stack -> outputKey(stack) + "#" + stack.getCount())
                .sorted().reduce("", (a, b) -> a + ";" + b);
        var fluid = recipe.fluidResult().isEmpty() ? "" : BuiltInRegistries.FLUID.getKey(recipe.fluidResult().getFluid())
                + "#" + recipe.fluidResult().getAmount();
        var ae = recipe.aeOutputs().stream().map(stack -> stack.what() + "#" + stack.amount())
                .sorted().reduce("", (a, b) -> a + ";" + b);
        return items + "|fluid=" + fluid + "|ae=" + ae;
    }

    public static Map<AEKey, Long> outputAmounts(MatterFabricationRecipe recipe) {
        var result = new LinkedHashMap<AEKey, Long>();
        for (var stack : recipe.results()) result.merge(AEItemKey.of(stack), (long) stack.getCount(), Math::addExact);
        if (!recipe.fluidResult().isEmpty()) result.merge(AEFluidKey.of(recipe.fluidResult()), (long) recipe.fluidResult().getAmount(), Math::addExact);
        for (var stack : recipe.aeOutputs()) result.merge(stack.what(), stack.amount(), Math::addExact);
        return result;
    }
}
