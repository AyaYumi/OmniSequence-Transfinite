package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import com.mojang.serialization.JsonOps;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

class MatterFabricationSourceIdentityTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), java.util.Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void attachesExactSourceToANewRecipeWithoutChangingItsProductionFields() {
        var original = new MatterFabricationRecipe(
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.STONE), 2)),
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.DIAMOND), 1)),
                List.of(new ItemStack(Items.IRON_INGOT, 3)),
                new FluidStack(Fluids.WATER, 1000), new FluidStack(Fluids.LAVA, 250),
                List.of(new GenericStack(AEItemKey.of(Items.EMERALD), 5)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 7)), 73, 128.0, true);
        var sourceId = ResourceLocation.parse("another_mod:machines/exact_recipe_id");
        var imported = original.withSourceRecipeId(sourceId);

        assertNull(original.sourceRecipeId());
        assertNotSame(original, imported);
        assertEquals(sourceId, imported.sourceRecipeId());
        assertEquals(original.ingredients(), imported.ingredients());
        assertEquals(original.catalysts(), imported.catalysts());
        assertTrue(ItemStack.matches(original.results().getFirst(), imported.results().getFirst()));
        assertNotSame(original.results().getFirst(), imported.results().getFirst());
        assertEquals(original.fluidInput().getAmount(), imported.fluidInput().getAmount());
        assertTrue(FluidStack.isSameFluidSameComponents(original.fluidInput(), imported.fluidInput()));
        assertEquals(MatterRecipeIndex.outputAmounts(original), MatterRecipeIndex.outputAmounts(imported));
        assertEquals(original.aeInputs(), imported.aeInputs());
        assertEquals(original.processingTime(), imported.processingTime());
        assertEquals(original.aePerTick(), imported.aePerTick());
        assertEquals(original.requiresResearch(), imported.requiresResearch());
        assertThrows(NullPointerException.class, () -> original.withSourceRecipeId(null));
    }

    @Test
    void sourceIdentityIsRuntimeMetadataNotADataPackPermissionClaim() {
        var original = new MatterFabricationRecipe(
                List.of(new MatterFabricationRecipe.CountedIngredient(Ingredient.of(Items.STONE), 1)),
                List.of(new ItemStack(Items.IRON_INGOT)), FluidStack.EMPTY, FluidStack.EMPTY, 20, 64.0);
        var sourceId = ResourceLocation.parse("another_mod:not_the_output_item");
        var codec = new MatterFabricationRecipe.Serializer().codec().codec();
        var json = codec.encodeStart(JsonOps.INSTANCE, original.withSourceRecipeId(sourceId)).getOrThrow().getAsJsonObject();
        assertFalse(json.has("source_recipe_id"));
        assertFalse(json.has("sourceRecipeId"));
        // A data pack cannot forge an imported source identity through an unrecognized JSON field.
        json.addProperty("source_recipe_id", sourceId.toString());
        var decoded = codec.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(decoded.sourceRecipeId());
        assertEquals(MatterRecipeIndex.outputAmounts(original), MatterRecipeIndex.outputAmounts(decoded));
    }
}
