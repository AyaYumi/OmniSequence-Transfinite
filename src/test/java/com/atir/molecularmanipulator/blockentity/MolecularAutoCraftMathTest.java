package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.api.Test;

class MolecularAutoCraftMathTest {
    @Test
    void ingredientReserveLimitsOnlyTheSpendableInventory() {
        assertEquals(20, MolecularAutoCraftMath.maxCraftsByIngredient(100, 20, 4));
        assertEquals(0, MolecularAutoCraftMath.maxCraftsByIngredient(20, 20, 4));
        assertEquals(0, MolecularAutoCraftMath.maxCraftsByIngredient(10, 20, 4));
    }

    @Test
    void ingredientCalculationPreservesLongMaxParallelism() {
        assertEquals(Long.MAX_VALUE,
                MolecularAutoCraftMath.maxCraftsByIngredient(Long.MAX_VALUE, 0, 1));
        assertEquals(3,
                MolecularAutoCraftMath.maxCraftsByIngredient(
                        Long.MAX_VALUE, Long.MAX_VALUE - 10, 3));
    }

    @Test
    void zeroOutputLimitMeansUnlimitedProduction() {
        assertEquals(Long.MAX_VALUE,
                MolecularAutoCraftMath.maxCraftsByOutput(
                        Long.MAX_VALUE, Long.MAX_VALUE, 0, Long.MAX_VALUE));
    }

    @Test
    void outputLimitCountsNetworkAndEscrowWithoutOverproduction() {
        assertEquals(10,
                MolecularAutoCraftMath.maxCraftsByOutput(40, 20, 100, 4));
        assertEquals(0,
                MolecularAutoCraftMath.maxCraftsByOutput(40, 20, 63, 4));
        assertEquals(0,
                MolecularAutoCraftMath.maxCraftsByOutput(100, 0, 100, 1));
    }

    @Test
    void outputLimitCannotWrapWhenNetworkAndEscrowOverflow() {
        assertEquals(0,
                MolecularAutoCraftMath.maxCraftsByOutput(
                        Long.MAX_VALUE - 5, 10, Long.MAX_VALUE, 1));
        assertEquals(2,
                MolecularAutoCraftMath.maxCraftsByOutput(
                        Long.MAX_VALUE - 10, 5, Long.MAX_VALUE, 2));
    }

    @Test
    void recursiveOutputLimitUsesNetGrowth() {
        long netGrowth = MolecularAutoCraftMath.netOutputGrowth(2, 1);
        assertEquals(1, netGrowth);
        assertEquals(99,
                MolecularAutoCraftMath.maxCraftsByOutput(1, 0, 100, netGrowth));
        assertEquals(0, MolecularAutoCraftMath.netOutputGrowth(1, 1));
    }

    @Test
    void aggregateArithmeticSaturatesInsteadOfWrapping() {
        assertEquals(Long.MAX_VALUE,
                MolecularAutoCraftMath.saturatedAdd(Long.MAX_VALUE - 1, 2));
        assertEquals(Long.MAX_VALUE,
                MolecularAutoCraftMath.saturatedMultiply(Long.MAX_VALUE, 2));
        assertEquals(42, MolecularAutoCraftMath.saturatedAdd(40, 2));
        assertEquals(42, MolecularAutoCraftMath.saturatedMultiply(6, 7));
    }

    @Test
    void rejectsNegativeAmountsAndZeroSizedRecipeEntries() {
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.maxCraftsByIngredient(-1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.maxCraftsByIngredient(1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.maxCraftsByOutput(0, -1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.maxCraftsByOutput(0, 0, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.saturatedAdd(-1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.saturatedMultiply(1, -1));
    }

    @Test
    void mergesAllFourLegacyOutputBuffersByKey() {
        var destination = amounts("shared", 5, "destination-only", 3);
        var pendingPrimary = amounts("shared", 10, "primary", 11);
        var pendingRemainder = amounts("shared", 20, "remainder", 13);
        var cachedPrimary = amounts("shared", 30, "cached-primary", 17);
        var cachedRemainder = amounts("shared", 40, "cached-remainder", 19);

        MolecularAutoCraftMath.mergeSaturated(destination,
                pendingPrimary, pendingRemainder, cachedPrimary, cachedRemainder);

        assertEquals(105, destination.getLong("shared"));
        assertEquals(3, destination.getLong("destination-only"));
        assertEquals(11, destination.getLong("primary"));
        assertEquals(13, destination.getLong("remainder"));
        assertEquals(17, destination.getLong("cached-primary"));
        assertEquals(19, destination.getLong("cached-remainder"));
    }

    @Test
    void legacyMergeSaturatesOverflowAndIgnoresInvalidEntries() {
        var destination = amounts("overflow", Long.MAX_VALUE - 2, "negative-old", -10);
        var first = amounts("overflow", 1, "zero", 0);
        var second = amounts("overflow", 2, "negative", -1, "negative-old", 4);

        MolecularAutoCraftMath.mergeSaturated(destination, first, second);

        assertEquals(Long.MAX_VALUE, destination.getLong("overflow"));
        assertEquals(0, destination.getLong("zero"));
        assertEquals(0, destination.getLong("negative"));
        assertEquals(4, destination.getLong("negative-old"));
    }

    @Test
    void legacyMergeRejectsNullMaps() {
        var destination = new Object2LongOpenHashMap<String>();
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.mergeSaturated(null, destination));
        assertThrows(IllegalArgumentException.class,
                () -> MolecularAutoCraftMath.mergeSaturated(destination,
                        (Object2LongOpenHashMap<String>) null));
    }

    private static Object2LongOpenHashMap<String> amounts(Object... entries) {
        var result = new Object2LongOpenHashMap<String>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], ((Number) entries[index + 1]).longValue());
        }
        return result;
    }
}
