package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.registry.ModContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;

public record MatterFabricationRecipe(net.minecraft.resources.ResourceLocation id,
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

    public MatterFabricationRecipe(List<CountedIngredient> ingredients, List<ItemStack> results, FluidStack fluidInput, FluidStack fluidResult, int processingTime, double aePerTick, boolean requiresResearch) {
        this(ingredients, results, fluidInput, fluidResult, List.of(), processingTime, aePerTick, requiresResearch);
    }

    public MatterFabricationRecipe(List<CountedIngredient> ingredients, List<ItemStack> results, FluidStack fluidInput,
            FluidStack fluidResult, List<GenericStack> aeInputs, int processingTime, double aePerTick, boolean requiresResearch) {
        this(com.atir.molecularmanipulator.MolecularManipulator.id("unregistered"), ingredients, results, fluidInput, fluidResult, aeInputs, processingTime, aePerTick, requiresResearch);
    }

    public MatterFabricationRecipe(net.minecraft.resources.ResourceLocation id, List<CountedIngredient> ingredients,
            List<ItemStack> results, FluidStack fluidInput, FluidStack fluidResult, int processingTime, double aePerTick, boolean requiresResearch) {
        this(id, ingredients, results, fluidInput, fluidResult, List.of(), processingTime, aePerTick, requiresResearch);
    }

    @Override public net.minecraft.resources.ResourceLocation getId() { return id; }
    public MatterFabricationRecipe value() { return this; }
    public MatterFabricationRecipe withId(net.minecraft.resources.ResourceLocation recipeId) {
        return new MatterFabricationRecipe(recipeId, ingredients, results, fluidInput, fluidResult, aeInputs, processingTime, aePerTick, requiresResearch);
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
                && fluidInput.isFluidEqual(available)
                && crafts <= Integer.MAX_VALUE / (long) fluidInput.getAmount()
                && available.getAmount() >= fluidInput.getAmount() * crafts;
    }

    public List<ItemStack> resultCopies() {
        return results.stream().map(ItemStack::copy).toList();
    }

    @Override
    public ItemStack assemble(MatterFabricationRecipeInput input, net.minecraft.core.RegistryAccess registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.RegistryAccess registries) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).copy();
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
                com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs.INGREDIENT.fieldOf("ingredient").forGetter(CountedIngredient::ingredient),
                Codec.INT.optionalFieldOf("count", 1).forGetter(CountedIngredient::count))
                .apply(instance, CountedIngredient::new));

        public CountedIngredient {
            if (count < 1 || count > 64) {
                throw new IllegalArgumentException("Ingredient count must be between 1 and 64");
            }
        }
    }

    public static final class Serializer implements RecipeSerializer<MatterFabricationRecipe> {
        public static final MapCodec<MatterFabricationRecipe> CODEC = RecordCodecBuilder.mapCodec(instance ->
                instance.group(
                        CountedIngredient.CODEC.codec().listOf()
                                .optionalFieldOf("ingredients", List.of()).forGetter(MatterFabricationRecipe::ingredients),
                        com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs.ITEM_STACK.listOf()
                                .optionalFieldOf("results", List.of()).forGetter(MatterFabricationRecipe::results),
                        com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs.FLUID_STACK.optionalFieldOf("fluid_input", FluidStack.EMPTY)
                                .forGetter(MatterFabricationRecipe::fluidInput),
                        com.atir.molecularmanipulator.crafting.ForgeRecipeCodecs.FLUID_STACK.optionalFieldOf("fluid_result", FluidStack.EMPTY)
                                .forGetter(MatterFabricationRecipe::fluidResult),
                        ForgeRecipeCodecs.GENERIC_INPUTS
                                .forGetter(MatterFabricationRecipe::aeInputs),
                        Codec.INT.optionalFieldOf("processing_time", 200)
                                .forGetter(MatterFabricationRecipe::processingTime),
                        Codec.DOUBLE.optionalFieldOf("ae_per_tick", 64.0)
                                .forGetter(MatterFabricationRecipe::aePerTick),
                        Codec.BOOL.optionalFieldOf("requires_research", false)
                                .forGetter(MatterFabricationRecipe::requiresResearch))
                        .apply(instance, MatterFabricationRecipe::new));

        @Override public MatterFabricationRecipe fromJson(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonObject json) {
            return CODEC.codec().parse(com.mojang.serialization.JsonOps.INSTANCE, json)
                    .getOrThrow(false, message -> {}).withId(id);
        }
        @Override public MatterFabricationRecipe fromNetwork(net.minecraft.resources.ResourceLocation id, FriendlyByteBuf buffer) {
            return fromJson(id, com.google.gson.JsonParser.parseString(buffer.readUtf(1_048_576)).getAsJsonObject());
        }
        @Override public void toNetwork(FriendlyByteBuf buffer, MatterFabricationRecipe recipe) {
            var json = CODEC.codec().encodeStart(com.mojang.serialization.JsonOps.INSTANCE, recipe)
                    .getOrThrow(false, message -> {});
            buffer.writeUtf(json.toString(), 1_048_576);
        }
    }
}
