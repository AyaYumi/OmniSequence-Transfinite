package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.mixin.RecipeManagerAccessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
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
            // Mixin-added registry/context fields are not recipe data. Never walk
            // their object graphs when a script replaces the original JSON snapshot.
            .addSerializationExclusionStrategy(new ExclusionStrategy() {
                public boolean shouldSkipField(FieldAttributes field) {
                    return !isDataType(field.getDeclaredClass());
                }
                public boolean shouldSkipClass(Class<?> type) { return false; }
            })
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

    private static boolean isDataType(Class<?> type) {
        if (type.isPrimitive() || type.isEnum() || type == String.class || Number.class.isAssignableFrom(type)
                || type == Boolean.class || JsonElement.class.isAssignableFrom(type)
                || Ingredient.class.isAssignableFrom(type) || type == ItemStack.class
                || type == FluidStack.class || type == GenericStack.class || type == ResourceLocation.class)
            return true;
        if (type.isArray()) return isDataType(type.getComponentType());
        if (java.util.Collection.class.isAssignableFrom(type) || Map.class.isAssignableFrom(type)) return true;
        var name = type.getPackageName();
        return name.contains(".recipe.") || name.endsWith(".recipe")
                || name.contains(".recipes.") || name.endsWith(".recipes");
    }

    /** KubeJS cancels RecipeManager.apply; capture its final JSON after post instead. */
    public static void captureKubeJs(RecipeManager manager, Object event) {
        try {
            var json = new LinkedHashMap<ResourceLocation, JsonElement>();
            var type = event.getClass();
            var original = (Map<?, ?>) type.getField("originalRecipes").get(event);
            var added = (java.util.Collection<?>) type.getField("addedRecipes").get(event);
            for (Object recipe : original.values()) addScriptJson(json, recipe);
            for (Object recipe : added) addScriptJson(json, recipe);
            capture(manager, json);
        } catch (ReflectiveOperationException | RuntimeException failure) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                    "Cannot read final KubeJS machine recipe JSON: {}", failure.toString());
        }
    }

    private static void addScriptJson(Map<ResourceLocation, JsonElement> json, Object recipe)
            throws ReflectiveOperationException {
        var type = recipe.getClass();
        if (Boolean.TRUE.equals(type.getField("removed").get(recipe))) return;
        var id = (ResourceLocation) type.getField("id").get(recipe);
        var value = (JsonElement) type.getField("json").get(recipe);
        if (id != null && value != null) json.put(id, value);
    }

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
            return encodeFields(recipe);
        } catch (RuntimeException | StackOverflowError exception) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                    "Cannot encode machine recipe JSON {} ({}): {}", recipe.getId(), recipe.getClass().getName(), exception.toString());
            return null;
        }
    }

    static JsonElement encodeFields(Object recipe) { return GSON.toJsonTree(recipe); }

    private record Snapshot(Map<ResourceLocation, Recipe<?>> recipes, Map<ResourceLocation, JsonElement> json,
            boolean client) {}
}
