package com.atir.molecularmanipulator.crafting;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Runtime dispatch contract understood only by the molecular crafting
 * providers. A deterministic-damage input may own a compressed pool of tools
 * with different initial damage values.
 */
public final class MolecularReusableBatchPlan {
    public enum InputMode {
        CONSUMABLE,
        INVARIANT_REUSABLE,
        DETERMINISTIC_DAMAGE
    }

    /** A group of identical tools that each execute {@code usesPerTool} crafts. */
    public record DamageGroup(AEItemKey initialKey, long count,
            long usesPerTool, @Nullable AEItemKey finalKey) {
        public DamageGroup {
            Objects.requireNonNull(initialKey, "initialKey");
            if (count <= 0 || usesPerTool <= 0) {
                throw new IllegalArgumentException(
                        "Damage group count and uses must be positive");
            }
            if (finalKey != null && !finalKey.equals(
                    damageKeyAfter(initialKey, usesPerTool))) {
                throw new IllegalArgumentException(
                        "Damage group has an inconsistent final key");
            }
            Math.multiplyExact(count, usesPerTool);
        }

        public long coveredCrafts() {
            return Math.multiplyExact(count, usesPerTool);
        }

        @Nullable
        public AEItemKey keyAfter(long uses) {
            if (uses < 0 || uses > usesPerTool) {
                throw new IllegalArgumentException("Invalid tool use count");
            }
            if (uses == usesPerTool && finalKey == null) {
                return null;
            }
            return damageKeyAfter(initialKey, uses);
        }
    }

    public record InputPlan(AEKey initialKey, long amountPerCraft,
            InputMode mode, @Nullable AEKey finalKey,
            List<DamageGroup> damageGroups) {
        public InputPlan {
            Objects.requireNonNull(initialKey, "initialKey");
            Objects.requireNonNull(mode, "mode");
            damageGroups = damageGroups == null
                    ? List.of()
                    : List.copyOf(damageGroups);
            if (amountPerCraft <= 0) {
                throw new IllegalArgumentException(
                        "amountPerCraft must be positive");
            }
            if (mode == InputMode.CONSUMABLE && finalKey != null) {
                throw new IllegalArgumentException(
                        "Consumable input cannot have a final key");
            }
            if (mode == InputMode.INVARIANT_REUSABLE
                    && !initialKey.equals(finalKey)) {
                throw new IllegalArgumentException(
                        "Invariant reusable input must return the same key");
            }
            if (mode == InputMode.DETERMINISTIC_DAMAGE) {
                if (amountPerCraft != 1 || damageGroups.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Damage pools currently require one tool per craft");
                }
                if (!(initialKey instanceof AEItemKey)
                        || !initialKey.equals(damageGroups.get(0).initialKey())) {
                    throw new IllegalArgumentException(
                            "Damage pool initial key is inconsistent");
                }
                if (finalKey != null) {
                    throw new IllegalArgumentException(
                            "Damage pool final keys belong to their groups");
                }
            } else if (!damageGroups.isEmpty()) {
                throw new IllegalArgumentException(
                        "Only damage inputs may contain tool groups");
            }
        }

        public InputPlan(AEKey initialKey, long amountPerCraft,
                InputMode mode, @Nullable AEKey finalKey) {
            this(initialKey, amountPerCraft, mode, finalKey, List.of());
        }

        public static InputPlan deterministicDamage(
                List<DamageGroup> groups) {
            if (groups == null || groups.isEmpty()) {
                throw new IllegalArgumentException("Damage pool is empty");
            }
            return new InputPlan(groups.get(0).initialKey(), 1,
                    InputMode.DETERMINISTIC_DAMAGE, null, groups);
        }

        public boolean reusable() {
            return mode != InputMode.CONSUMABLE;
        }

        public void addProvidedInputs(KeyCounter target, long craftCount) {
            switch (mode) {
                case CONSUMABLE -> target.add(initialKey,
                        Math.multiplyExact(amountPerCraft, craftCount));
                case INVARIANT_REUSABLE -> target.add(initialKey,
                        amountPerCraft);
                case DETERMINISTIC_DAMAGE -> {
                    for (var group : damageGroups) {
                        target.add(group.initialKey(), group.count());
                    }
                }
            }
        }

        public long coveredCrafts() {
            if (mode != InputMode.DETERMINISTIC_DAMAGE) {
                return Long.MAX_VALUE;
            }
            long result = 0;
            for (var group : damageGroups) {
                result = Math.addExact(result, group.coveredCrafts());
            }
            return result;
        }

        /** Returns the exact tool key used for the requested zero-based craft. */
        public AEKey keyForCraft(long completedCrafts) {
            if (completedCrafts < 0) {
                throw new IllegalArgumentException("Invalid completed craft count");
            }
            if (mode != InputMode.DETERMINISTIC_DAMAGE) {
                return initialKey;
            }

            long remaining = completedCrafts;
            for (var group : damageGroups) {
                long covered = group.coveredCrafts();
                if (remaining >= covered) {
                    remaining -= covered;
                    continue;
                }
                long uses = remaining % group.usesPerTool();
                AEItemKey key = group.keyAfter(uses);
                if (key == null) {
                    throw new IllegalStateException(
                            "Completed tool selected for another craft");
                }
                return key;
            }
            throw new IllegalArgumentException(
                    "Craft exceeds damage pool capacity");
        }

        /** Adds all tools exactly as they exist after a batch checkpoint. */
        public void addCheckpointRemainders(KeyCounter target,
                long completedCrafts) {
            if (mode == InputMode.CONSUMABLE) {
                return;
            }
            if (mode == InputMode.INVARIANT_REUSABLE) {
                target.add(initialKey, amountPerCraft);
                return;
            }
            if (completedCrafts < 0 || completedCrafts > coveredCrafts()) {
                throw new IllegalArgumentException(
                        "Invalid damage-pool checkpoint");
            }

            long remaining = completedCrafts;
            for (var group : damageGroups) {
                long fullyUsed = Math.min(group.count(),
                        remaining / group.usesPerTool());
                remaining -= fullyUsed * group.usesPerTool();
                if (fullyUsed > 0 && group.finalKey() != null) {
                    target.add(group.finalKey(), fullyUsed);
                }

                long untouched = group.count() - fullyUsed;
                if (untouched <= 0) {
                    continue;
                }
                if (remaining > 0) {
                    AEItemKey partial = group.keyAfter(remaining);
                    if (partial == null) {
                        throw new IllegalStateException(
                                "Partial tool unexpectedly disappeared");
                    }
                    target.add(partial, 1);
                    untouched--;
                    remaining = 0;
                }
                if (untouched > 0) {
                    target.add(group.initialKey(), untouched);
                }
            }
            if (remaining != 0) {
                throw new IllegalArgumentException(
                        "Damage-pool checkpoint exceeds capacity");
            }
        }
    }

    private final long craftCount;
    private final InputPlan[] inputs;

    public MolecularReusableBatchPlan(long craftCount, InputPlan[] inputs) {
        if (craftCount <= 1) {
            throw new IllegalArgumentException(
                    "Reusable batch must contain multiple crafts");
        }
        this.inputs = Objects.requireNonNull(inputs, "inputs").clone();
        if (this.inputs.length == 0) {
            throw new IllegalArgumentException("Reusable batch has no inputs");
        }
        boolean reusable = false;
        int damageInputs = 0;
        for (var input : this.inputs) {
            input = Objects.requireNonNull(input, "input");
            reusable |= input.reusable();
            if (input.mode() == InputMode.DETERMINISTIC_DAMAGE) {
                damageInputs++;
                if (input.coveredCrafts() != craftCount) {
                    throw new IllegalArgumentException(
                            "Damage pool does not cover the complete batch");
                }
            }
        }
        if (!reusable) {
            throw new IllegalArgumentException(
                    "Reusable batch has no reusable input");
        }
        if (damageInputs > 1) {
            throw new IllegalArgumentException(
                    "Multiple deterministic-damage slots are not yet supported");
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
                var provided = copyPositive(providedInputs[index]);
                var expected = new KeyCounter();
                inputs[index].addProvidedInputs(expected, craftCount);
                if (!countersEqual(provided, expected)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /** All reusable items exactly as they exist after {@code completedCrafts}. */
    public KeyCounter expectedRemainders(long completedCrafts) {
        if (completedCrafts < 0 || completedCrafts > craftCount) {
            throw new IllegalArgumentException("Invalid completed craft count");
        }
        var result = new KeyCounter();
        for (var input : inputs) {
            input.addCheckpointRemainders(result, completedCrafts);
        }
        return result;
    }

    /** Remainders produced by exactly one craft at this batch position. */
    public KeyCounter expectedCraftRemainders(long completedBefore) {
        if (completedBefore < 0 || completedBefore >= craftCount) {
            throw new IllegalArgumentException("Invalid craft position");
        }
        var result = new KeyCounter();
        for (var input : inputs) {
            if (input.mode() == InputMode.INVARIANT_REUSABLE) {
                result.add(input.initialKey(), input.amountPerCraft());
            } else if (input.mode() == InputMode.DETERMINISTIC_DAMAGE) {
                AEKey current = input.keyForCraft(completedBefore);
                AEKey next = nextDamageKey(input, completedBefore, current);
                if (next != null) {
                    result.add(next, 1);
                }
            }
        }
        return result;
    }

    @Nullable
    private static AEKey nextDamageKey(InputPlan input, long completedBefore,
            AEKey current) {
        for (var group : input.damageGroups()) {
            long covered = group.coveredCrafts();
            if (completedBefore >= covered) {
                completedBefore -= covered;
                continue;
            }
            long usesBefore = completedBefore % group.usesPerTool();
            if (!Objects.equals(group.keyAfter(usesBefore), current)) {
                throw new IllegalStateException("Damage-pool key mismatch");
            }
            return group.keyAfter(usesBefore + 1);
        }
        throw new IllegalArgumentException("Craft exceeds damage pool capacity");
    }

    private static KeyCounter copyPositive(KeyCounter source) {
        if (source == null) {
            throw new IllegalArgumentException("Missing provided input");
        }
        var copy = new KeyCounter();
        for (var entry : source) {
            if (entry.getKey() == null || entry.getLongValue() < 0) {
                throw new IllegalArgumentException("Invalid provided input");
            }
            if (entry.getLongValue() > 0) {
                copy.add(entry.getKey(), entry.getLongValue());
            }
        }
        return copy;
    }

    private static boolean countersEqual(KeyCounter left, KeyCounter right) {
        left.removeZeros();
        right.removeZeros();
        if (left.size() != right.size()) {
            return false;
        }
        for (var entry : left) {
            if (right.get(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private static AEItemKey damageKeyAfter(AEItemKey initialKey, long uses) {
        var stack = initialKey.toStack();
        long damage = Math.addExact(stack.getDamageValue(), uses);
        if (damage > Integer.MAX_VALUE) {
            throw new ArithmeticException("Damage value overflow");
        }
        stack.setDamageValue((int) damage);
        return Objects.requireNonNull(AEItemKey.of(stack),
                "Could not create transitioned item key");
    }
}
