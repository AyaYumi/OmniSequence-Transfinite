package com.atir.molecularmanipulator.crafting;

import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

public record MatterFabricationRecipe(
        List<CountedIngredient> ingredients,
        List<ItemStack> results,
        int processingTime,
        double aePerTick) implements Recipe<MatterFabricationRecipeInput> {
    public static final int MAX_INPUTS = 4;
    public static final int MAX_OUTPUTS = 2;

    public MatterFabricationRecipe {
        ingredients = List.copyOf(ingredients);
        results = results.stream().map(ItemStack::copy).toList();
        if (ingredients.isEmpty() || ingredients.size() > MAX_INPUTS) {
            throw new IllegalArgumentException("Matter fabrication recipes require 1-4 ingredients");
        }
        if (results.isEmpty() || results.size() > MAX_OUTPUTS) {
            throw new IllegalArgumentException("Matter fabrication recipes require 1-2 results");
        }
        processingTime = Math.max(1, processingTime);
        aePerTick = Math.max(0.0, aePerTick);
    }

    @Override
    public boolean matches(MatterFabricationRecipeInput input, Level level) {
        return consumptionPlan(input) != null;
    }

    public int[] consumptionPlan(MatterFabricationRecipeInput input) {
        int[] available = new int[input.size()];
        int[] consumed = new int[input.size()];
        for (int slot = 0; slot < input.size(); slot++) {
            available[slot] = input.getItem(slot).getCount();
        }

        for (var counted : ingredients) {
            int remaining = counted.count();
            for (int slot = 0; slot < input.size() && remaining > 0; slot++) {
                var stack = input.getItem(slot);
                if (available[slot] <= 0 || !counted.ingredient().test(stack)) {
                    continue;
                }
                int used = Math.min(remaining, available[slot]);
                available[slot] -= used;
                consumed[slot] += used;
                remaining -= used;
            }
            if (remaining > 0) {
                return null;
            }
        }

        for (int slot = 0; slot < input.size(); slot++) {
            if (!input.getItem(slot).isEmpty() && consumed[slot] == 0) {
                return null;
            }
        }
        return consumed;
    }

    public List<ItemStack> resultCopies() {
        return results.stream().map(ItemStack::copy).toList();
    }

    @Override
    public ItemStack assemble(MatterFabricationRecipeInput input, HolderLookup.Provider registries) {
        return results.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.getFirst().copy();
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModContent.MATTER_FABRICATION_RECIPE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModContent.MATTER_FABRICATION_RECIPE_TYPE.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public record CountedIngredient(Ingredient ingredient, int count) {
        public static final MapCodec<CountedIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                Codec.INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count))
                .apply(instance, CountedIngredient::new));

        public CountedIngredient {
            if (count < 1 || count > 64) {
                throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
            }
        }
    }

    public static final class Serializer implements RecipeSerializer<MatterFabricationRecipe> {
        private static final MapCodec<MatterFabricationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        CountedIngredient.CODEC.codec().listOf(1, MAX_INPUTS)
                                .fieldOf("ingredients").forGetter(MatterFabricationRecipe::ingredients),
                        ItemStack.STRICT_CODEC.listOf(1, MAX_OUTPUTS)
                                .fieldOf("results").forGetter(MatterFabricationRecipe::results),
                        Codec.INT.optionalFieldOf("processing_time", 200)
                                .forGetter(MatterFabricationRecipe::processingTime),
                        Codec.DOUBLE.optionalFieldOf("ae_per_tick", 64.0)
                                .forGetter(MatterFabricationRecipe::aePerTick))
                        .apply(instance, MatterFabricationRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, MatterFabricationRecipe> STREAM_CODEC =
                new StreamCodec<>() {
                    @Override
                    public MatterFabricationRecipe decode(RegistryFriendlyByteBuf buffer) {
                        int ingredientCount = buffer.readVarInt();
                        var ingredients = new ArrayList<CountedIngredient>(ingredientCount);
                        for (int index = 0; index < ingredientCount; index++) {
                            var ingredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
                            ingredients.add(new CountedIngredient(ingredient, buffer.readVarInt()));
                        }
                        int resultCount = buffer.readVarInt();
                        var results = new ArrayList<ItemStack>(resultCount);
                        for (int index = 0; index < resultCount; index++) {
                            results.add(ItemStack.STREAM_CODEC.decode(buffer));
                        }
                        return new MatterFabricationRecipe(ingredients, results,
                                buffer.readVarInt(), buffer.readDouble());
                    }

                    @Override
                    public void encode(RegistryFriendlyByteBuf buffer, MatterFabricationRecipe recipe) {
                        buffer.writeVarInt(recipe.ingredients.size());
                        for (var counted : recipe.ingredients) {
                            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, counted.ingredient);
                            buffer.writeVarInt(counted.count);
                        }
                        buffer.writeVarInt(recipe.results.size());
                        for (var result : recipe.results) {
                            ItemStack.STREAM_CODEC.encode(buffer, result);
                        }
                        buffer.writeVarInt(recipe.processingTime);
                        buffer.writeDouble(recipe.aePerTick);
                    }
                };

        @Override
        public MapCodec<MatterFabricationRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, MatterFabricationRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
