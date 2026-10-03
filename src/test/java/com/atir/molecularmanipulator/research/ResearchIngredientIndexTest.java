package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import java.util.Map;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import org.junit.jupiter.api.Test;

class ResearchIngredientIndexTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void ordinaryItemsMatchComponentVariantsAndIgnoreUnrelatedKeys() {
        var named = Items.QUARTZ.getDefaultInstance(); named.set(DataComponents.CUSTOM_NAME, Component.literal("named"));
        var plain = AEItemKey.of(Items.QUARTZ); var variant = AEItemKey.of(named); var other = AEItemKey.of(Items.DIAMOND);
        var stock = Map.of(plain, 3L, variant, 4L, other, 100L);
        var matcher = new ResearchIngredientIndex(List.of(new MatterResearchRecipe.Cost(Ingredient.of(Items.QUARTZ), 7)));
        var matches = matcher.matches(stock, ResearchIngredientIndex.indexStock(stock));
        assertEquals(java.util.Set.of(plain, variant), matches.keySet());
        assertTrue(matches.values().stream().allMatch(rows -> rows.get(0)));
    }
    @Test void customIngredientRetainsComponentPredicate() {
        var named = Items.QUARTZ.getDefaultInstance(); named.set(DataComponents.CUSTOM_NAME, Component.literal("required"));
        var exact = AEItemKey.of(named); var plain = AEItemKey.of(Items.QUARTZ);
        var ingredient = DataComponentIngredient.of(false, named);
        var stock = Map.of(plain, 10L, exact, 3L);
        var matcher = new ResearchIngredientIndex(List.of(new MatterResearchRecipe.Cost(ingredient, 3)));
        assertEquals(java.util.Set.of(exact), matcher.matches(stock, ResearchIngredientIndex.indexStock(stock)).keySet());
    }
}
