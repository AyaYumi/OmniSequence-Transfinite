package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.GenericStack;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import java.util.List;
import java.util.stream.Stream;
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
    /** AE2 15 uses native key NBT (#c), while recipe JSON keeps the 2.0.1 #t/# fields. */
    public static final Codec<GenericStack> GENERIC_STACK = json(value -> {
        var object = value.getAsJsonObject();
        var amountValue = object.getAsJsonPrimitive("#");
        if (amountValue == null || !amountValue.isNumber()) throw new IllegalArgumentException("AE input amount must be an integer");
        long amount = amountValue.getAsBigDecimal().longValueExact();
        if (amount <= 0) throw new IllegalArgumentException("AE input amount must be positive");
        var keyData = object.deepCopy();
        keyData.remove("#"); keyData.remove("#t");
        var tag = object.has("key_nbt") ? parseTag(object.get("key_nbt")) : parseTag(keyData);
        tag.putString("#c", object.get("#t").getAsString());
        tag.remove("#t");
        tag.putLong("#", amount);
        var stack = GenericStack.readTag(tag);
        if (stack == null) throw new IllegalArgumentException("Unknown or invalid AE input key");
        return stack;
    }, stack -> {
        var json = new JsonObject();
        json.addProperty("#t", stack.what().getType().getId().toString());
        json.addProperty("#", stack.amount());
        // SNBT preserves numeric tag widths and array types for arbitrary addon keys.
        json.addProperty("key_nbt", stack.what().toTag().toString());
        return json;
    });

    /** DFU's 1.20 optionalFieldOf swallows invalid values; never turn malformed resource costs into free inputs. */
    public static final MapCodec<List<GenericStack>> GENERIC_INPUTS = new MapCodec<>() {
        private final Codec<List<GenericStack>> codec = GENERIC_STACK.listOf();
        @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString("ae_inputs")); }
        @Override public <T> DataResult<List<GenericStack>> decode(DynamicOps<T> ops, MapLike<T> input) {
            T value = input.get("ae_inputs");
            return value == null ? DataResult.success(List.of()) : codec.parse(ops, value);
        }
        @Override public <T> RecordBuilder<T> encode(List<GenericStack> input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            if (!input.isEmpty()) prefix.add("ae_inputs", codec.encodeStart(ops, input));
            return prefix;
        }
    };

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
