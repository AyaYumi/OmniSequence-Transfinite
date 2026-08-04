package com.atir.molecularmanipulator.api.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable description of the exact materials delivered for an Omni batch.
 * Entries retain their original pattern-input slot, including substitutions.
 *
 * @since 1.3.9 (API version 1)
 */
public record OmniBatchRequest(
        UUID dispatchId,
        @Nullable UUID craftingJobId,
        IPatternDetails pattern,
        long craftCount,
        List<Input> inputs,
        List<GenericStack> expectedOutputs) {
    public OmniBatchRequest {
        Objects.requireNonNull(dispatchId, "dispatchId");
        Objects.requireNonNull(pattern, "pattern");
        inputs = List.copyOf(inputs);
        expectedOutputs = List.copyOf(expectedOutputs);
        if (craftCount < 2) {
            throw new IllegalArgumentException("craftCount must be at least two");
        }
        if (inputs.isEmpty() || expectedOutputs.isEmpty()) {
            throw new IllegalArgumentException("inputs and expectedOutputs must not be empty");
        }
        for (var output : expectedOutputs) {
            if (output == null || output.what() == null || output.amount() <= 0) {
                throw new IllegalArgumentException("expectedOutputs contains an invalid stack");
            }
        }
    }

    /** One authoritative delivered AE key and total amount for a pattern slot. */
    public record Input(int slot, AEKey key, long amount) {
        public Input {
            Objects.requireNonNull(key, "key");
            if (slot < 0 || amount <= 0) {
                throw new IllegalArgumentException("slot and amount must be valid");
            }
        }
    }
}
