package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import java.util.Map;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.common.crafting.StrictNBTIngredient;
import org.junit.jupiter.api.Test;

class ResearchIngredientIndexTest {
    static {
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null)
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void ordinaryItemsMatchComponentVariantsAndIgnoreUnrelatedKeys() {
        var named = Items.QUARTZ.getDefaultInstance(); named.setHoverName(Component.literal("named"));
        var plain = AEItemKey.of(Items.QUARTZ); var variant = AEItemKey.of(named); var other = AEItemKey.of(Items.DIAMOND);
        var stock = Map.of(plain, 3L, variant, 4L, other, 100L);
        var matcher = new ResearchIngredientIndex(List.of(new MatterResearchRecipe.Cost(Ingredient.of(Items.QUARTZ), 7)));
        var matches = matcher.matches(stock, ResearchIngredientIndex.indexStock(stock));
        assertEquals(java.util.Set.of(plain, variant), matches.keySet());
        assertTrue(matches.values().stream().allMatch(rows -> rows.get(0)));
    }
    @Test void customIngredientRetainsComponentPredicate() {
        var named = Items.QUARTZ.getDefaultInstance(); named.setHoverName(Component.literal("required"));
        var exact = AEItemKey.of(named); var plain = AEItemKey.of(Items.QUARTZ);
        var ingredient = StrictNBTIngredient.of(named);
        var stock = Map.of(plain, 10L, exact, 3L);
        var matcher = new ResearchIngredientIndex(List.of(new MatterResearchRecipe.Cost(ingredient, 3)));
        assertEquals(java.util.Set.of(exact), matcher.matches(stock, ResearchIngredientIndex.indexStock(stock)).keySet());
    }

    @Test void sharedIndexIgnoresStockConsumedByEarlierResearch() {
        var plain = AEItemKey.of(Items.QUARTZ);
        var named = Items.QUARTZ.getDefaultInstance();
        named.setHoverName(Component.literal("remaining"));
        var variant = AEItemKey.of(named);
        var stock = new java.util.LinkedHashMap<>(Map.of(plain, 3L, variant, 4L));
        var indexed = ResearchIngredientIndex.indexStock(stock);
        var matcher = new ResearchIngredientIndex(List.of(new MatterResearchRecipe.Cost(Ingredient.of(Items.QUARTZ), 4)));

        stock.put(plain, 0L);
        assertEquals(java.util.Set.of(variant), matcher.matches(stock, indexed).keySet());
        stock.remove(plain);
        assertEquals(java.util.Set.of(variant), matcher.matches(stock, indexed).keySet());
    }
}
