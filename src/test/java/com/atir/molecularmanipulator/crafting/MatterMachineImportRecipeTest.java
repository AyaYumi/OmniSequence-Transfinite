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
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MatterMachineImportRecipeTest {
    @BeforeAll
    static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
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
        var declaration = MatterMachineImportRecipe.CODEC.codec().parse(JsonOps.INSTANCE, json).getOrThrow();
        var id = ResourceLocation.parse("kubejs:vanilla_furnace");
        var machines = MatterRecipeBridge.machines(List.of(new RecipeHolder<>(id, declaration)));
        var machine = machines.getLast();
        assertEquals("kubejs/vanilla_furnace", machine.key());
        assertEquals(List.of("minecraft:smelting"), machine.recipeTypes());
        assertEquals(List.of("/ingredient"), machine.declaration().inputs().orElseThrow().items());
        assertEquals(MatterRecipeBridge.machines().size(), MatterRecipeBridge.machines(List.of()).size());
        var roundTrip = MatterMachineImportRecipe.CODEC.codec().encodeStart(JsonOps.INSTANCE, declaration).getOrThrow();
        assertEquals(declaration, MatterMachineImportRecipe.CODEC.codec().parse(JsonOps.INSTANCE, roundTrip).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var stream = new MatterMachineImportRecipe.Serializer().streamCodec();
            stream.encode(buffer, declaration);
            assertEquals(declaration, stream.decode(buffer));
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

        var rule = new MatterMachineImportRecipe.ResourceRule(ResourceLocation.parse("ae2lt:lightning"),
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
        assertEquals(2, inputs.getFirst().ingredient().getItems().length);
        assertEquals(2, inputs.getFirst().count());
        assertThrows(IllegalStateException.class, () -> MatterRecipeBridge.readMappedItemInputs(JsonOps.INSTANCE,
                JsonParser.parseString("[{\"item\":\"minecraft:iron_ingot\"},{\"item\":\"missing:resource\"}]")));

        var output = MatterRecipeBridge.readMappedItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("{\"item\":\"minecraft:iron_ingot\",\"count\":128}"));
        assertEquals(128, output.getFirst().getCount());
        assertThrows(IllegalStateException.class, () -> MatterRecipeBridge.readMappedItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("[{\"id\":\"minecraft:iron_ingot\"},{\"id\":\"missing:resource\"}]")));
        assertThrows(ArithmeticException.class, () -> MatterRecipeBridge.readMappedItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"minecraft:iron_ingot\",\"count\":1.5}")));
    }
}
