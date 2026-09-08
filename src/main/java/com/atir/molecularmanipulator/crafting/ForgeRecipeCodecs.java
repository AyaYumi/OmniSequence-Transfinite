package com.atir.molecularmanipulator.crafting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import java.util.function.Function;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

/** Preserves the mod's data-pack format while using Forge 1.20.1 items and NBT. */
public final class ForgeRecipeCodecs {
    public static final Codec<Ingredient> INGREDIENT = json(
            value -> Ingredient.fromJson(value, false), Ingredient::toJson);
    public static final Codec<ItemStack> ITEM_STACK = json(value -> {
        var object = value.getAsJsonObject();
        if (object.has("components")) throw new IllegalArgumentException("Forge 1.20.1 recipes use nbt, not components");
        var id = new ResourceLocation(object.get(object.has("id") ? "id" : "item").getAsString());
        if (!BuiltInRegistries.ITEM.containsKey(id)) throw new IllegalArgumentException("Unknown item " + id);
        int count = object.has("count") ? object.get("count").getAsBigDecimal().intValueExact() : 1;
        if (count < 1) throw new IllegalArgumentException("Non-positive item count");
        var stack = new ItemStack(BuiltInRegistries.ITEM.get(id), count);
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty item result");
        if (object.has("nbt")) stack.setTag(parseTag(object.get("nbt")));
        return stack;
    }, stack -> {
        var json = new JsonObject();
        json.addProperty("id", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (stack.hasTag()) json.addProperty("nbt", stack.getTag().toString());
        return json;
    });
    public static final Codec<FluidStack> FLUID_STACK = json(value -> {
        var object = value.getAsJsonObject();
        if (object.has("components")) throw new IllegalArgumentException("Forge 1.20.1 fluids use nbt, not components");
        if (object.size() == 0) return FluidStack.EMPTY;
        var id = new ResourceLocation(object.get(object.has("id") ? "id" : "fluid").getAsString());
        if (!BuiltInRegistries.FLUID.containsKey(id)) throw new IllegalArgumentException("Unknown fluid " + id);
        var fluid = BuiltInRegistries.FLUID.get(id);
        int amount = object.has("amount") ? object.get("amount").getAsBigDecimal().intValueExact() : 1000;
        if (fluid == Fluids.EMPTY || amount <= 0) throw new IllegalArgumentException("Empty fluid result");
        var stack = new FluidStack(fluid, amount);
        if (object.has("nbt")) stack.setTag(parseTag(object.get("nbt")));
        return stack;
    }, stack -> {
        var json = new JsonObject();
        if (!stack.isEmpty()) {
            json.addProperty("id", BuiltInRegistries.FLUID.getKey(stack.getFluid()).toString());
            json.addProperty("amount", stack.getAmount());
            if (stack.hasTag()) json.addProperty("nbt", stack.getTag().toString());
        }
        return json;
    });

    private ForgeRecipeCodecs() {}

    private static CompoundTag parseTag(JsonElement element) {
        try {
            return TagParser.parseTag(element.isJsonPrimitive() ? element.getAsString() : element.toString());
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new IllegalArgumentException("Invalid recipe NBT", error);
        }
    }

    private static <T> Codec<T> json(Function<JsonElement, T> reader, Function<T, JsonElement> writer) {
        return Codec.PASSTHROUGH.comapFlatMap(value -> {
            try {
                return DataResult.success(reader.apply(value.convert(JsonOps.INSTANCE).getValue()));
            } catch (RuntimeException error) {
                return DataResult.error(() -> "Invalid recipe value: " + error.getMessage());
            }
        }, value -> new Dynamic<>(JsonOps.INSTANCE, writer.apply(value)));
    }
}
