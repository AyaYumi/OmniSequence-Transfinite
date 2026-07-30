package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Runtime dispatch contract understood only by the molecular center and the
 * assembler-matrix molecular core.
 */
public final class MolecularReusableBatchPlan {
    public enum InputMode {
        CONSUMABLE,
        INVARIANT_REUSABLE,
        DETERMINISTIC_DAMAGE
    }

    public record InputPlan(AEKey initialKey, long amountPerCraft, InputMode mode,
            @Nullable AEKey finalKey) {
        public InputPlan {
            Objects.requireNonNull(initialKey, "initialKey");
            Objects.requireNonNull(mode, "mode");
            if (amountPerCraft <= 0) {
                throw new IllegalArgumentException("amountPerCraft must be positive");
            }
            if (mode == InputMode.INVARIANT_REUSABLE
                    && !initialKey.equals(finalKey)) {
                throw new IllegalArgumentException(
                        "Invariant reusable input must return the same key");
            }
            if (mode == InputMode.DETERMINISTIC_DAMAGE
                    && !(initialKey instanceof AEItemKey)) {
                throw new IllegalArgumentException(
                        "Damage transition requires an item key");
            }
        }

        public boolean reusable() {
            return mode != InputMode.CONSUMABLE;
        }

        public long providedAmount(long craftCount) {
            return mode == InputMode.CONSUMABLE
                    ? Math.multiplyExact(amountPerCraft, craftCount)
                    : amountPerCraft;
        }

        @Nullable
        public AEKey keyAfter(long completedCrafts, long totalCrafts) {
            if (completedCrafts < 0 || completedCrafts > totalCrafts) {
                throw new IllegalArgumentException("Invalid completed craft count");
            }
            if (mode == InputMode.CONSUMABLE) {
                return null;
            }
            if (mode == InputMode.INVARIANT_REUSABLE || completedCrafts == 0) {
                return initialKey;
            }
            if (completedCrafts == totalCrafts && finalKey == null) {
                return null;
            }

            var stack = ((AEItemKey) initialKey).toStack();
            long damage = Math.addExact(stack.getDamageValue(), completedCrafts);
            if (damage > Integer.MAX_VALUE) {
                throw new ArithmeticException("Damage value overflow");
            }
            stack.setDamageValue((int) damage);
            return Objects.requireNonNull(AEItemKey.of(stack),
                    "Could not create transitioned item key");
        }
    }

    private final long craftCount;
    private final InputPlan[] inputs;

    public MolecularReusableBatchPlan(long craftCount, InputPlan[] inputs) {
        if (craftCount <= 1) {
            throw new IllegalArgumentException("Reusable batch must contain multiple crafts");
        }
        this.inputs = Objects.requireNonNull(inputs, "inputs").clone();
        if (this.inputs.length == 0) {
            throw new IllegalArgumentException("Reusable batch has no inputs");
        }
        boolean reusable = false;
        for (var input : this.inputs) {
            input = Objects.requireNonNull(input, "input");
            reusable |= input.reusable();
            if (input.mode() == InputMode.DETERMINISTIC_DAMAGE
                    && input.finalKey() != null
                    && !input.finalKey().equals(
                            input.keyAfter(craftCount, craftCount))) {
                throw new IllegalArgumentException(
                        "Finite reusable input has an inconsistent final key");
            }
        }
        if (!reusable) {
            throw new IllegalArgumentException("Reusable batch has no reusable input");
        }
        this.craftCount = craftCount;
    }

    public long craftCount() {
        return craftCount;
    }

    public InputPlan[] inputs() {
        return inputs.clone();
    }

    public boolean matchesProvidedInputs(KeyCounter[] providedInputs) {
        if (providedInputs == null || providedInputs.length != inputs.length) {
            return false;
        }
        try {
            for (int index = 0; index < inputs.length; index++) {
                var provided = providedInputs[index];
                if (provided == null) {
                    return false;
                }
                provided.removeZeros();
                if (provided.size() != 1
                        || provided.get(inputs[index].initialKey())
                                != inputs[index].providedAmount(craftCount)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public KeyCounter expectedRemainders(long completedCrafts) {
        var result = new KeyCounter();
        for (var input : inputs) {
            if (!input.reusable()) {
                continue;
            }
            AEKey key = input.keyAfter(completedCrafts, craftCount);
            if (key != null) {
                result.add(key, input.amountPerCraft());
            }
        }
        return result;
    }
}
