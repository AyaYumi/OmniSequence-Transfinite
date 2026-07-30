package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputMode;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputPlan;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Persisted, provider-owned execution state for one reusable crafting batch.
 * Inputs move into this object at the provider's commit point.
 */
final class MolecularReusableBatchJob {
    private static final String CRAFTING_ID_TAG = "crafting_id";
    private static final String PATTERN_TAG = "pattern";
    private static final String TOTAL_CRAFTS_TAG = "total_crafts";
    private static final String COMPLETED_CRAFTS_TAG = "completed_crafts";
    private static final String INPUTS_TAG = "inputs";
    private static final String PRIMARY_OUTPUTS_TAG = "primary_outputs";
    private static final String MODE_TAG = "mode";
    private static final String INITIAL_KEY_TAG = "initial_key";
    private static final String CURRENT_KEY_TAG = "current_key";
    private static final String FINAL_KEY_TAG = "final_key";
    private static final String AMOUNT_PER_CRAFT_TAG = "amount_per_craft";
    private static final String REMAINING_AMOUNT_TAG = "remaining_amount";

    private final UUID craftingId;
    private final AEItemKey patternDefinition;
    private final long totalCrafts;
    private final InputState[] inputs;
    private final Object2LongOpenHashMap<AEKey> primaryPerCraft;
    private long completedCrafts;

    private MolecularReusableBatchJob(UUID craftingId, AEItemKey patternDefinition,
            long totalCrafts, long completedCrafts, InputState[] inputs,
            Object2LongOpenHashMap<AEKey> primaryPerCraft) {
        this.craftingId = craftingId;
        this.patternDefinition = patternDefinition;
        this.totalCrafts = totalCrafts;
        this.completedCrafts = completedCrafts;
        this.inputs = inputs;
        this.primaryPerCraft = primaryPerCraft;
    }

    static MolecularReusableBatchJob create(UUID craftingId,
            AEItemKey patternDefinition, MolecularReusableBatchPlan plan,
            KeyCounter[] providedInputs,
            Object2LongOpenHashMap<AEKey> primaryPerCraft) {
        if (craftingId == null || patternDefinition == null || plan == null
                || primaryPerCraft == null || primaryPerCraft.isEmpty()
                || !plan.matchesProvidedInputs(providedInputs)) {
            return null;
        }

        try {
            InputPlan[] plannedInputs = plan.inputs();
            var states = new InputState[plannedInputs.length];
            for (int index = 0; index < plannedInputs.length; index++) {
                var input = plannedInputs[index];
                states[index] = new InputState(input.mode(), input.initialKey(),
                        input.initialKey(), input.finalKey(), input.amountPerCraft(),
                        input.providedAmount(plan.craftCount()));
            }

            var primaryCopy = new Object2LongOpenHashMap<AEKey>();
            for (var entry : primaryPerCraft.object2LongEntrySet()) {
                if (entry.getKey() == null || entry.getLongValue() <= 0) {
                    return null;
                }
                primaryCopy.put(entry.getKey(), entry.getLongValue());
                Math.multiplyExact(entry.getLongValue(), plan.craftCount());
            }

            return new MolecularReusableBatchJob(craftingId, patternDefinition,
                    plan.craftCount(), 0, states, primaryCopy);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    UUID craftingId() {
        return craftingId;
    }

    long totalCrafts() {
        return totalCrafts;
    }

    long completedCrafts() {
        return completedCrafts;
    }

    boolean isComplete() {
        return completedCrafts >= totalCrafts;
    }

    long nextStep(long maximum) {
        if (maximum <= 0 || isComplete()) {
            return 0;
        }
        return Math.min(maximum, totalCrafts - completedCrafts);
    }

    Object2LongOpenHashMap<AEKey> primaryOutputsFor(long crafts) {
        var result = new Object2LongOpenHashMap<AEKey>();
        if (crafts <= 0) {
            return result;
        }
        for (var entry : primaryPerCraft.object2LongEntrySet()) {
            result.put(entry.getKey(), Math.multiplyExact(entry.getLongValue(), crafts));
        }
        return result;
    }

    Object2LongOpenHashMap<AEKey> projectedPrimaryOutputs() {
        return primaryOutputsFor(totalCrafts);
    }

    Object2LongOpenHashMap<AEKey> projectedFinalRemainders() {
        var result = new Object2LongOpenHashMap<AEKey>();
        for (var input : inputs) {
            if (input.mode == InputMode.CONSUMABLE || input.finalKey == null) {
                continue;
            }
            AEKey finalKey = input.mode == InputMode.INVARIANT_REUSABLE
                    ? input.initialKey
                    : damageKeyAfter(input.initialKey, totalCrafts);
            addChecked(result, finalKey, input.amountPerCraft);
        }
        return result;
    }

    void advance(long crafts) {
        long step = nextStep(crafts);
        if (step != crafts || step <= 0) {
            throw new IllegalArgumentException("Invalid reusable batch step");
        }
        long nextCompleted = Math.addExact(completedCrafts, step);

        for (var input : inputs) {
            if (input.mode == InputMode.CONSUMABLE) {
                long consumed = Math.multiplyExact(input.amountPerCraft, step);
                if (consumed > input.remainingAmount) {
                    throw new IllegalStateException("Reusable batch consumed too much input");
                }
                input.remainingAmount -= consumed;
            } else if (input.mode == InputMode.DETERMINISTIC_DAMAGE) {
                if (nextCompleted == totalCrafts && input.finalKey == null) {
                    input.currentKey = null;
                } else {
                    input.currentKey = damageKeyAfter(input.initialKey, nextCompleted);
                }
            }
        }
        completedCrafts = nextCompleted;
    }

    Object2LongOpenHashMap<AEKey> completedRemainders() {
        if (!isComplete()) {
            throw new IllegalStateException("Reusable batch is not complete");
        }
        var result = new Object2LongOpenHashMap<AEKey>();
        for (var input : inputs) {
            if (input.mode != InputMode.CONSUMABLE && input.currentKey != null) {
                addChecked(result, input.currentKey, input.amountPerCraft);
            }
        }
        return result;
    }

    Object2LongOpenHashMap<AEKey> cancellationRefunds() {
        var result = new Object2LongOpenHashMap<AEKey>();
        for (var input : inputs) {
            if (input.mode == InputMode.CONSUMABLE) {
                if (input.remainingAmount > 0) {
                    addChecked(result, input.initialKey, input.remainingAmount);
                }
            } else if (input.currentKey != null) {
                addChecked(result, input.currentKey, input.amountPerCraft);
            }
        }
        return result;
    }

    CompoundTag writeToTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putUUID(CRAFTING_ID_TAG, craftingId);
        tag.put(PATTERN_TAG, patternDefinition.toTag(registries));
        tag.putLong(TOTAL_CRAFTS_TAG, totalCrafts);
        tag.putLong(COMPLETED_CRAFTS_TAG, completedCrafts);

        var inputList = new ListTag();
        for (var input : inputs) {
            var inputTag = new CompoundTag();
            inputTag.putString(MODE_TAG, input.mode.name());
            inputTag.put(INITIAL_KEY_TAG, writeKey(registries, input.initialKey));
            if (input.currentKey != null) {
                inputTag.put(CURRENT_KEY_TAG, writeKey(registries, input.currentKey));
            }
            if (input.finalKey != null) {
                inputTag.put(FINAL_KEY_TAG, writeKey(registries, input.finalKey));
            }
            inputTag.putLong(AMOUNT_PER_CRAFT_TAG, input.amountPerCraft);
            inputTag.putLong(REMAINING_AMOUNT_TAG, input.remainingAmount);
            inputList.add(inputTag);
        }
        tag.put(INPUTS_TAG, inputList);
        tag.put(PRIMARY_OUTPUTS_TAG, writeMap(registries, primaryPerCraft));
        return tag;
    }

    @Nullable
    static MolecularReusableBatchJob readFromTag(CompoundTag tag,
            HolderLookup.Provider registries) {
        try {
            if (tag == null || !tag.hasUUID(CRAFTING_ID_TAG)
                    || !tag.contains(PATTERN_TAG, Tag.TAG_COMPOUND)) {
                return null;
            }
            UUID craftingId = tag.getUUID(CRAFTING_ID_TAG);
            AEItemKey definition = AEItemKey.fromTag(
                    registries, tag.getCompound(PATTERN_TAG));
            long totalCrafts = tag.getLong(TOTAL_CRAFTS_TAG);
            long completedCrafts = tag.getLong(COMPLETED_CRAFTS_TAG);
            if (definition == null || totalCrafts <= 1
                    || completedCrafts < 0 || completedCrafts >= totalCrafts) {
                return null;
            }

            var inputList = tag.getList(INPUTS_TAG, Tag.TAG_COMPOUND);
            if (inputList.isEmpty()) {
                return null;
            }
            var states = new InputState[inputList.size()];
            for (int index = 0; index < inputList.size(); index++) {
                var inputTag = inputList.getCompound(index);
                InputMode mode = InputMode.valueOf(inputTag.getString(MODE_TAG));
                AEKey initialKey = readKey(registries, inputTag, INITIAL_KEY_TAG);
                AEKey currentKey = inputTag.contains(CURRENT_KEY_TAG, Tag.TAG_COMPOUND)
                        ? readKey(registries, inputTag, CURRENT_KEY_TAG) : null;
                AEKey finalKey = inputTag.contains(FINAL_KEY_TAG, Tag.TAG_COMPOUND)
                        ? readKey(registries, inputTag, FINAL_KEY_TAG) : null;
                long amountPerCraft = inputTag.getLong(AMOUNT_PER_CRAFT_TAG);
                long remainingAmount = inputTag.getLong(REMAINING_AMOUNT_TAG);
                if (!isValidPersistedInput(mode, initialKey, currentKey,
                        finalKey, amountPerCraft, remainingAmount,
                        totalCrafts, completedCrafts)) {
                    return null;
                }
                states[index] = new InputState(mode, initialKey, currentKey,
                        finalKey, amountPerCraft, remainingAmount);
            }

            var primaryOutputs = readMap(registries, tag.getList(
                    PRIMARY_OUTPUTS_TAG, Tag.TAG_COMPOUND));
            if (primaryOutputs.isEmpty()) {
                return null;
            }
            for (var entry : primaryOutputs.object2LongEntrySet()) {
                Math.multiplyExact(entry.getLongValue(),
                        totalCrafts - completedCrafts);
            }
            return new MolecularReusableBatchJob(craftingId, definition,
                    totalCrafts, completedCrafts, states, primaryOutputs);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean isValidPersistedInput(InputMode mode,
            @Nullable AEKey initialKey, @Nullable AEKey currentKey,
            @Nullable AEKey finalKey, long amountPerCraft,
            long remainingAmount, long totalCrafts, long completedCrafts) {
        if (mode == null || initialKey == null || amountPerCraft <= 0
                || remainingAmount < 0) {
            return false;
        }
        try {
            return switch (mode) {
                case CONSUMABLE -> currentKey != null
                        && currentKey.equals(initialKey)
                        && finalKey == null
                        && remainingAmount == Math.multiplyExact(
                                amountPerCraft,
                                totalCrafts - completedCrafts);
                case INVARIANT_REUSABLE -> currentKey != null
                        && currentKey.equals(initialKey)
                        && finalKey != null
                        && finalKey.equals(initialKey)
                        && remainingAmount == amountPerCraft;
                case DETERMINISTIC_DAMAGE -> currentKey != null
                        && currentKey.equals(
                                damageKeyAfter(initialKey, completedCrafts))
                        && (finalKey == null || finalKey.equals(
                                damageKeyAfter(initialKey, totalCrafts)))
                        && remainingAmount == amountPerCraft;
            };
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static AEKey damageKeyAfter(AEKey initialKey, long crafts) {
        var stack = ((AEItemKey) initialKey).toStack();
        long damage = Math.addExact(stack.getDamageValue(), crafts);
        if (damage > Integer.MAX_VALUE) {
            throw new ArithmeticException("Damage value overflow");
        }
        stack.setDamageValue((int) damage);
        var key = AEItemKey.of(stack);
        if (key == null) {
            throw new IllegalStateException("Could not advance damageable input");
        }
        return key;
    }

    private static CompoundTag writeKey(HolderLookup.Provider registries,
            AEKey key) {
        return GenericStack.writeTag(registries, new GenericStack(key, 1));
    }

    @Nullable
    private static AEKey readKey(HolderLookup.Provider registries,
            CompoundTag parent, String name) {
        var stack = GenericStack.readTag(registries, parent.getCompound(name));
        return stack == null ? null : stack.what();
    }

    private static ListTag writeMap(HolderLookup.Provider registries,
            Object2LongOpenHashMap<AEKey> values) {
        var list = new ListTag();
        for (var entry : values.object2LongEntrySet()) {
            if (entry.getKey() != null && entry.getLongValue() > 0) {
                list.add(GenericStack.writeTag(registries,
                        new GenericStack(entry.getKey(), entry.getLongValue())));
            }
        }
        return list;
    }

    private static Object2LongOpenHashMap<AEKey> readMap(
            HolderLookup.Provider registries, ListTag list) {
        var result = new Object2LongOpenHashMap<AEKey>();
        for (var entry : list) {
            var stack = GenericStack.readTag(registries, (CompoundTag) entry);
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                throw new IllegalArgumentException("Invalid reusable batch stack");
            }
            addChecked(result, stack.what(), stack.amount());
        }
        return result;
    }

    private static void addChecked(Object2LongOpenHashMap<AEKey> target,
            AEKey key, long amount) {
        target.put(key, Math.addExact(target.getLong(key), amount));
    }

    private static final class InputState {
        private final InputMode mode;
        private final AEKey initialKey;
        @Nullable
        private AEKey currentKey;
        @Nullable
        private final AEKey finalKey;
        private final long amountPerCraft;
        private long remainingAmount;

        private InputState(InputMode mode, AEKey initialKey,
                @Nullable AEKey currentKey, @Nullable AEKey finalKey,
                long amountPerCraft, long remainingAmount) {
            this.mode = mode;
            this.initialKey = initialKey;
            this.currentKey = currentKey;
            this.finalKey = finalKey;
            this.amountPerCraft = amountPerCraft;
            this.remainingAmount = remainingAmount;
        }
    }
}
