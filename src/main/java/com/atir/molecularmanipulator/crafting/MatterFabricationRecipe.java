package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.GenericStack;
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
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public record MatterFabricationRecipe(
        List<CountedIngredient> ingredients,
        List<ItemStack> results,
        FluidStack fluidInput,
        FluidStack fluidResult,
        List<GenericStack> aeInputs,
        int processingTime,
        double aePerTick,
        boolean requiresResearch) implements Recipe<MatterFabricationRecipeInput> {
    public static final int MAX_INPUTS = 9;
    public static final int MAX_OUTPUTS = 2;

    public MatterFabricationRecipe(List<CountedIngredient> ingredients, List<ItemStack> results,
            FluidStack fluidInput, FluidStack fluidResult, int processingTime, double aePerTick) {
        this(ingredients, results, fluidInput, fluidResult, processingTime, aePerTick, false);
    }

    public MatterFabricationRecipe(List<CountedIngredient> ingredients, List<ItemStack> results,
            FluidStack fluidInput, FluidStack fluidResult, int processingTime, double aePerTick, boolean requiresResearch) {
        this(ingredients, results, fluidInput, fluidResult, List.of(), processingTime, aePerTick, requiresResearch);
    }

    public MatterFabricationRecipe {
        ingredients = List.copyOf(ingredients);
        results = results.stream().map(ItemStack::copy).toList();
        fluidInput = fluidInput.copy();
        fluidResult = fluidResult.copy();
        aeInputs = List.copyOf(aeInputs);
        if (ingredients.size() + aeInputs.size() > MAX_INPUTS || ingredients.isEmpty() && fluidInput.isEmpty() && aeInputs.isEmpty()) {
            throw new IllegalArgumentException("Matter fabrication recipes require between 1 and 9 item/AE inputs or a fluid input");
        }
        if (aeInputs.stream().anyMatch(stack -> stack.amount() <= 0)) {
            throw new IllegalArgumentException("AE input amounts must be positive");
        }
        if (results.size() > MAX_OUTPUTS || results.isEmpty() && fluidResult.isEmpty()) {
            throw new IllegalArgumentException("Matter fabrication recipes require at least one item or fluid result");
        }
        processingTime = Math.max(1, processingTime);
        aePerTick = Math.max(0.0, aePerTick);
    }

    @Override
    public boolean matches(MatterFabricationRecipeInput input, Level level) {
        return consumptionPlan(input) != null;
    }

    public int[] consumptionPlan(MatterFabricationRecipeInput input) {
        return consumptionPlan(input, 1);
    }

    public int[] consumptionPlan(MatterFabricationRecipeInput input, long crafts) {
        // Manual ports have no generic AE storage; these recipes must be supplied by an assembly.
        if (!aeInputs.isEmpty() || crafts < 1 || !fluidMatches(input.fluid(), crafts)) {
            return null;
        }
        int[] available = new int[input.size()];
        int[] consumed = new int[input.size()];
        for (int slot = 0; slot < input.size(); slot++) {
            available[slot] = input.getItem(slot).getCount();
        }

        for (var counted : ingredients) {
            if (crafts > Long.MAX_VALUE / counted.count()) return null;
            long remaining = counted.count() * crafts;
            for (int slot = 0; slot < input.size() && remaining > 0; slot++) {
                var stack = input.getItem(slot);
                if (available[slot] <= 0 || !counted.ingredient().test(stack)) {
                    continue;
                }
                int used = (int) Math.min(remaining, available[slot]);
                available[slot] -= used;
                consumed[slot] += used;
                remaining -= used;
            }
            if (remaining > 0) {
                return null;
            }
        }

        for (int slot = 0; slot < input.size(); slot++) {
            var stack = input.getItem(slot);
            if (!stack.isEmpty() && ingredients.stream().noneMatch(counted -> counted.ingredient().test(stack))) {
                return null;
            }
        }
        return consumed;
    }

    public boolean fluidMatches(FluidStack available) {
        return fluidMatches(available, 1);
    }

    private boolean fluidMatches(FluidStack available, long crafts) {
        if (fluidInput.isEmpty()) {
            return available.isEmpty();
        }
        return !available.isEmpty()
                && FluidStack.isSameFluidSameComponents(fluidInput, available)
                && crafts <= Integer.MAX_VALUE / (long) fluidInput.getAmount()
                && available.getAmount() >= fluidInput.getAmount() * crafts;
    }

    public List<ItemStack> resultCopies() {
        return results.stream().map(ItemStack::copy).toList();
    }

    @Override
    public ItemStack assemble(MatterFabricationRecipeInput input, HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.getFirst().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.getFirst().copy();
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
                                .optionalFieldOf("ingredients", List.of()).forGetter(MatterFabricationRecipe::ingredients),
                        ItemStack.STRICT_CODEC.listOf(0, MAX_OUTPUTS)
                                .optionalFieldOf("results", List.of()).forGetter(MatterFabricationRecipe::results),
                        FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid_input", FluidStack.EMPTY)
                                .forGetter(MatterFabricationRecipe::fluidInput),
                        FluidStack.OPTIONAL_CODEC.optionalFieldOf("fluid_result", FluidStack.EMPTY)
                                .forGetter(MatterFabricationRecipe::fluidResult),
                        GenericStack.CODEC.listOf(0, MAX_INPUTS).optionalFieldOf("ae_inputs", List.of())
                                .forGetter(MatterFabricationRecipe::aeInputs),
                        Codec.INT.optionalFieldOf("processing_time", 200)
                                .forGetter(MatterFabricationRecipe::processingTime),
                        Codec.DOUBLE.optionalFieldOf("ae_per_tick", 64.0)
                                .forGetter(MatterFabricationRecipe::aePerTick),
                        Codec.BOOL.optionalFieldOf("requires_research", false)
                                .forGetter(MatterFabricationRecipe::requiresResearch))
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
                        var fluidInput = FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                        var fluidResult = FluidStack.OPTIONAL_STREAM_CODEC.decode(buffer);
                        int aeInputCount = buffer.readVarInt();
                        if (aeInputCount < 0 || aeInputCount > MAX_INPUTS) throw new IllegalArgumentException("Invalid AE input count");
                        var aeInputs = new ArrayList<GenericStack>(aeInputCount);
                        for (int index = 0; index < aeInputCount; index++) aeInputs.add(GenericStack.STREAM_CODEC.decode(buffer));
                        return new MatterFabricationRecipe(ingredients, results, fluidInput, fluidResult, aeInputs,
                                buffer.readVarInt(), buffer.readDouble(), buffer.readBoolean());
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
                        FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.fluidInput);
                        FluidStack.OPTIONAL_STREAM_CODEC.encode(buffer, recipe.fluidResult);
                        buffer.writeVarInt(recipe.aeInputs.size());
                        for (var input : recipe.aeInputs) GenericStack.STREAM_CODEC.encode(buffer, input);
                        buffer.writeVarInt(recipe.processingTime);
                        buffer.writeDouble(recipe.aePerTick);
                        buffer.writeBoolean(recipe.requiresResearch);
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
