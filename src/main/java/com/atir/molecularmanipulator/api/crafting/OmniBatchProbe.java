package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;

import java.util.List;
import java.util.Objects;

/**
 * Immutable capacity probe for one provider and one advertised AE2 pattern.
 *
 * @since 1.3.9 (API version 1)
 */
public record OmniBatchProbe(
        IPatternDetails pattern,
        List<Input> oneCraftInputs,
        long requestedMaxCrafts) {
    public OmniBatchProbe {
        Objects.requireNonNull(pattern, "pattern");
        oneCraftInputs = List.copyOf(oneCraftInputs);
        if (oneCraftInputs.isEmpty()) {
            throw new IllegalArgumentException("oneCraftInputs must not be empty");
        }
        if (requestedMaxCrafts < 2) {
            throw new IllegalArgumentException("requestedMaxCrafts must be at least two");
        }
    }

    /**
     * One input selected for the initial craft. This is a capacity hint only;
     * substitutions may select different keys or ratios for the final batch.
     */
    public record Input(int slot, AEKey key, long amount) {
        public Input {
            Objects.requireNonNull(key, "key");
            if (slot < 0 || amount <= 0) {
                throw new IllegalArgumentException("slot and amount must be valid");
            }
        }
    }
}
