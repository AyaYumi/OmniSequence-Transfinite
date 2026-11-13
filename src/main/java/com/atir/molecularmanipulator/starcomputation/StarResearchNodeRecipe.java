package com.atir.molecularmanipulator.starcomputation;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.crafting.MatterFabricationRecipeInput;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** Ordinary molecularmanipulator:star_research declaration. Its recipe ID is the permanent node ID. */
public record StarResearchNodeRecipe(ResourceLocation id, String title, int requiredLevel, long informationCost,
                                     List<ResourceLocation> prerequisites)
        implements Recipe<MatterFabricationRecipeInput> {
    // Codec.LONG silently truncates fractions/overflow. Information balances must remain exact signed longs.
    private static final Codec<Long> EXACT_LONG = new Codec<>() {
        @Override public <T> DataResult<Pair<Long, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getNumberValue(input).flatMap(number -> {
                try {
                    return DataResult.success(Pair.of(new BigDecimal(number.toString()).longValueExact(), ops.empty()));
                } catch (ArithmeticException | NumberFormatException invalid) {
                    return DataResult.error(() -> "Expected an exact 64-bit integer: " + number);
                }
            });
        }
        @Override public <T> DataResult<T> encode(Long value, DynamicOps<T> ops, T prefix) {
            return ops.mergeToPrimitive(prefix, ops.createLong(value));
        }
    };
    private static final Codec<Integer> LEVEL = ExtraCodecs.validate(EXACT_LONG, value -> value >= 1 && value <= 9
            ? DataResult.success(value) : DataResult.error(() -> "required_level must be in 1..9"))
            .xmap(Long::intValue, Integer::longValue);
    private static final Codec<Long> COST = ExtraCodecs.validate(EXACT_LONG, value -> value >= 0
            ? DataResult.success(value) : DataResult.error(() -> "information_cost must be nonnegative"));

    public static final MapCodec<StarResearchNodeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("title").forGetter(StarResearchNodeRecipe::title),
            LEVEL.fieldOf("required_level").forGetter(StarResearchNodeRecipe::requiredLevel),
            COST.fieldOf("information_cost").forGetter(StarResearchNodeRecipe::informationCost),
            StarComputationRecipeCodecs.optionalField(ResourceLocation.CODEC.listOf(), "prerequisites", List.of())
                    .forGetter(StarResearchNodeRecipe::prerequisites)
    ).apply(instance, StarResearchNodeRecipe::new));
    public StarResearchNodeRecipe(String title, int requiredLevel, long informationCost,
            List<ResourceLocation> prerequisites) {
        this(MolecularManipulator.id("unregistered"), title, requiredLevel, informationCost, prerequisites);
    }

    @Override public ResourceLocation getId() { return id; }
    public StarResearchNodeRecipe value() { return this; }
    public StarResearchNodeRecipe withId(ResourceLocation recipeId) {
        return new StarResearchNodeRecipe(recipeId, title, requiredLevel, informationCost, prerequisites);
    }

    public StarResearchNodeRecipe {
        prerequisites = List.copyOf(prerequisites);
    }

    /** Defense for Java/KubeJS records not decoded through CODEC; graph validity belongs to StarResearchNodes. */
    public String error() {
        if (title == null) return "Missing node title";
        if (requiredLevel < 1 || requiredLevel > 9) return "required_level must be in 1..9";
        if (informationCost < 0) return "information_cost must be nonnegative";
        return null;
    }

    @Override public boolean matches(MatterFabricationRecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(MatterFabricationRecipeInput input, RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(RegistryAccess registries) { return ItemStack.EMPTY; }
    @Override public boolean isSpecial() { return true; }
    @Override public RecipeSerializer<?> getSerializer() { return ModContent.STAR_RESEARCH_RECIPE_SERIALIZER.get(); }
    @Override public RecipeType<?> getType() { return ModContent.STAR_RESEARCH_RECIPE_TYPE.get(); }

    public static final class Serializer implements RecipeSerializer<StarResearchNodeRecipe> {
        @Override public StarResearchNodeRecipe fromJson(ResourceLocation id, JsonObject json) {
            return CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> {}).withId(id);
        }
        @Override public StarResearchNodeRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            return fromJson(id, JsonParser.parseString(buffer.readUtf(1_048_576)).getAsJsonObject());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, StarResearchNodeRecipe recipe) {
            buffer.writeUtf(CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe)
                    .getOrThrow(false, message -> {}).toString(), 1_048_576);
        }
    }
}
