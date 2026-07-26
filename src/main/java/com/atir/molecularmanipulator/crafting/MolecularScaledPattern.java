package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runtime-only view of a pattern whose complete recipe is repeated a fixed number of times.
 * The encoded pattern definition is deliberately left unchanged; callers that look up a
 * provider by pattern identity must use {@link #unwrap(IPatternDetails)} first.
 */
public final class MolecularScaledPattern implements IPatternDetails {
    private final IPatternDetails base;
    private final long multiplier;
    private final IInput[] inputs;
    private final List<GenericStack> outputs;
    private final int hashCode;

    public MolecularScaledPattern(IPatternDetails base, long multiplier) {
        Objects.requireNonNull(base, "base");
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Pattern multiplier must be positive: " + multiplier);
        }

        if (base instanceof MolecularScaledPattern scaled) {
            this.base = scaled.base;
            this.multiplier = Math.multiplyExact(scaled.multiplier, multiplier);
        } else {
            this.base = base;
            this.multiplier = multiplier;
        }

        this.inputs = scaleInputs(this.base.getInputs(), this.multiplier);
        this.outputs = scaleOutputs(this.base.getOutputs(), this.multiplier);
        this.hashCode = 31 * this.base.hashCode() + Long.hashCode(this.multiplier);
    }

    /**
     * Returns the unscaled pattern. Nested scaled patterns are normalized by the constructor,
     * so this is always the original pattern rather than another wrapper.
     */
    public IPatternDetails base() {
        return base;
    }

    public long multiplier() {
        return multiplier;
    }

    public IPatternDetails unwrap() {
        return base;
    }

    public static IPatternDetails unwrap(IPatternDetails patternDetails) {
        Objects.requireNonNull(patternDetails, "patternDetails");
        while (patternDetails instanceof MolecularScaledPattern scaled) {
            patternDetails = scaled.base;
        }
        return patternDetails;
    }

    @Override
    public AEItemKey getDefinition() {
        return base.getDefinition();
    }

    @Override
    public IInput[] getInputs() {
        return inputs;
    }

    @Override
    public List<GenericStack> getOutputs() {
        return outputs;
    }

    @Override
    public boolean supportsPushInputsToExternalInventory() {
        return base.supportsPushInputsToExternalInventory();
    }

    /**
     * A scaled processing pattern cannot delegate this method to
     * {@code AEProcessingPattern}: that implementation uses the encoded sparse recipe amounts,
     * which are intentionally unscaled. Forward the actual extracted counters instead, ensuring
     * that every accepted material remains owned by the provider and reaches its sink.
     */
    @Override
    public void pushInputsToExternalInventory(KeyCounter[] inputHolder, PatternInputSink inputSink) {
        Objects.requireNonNull(inputHolder, "inputHolder");
        Objects.requireNonNull(inputSink, "inputSink");

        // Validate the complete holder before sending anything so malformed input cannot result
        // in a partially transferred recipe.
        for (int index = 0; index < inputHolder.length; index++) {
            var counter = Objects.requireNonNull(inputHolder[index],
                    "inputHolder[" + index + "]");
            for (var entry : counter) {
                Objects.requireNonNull(entry.getKey(), "input key");
                if (entry.getLongValue() <= 0) {
                    throw new IllegalArgumentException(
                            "Pattern input amount must be positive: " + entry.getLongValue());
                }
            }
        }

        for (var counter : inputHolder) {
            for (var entry : counter) {
                inputSink.pushInput(entry.getKey(), entry.getLongValue());
            }
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof MolecularScaledPattern other)) {
            return false;
        }
        return multiplier == other.multiplier && base.equals(other.base);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        return "MolecularScaledPattern[base=" + base + ", multiplier=" + multiplier + "]";
    }

    private static IInput[] scaleInputs(IInput[] baseInputs, long multiplier) {
        Objects.requireNonNull(baseInputs, "base inputs");
        var scaledInputs = new IInput[baseInputs.length];
        for (int index = 0; index < baseInputs.length; index++) {
            scaledInputs[index] = new ScaledInput(
                    Objects.requireNonNull(baseInputs[index], "base input " + index),
                    multiplier);
        }
        return scaledInputs;
    }

    private static List<GenericStack> scaleOutputs(List<GenericStack> baseOutputs, long multiplier) {
        Objects.requireNonNull(baseOutputs, "base outputs");
        var scaledOutputs = new ArrayList<GenericStack>(baseOutputs.size());
        for (int index = 0; index < baseOutputs.size(); index++) {
            var output = Objects.requireNonNull(baseOutputs.get(index), "base output " + index);
            if (output.amount() <= 0) {
                throw new IllegalArgumentException(
                        "Pattern output amount must be positive: " + output.amount());
            }
            scaledOutputs.add(new GenericStack(output.what(),
                    Math.multiplyExact(output.amount(), multiplier)));
        }
        return List.copyOf(scaledOutputs);
    }

    private static final class ScaledInput implements IInput {
        private final IInput base;
        private final GenericStack[] possibleInputs;
        private final long multiplier;

        private ScaledInput(IInput base, long patternMultiplier) {
            this.base = base;

            long baseMultiplier = base.getMultiplier();
            if (baseMultiplier <= 0) {
                throw new IllegalArgumentException(
                        "Pattern input multiplier must be positive: " + baseMultiplier);
            }
            this.multiplier = Math.multiplyExact(baseMultiplier, patternMultiplier);

            this.possibleInputs = Objects.requireNonNull(base.getPossibleInputs(),
                    "possible inputs");
            if (possibleInputs.length == 0) {
                throw new IllegalArgumentException("Pattern input has no possible inputs");
            }
            for (var possibleInput : possibleInputs) {
                Objects.requireNonNull(possibleInput, "possible input");
                if (possibleInput.amount() <= 0) {
                    throw new IllegalArgumentException(
                            "Possible input amount must be positive: " + possibleInput.amount());
                }

                // AE2 later multiplies this amount with IInput#getMultiplier using ordinary
                // long arithmetic. Preflight the same product here so it cannot wrap there.
                Math.multiplyExact(possibleInput.amount(), this.multiplier);
            }
        }

        @Override
        public GenericStack[] getPossibleInputs() {
            return possibleInputs;
        }

        @Override
        public long getMultiplier() {
            return multiplier;
        }

        @Override
        public boolean isValid(AEKey input, Level level) {
            return base.isValid(input, level);
        }

        @Nullable
        @Override
        public AEKey getRemainingKey(AEKey template) {
            return base.getRemainingKey(template);
        }
    }
}
