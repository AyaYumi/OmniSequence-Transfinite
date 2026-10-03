package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import java.util.List;
import java.util.Map;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MatterMachineImportRecipeTest {
    @BeforeAll
    static void bootstrap() {
        net.minecraftforge.common.crafting.CraftingHelper.register(new ResourceLocation("minecraft:item"),
                net.minecraftforge.common.crafting.VanillaIngredientSerializer.INSTANCE);
    }

    @Test
    void declarationCodecAndMachineListFollowReloadedRecipes() {
        var json = JsonParser.parseString("""
                {
                  "machine": "minecraft:furnace",
                  "serializers": ["minecraft:smelting"],
                  "research": {
                    "title": "research.kubejs.furnace",
                    "ingredients": [{"ingredient": {"item": "minecraft:iron_ingot"}, "count": "1"}]
                  },
                  "inputs": {"items": ["/ingredient"], "ae_keys": [{
                    "key_type": "ae2lt:lightning", "amount_path": "/lightningCost", "amount_default": 4,
                    "fields": {"tier": "high_voltage", "extra": {"nested": ["example", 2]}},
                    "field_paths": {"tier": "/lightningTier"}
                  }]},
                  "outputs": {"items": ["/result"]}
                }
                """);
        var declaration = MatterMachineImportRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json)
                .getOrThrow(false, message -> {});
        var id = new ResourceLocation("kubejs:vanilla_furnace");
        var machines = MatterRecipeBridge.machines(List.of(declaration.withId(id)));
        var machine = machines.get(machines.size() - 1);
        assertEquals("kubejs/vanilla_furnace", machine.key());
        assertEquals(List.of("minecraft:smelting"), machine.recipeTypes());
        assertEquals(List.of("/ingredient"), machine.declaration().inputs().orElseThrow().items());
        assertEquals(MatterRecipeBridge.machines().size(), MatterRecipeBridge.machines(List.of()).size());
        var roundTrip = MatterMachineImportRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, declaration)
                .getOrThrow(false, message -> {});
        assertEquals(roundTrip, MatterMachineImportRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE,
                MatterMachineImportRecipe.CODEC.codec().parse(JsonOps.INSTANCE, roundTrip).getOrThrow(false, message -> {}))
                .getOrThrow(false, message -> {}));
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            var serializer = new MatterMachineImportRecipe.Serializer();
            serializer.toNetwork(buffer, declaration);
            var decoded = serializer.fromNetwork(id, buffer);
            assertEquals(id, decoded.id());
            assertEquals(roundTrip, MatterMachineImportRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, decoded)
                    .getOrThrow(false, message -> {}));
        } finally { buffer.release(); }
    }

    @Test
    void jsonPointersAndAeKeyFieldsPreserveResourceIdentity() {
        var encoded = JsonParser.parseString("""
                {"input/slot": [{"item":"minecraft:iron_ingot"}],
                 "lightningTier":"extreme_high_voltage","lightningCost":128}
                """);
        assertNotNull(MatterRecipeBridge.atPath(encoded, "/input~1slot/0"));
        assertNull(MatterRecipeBridge.atPath(encoded, "/input~1slot/1"));

        var rule = new MatterMachineImportRecipe.ResourceRule(new ResourceLocation("ae2lt:lightning"),
                "/lightningCost", 4, Map.of("tier", new JsonPrimitive("high_voltage")), Map.of("tier", "/lightningTier"));
        var resource = MatterRecipeBridge.mappedResourceData(encoded, rule);
        assertEquals("ae2lt:lightning", resource.get("#t").getAsString());
        assertEquals(128, resource.get("#").getAsLong());
        assertEquals("extreme_high_voltage", resource.get("tier").getAsString());

        var defaults = MatterRecipeBridge.mappedResourceData(JsonParser.parseString("{}"), rule);
        assertEquals(4, defaults.get("#").getAsLong());
        assertEquals("high_voltage", defaults.get("tier").getAsString());
        assertNull(MatterRecipeBridge.mappedResourceData(JsonParser.parseString("{\"lightningCost\":1.5}"), rule));
        assertNull(MatterRecipeBridge.mappedResourceData(JsonParser.parseString("{\"lightningCost\":0}"), rule));
    }

    @Test
    void explicitMappingsNeverKeepOnlyReadablePartsOfARecipe() {
        var inputs = MatterRecipeBridge.readMappedItemInputs(JsonOps.INSTANCE, JsonParser.parseString("""
                [{"ingredient":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:gold_ingot"}],"count":2},
                 {"item":"minecraft:redstone","count":3}]
                """));
        assertEquals(2, inputs.size());
        assertEquals(2, inputs.get(0).ingredient().getItems().length);
        assertEquals(2, inputs.get(0).count());
        assertThrows(RuntimeException.class, () -> MatterRecipeBridge.readMappedItemInputs(JsonOps.INSTANCE,
                JsonParser.parseString("[{\"item\":\"minecraft:iron_ingot\"},{\"item\":\"missing:resource\"}]")));

        var output = MatterRecipeBridge.readMappedItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\",\"count\":128}"));
        assertEquals(128, output.get(0).getCount());
        assertThrows(RuntimeException.class, () -> MatterRecipeBridge.readMappedItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("[{\"id\":\"minecraft:iron_ingot\"},{\"id\":\"missing:resource\"}]")));
        assertThrows(ArithmeticException.class, () -> MatterRecipeBridge.readMappedItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"minecraft:iron_ingot\",\"count\":1.5}")));
    }
}
