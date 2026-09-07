package com.atir.molecularmanipulator.blockentity;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

/**
 * Overflow-safe arithmetic shared by automatic-crafting planning and tests.
 *
 * <p>All quantities in this class are non-negative AE amounts. A zero output
 * limit deliberately means unlimited production.</p>
 */
final class MolecularAutoCraftMath {
    private MolecularAutoCraftMath() {
    }

    static long maxCraftsByIngredient(long available, long reserve,
            long amountPerCraft) {
        requireNonNegative(available, "available");
        requireNonNegative(reserve, "reserve");
        requirePositive(amountPerCraft, "amountPerCraft");
        if (available <= reserve) {
            return 0;
        }
        return (available - reserve) / amountPerCraft;
    }

    static long maxCraftsByOutput(long networkAmount, long escrowAmount,
            long outputLimit, long amountPerCraft) {
        requireNonNegative(networkAmount, "networkAmount");
        requireNonNegative(escrowAmount, "escrowAmount");
        requireNonNegative(outputLimit, "outputLimit");
        requirePositive(amountPerCraft, "amountPerCraft");
        if (outputLimit == 0) {
            return Long.MAX_VALUE;
        }

        long occupied = saturatedAdd(networkAmount, escrowAmount);
        if (occupied >= outputLimit) {
            return 0;
        }
        return (outputLimit - occupied) / amountPerCraft;
    }

    static long netOutputGrowth(long producedPerCraft, long recycledInputPerCraft) {
        requireNonNegative(producedPerCraft, "producedPerCraft");
        requireNonNegative(recycledInputPerCraft, "recycledInputPerCraft");
        return producedPerCraft <= recycledInputPerCraft
                ? 0 : producedPerCraft - recycledInputPerCraft;
    }

    static long saturatedAdd(long left, long right) {
        requireNonNegative(left, "left");
        requireNonNegative(right, "right");
        return left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }

    static long saturatedMultiply(long left, long right) {
        requireNonNegative(left, "left");
        requireNonNegative(right, "right");
        if (left == 0 || right == 0) {
            return 0;
        }
        return left > Long.MAX_VALUE / right ? Long.MAX_VALUE : left * right;
    }

    @SafeVarargs
    static <K> void mergeSaturated(Object2LongOpenHashMap<K> destination,
            Object2LongOpenHashMap<K>... sources) {
        if (destination == null || sources == null) {
            throw new IllegalArgumentException("maps must not be null");
        }
        for (var source : sources) {
            if (source == null) {
                throw new IllegalArgumentException("source must not be null");
            }
            for (var entry : source.object2LongEntrySet()) {
                if (entry.getKey() != null && entry.getLongValue() > 0) {
                    destination.put(entry.getKey(), saturatedAdd(
                            Math.max(0, destination.getLong(entry.getKey())),
                            entry.getLongValue()));
                }
            }
        }
    }

    private static void requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
