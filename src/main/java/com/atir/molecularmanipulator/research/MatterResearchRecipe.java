package com.atir.molecularmanipulator.research;

import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

/** Research is a normal data-pack recipe so KubeJS can add, replace and remove definitions. */
public record MatterResearchRecipe(String title, List<ResourceLocation> prerequisites, List<Cost> ingredients,
        int duration, double aePerTick, List<ResourceLocation> unlocks, List<String> requiredMods, int sortOrder, int stage,
        List<ResearchDepth> depths, Map<ResourceLocation, Integer> prerequisiteLevels)
        implements Recipe<MatterFabricationRecipeInput> {
    public static final MapCodec<MatterResearchRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("title").forGetter(MatterResearchRecipe::title),
            ResourceLocation.CODEC.listOf().optionalFieldOf("prerequisites", List.of()).forGetter(MatterResearchRecipe::prerequisites),
            Cost.CODEC.listOf().fieldOf("ingredients").forGetter(MatterResearchRecipe::ingredients),
            Codec.intRange(1, Integer.MAX_VALUE).optionalFieldOf("duration", 1200).forGetter(MatterResearchRecipe::duration),
            Codec.doubleRange(0, Double.MAX_VALUE).optionalFieldOf("ae_per_tick", 256.0).forGetter(MatterResearchRecipe::aePerTick),
            ResourceLocation.CODEC.listOf().optionalFieldOf("unlocks", List.of()).forGetter(MatterResearchRecipe::unlocks),
            Codec.STRING.listOf().optionalFieldOf("required_mods", List.of()).forGetter(MatterResearchRecipe::requiredMods),
            Codec.INT.optionalFieldOf("sort_order", 0).forGetter(MatterResearchRecipe::sortOrder),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("stage", 1).forGetter(MatterResearchRecipe::stage),
            ResearchDepth.CODEC.listOf().optionalFieldOf("depths", ResearchDepth.DEFAULTS).forGetter(MatterResearchRecipe::depths),
            Codec.unboundedMap(ResourceLocation.CODEC, ResearchCodecs.PREREQUISITE_LEVEL)
                    .optionalFieldOf("prerequisite_levels", Map.of()).forGetter(MatterResearchRecipe::prerequisiteLevels)
    ).apply(instance, MatterResearchRecipe::new));

    public MatterResearchRecipe {
        prerequisiteLevels = Map.copyOf(prerequisiteLevels);
        if (prerequisiteLevels.values().stream().anyMatch(level -> level < 0)) throw new IllegalArgumentException("Negative prerequisite level");
        var parents = new LinkedHashSet<>(prerequisites);
        parents.addAll(prerequisiteLevels.keySet());
        prerequisites = List.copyOf(parents);
        ingredients = List.copyOf(ingredients);
        unlocks = List.copyOf(unlocks);
        requiredMods = List.copyOf(requiredMods);
        depths = List.copyOf(depths);
        if (title.isBlank() || duration < 1 || stage < 0 || depths.isEmpty() || !Double.isFinite(aePerTick) || aePerTick < 0) {
            throw new IllegalArgumentException("Research needs a title, positive duration and finite nonnegative power");
        }
    }

    public MatterResearchRecipe(String title, List<ResourceLocation> prerequisites, List<Cost> ingredients,
            int duration, double aePerTick, List<ResourceLocation> unlocks, List<String> requiredMods, int sortOrder, int stage) {
        this(title, prerequisites, ingredients, duration, aePerTick, unlocks, requiredMods, sortOrder, stage, ResearchDepth.DEFAULTS, Map.of());
    }

    public MatterResearchRecipe(String title, List<ResourceLocation> prerequisites, List<Cost> ingredients,
            int duration, double aePerTick, List<ResourceLocation> unlocks, List<String> requiredMods, int sortOrder, int stage,
            List<ResearchDepth> depths) {
        this(title, prerequisites, ingredients, duration, aePerTick, unlocks, requiredMods, sortOrder, stage, depths, Map.of());
    }

    /** Returns a replacement definition. Positive values are completion counts; zero means the parent's current maximum. */
    public MatterResearchRecipe withPrerequisiteLevels(Map<ResourceLocation, Integer> levels) {
        return new MatterResearchRecipe(title, prerequisites, ingredients, duration, aePerTick, unlocks, requiredMods,
                sortOrder, stage, depths, levels);
    }

    public List<Cost> costsFor(int round) { return depths.get(round - 1).costs(ingredients); }

    public boolean available() {
        return requiredMods.stream().allMatch(id -> ModList.get().isLoaded(id));
    }

    @Override public boolean matches(MatterFabricationRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(MatterFabricationRecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.MATTER_RESEARCH_RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.MATTER_RESEARCH_RECIPE_TYPE.get(); }

    public record Cost(Ingredient ingredient, long count) {
        public static final Codec<Cost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Cost::ingredient),
                ResearchCodecs.POSITIVE_LONG.fieldOf("count").forGetter(Cost::count)
        ).apply(instance, Cost::new));
        public Cost {
            if (ingredient.isEmpty() || count <= 0) throw new IllegalArgumentException("Empty research cost");
        }
    }

    public static final class Serializer implements RecipeSerializer<MatterResearchRecipe> {
        @Override public MapCodec<MatterResearchRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MatterResearchRecipe> streamCodec() {
            return ByteBufCodecs.fromCodecWithRegistries(CODEC.codec());
        }
    }
}
