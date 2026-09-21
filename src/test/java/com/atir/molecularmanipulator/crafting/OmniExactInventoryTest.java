package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.stacks.*;
import appeng.crafting.inv.ListCraftingInventory;
import java.math.BigInteger;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OmniExactInventoryTest {
    static { net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap(); }
    static final AEKey KEY = AEItemKey.of(Items.IRON_INGOT);
    static final AEKey OTHER = AEItemKey.of(Items.DIAMOND);
    static final BigInteger MAX = BigInteger.valueOf(Long.MAX_VALUE);
    @Test void storesAndExtractsBeyondLongWithoutSaturation() {
        var stock = new OmniExactInventory(); var window = new ListCraftingInventory(k -> {});
        var total = MAX.multiply(BigInteger.valueOf(123)).add(BigInteger.TEN);
        stock.insert(window, KEY, total);
        assertEquals(Long.MAX_VALUE, window.list.get(KEY));
        assertEquals(total, stock.amount(window, KEY));
        assertEquals(total, stock.extract(window, KEY, total, Actionable.SIMULATE));
        assertEquals(total, stock.amount(window, KEY));
        assertEquals(total, stock.extract(window, KEY, total, Actionable.MODULATE));
        assertTrue(stock.isEmpty()); assertTrue(window.list.isEmpty());
    }
    @Test void finiteReservationRollbackAndCommitConserveStock() {
        var stock = new OmniExactInventory(); var window = new ListCraftingInventory(k -> {});
        var initial = MAX.multiply(BigInteger.valueOf(4));
        stock.insert(window, KEY, initial);
        var unit = Map.of(KEY, MAX);
        assertEquals(BigInteger.valueOf(5), OmniExactInputReservation.maximum(stock, window, unit, BigInteger.TEN));
        try (var reservation = OmniExactInputReservation.reserve(stock, window, unit, BigInteger.valueOf(4))) {
            assertNotNull(reservation); assertEquals(MAX, stock.amount(window, KEY));
        }
        assertEquals(initial, stock.amount(window, KEY));
        try (var reservation = OmniExactInputReservation.reserve(stock, window, unit, BigInteger.valueOf(4))) {
            reservation.commit();
        }
        assertEquals(MAX, stock.amount(window, KEY));
    }
    @Test void multiKeyShortageDoesNotPartiallyConsume() {
        var stock = new OmniExactInventory(); var window = new ListCraftingInventory(k -> {});
        stock.insert(window, KEY, MAX.multiply(BigInteger.TEN));
        stock.insert(window, OTHER, BigInteger.ONE);
        assertNull(OmniExactInputReservation.reserve(stock, window,
                Map.of(KEY, MAX, OTHER, BigInteger.ONE), BigInteger.valueOf(3)));
        assertEquals(MAX.multiply(BigInteger.TEN), stock.amount(window, KEY));
        assertEquals(BigInteger.ONE, stock.amount(window, OTHER));
    }
    @Test void duplicateSlotsAggregateAndOnlyExplicitInfiniteKeysAreFree() {
        var a = new KeyCounter(); a.add(KEY, Long.MAX_VALUE); a.add(OTHER, 1);
        var b = new KeyCounter(); b.add(KEY, Long.MAX_VALUE);
        assertEquals(Map.of(KEY, MAX.multiply(BigInteger.TWO)),
                OmniExactInputReservation.unitInputs(new KeyCounter[]{a,b}, Set.of(OTHER)));
    }
    @Test void refundWindowRetainsRemainderUnderPartialStorageAcceptance() {
        var stock = new OmniExactInventory(); var window = new ListCraftingInventory(k -> {});
        var total = MAX.multiply(BigInteger.valueOf(3));
        stock.insert(window, KEY, total);
        window.extract(KEY, 17, Actionable.MODULATE);
        stock.refill(window);
        assertEquals(Long.MAX_VALUE, window.list.get(KEY));
        assertEquals(total.subtract(BigInteger.valueOf(17)), stock.amount(window, KEY));
        stock.refill(window);
        assertEquals(total.subtract(BigInteger.valueOf(17)), stock.amount(window, KEY));
    }
    @Test void wrapperFindsAndRollsBackOverflowOnlyInputs() {
        var stock = new OmniExactInventory(); var window = new ListCraftingInventory(k -> {});
        stock.insert(window, KEY, MAX.multiply(BigInteger.TWO));
        window.extract(KEY, Long.MAX_VALUE, Actionable.MODULATE);
        var wrapped = stock.wrap(window);
        assertTrue(wrapped.findFuzzyTemplates(KEY).iterator().hasNext());
        assertEquals(3, wrapped.extract(KEY, 3, Actionable.MODULATE));
        wrapped.insert(KEY, 3, Actionable.MODULATE);
        assertEquals(MAX, stock.amount(window, KEY));
    }
}
