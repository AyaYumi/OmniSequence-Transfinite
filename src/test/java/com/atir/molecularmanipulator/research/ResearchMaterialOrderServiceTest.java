package com.atir.molecularmanipulator.research;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.CraftingPlan;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Map;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

class ResearchMaterialOrderServiceTest {
    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void reportsExactMissingItemAndFluidAmountsFromAePlan() {
        var item = AEItemKey.of(Items.QUARTZ);
        var fluid = AEFluidKey.of(Fluids.WATER);
        var missing = new KeyCounter();
        missing.add(item, 2_048);
        missing.add(fluid, 8_000);
        var plan = new CraftingPlan(new GenericStack(AEItemKey.of(Items.DIAMOND), 32),
                1, true, false, new KeyCounter(), new KeyCounter(), missing, Map.of());

        assertEquals(Map.of(item, 2_048L, fluid, 8_000L),
                ResearchMaterialOrderService.missingItems(plan).stream()
                        .collect(java.util.stream.Collectors.toMap(GenericStack::what, GenericStack::amount)));
    }

    @Test
    void displayOrderDoesNotFollowRetryQueueRotation() {
        var first = AEItemKey.of(Items.APPLE);
        var second = AEItemKey.of(Items.DIAMOND);
        var third = AEItemKey.of(Items.QUARTZ);

        assertEquals(List.of(first, second, third), ResearchMaterialOrderService.displayOrder(
                new LinkedHashSet<>(List.of(third, first, second))));
        assertEquals(List.of(first, second, third), ResearchMaterialOrderService.displayOrder(
                new LinkedHashSet<>(List.of(second, third, first))));
    }
}
