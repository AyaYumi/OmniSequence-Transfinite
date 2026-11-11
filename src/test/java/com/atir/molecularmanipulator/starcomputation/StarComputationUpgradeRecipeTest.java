package com.atir.molecularmanipulator.starcomputation;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarComputationUpgradeRecipeTest {
    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void ordinaryDeclarationRoundTripsNestedConditionsAndRegistryAwareStream() {
        var json = JsonParser.parseString("""
                {
                  "level": 7, "energy": 0, "ae_per_tick": 8192.5, "auto_build": true,
                  "conditions": {"type": "all", "title": "kubejs.orrery.level7", "children": [
                    {"type": "at_least", "required": 2, "children": [
                      {"type": "ftb_quests", "match": "any", "targets": [
                        {"id": "AB0123456789CDEF"}, {"id": "1", "title": "My quest"}]},
                      {"type": "machines", "formed": false, "targets": [
                        {"id": "minecraft:furnace", "count": 3}, {"id": "#c:machines"}]},
                      {"type": "nodes", "targets": [{"id": "quantum"}]}
                    ]},
                    {"type": "any", "children": [
                      {"type": "research", "same_machine": false, "match": "at_least", "required": 2,
                       "targets": [{"id": "kubejs:alpha", "count": 3}, {"id": "kubejs:beta", "title": "Beta"}]},
                      {"type": "research", "targets": [{"id": "kubejs:gamma"}]}
                    ]}
                  ]}
                }
                """);
        var recipe = parse(json);
        assertEquals(7, recipe.level());
        assertEquals(0, recipe.energy());
        assertEquals(8192.5, recipe.aePerTick());
        assertTrue(recipe.autoBuild());
        assertNull(recipe.error());
        var first = recipe.conditions().children().getFirst().children();
        assertEquals(1, first.getFirst().targets().getFirst().count());
        assertEquals("", first.getFirst().targets().getFirst().title());
        assertEquals("all", first.get(1).match());
        assertFalse(first.get(1).formed());
        var research = recipe.conditions().children().get(1).children();
        assertFalse(research.getFirst().sameMachine());
        assertTrue(research.get(1).sameMachine());
        assertTrue(research.get(1).formed());
        assertEquals(1, research.get(1).required());
        var encoded = StarComputationUpgradeRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, recipe).getOrThrow();
        assertEquals(recipe, parse(encoded));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var codec = new StarComputationUpgradeRecipe.Serializer().streamCodec();
            codec.encode(buffer, recipe);
            assertEquals(recipe, codec.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertTrue(recipe.isSpecial());
        assertFalse(recipe.canCraftInDimensions(100, 100));
    }

    @Test
    void automaticBuildingIsOptInForExistingDeclarationsAndConstructors() {
        var json = declaration("{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\"}]}");
        var legacy = parse(json);
        assertFalse(legacy.autoBuild());
        assertEquals(legacy, new StarComputationUpgradeRecipe(
                legacy.level(), legacy.energy(), legacy.aePerTick(), legacy.conditions()));
        assertEquals(legacy, parse(StarComputationUpgradeRecipe.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, legacy).getOrThrow()));
        json.addProperty("auto_build", false);
        assertEquals(legacy, parse(json));
        json.addProperty("auto_build", true);
        var automatic = parse(json);
        assertTrue(automatic.autoBuild());
        assertEquals(automatic, new StarComputationUpgradeRecipe(
                legacy.level(), legacy.energy(), legacy.aePerTick(), legacy.conditions(), true));
        assertEquals(automatic, parse(StarComputationUpgradeRecipe.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, automatic).getOrThrow()));
    }

    @Test
    void anchorTargetsAcceptOnlyTheThreeExactTiersAndPositiveIntegerCounts() {
        var recipe = parse(declaration("""
                {"type":"anchors","match":"all","targets":[
                  {"id":"reference","count":96},
                  {"id":"quantum","count":168},
                  {"id":"terminal","count":240}
                ]}
                """));
        assertNull(recipe.error());
        assertEquals(List.of("reference", "quantum", "terminal"),
                recipe.conditions().targets().stream().map(StarComputationUpgradeRecipe.Target::id).toList());
        assertEquals(List.of(96, 168, 240),
                recipe.conditions().targets().stream().map(StarComputationUpgradeRecipe.Target::count).toList());
        assertEquals(recipe, parse(StarComputationUpgradeRecipe.CODEC.codec()
                .encodeStart(JsonOps.INSTANCE, recipe).getOrThrow()));
        for (String tier : List.of("reference", "quantum", "terminal")) {
            assertEquals(1, parse(declaration("{\"type\":\"anchors\",\"targets\":[{\"id\":\""
                    + tier + "\"}]}")).conditions().targets().getFirst().count());
        }
        for (String tier : List.of("material", "foundation", "Reference", "missing", "molecularmanipulator:reference", "#c:anchors")) {
            invalid(declaration("{\"type\":\"anchors\",\"targets\":[{\"id\":\"" + tier + "\"}]}"));
            var condition = new StarComputationUpgradeRecipe.Condition("anchors", List.of(),
                    List.of(new StarComputationUpgradeRecipe.Target(tier, 1, "")), "all", 1, true, true, "");
            assertNotNull(condition.error());
        }
        invalid(declaration("{\"type\":\"anchors\",\"targets\":[{\"id\":\"reference\",\"count\":0}]}"));
        invalid(declaration("{\"type\":\"anchors\",\"targets\":[{\"id\":\"quantum\",\"count\":1.5}]}"));
    }

    @Test
    void levelRangeAndFiniteEnergyAndStrictlyPositiveRateAreCodecErrorsNotExceptions() {
        var valid = declaration("{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\"}]}");
        for (int level = 1; level <= 9; level++) {
            var json = valid.deepCopy(); json.addProperty("level", level);
            assertEquals(level, parse(json).level());
        }
        for (int level : new int[] {-1, 0, 10, Integer.MAX_VALUE}) {
            var json = valid.deepCopy(); json.addProperty("level", level); invalid(json);
        }
        for (double energy : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var json = valid.deepCopy(); json.addProperty("energy", energy); invalid(json);
        }
        for (double rate : new double[] {-1, 0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            var json = valid.deepCopy(); json.addProperty("energy", 0); json.addProperty("ae_per_tick", rate); invalid(json);
        }
        var fractional = valid.deepCopy(); fractional.addProperty("level", 4.5); invalid(fractional);
        var json = valid.deepCopy(); json.addProperty("ae_per_tick", 0.000001);
        assertNotNull(parse(json));
    }

    @Test
    void invalidGroupsTargetsMatchingAndHexIdsFailThroughDataResult() {
        for (String condition : List.of(
                "{\"type\":\"typo\",\"targets\":[{\"id\":\"x\"}]}",
                "{\"type\":\"nodes\",\"match\":\"typo\",\"targets\":[{\"id\":\"foundation\"}]}",
                "{\"type\":\"all\"}", "{\"type\":\"any\",\"children\":[]}",
                "{\"type\":\"nodes\"}", "{\"type\":\"research\",\"targets\":[]}",
                "{\"type\":\"nodes\",\"required\":0,\"targets\":[{\"id\":\"foundation\"}]}",
                "{\"type\":\"nodes\",\"required\":1.5,\"targets\":[{\"id\":\"foundation\"}]}",
                "{\"type\":\"nodes\",\"match\":\"at_least\",\"required\":2,\"targets\":[{\"id\":\"foundation\"}]}",
                "{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\",\"count\":0}]}",
                "{\"type\":\"nodes\",\"targets\":[{\"id\":\"\"}]}",
                "{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\",\"count\":1.5}]}",
                "{\"type\":\"nodes\",\"children\":[{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\"}]}],\"targets\":[{\"id\":\"quantum\"}]}",
                "{\"type\":\"all\",\"children\":[{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\"}]}],\"targets\":[{\"id\":\"quantum\"}]}",
                "{\"type\":\"at_least\",\"required\":2,\"children\":[{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\"}]}]}",
                "{\"type\":\"machines\",\"targets\":[{\"id\":\"#\"}]}",
                "{\"type\":\"machines\",\"targets\":[{\"id\":\"Bad ID\"}]}",
                "{\"type\":\"research\",\"targets\":[{\"id\":\"#c:research\"}]}",
                "{\"type\":\"ftb_quests\",\"targets\":[{\"id\":\"not_hex\"}]}",
                "{\"type\":\"ftb_quests\",\"targets\":[{\"id\":\"1234567890ABCDEF0\"}]}")) {
            invalid(declaration(condition));
        }
        // Well-formed but nonexistent names remain runtime failures, so their IDs and reasons can be shown in the UI.
        assertNotNull(parse(declaration("{\"type\":\"machines\",\"targets\":[{\"id\":\"missing:controller\"}]}")));
        assertNotNull(parse(declaration("{\"type\":\"nodes\",\"targets\":[{\"id\":\"missing_node\"}]}")));
    }

    @Test
    void excessiveNestingAndElementCountsReturnErrorsBeforeEvaluationOrPacketAllocation() {
        JsonElement child = JsonParser.parseString("{\"type\":\"nodes\",\"targets\":[{\"id\":\"foundation\"}]}");
        for (int depth = 0; depth < StarComputationUpgradeRecipe.MAX_DEPTH + 1; depth++) {
            var parent = new JsonObject(); parent.addProperty("type", "all");
            var children = new JsonArray(); children.add(child); parent.add("children", children); child = parent;
        }
        var json = declaration("{}"); json.add("conditions", child); invalid(json);
        var targets = new JsonArray();
        for (int index = 0; index < StarComputationUpgradeRecipe.MAX_ELEMENTS; index++) {
            targets.add(JsonParser.parseString("{\"id\":\"foundation\"}"));
        }
        var condition = new JsonObject(); condition.addProperty("type", "nodes"); condition.add("targets", targets);
        json.add("conditions", condition); invalid(json);
    }

    @Test
    void conditionListsAreImmutableSnapshots() {
        var targets = new ArrayList<>(List.of(new StarComputationUpgradeRecipe.Target("foundation", 1, "")));
        var leaf = new StarComputationUpgradeRecipe.Condition("nodes", List.of(), targets, "all", 1, true, true, "");
        var children = new ArrayList<>(List.of(leaf));
        var group = new StarComputationUpgradeRecipe.Condition("all", children, List.of(), "all", 1, true, true, "");
        targets.clear(); children.clear();
        assertEquals(1, group.children().getFirst().targets().size());
        assertThrows(UnsupportedOperationException.class, () -> group.children().clear());
        assertThrows(UnsupportedOperationException.class, () -> leaf.targets().clear());
    }

    private static JsonObject declaration(String condition) {
        var json = new JsonObject();
        json.addProperty("level", 4); json.addProperty("energy", 0); json.addProperty("ae_per_tick", 1);
        json.add("conditions", JsonParser.parseString(condition));
        return json;
    }

    private static StarComputationUpgradeRecipe parse(JsonElement json) {
        return StarComputationUpgradeRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    private static void invalid(JsonElement json) {
        var result = assertDoesNotThrow(() -> StarComputationUpgradeRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json), json::toString);
        assertTrue(result.error().isPresent(), () -> "Expected codec error: " + json);
    }
}
