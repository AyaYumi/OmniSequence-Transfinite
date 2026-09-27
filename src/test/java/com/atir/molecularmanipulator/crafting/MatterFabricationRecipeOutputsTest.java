package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

class MatterFabricationRecipeOutputsTest {
    @Test
    void readsEveryResultFromAConvertedMachineRecipe() {
        var json = JsonParser.parseString("""
                [{"id":"minecraft:diamond","count":1},
                 {"id":"minecraft:gold_ingot","count":2},
                 {"id":"minecraft:iron_ingot","count":3},
                 {"id":"minecraft:copper_ingot","count":4}]
                """);

        var outputs = MatterRecipeBridge.readItemOutputs(JsonOps.INSTANCE, json);
        assertEquals(List.of(Items.DIAMOND, Items.GOLD_INGOT, Items.IRON_INGOT, Items.COPPER_INGOT),
                outputs.stream().map(ItemStack::getItem).toList());
        assertEquals(List.of(1, 2, 3, 4), outputs.stream().map(ItemStack::getCount).toList());
        var largeOutput = MatterRecipeBridge.readItemOutputs(JsonOps.INSTANCE,
                JsonParser.parseString("{\"id\":\"minecraft:amethyst_shard\",\"count\":72}"));
        assertEquals(72, largeOutput.get(0).getCount());
    }

    @Test
    void preservesEveryOutputAndRequiresAssemblyForAeOutputs() {
        var recipe = new MatterFabricationRecipe(
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.STONE), 1)),
                List.of(new ItemStack(Items.DIAMOND, 2), new ItemStack(Items.GOLD_INGOT, 3),
                        new ItemStack(Items.IRON_INGOT, 4), new ItemStack(Items.COPPER_INGOT, 5)),
                FluidStack.EMPTY, new FluidStack(Fluids.WATER, 1000), List.of(),
                List.of(new GenericStack(AEFluidKey.of(Fluids.LAVA), 2000),
                        new GenericStack(AEItemKey.of(Items.EMERALD), 5)),
                200, 256, true);

        var outputs = MatterRecipeIndex.outputAmounts(recipe);
        assertEquals(2L, outputs.get(AEItemKey.of(Items.DIAMOND)));
        assertEquals(3L, outputs.get(AEItemKey.of(Items.GOLD_INGOT)));
        assertEquals(4L, outputs.get(AEItemKey.of(Items.IRON_INGOT)));
        assertEquals(5L, outputs.get(AEItemKey.of(Items.COPPER_INGOT)));
        assertEquals(1000L, outputs.get(AEFluidKey.of(Fluids.WATER)));
        assertEquals(2000L, outputs.get(AEFluidKey.of(Fluids.LAVA)));
        assertEquals(5L, outputs.get(AEItemKey.of(Items.EMERALD)));
        assertNull(recipe.consumptionPlan(new MatterFabricationRecipeInput(List.of(new ItemStack(Items.STONE)))));
    }
}
