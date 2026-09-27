package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;

/**
 * Optional capability for a provider that accepts a one-craft input prototype
 * and tracks the logical craft count independently of AE2's long counters.
 */
public interface OmniBigIntegerCraftingProvider extends ICraftingProvider {
    /**
     * Native providers perform one atomic BigInteger admission/commit.  Such a
     * provider must not be driven through the legacy repeated-long dispatch
     * loop, since that loop intentionally yields between ordinary pushes.
     */
    default boolean usesNativeBigIntegerBatch() {
        return false;
    }

    /** Returns the currently admissible count, never greater than requested. */
    BigInteger getMaximumBigIntegerCrafts(
            IPatternDetails pattern,
            KeyCounter[] unitPrototype,
            BigInteger requested);

    /**
     * Transfers one unit prototype and the exact logical count to the provider.
     * A false result must leave ownership of the prototype with the caller.
     */
    boolean pushBigIntegerCraftingPattern(
            IPatternDetails pattern,
            BigInteger count,
            KeyCounter[] unitPrototype);

    /**
     * Exact providers may optionally bind a completion token to the accepted
     * batch. Older providers do not need the token and keep the original path.
     */
    default boolean pushBigIntegerCraftingPattern(
            IPatternDetails pattern,
            BigInteger count,
            KeyCounter[] unitPrototype,
            Object completionToken) {
        return pushBigIntegerCraftingPattern(pattern, count, unitPrototype);
    }

    /**
     * Returns the logical count actually accepted by the last successful
     * push. Native providers may shrink a batch between the capacity probe and
     * the atomic admission when another task consumes the remaining machine
     * window. A null result means the provider guarantees the requested count.
     */
    default BigInteger lastAcceptedBigIntegerCrafts() {
        return null;
    }
}
