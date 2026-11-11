package com.atir.molecularmanipulator.recipelock;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RecipeLockRecipeTest {
    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void minimalOrdinaryDeclarationDefaultsToAllAndIsNeverAnExecutableRecipe() {
        var recipe = parse("""
                {"type":"molecularmanipulator:recipe_lock", "requires":["pack:research"],
                 "match":{"recipe_ids":["minecraft:iron_ingot"]}}
                """);
        assertEquals(List.of(id("pack:research")), recipe.requires());
        assertEquals("all", recipe.mode());
        assertEquals(List.of(id("minecraft:iron_ingot")), recipe.match().recipeIds());
        assertEquals(List.of(), recipe.match().recipeIdRegex());
        assertEquals(List.of(), recipe.match().recipeTypes());
        assertEquals(List.of(), recipe.match().namespaces());
        assertEquals(List.of(), recipe.match().excludeIds());
        assertNull(recipe.error());
        assertFalse(recipe.matches(null, null));
        assertFalse(recipe.canCraftInDimensions(3, 3));
        assertTrue(recipe.assemble(null, RegistryAccess.EMPTY).isEmpty());
        assertTrue(recipe.getResultItem(RegistryAccess.EMPTY).isEmpty());
        assertTrue(recipe.isSpecial());
    }

    @Test
    void everySelectorAndAnyModeSurviveJsonAndRegistryAwareStreamRoundTrips() {
        var recipe = parse("""
                {"requires":["pack:one","pack:two"], "mode":"any",
                 "match":{"recipe_ids":["pack:one","other:two"],
                          "recipe_id_regex":["pack:.*","other:two"],
                          "recipe_types":["minecraft:crafting","minecraft:smelting"],
                          "namespaces":["pack","other"],
                          "exclude_ids":["pack:excluded"]}}
                """);
        assertEquals("any", recipe.mode());
        assertEquals(List.of(id("pack:one"), id("pack:two")), recipe.requires());
        assertEquals(List.of("pack:.*", "other:two"), recipe.match().recipeIdRegex());
        assertEquals(List.of(id("minecraft:crafting"), id("minecraft:smelting")), recipe.match().recipeTypes());
        assertEquals(List.of("pack", "other"), recipe.match().namespaces());
        assertEquals(List.of(id("pack:excluded")), recipe.match().excludeIds());
        assertEquals(recipe, parse(RecipeLockRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow()));

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var serializer = new RecipeLockRecipe.Serializer();
            assertSame(RecipeLockRecipe.CODEC, serializer.codec());
            serializer.streamCodec().encode(buffer, recipe);
            assertEquals(recipe, serializer.streamCodec().decode(buffer));
            assertEquals(0, buffer.readableBytes(), "The declaration consumes exactly its own network payload");
        } finally {
            buffer.release();
        }
    }

    @Test
    void eachPositiveSelectorCanBeUsedAloneIncludingAnExplicitEmptyNegativeList() {
        for (String selector : List.of(
                "\"recipe_ids\":[\"pack:target\"]",
                "\"recipe_id_regex\":[\"pack:.*\"]",
                "\"recipe_types\":[\"minecraft:crafting\"]",
                "\"namespaces\":[\"pack\"]")) {
            var recipe = parse("{\"requires\":[\"pack:node\"],\"match\":{" + selector + ",\"exclude_ids\":[]}}");
            assertNull(recipe.error(), selector);
            assertEquals("all", recipe.mode());
        }
    }

    @Test
    void missingEmptyOrMistypedRequiredFieldsAreCodecErrorsRatherThanThrownExceptions() {
        for (String json : List.of(
                "{}",
                "{\"match\":{\"namespaces\":[\"pack\"]}}",
                "{\"requires\":[],\"match\":{\"namespaces\":[\"pack\"]}}",
                "{\"requires\":\"pack:node\",\"match\":{\"namespaces\":[\"pack\"]}}",
                "{\"requires\":[\"pack:node\"]}",
                "{\"requires\":[\"pack:node\"],\"match\":null}",
                "{\"requires\":[\"pack:node\"],\"match\":[]}",
                "{\"requires\":[\"pack:node\"],\"match\":{}}",
                "{\"requires\":[\"pack:node\"],\"match\":{\"recipe_ids\":[],\"recipe_id_regex\":[],\"recipe_types\":[],\"namespaces\":[]}}",
                "{\"requires\":[\"pack:node\"],\"match\":{\"exclude_ids\":[\"pack:target\"]}}")) {
            invalid(json);
        }
    }

    @Test
    void modeIsStrictAndMalformedResourceIdsInEveryIdFieldAreRejected() {
        for (String mode : List.of("ALL", "Any", "", "either", "all ")) {
            invalid("{\"requires\":[\"pack:node\"],\"mode\":\"" + mode + "\",\"match\":{\"namespaces\":[\"pack\"]}}");
        }
        invalid("{\"requires\":[\"BAD ID\"],\"match\":{\"namespaces\":[\"pack\"]}}");
        for (String field : List.of("recipe_ids", "recipe_types", "exclude_ids")) {
            invalid("{\"requires\":[\"pack:node\"],\"match\":{\"namespaces\":[\"pack\"],\"" + field + "\":[\"BAD ID\"]}}");
        }
    }

    @Test
    void invalidRegexAndNamespaceEntriesAreRejectedEvenAfterAValidAlternative() {
        for (String expression : List.of("[", "(", "*", "(?<unclosed")) {
            invalid("{\"requires\":[\"pack:node\"],\"match\":{\"recipe_id_regex\":[\"pack:.*\",\"" + expression + "\"]}}");
        }
        for (String namespace : List.of("", "UpperCase", "bad:namespace", "bad/path", "white space")) {
            invalid("{\"requires\":[\"pack:node\"],\"match\":{\"namespaces\":[\"pack\",\"" + namespace + "\"]}}");
        }
        for (String field : List.of("recipe_ids", "recipe_id_regex", "recipe_types", "namespaces", "exclude_ids")) {
            String otherPositive = field.equals("recipe_ids") ? "\"namespaces\":[\"pack\"]" : "\"recipe_ids\":[\"pack:target\"]";
            invalid("{\"requires\":[\"pack:node\"],\"match\":{" + otherPositive + ",\"" + field + "\":{}}}");
        }
    }

    @Test
    void constructorsDefensivelyCopyEveryListAndRequirementsAreNotLimitedToAMask() {
        var requirements = new ArrayList<ResourceLocation>();
        for (int index = 0; index < 96; index++) requirements.add(id("pack:research_" + index));
        var ids = new ArrayList<>(List.of(id("pack:target")));
        var regex = new ArrayList<>(List.of("pack:.*"));
        var types = new ArrayList<>(List.of(id("minecraft:crafting")));
        var namespaces = new ArrayList<>(List.of("pack"));
        var exclusions = new ArrayList<>(List.of(id("pack:excluded")));
        var selector = new RecipeLockRecipe.Selector(ids, regex, types, namespaces, exclusions);
        var recipe = new RecipeLockRecipe(requirements, "all", selector);
        requirements.clear();
        ids.clear();
        regex.clear();
        types.clear();
        namespaces.clear();
        exclusions.clear();

        assertEquals(96, recipe.requires().size());
        assertEquals(List.of(id("pack:target")), selector.recipeIds());
        assertEquals(List.of("pack:.*"), selector.recipeIdRegex());
        assertEquals(List.of(id("minecraft:crafting")), selector.recipeTypes());
        assertEquals(List.of("pack"), selector.namespaces());
        assertEquals(List.of(id("pack:excluded")), selector.excludeIds());
        assertThrows(UnsupportedOperationException.class, () -> recipe.requires().clear());
        assertThrows(UnsupportedOperationException.class, () -> selector.recipeIds().clear());
        assertThrows(UnsupportedOperationException.class, () -> selector.recipeIdRegex().clear());
        assertThrows(UnsupportedOperationException.class, () -> selector.recipeTypes().clear());
        assertThrows(UnsupportedOperationException.class, () -> selector.namespaces().clear());
        assertThrows(UnsupportedOperationException.class, () -> selector.excludeIds().clear());
        assertEquals(recipe, parse(RecipeLockRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow()));
    }

    @Test
    void programmaticInvalidDeclarationsAlsoFailEncodingValidation() {
        var selector = new RecipeLockRecipe.Selector(List.of(id("pack:target")), List.of(), List.of(), List.of(), List.of());
        for (var recipe : List.of(new RecipeLockRecipe(List.of(), "all", selector),
                new RecipeLockRecipe(List.of(id("pack:node")), "xor", selector))) {
            assertNotNull(recipe.error());
            assertTrue(RecipeLockRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).error().isPresent());
        }
        assertNotNull(new RecipeLockRecipe(List.of(id("pack:node")), "all", null).error());
    }

    private static RecipeLockRecipe parse(String json) { return parse(JsonParser.parseString(json)); }

    private static RecipeLockRecipe parse(JsonElement json) {
        return RecipeLockRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static void invalid(String json) {
        var result = assertDoesNotThrow(() -> RecipeLockRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)));
        assertTrue(result.error().isPresent(), json);
    }

    private static ResourceLocation id(String name) { return ResourceLocation.parse(name); }
}
