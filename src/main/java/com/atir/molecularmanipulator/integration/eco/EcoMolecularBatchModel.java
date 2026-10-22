package com.atir.molecularmanipulator.integration.eco;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import cn.dancingsnow.neoecoae.api.me.provider.ECOBatchDispatchContext;
import cn.dancingsnow.neoecoae.api.me.provider.ECOFastPathDispatchProvider;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECODurabilityBatchModel;
import cn.dancingsnow.neoecoae.crafting.execution.fastpath.ECOReusableStateModel;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.DamageGroup;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputMode;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableInputAdapters;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Our ownership model, exposed through ECO's public calculator protocol. In
 * particular, ECO's built-in durability model assumes disappearance at max
 * damage; molecular recipes may instead return a tool at max damage. Never
 * substitute that assumption for the actual IInput transitions.
 *
 * A validated window can be repeated for identical initial tools. Thus a large
 * stock costs at most 2048 distinct transition checks, rather than one check
 * per craft. Standard breaking tools use ECO's inventory-aware pool, including
 * stock with different initial damage and identical remaining components.
 */
final class EcoMolecularBatchModel implements ECOReusableStateModel {
    private record Slot(AEKey key, long amount, InputMode mode,
            long window, @Nullable AEItemKey windowRemainder) { }

    private final Slot[] slots;
    private final KeyCounter firstRemainders;
    private final boolean reusable;
    @Nullable
    private final ECODurabilityBatchModel nativeToolPool;

    private EcoMolecularBatchModel(Slot[] slots, KeyCounter firstRemainders,
            boolean reusable) {
        this.slots = slots;
        this.firstRemainders = firstRemainders;
        this.reusable = reusable;
        this.nativeToolPool = nativeToolPool(slots);
    }

    ECOReusableStateModel calculatorModel() {
        return nativeToolPool == null ? this : nativeToolPool;
    }

    /** Use ECO's inventory-aware pool only after proving its break convention. */
    @Nullable
    private static ECODurabilityBatchModel nativeToolPool(Slot[] slots) {
        boolean damage = false;
        var before = new ArrayList<net.minecraft.world.item.ItemStack>(1);
        var after = new ArrayList<net.minecraft.world.item.ItemStack>(1);
        for (var slot : slots) {
            if (slot.mode() == InputMode.INVARIANT_REUSABLE) {
                return null;
            }
            if (slot.mode() != InputMode.DETERMINISTIC_DAMAGE) {
                continue;
            }
            var stack = ((AEItemKey) slot.key()).toStack();
            before.add(stack);
            // Every stock candidate with the same components must fit in the
            // shared validation budget, including less damaged initial tools.
            if (stack.getMaxDamage() > MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS
                    || slot.window() != (long) stack.getMaxDamage() - stack.getDamageValue()
                    || slot.windowRemainder() != null) {
                return null;
            }
            damage = true;
            if (slot.window() == 1) {
                after.add(net.minecraft.world.item.ItemStack.EMPTY);
            } else {
                stack = stack.copy();
                stack.setDamageValue(stack.getDamageValue() + 1);
                after.add(stack);
            }
        }
        return damage ? ECODurabilityBatchModel.analyze(before, after).orElse(null) : null;
    }

    record Delivery(KeyCounter[] inputs, @Nullable MolecularReusableBatchPlan plan) { }

    @Nullable
    Delivery delivery(ECOBatchDispatchContext context, ECOFastPathDispatchProvider.Batch batch) {
        long crafts = batch.craftCount();
        if (nativeToolPool == null) {
            return new Delivery(inputs(crafts), reusablePlan(crafts));
        }
        var owned = counter(batch.inputTotal());
        var plans = new InputPlan[slots.length];
        int damageSlot = -1;
        for (int index = 0; index < slots.length; index++) {
            var slot = slots[index];
            if (slot.mode() == InputMode.DETERMINISTIC_DAMAGE) {
                damageSlot = index;
                continue;
            }
            long required = Math.multiplyExact(slot.amount(), crafts);
            if (owned.get(slot.key()) < required) {
                return null;
            }
            owned.remove(slot.key(), required);
            plans[index] = new InputPlan(slot.key(), slot.amount(), InputMode.CONSUMABLE, null);
        }
        owned.removeZeros();
        if (damageSlot < 0 || owned.isEmpty()) {
            return null;
        }
        var template = ((AEItemKey) slots[damageSlot].key()).toStack();
        template.setDamageValue(0);
        var keys = new ArrayList<AEItemKey>();
        for (var entry : owned) {
            if (!(entry.getKey() instanceof AEItemKey item) || entry.getLongValue() <= 0) {
                return null;
            }
            var normalized = item.toStack();
            normalized.setDamageValue(0);
            if (!net.minecraft.world.item.ItemStack.isSameItemSameComponents(template, normalized)) {
                return null;
            }
            keys.add(item);
        }
        // ECO 21.2.9 selects most damaged tools first. Reconstruct that exact
        // order so a cancellation checkpoint returns each actual tool state.
        keys.sort(Comparator.<AEItemKey>comparingInt(key -> key.toStack().getDamageValue())
                .reversed());
        long remaining = crafts;
        var groups = new ArrayList<DamageGroup>();
        for (var key : keys) {
            var analysis = MolecularReusableInputAdapters.analyze(context.pattern().getInputs()[damageSlot],
                    key, context.level(), MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS);
            long uses = analysis.safeCrafts();
            if (analysis.mode() != MolecularReusableInputAdapters.Mode.DETERMINISTIC_DAMAGE
                    || analysis.finalKey() != null
                    || uses != (long) key.toStack().getMaxDamage() - key.toStack().getDamageValue()
                    || uses <= 0) {
                return null;
            }
            long available = owned.get(key);
            long full = Math.min(available, remaining / uses);
            if (full > 0) {
                groups.add(new DamageGroup(key, full, uses, null));
                remaining -= Math.multiplyExact(full, uses);
                available -= full;
            }
            if (available > 0) {
                if (available != 1 || remaining <= 0 || remaining >= uses) {
                    return null;
                }
                var partial = key.toStack();
                partial.setDamageValue(Math.toIntExact(Math.addExact(partial.getDamageValue(), remaining)));
                groups.add(new DamageGroup(key, 1, remaining, AEItemKey.of(partial)));
                remaining = 0;
            }
        }
        if (remaining != 0 || groups.isEmpty()) {
            return null;
        }
        plans[damageSlot] = InputPlan.deterministicDamage(groups);
        var plan = new MolecularReusableBatchPlan(crafts, plans);
        var inputs = new KeyCounter[plans.length];
        for (int index = 0; index < plans.length; index++) {
            inputs[index] = new KeyCounter();
            plans[index].addProvidedInputs(inputs[index], crafts);
        }
        return new Delivery(inputs, plan);
    }

    @Nullable
    static EcoMolecularBatchModel analyze(ECOBatchDispatchContext context) {
        return analyze(context.pattern(), context.inputCounters(), context.level(),
                counter(context.containerItems()));
    }

    @Nullable
    static EcoMolecularBatchModel analyze(IPatternDetails pattern,
            KeyCounter[] first, Level level, KeyCounter expectedRemainders) {
        var patternInputs = pattern.getInputs();
        if (first.length == 0 || patternInputs.length != first.length) {
            return null;
        }
        var slots = new Slot[first.length];
        var actualRemainders = new KeyCounter();
        boolean reusable = false;
        boolean linearRemainder = false;
        int damageSlots = 0;
        for (int index = 0; index < first.length; index++) {
            var holder = first[index];
            if (holder == null || holder.size() != 1) {
                return null;
            }
            var entry = holder.getFirstEntry();
            if (!(entry.getKey() instanceof AEItemKey key)
                    || entry.getLongValue() <= 0) {
                return null;
            }
            long amount = entry.getLongValue();
            var input = patternInputs[index];
            // Multipliers describe occupied grid slots. Reusable inputs with
            // larger template units have a different remainder multiplicity.
            if (input == null || input.getMultiplier() != amount) {
                return null;
            }
            var analysis = MolecularReusableInputAdapters.analyze(input, key,
                    level, MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS);
            AEKey remainder = input.getRemainingKey(key);
            if (remainder != null) {
                add(actualRemainders, remainder, amount);
            }
            InputMode mode;
            long window = Long.MAX_VALUE;
            AEItemKey finalKey = null;
            switch (analysis.mode()) {
                case CONSUMABLE -> mode = InputMode.CONSUMABLE;
                case INVARIANT_REUSABLE -> {
                    mode = InputMode.INVARIANT_REUSABLE;
                    reusable = true;
                }
                case DETERMINISTIC_DAMAGE -> {
                    if (amount != 1 || ++damageSlots > 1
                            || analysis.safeCrafts() <= 0) {
                        return null;
                    }
                    mode = InputMode.DETERMINISTIC_DAMAGE;
                    window = analysis.safeCrafts();
                    finalKey = (AEItemKey) analysis.finalKey();
                    reusable = true;
                }
                case UNSUPPORTED -> {
                    // A consumed filled container returning another item is
                    // linear. Same-item state mutations remain unsupported.
                    if (remainder == null || !input.isValid(key, level)
                            || !(remainder instanceof AEItemKey returned)
                            || key.getItem() == returned.getItem()) {
                        return null;
                    }
                    mode = InputMode.CONSUMABLE;
                    linearRemainder = true;
                }
                default -> throw new IllegalStateException("Unknown molecular input mode");
            }
            slots[index] = new Slot(key, amount, mode, window, finalKey);
        }
        // Persisted reusable jobs currently represent tools/catalysts and
        // consumed ingredients. They do not represent independent byproducts.
        if (reusable && linearRemainder
                || !EcoMolecularRemainderContext.matches(expectedRemainders,
                        actualRemainders)) {
            return null;
        }
        return new EcoMolecularBatchModel(slots, actualRemainders, reusable);
    }

    boolean reusable() {
        return reusable;
    }

    KeyCounter[] inputs(long crafts) {
        var plans = inputPlans(crafts);
        var result = new KeyCounter[plans.length];
        for (int index = 0; index < plans.length; index++) {
            result[index] = new KeyCounter();
            plans[index].addProvidedInputs(result[index], crafts);
        }
        return result;
    }

    @Nullable
    MolecularReusableBatchPlan reusablePlan(long crafts) {
        return reusable && crafts > 1
                ? new MolecularReusableBatchPlan(crafts, inputPlans(crafts)) : null;
    }

    private InputPlan[] inputPlans(long crafts) {
        if (crafts <= 0) {
            throw new IllegalArgumentException("Non-positive molecular batch");
        }
        var result = new InputPlan[slots.length];
        for (int index = 0; index < slots.length; index++) {
            var slot = slots[index];
            result[index] = switch (slot.mode()) {
                case CONSUMABLE -> new InputPlan(slot.key(), slot.amount(),
                        InputMode.CONSUMABLE, null);
                case INVARIANT_REUSABLE -> new InputPlan(slot.key(), slot.amount(),
                        InputMode.INVARIANT_REUSABLE, slot.key());
                case DETERMINISTIC_DAMAGE -> {
                    var groups = new ArrayList<DamageGroup>(2);
                    long full = crafts / slot.window();
                    long partial = crafts % slot.window();
                    if (full > 0) {
                        groups.add(new DamageGroup((AEItemKey) slot.key(), full,
                                slot.window(), slot.windowRemainder()));
                    }
                    if (partial > 0) {
                        var stack = ((AEItemKey) slot.key()).toStack();
                        stack.setDamageValue(Math.toIntExact(Math.addExact(
                                stack.getDamageValue(), partial)));
                        groups.add(new DamageGroup((AEItemKey) slot.key(), 1,
                                partial, Objects.requireNonNull(AEItemKey.of(stack))));
                    }
                    yield InputPlan.deterministicDamage(groups);
                }
            };
        }
        return result;
    }

    @Override
    public long maxBatchSize() {
        return Long.MAX_VALUE;
    }

    @Override
    public List<GenericStack> batchInputs(List<GenericStack> ordinaryInputs, long crafts) {
        var result = new KeyCounter();
        for (var holder : inputs(crafts)) {
            for (var entry : holder) {
                add(result, entry.getKey(), entry.getLongValue());
            }
        }
        return stacks(result);
    }

    @Override
    public List<GenericStack> batchRemainders(List<GenericStack> ordinaryRemainders,
            long crafts) {
        var result = new KeyCounter();
        if (reusable) {
            for (var input : inputPlans(crafts)) {
                var slotRemainders = new KeyCounter();
                input.addCheckpointRemainders(slotRemainders, crafts);
                for (var entry : slotRemainders) {
                    add(result, entry.getKey(), entry.getLongValue());
                }
            }
        } else {
            for (var entry : firstRemainders) {
                add(result, entry.getKey(), Math.multiplyExact(entry.getLongValue(), crafts));
            }
        }
        return stacks(result);
    }

    @Override
    public boolean requiresSecondStepProof() {
        return false; // MolecularReusableInputAdapters proves every window state.
    }

    @Override
    public boolean sameTransition(ECOReusableStateModel other) {
        return other == this;
    }

    static KeyCounter counter(List<GenericStack> stacks) {
        var result = new KeyCounter();
        for (var stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                throw new IllegalArgumentException("Invalid ECO stack");
            }
            add(result, stack.what(), stack.amount());
        }
        return result;
    }

    static List<GenericStack> stacks(KeyCounter counter) {
        var result = new ArrayList<GenericStack>();
        for (var entry : counter) {
            if (entry.getLongValue() > 0) {
                result.add(new GenericStack(entry.getKey(), entry.getLongValue()));
            }
        }
        return List.copyOf(result);
    }

    private static void add(KeyCounter target, AEKey key, long amount) {
        Math.addExact(target.get(key), amount);
        target.add(key, amount);
    }
}
