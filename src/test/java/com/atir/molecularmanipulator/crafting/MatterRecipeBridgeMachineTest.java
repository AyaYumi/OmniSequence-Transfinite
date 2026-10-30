package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.recipes.handlers.ChargerRecipe;
import appeng.recipes.handlers.InscriberProcessType;
import appeng.recipes.handlers.InscriberRecipe;
import com.atir.molecularmanipulator.ForgeTestBootstrap;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.junit.jupiter.api.Test;

class MatterRecipeBridgeMachineTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null)
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        ForgeTestBootstrap.bootstrapAeKeyTypes();
    }

    @Test
    void registersActualForgeMachinesIncludingSharedAe2RecipeTypes() {
        var machines = MatterRecipeBridge.machines();
        for (var expected : List.of(
                List.of("ae2_inscriber", "ae2:inscriber", "ae2:inscriber"),
                List.of("ae2_charger", "ae2:charger", "ae2:charger"),
                List.of("eae_inscriber", "expatternprovider:ex_inscriber", "ae2:inscriber"),
                List.of("eae_charger", "expatternprovider:ex_charger", "ae2:charger"),
                List.of("eae_circuit_slicer", "expatternprovider:circuit_cutter", "expatternprovider:circuit_cutter"),
                List.of("aae_reaction_chamber", "advanced_ae:reaction_chamber", "advanced_ae:reaction"))) {
            var machine = machines.stream().filter(value -> value.key().equals(expected.get(0))).findFirst().orElseThrow();
            assertEquals(expected.get(1), machine.machineItem().toString());
            assertEquals(List.of(expected.get(2)), machine.recipeTypes());
        }
        assertTrue(machines.stream().noneMatch(machine -> machine.key().equals("eae_crystal_assembler")));
    }

    @Test
    void serializesLiveInscriberSlotsAndProcessMode() {
        var source = new InscriberRecipe(new ResourceLocation("test:inscribe"), Ingredient.of(Items.GOLD_INGOT),
                new ItemStack(Items.DIAMOND, 3), Ingredient.of(Items.IRON_INGOT), Ingredient.EMPTY,
                InscriberProcessType.INSCRIBE);
        var json = ForgeMachineRecipeJson.encodeFields(source).getAsJsonObject();
        assertEquals("inscribe", json.get("mode").getAsString());
        assertEquals("minecraft:gold_ingot", json.getAsJsonObject("ingredients").getAsJsonObject("middle").get("item").getAsString());
        assertEquals("minecraft:iron_ingot", json.getAsJsonObject("ingredients").getAsJsonObject("top").get("item").getAsString());
        assertFalse(json.getAsJsonObject("ingredients").has("bottom"));
        assertEquals(3, json.getAsJsonObject("result").get("count").getAsInt());
    }

    @Test
    void chargerSerializationDoesNotDuplicateItsIngredientList() {
        var source = new ChargerRecipe(new ResourceLocation("test:charge"), Ingredient.of(Items.STONE), Items.DIAMOND);
        var json = ForgeMachineRecipeJson.encodeFields(source).getAsJsonObject();
        assertTrue(json.has("ingredient"));
        assertFalse(json.has("ingredients"));
        assertEquals("minecraft:diamond", json.getAsJsonObject("result").get("id").getAsString());
    }

    @Test
    void readsNativeAe2GenericStackCountsAndNbt() {
        var value = JsonParser.parseString("""
                {"#c":"ae2:i","#":16,"id":"minecraft:nether_star","tag":{"marker":7}}
                """);
        var generic = ForgeRecipeCodecs.GENERIC_STACK.parse(JsonOps.INSTANCE, value).getOrThrow(false, ignored -> {});
        assertEquals(16, generic.amount());
        var outputs = MatterRecipeBridge.readItemOutputs(JsonOps.INSTANCE, value);
        assertEquals(1, outputs.size());
        assertEquals(16, outputs.get(0).getCount());
        assertEquals(Items.NETHER_STAR, outputs.get(0).getItem());
        assertEquals(7, outputs.get(0).getTag().getInt("marker"));
        assertEquals(generic.what(), AEItemKey.of(outputs.get(0)));
    }
}
