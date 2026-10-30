package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.GenericStack;
import com.atir.molecularmanipulator.mixin.RecipeManagerAccessor;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
        try {
            // Read live machine fields first: conditional wrappers and script replacements
            // need not have the same shape as the serializer's original reload JSON.
            var supported = encodeSupportedMachine(recipe);
            if (supported != null) return supported;
            synchronized (SOURCES) {
                var snapshot = SOURCES.get(level.getRecipeManager());
                if (snapshot != null && (snapshot.client || snapshot.recipes.get(recipe.getId()) == recipe)) {
                    var json = snapshot.json.get(recipe.getId());
                    if (json != null) return json.deepCopy();
                }
            }
            return encodeFields(recipe);
        } catch (RuntimeException | StackOverflowError exception) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn(
                    "Cannot encode machine recipe JSON {} ({}): {}", recipe.getId(), recipe.getClass().getName(), exception.toString());
            return null;
        }
    }

    static JsonElement encodeFields(Object recipe) {
        var supported = encodeSupportedMachine(recipe);
        return supported == null ? GSON.toJsonTree(recipe) : supported;
    }

    private static JsonObject encodeSupportedMachine(Object recipe) {
        var root = new com.google.gson.JsonObject();
        if (recipe instanceof appeng.recipes.handlers.InscriberRecipe inscriber) {
            var inputs = new com.google.gson.JsonObject();
            inputs.add("middle", inscriber.getMiddleInput().toJson());
            if (!inscriber.getTopOptional().isEmpty()) inputs.add("top", inscriber.getTopOptional().toJson());
            if (!inscriber.getBottomOptional().isEmpty()) inputs.add("bottom", inscriber.getBottomOptional().toJson());
            root.add("ingredients", inputs);
            root.addProperty("mode", inscriber.getProcessType().name().toLowerCase(java.util.Locale.ROOT));
            root.add("result", GSON.toJsonTree(inscriber.getResultItem()));
        } else if (recipe instanceof appeng.recipes.handlers.ChargerRecipe charger) {
            root.add("ingredient", charger.getIngredient().toJson());
            root.add("result", GSON.toJsonTree(charger.getResultItem()));
        } else if (recipe instanceof com.glodblock.github.extendedae.recipe.CircuitCutterRecipe cutter) {
            root.add("item_input", cutter.getInput().toJson());
            root.add("fluid_input", cutter.getFluid().toJson());
            root.add("output", GSON.toJsonTree(cutter.output));
        } else if (recipe.getClass().getName().equals("net.pedroksl.advanced_ae.recipes.ReactionChamberRecipe")) {
            // AdvancedAE is optional. These methods belong to its installed Forge 1.3.6 API.
            try {
                var inputs = new com.google.gson.JsonArray();
                for (var input : (java.util.List<?>) recipe.getClass().getMethod("getInputs").invoke(recipe)) {
                    inputs.add((JsonElement) input.getClass().getMethod("toJson").invoke(input));
                }
                root.add("input_items", inputs);
                var fluid = recipe.getClass().getMethod("getFluid").invoke(recipe);
                root.add("fluid", (JsonElement) fluid.getClass().getMethod("toJson").invoke(fluid));
                root.add("output", GSON.toJsonTree(recipe.getClass().getField("output").get(recipe)));
            } catch (ReflectiveOperationException failure) {
                throw new IllegalArgumentException("Cannot read AdvancedAE reaction recipe", failure);
            }
        } else return null;
        return root;
    }

    private record Snapshot(Map<ResourceLocation, Recipe<?>> recipes, Map<ResourceLocation, JsonElement> json,
            boolean client) {}
}
