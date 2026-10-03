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
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }

    @Test
    void idleOrdersNeverResolveTheMachineOrNetwork() throws ReflectiveOperationException {
        // An idle service must not touch its host: resolving a grid/stock view here
        // adds work to every placed well even when no research was ordered.
        var allocatorField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        allocatorField.setAccessible(true);
        var allocator = (sun.misc.Unsafe) allocatorField.get(null);
        var host = (com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity)
                allocator.allocateInstance(com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity.class);
        var orders = new ResearchMaterialOrderService(host);
        for (int tick = 0; tick < 20_000; tick++) orders.tick();
        assertEquals("idle", orders.status());
        assertEquals(0, orders.queuedTypes());
        assertEquals(0, orders.activeJobs());
        orders.request(Map.of(AEItemKey.of(Items.DIAMOND), 64L));
        orders.clear();
        for (int tick = 0; tick < 20_000; tick++) orders.tick();
        assertEquals("idle", orders.status());
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
