package com.atir.molecularmanipulator.menu;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class AutoCrafterOutputViewTest {
    static {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        if (net.minecraftforge.fml.loading.LoadingModList.get() == null) {
            net.minecraftforge.fml.loading.LoadingModList.of(List.of(), List.of(), null);
        }
    }

    @Test
    void displaysFullItemAndFluidAmountsWithoutAllowingTransfers() {
        var view = newView();
        var item = new GenericStack(AEItemKey.of(Items.DIAMOND), Long.MAX_VALUE);
        var fluid = new GenericStack(AEFluidKey.of(Fluids.WATER), 9_000_000_000L);
        view.refresh(List.of(item, fluid), 0);
        assertEquals(item, view.getStack(0));
        assertEquals(fluid, view.getStack(1));
        assertEquals(0, view.extract(0, item.what(), 64, Actionable.MODULATE));
        assertEquals(0, view.insert(0, item.what(), 64, Actionable.MODULATE));
        assertEquals(item, view.getStack(0));
    }

    @Test
    void paginatesOverflowAndClearsOldSlotsWhenTheBufferDrains() {
        var outputs = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR).limit(37)
                .map(item -> new GenericStack(AEItemKey.of(item), 80)).toList();
        var view = newView();
        assertEquals(2, view.refresh(outputs, Integer.MAX_VALUE));
        assertEquals(3, view.pageCount());
        assertEquals(outputs.get(36), view.getStack(0));
        assertNull(view.getStack(1));
        assertEquals(1, view.refresh(outputs, 1));
        assertEquals(outputs.subList(18, 36), java.util.stream.IntStream.range(0, view.size())
                .mapToObj(view::getStack).toList());
        assertEquals(0, view.refresh(List.of(outputs.get(0)), 2));
        assertEquals(1, view.pageCount());
        for (int slot = 1; slot < view.size(); slot++) assertNull(view.getStack(slot));
        view.refresh(List.of(), 0);
        assertTrue(view.isEmpty());
    }

    @Test
    void changingHashIterationOrderDoesNotShuffleVisibleOutputs() {
        var view = newView();
        var first = new GenericStack(AEItemKey.of(Items.STONE), 100);
        var second = new GenericStack(AEItemKey.of(Items.DIRT), 200);
        view.refresh(List.of(first, second), 0);
        var updatedFirst = new GenericStack(first.what(), 80);
        view.refresh(List.of(second, updatedFirst), -1);
        assertEquals(updatedFirst, view.getStack(0));
        assertEquals(second, view.getStack(1));
    }

    private static AutoCrafterOutputView newView() {
        return new AutoCrafterOutputView(Set.of(AEKeyType.items(), AEKeyType.fluids()));
    }
}
