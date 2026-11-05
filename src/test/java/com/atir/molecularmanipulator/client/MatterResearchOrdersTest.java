package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MatterResearchOrdersTest {
    @BeforeAll static void bootstrap() throws Exception { Class.forName(UiRenderRecorder.class.getName()); }

    @Test void selectionKeepsGlobalOrdersAndFlattensTargetsThenMissingWithoutSortingOrDeduplication() {
        var selected = ResourceLocation.parse("test:selected");
        var other = ResourceLocation.parse("test:other");
        var target = item(Items.DIAMOND, 4_000_000_000L);
        var iron = item(Items.IRON_INGOT, 8);
        var water = new GenericStack(AEFluidKey.of(Fluids.WATER), 1_250);
        var global = entry(target, 7, 0, "queued", List.of(iron, water), null);
        var owned = entry(item(Items.GOLD_INGOT, 9), 0, 5, "submitted", List.of(iron, iron), selected);
        var hidden = entry(item(Items.EMERALD, 1), 1, 3, "calculating", List.of(item(Items.QUARTZ, 1)), other);
        var source = List.of(global, owned, hidden);
        var cache = new MatterResearchOrders();
        var view = cache.forSelection(source, selected);
        assertEquals(List.of(global, owned), view.entries());
        assertEquals(List.of(target, iron, water, owned.target(), iron, iron),
                view.rows().stream().map(MatterResearchOrders.Row::stack).toList());
        assertEquals(List.of(false, true, true, false, true, true),
                view.rows().stream().map(MatterResearchOrders.Row::missing).toList());
        assertEquals(2, view.missingCount(), "Count distinct AEKeys, not rows or amounts");
        assertEquals(1, view.queued(), "Summary counts order types, not item quantities");
        assertEquals(1, view.active());
        assertEquals("queued", view.status(), "The first matching order still supplies the status");
        assertEquals(4_000_000_000L, view.rows().getFirst().stack().amount());
        assertSame(view, cache.forSelection(source, ResourceLocation.parse("test:selected")));
        assertEquals(List.of(global, hidden), cache.forSelection(source, other).entries());
        assertEquals(List.of(global), cache.forSelection(source, null).entries());
    }

    @Test void sourceChangesRefreshAmountsStatusesAndSummaryInsteadOfFreezingLiveOrders() {
        var selected = ResourceLocation.parse("test:project");
        var cache = new MatterResearchOrders();
        var old = List.of(entry(item(Items.DIAMOND, 100), 100, 0, "queued", List.of(item(Items.IRON_INGOT, 40)), selected));
        var first = cache.forSelection(old, selected);
        assertSame(first, cache.forSelection(old, selected));
        var updated = List.of(entry(item(Items.DIAMOND, 75), 0, 75, "submitted", List.of(item(Items.IRON_INGOT, 6)), selected));
        var current = cache.forSelection(updated, selected);
        assertNotSame(first, current);
        assertEquals(75, current.rows().getFirst().stack().amount());
        assertEquals(6, current.rows().getLast().stack().amount());
        assertEquals("submitted", current.rows().getFirst().status());
        assertEquals(0, current.queued());
        assertEquals(1, current.active());
        assertEquals("submitted", current.status());
        var cleared = cache.forSelection(List.of(), selected);
        assertTrue(cleared.rows().isEmpty());
        assertEquals(0, cleared.missingCount());
        assertEquals(0, cleared.queued());
        assertEquals(0, cleared.active());
        assertEquals("idle", cleared.status());
    }

    @Test void distinctCountKeepsComponentVariantsSeparateAndMissingListsCannotBeMutated() {
        var named = Items.IRON_INGOT.getDefaultInstance();
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Special Iron"));
        var missing = new ArrayList<>(List.of(item(Items.IRON_INGOT, 1),
                new GenericStack(AEItemKey.of(named), 2), item(Items.IRON_INGOT, 3)));
        var order = entry(item(Items.DIAMOND, 1), 0, 0, "incomplete_plan", missing, null);
        missing.clear();
        var view = new MatterResearchOrders().forSelection(List.of(order), null);
        assertEquals(2, view.missingCount());
        assertEquals(4, view.rows().size());
        assertThrows(UnsupportedOperationException.class, () -> view.rows().clear());
        assertThrows(UnsupportedOperationException.class, () -> view.entries().clear());
        assertThrows(UnsupportedOperationException.class, () -> order.missing().clear());
    }

    @Test void largeSourceIsTraversedOnlyWhenItsSourceOrSelectionChanges() {
        var selected = ResourceLocation.parse("test:project");
        var source = new ArrayList<MatterResearchOrders.Entry>();
        for (int i = 0; i < 10_000; i++) {
            source.add(entry(item(Items.DIAMOND, i + 1), i % 2, i % 3, "queued",
                    List.of(item(Items.IRON_INGOT, i + 1)), selected));
        }
        int[] reads = {0};
        var immutable = List.copyOf(source);
        List<MatterResearchOrders.Entry> counted = new java.util.AbstractList<>() {
            @Override public MatterResearchOrders.Entry get(int index) { reads[0]++; return immutable.get(index); }
            @Override public int size() { return immutable.size(); }
        };
        var cache = new MatterResearchOrders();
        var view = cache.forSelection(counted, selected);
        assertEquals(20_000, view.rows().size());
        int builtReads = reads[0];
        assertEquals(10_000, builtReads);
        for (int i = 0; i < 100; i++) assertSame(view, cache.forSelection(counted, selected));
        assertEquals(builtReads, reads[0], "Unchanged live ticks must not filter/flatten/count the orders again");
        cache.forSelection(counted, null);
        assertEquals(builtReads + 10_000, reads[0]);
    }

    private static GenericStack item(net.minecraft.world.item.Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }
    private static MatterResearchOrders.Entry entry(GenericStack target, long queued, long active, String status,
            List<GenericStack> missing, ResourceLocation owner) {
        return new MatterResearchOrders.Entry(target, queued, active, status, missing, owner);
    }
}
