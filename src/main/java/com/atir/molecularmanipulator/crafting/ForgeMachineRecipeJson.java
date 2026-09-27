package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.mixin.RecipeManagerAccessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;
import com.mojang.serialization.JsonOps;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.WeakHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

/** Forge serializers have no common JSON writer; retain reload JSON and encode client recipe fields. */
public final class ForgeMachineRecipeJson {
    private static final Map<RecipeManager, Snapshot> SOURCES = new WeakHashMap<>();
    private static final Gson GSON = new GsonBuilder()
            .registerTypeHierarchyAdapter(Ingredient.class,
                    (JsonSerializer<Ingredient>) (value, type, context) -> value.toJson())
            .registerTypeAdapter(ItemStack.class, (JsonSerializer<ItemStack>) (value, type, context) ->
                    ForgeRecipeCodecs.ITEM_STACK.encodeStart(JsonOps.INSTANCE, value).getOrThrow(false, message -> {}))
            .registerTypeAdapter(FluidStack.class, (JsonSerializer<FluidStack>) (value, type, context) ->
                    ForgeRecipeCodecs.FLUID_STACK.encodeStart(JsonOps.INSTANCE, value).getOrThrow(false, message -> {}))
            .registerTypeAdapter(GenericStack.class, (JsonSerializer<GenericStack>) (value, type, context) ->
                    ForgeRecipeCodecs.GENERIC_STACK.encodeStart(JsonOps.INSTANCE, value).getOrThrow(false, message -> {}))
            .registerTypeAdapter(ResourceLocation.class, (JsonSerializer<ResourceLocation>) (value, type, context) ->
                    new com.google.gson.JsonPrimitive(value.toString()))
            .create();

    private ForgeMachineRecipeJson() {}

    public static void capture(RecipeManager manager, Map<ResourceLocation, JsonElement> json) {
        var copied = new LinkedHashMap<ResourceLocation, JsonElement>();
        json.forEach((id, value) -> copied.put(id, value.deepCopy()));
        synchronized (SOURCES) {
            SOURCES.put(manager, new Snapshot(
                    ((RecipeManagerAccessor) manager).molecularmanipulator$getRecipesByName(), Map.copyOf(copied), false));
        }
    }

    public static void receive(RecipeManager manager, Map<ResourceLocation, JsonElement> json) {
        synchronized (SOURCES) {
            SOURCES.put(manager, new Snapshot(Map.of(), Map.copyOf(json), true));
        }
        MatterRecipeIndex.invalidate(manager);
    }

    public static JsonElement encode(Level level, Recipe<?> recipe) {
        synchronized (SOURCES) {
            var snapshot = SOURCES.get(level.getRecipeManager());
            if (snapshot != null && (snapshot.client || snapshot.recipes.get(recipe.getId()) == recipe)) {
                var json = snapshot.json.get(recipe.getId());
                if (json != null) return json.deepCopy();
            }
        }
        try {
            return GSON.toJsonTree(recipe);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private record Snapshot(Map<ResourceLocation, Recipe<?>> recipes, Map<ResourceLocation, JsonElement> json,
            boolean client) {}
}
