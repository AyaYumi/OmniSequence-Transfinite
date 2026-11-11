package com.atir.molecularmanipulator.starcomputation;

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

class StarResearchNodeRecipeTest {
    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void ordinaryDeclarationAndRegistryAwareStreamRoundTripExactLongCosts() {
        var recipe = parse(JsonParser.parseString("""
                {"type":"molecularmanipulator:star_research", "title":"星海拓扑", "required_level":9,
                 "information_cost":9223372036854775807,
                 "prerequisites":["molecularmanipulator:foundation", "kubejs:deep_space"]}
                """));
        assertEquals("星海拓扑", recipe.title());
        assertEquals(9, recipe.requiredLevel());
        assertEquals(Long.MAX_VALUE, recipe.informationCost());
        assertEquals(List.of(ResourceLocation.parse("molecularmanipulator:foundation"),
                ResourceLocation.parse("kubejs:deep_space")), recipe.prerequisites());
        assertNull(recipe.error());
        assertEquals(recipe, parse(StarResearchNodeRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow()));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var serializer = new StarResearchNodeRecipe.Serializer();
            assertSame(StarResearchNodeRecipe.CODEC, serializer.codec());
            serializer.streamCodec().encode(buffer, recipe);
            assertEquals(recipe, serializer.streamCodec().decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertFalse(recipe.matches(null, null));
        assertFalse(recipe.canCraftInDimensions(100, 100));
        assertTrue(recipe.isSpecial());
    }

    @Test
    void emptyPrerequisitesAndZeroCostAreSupported() {
        for (int level = 1; level <= 9; level++) {
            var recipe = parse(JsonParser.parseString("{\"title\":\"Root\",\"required_level\":" + level + ",\"information_cost\":0}"));
            assertEquals(level, recipe.requiredLevel());
            assertEquals(0, recipe.informationCost());
            assertEquals(List.of(), recipe.prerequisites());
            assertNull(recipe.error());
        }
    }

    @Test
    void fractionalNegativeOverflowAndMalformedIdsReturnCodecErrors() {
        for (String level : List.of("0", "-1", "10", "4.5", "9223372036854775808", "1e30")) {
            invalid("{\"title\":\"Node\",\"required_level\":" + level + ",\"information_cost\":1}");
        }
        for (String cost : List.of("-1", "0.5", "9223372036854775808", "-9223372036854775809", "1e30")) {
            invalid("{\"title\":\"Node\",\"required_level\":1,\"information_cost\":" + cost + "}");
        }
        invalid("{\"title\":\"Node\",\"required_level\":1,\"information_cost\":0,\"prerequisites\":[\"BAD ID\"]}");
        invalid("{\"required_level\":1,\"information_cost\":0}");
        assertNotNull(new StarResearchNodeRecipe("Node", 10, 0, List.of()).error());
        assertNotNull(new StarResearchNodeRecipe("Node", 1, -1, List.of()).error());
    }

    @Test
    void prerequisiteListsAreImmutableAndAreNotLimitedTo32Ids() {
        var ids = new ArrayList<ResourceLocation>();
        for (int index = 0; index < 96; index++) ids.add(ResourceLocation.parse("pack:prerequisite_" + index));
        var recipe = new StarResearchNodeRecipe("Many prerequisites", 1, 0, ids);
        ids.clear();
        assertEquals(96, recipe.prerequisites().size());
        assertThrows(UnsupportedOperationException.class, () -> recipe.prerequisites().clear());
        assertEquals(recipe, parse(StarResearchNodeRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow()));
    }

    private static StarResearchNodeRecipe parse(JsonElement json) {
        return StarResearchNodeRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static void invalid(String json) {
        var result = assertDoesNotThrow(() -> StarResearchNodeRecipe.CODEC.codec().parse(JsonOps.INSTANCE, JsonParser.parseString(json)));
        assertTrue(result.error().isPresent(), json);
    }
}
