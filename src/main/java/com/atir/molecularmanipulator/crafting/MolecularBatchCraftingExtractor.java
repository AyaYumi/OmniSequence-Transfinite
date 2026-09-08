package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.DamageGroup;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class MolecularBatchCraftingExtractor {
    private static final double POWER_EPSILON = 0.01;
    /*
     * Every selected damage state can become a distinct remainder key. Leave
     * headroom in the machines' 256-type buffers for the crafted result and
     * the other eight crafting-grid slots, otherwise a valid extraction could
     * be rejected forever by the provider's final capacity check.
     */
    private static final int MAX_TOOL_POOL_CANDIDATES = 240;

    private MolecularBatchCraftingExtractor() {
    }

    /**
     * Expands an input set already extracted by AE2. Returning {@code null} leaves
     * the original one-craft extraction untouched so the caller can dispatch it normally.
     */
    public static BatchExtraction expandFromFirst(IPatternDetails patternDetails,
            ICraftingInventory inventory, IEnergyService energyService, Level level,
            KeyCounter[] firstInputs, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, long maxCrafts,
            boolean allowReusableInputs) {
        if (maxCrafts <= 1 || level == null) {
            return null;
        }

        /*
         * A substitutable crafting input cannot be expanded by multiplying the key
         * AE2 happened to select for the first craft. That key may be exhausted while
         * another valid ingredient is still abundant. Ask AE2 to select the additional
         * ingredients again against a read-only inventory overlay, then reserve exactly
         * that selection once the largest viable batch has been found.
         */
        var substitutionExtraction = expandConsumableSubstitution(
                patternDetails, inventory, energyService, level, firstInputs,
                expectedOutputs, expectedContainerItems, maxCrafts);
        if (substitutionExtraction != null) {
            return substitutionExtraction;
        }

        if (allowReusableInputs) {
            var toolPoolExtraction = expandDeterministicToolPool(
                    patternDetails, inventory, energyService, level,
                    firstInputs, expectedOutputs, expectedContainerItems,
                    maxCrafts);
            if (toolPoolExtraction != null) {
                return toolPoolExtraction;
            }
        }

        var plan = ExpansionPlan.fromFirst(patternDetails, level, firstInputs,
                expectedOutputs, expectedContainerItems, maxCrafts,
                allowReusableInputs);
        if (plan == null) {
            return null;
        }

        try {
            long craftCount = plan.limitByInventory(inventory, maxCrafts);
            if (craftCount <= 1) {
                return null;
            }

            craftCount = limitByEnergy(energyService, plan, craftCount);
            if (craftCount <= 1) {
                return null;
            }

            return plan.extractAdditional(inventory, expectedOutputs,
                    expectedContainerItems, firstInputs, craftCount);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    /**
     * Builds one atomic batch from a pool of deterministic +1-damage tools.
     * The first tool was already extracted by AE2; additional tools and all
     * additional consumables are reserved atomically only after the largest
     * powered batch has been selected.
     */
    @Nullable
    private static BatchExtraction expandDeterministicToolPool(
            IPatternDetails patternDetails, ICraftingInventory inventory,
            IEnergyService energyService, Level level,
            KeyCounter[] firstInputs, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, long maxCrafts) {
        if (patternDetails == null || inventory == null || level == null
                || firstInputs == null || expectedOutputs == null
                || expectedContainerItems == null || maxCrafts <= 1) {
            return null;
        }

        var patternInputs = patternDetails.getInputs();
        if (patternInputs == null || patternInputs.length != firstInputs.length) {
            return null;
        }

        var plannedInputs = new ToolPoolInput[firstInputs.length];
        var calculatedFirstRemainders = new KeyCounter();
        int damageSlot = -1;
        try {
            for (int index = 0; index < firstInputs.length; index++) {
                var holder = firstInputs[index];
                if (holder == null || holder.size() != 1) {
                    return null;
                }
                var entry = holder.getFirstEntry();
                if (entry == null || entry.getKey() == null
                        || entry.getLongValue() <= 0) {
                    return null;
                }

                var analysis = MolecularReusableInputAdapters.analyze(
                        patternInputs[index], entry.getKey(), level,
                        maxCrafts);
                if (!analysis.isSupported()) {
                    return null;
                }
                if (analysis.mode()
                        == MolecularReusableInputAdapters.Mode.DETERMINISTIC_DAMAGE) {
                    if (damageSlot >= 0 || entry.getLongValue() != 1
                            || !(entry.getKey() instanceof AEItemKey)) {
                        return null;
                    }
                    damageSlot = index;
                }

                plannedInputs[index] = new ToolPoolInput(
                        entry.getKey(), entry.getLongValue(), analysis);
                if (analysis.isReusable()) {
                    AEKey firstRemainder = patternInputs[index]
                            .getRemainingKey(entry.getKey());
                    if (firstRemainder != null) {
                        calculatedFirstRemainders.add(firstRemainder,
                                entry.getLongValue());
                    }
                }
            }
            if (damageSlot < 0
                    || !countersEqualWithoutMutation(
                            calculatedFirstRemainders,
                            expectedContainerItems)) {
                return null;
            }

            var firstExpectedOutputs = copyCounter(expectedOutputs);
            var firstExpectedContainerItems = copyCounter(
                    expectedContainerItems);
            if (!hasOnlyPositiveEntries(firstExpectedOutputs)) {
                return null;
            }

            var firstTool = (AEItemKey) plannedInputs[damageSlot].key();
            var candidates = collectToolCandidates(
                    patternInputs[damageSlot], firstTool,
                    plannedInputs[damageSlot].analysis(), inventory,
                    level, maxCrafts);
            if (candidates.isEmpty()
                    || !candidates.get(0).key().equals(firstTool)) {
                return null;
            }

            long craftLimit = Math.min(maxCrafts,
                    toolCapacity(candidates));
            var consumableAmounts = new Object2LongOpenHashMap<AEKey>();
            for (var input : plannedInputs) {
                if (input.analysis().mode()
                        != MolecularReusableInputAdapters.Mode.CONSUMABLE) {
                    continue;
                }
                consumableAmounts.put(input.key(), Math.addExact(
                        consumableAmounts.getLong(input.key()),
                        input.amountPerCraft()));
            }
            for (var entry : consumableAmounts.object2LongEntrySet()) {
                long available = inventory.extract(entry.getKey(),
                        Long.MAX_VALUE, Actionable.SIMULATE);
                long totalAvailable = saturatedAdd(
                        available, entry.getLongValue());
                craftLimit = Math.min(craftLimit,
                        totalAvailable / entry.getLongValue());
            }
            if (craftLimit <= 1) {
                return null;
            }

            long craftCount = limitToolPoolByEnergy(
                    energyService, plannedInputs, damageSlot,
                    candidates, craftLimit);
            if (craftCount <= 1) {
                return null;
            }

            var selection = selectTools(candidates, craftCount);
            if (selection == null) {
                return null;
            }
            var combinedInputs = combinedToolPoolInputs(
                    plannedInputs, damageSlot, selection, craftCount);
            var additionalInputs = additionalInputs(
                    combinedInputs, firstInputs);
            if (additionalInputs == null
                    || !reserveAdditionalInputs(
                            inventory, additionalInputs)) {
                return null;
            }

            try {
                var inputPlans = new MolecularReusableBatchPlan.InputPlan[
                        plannedInputs.length];
                for (int index = 0; index < plannedInputs.length; index++) {
                    var input = plannedInputs[index];
                    inputPlans[index] = switch (input.analysis().mode()) {
                        case CONSUMABLE -> new MolecularReusableBatchPlan.InputPlan(
                                input.key(), input.amountPerCraft(),
                                MolecularReusableBatchPlan.InputMode.CONSUMABLE,
                                null);
                        case INVARIANT_REUSABLE ->
                            new MolecularReusableBatchPlan.InputPlan(
                                    input.key(), input.amountPerCraft(),
                                    MolecularReusableBatchPlan.InputMode.INVARIANT_REUSABLE,
                                    input.key());
                        case DETERMINISTIC_DAMAGE ->
                            MolecularReusableBatchPlan.InputPlan
                                    .deterministicDamage(selection.groups());
                        case UNSUPPORTED -> throw new IllegalStateException(
                                "Unsupported tool-pool input escaped validation");
                    };
                }
                var reusablePlan = new MolecularReusableBatchPlan(
                        craftCount, inputPlans);
                if (!reusablePlan.matchesProvidedInputs(combinedInputs)) {
                    throw new IllegalStateException(
                            "Tool-pool inputs do not match the dispatch plan");
                }

                expectedOutputs.reset();
                expectedOutputs.addAll(scaleCounter(
                        firstExpectedOutputs, craftCount));
                expectedContainerItems.reset();
                expectedContainerItems.addAll(
                        reusablePlan.expectedRemainders(craftCount));
                return new BatchExtraction(combinedInputs, firstInputs,
                        additionalInputs, firstExpectedOutputs,
                        firstExpectedContainerItems, craftCount,
                        reusablePlan);
            } catch (RuntimeException exception) {
                CraftingCpuHelper.reinjectPatternInputs(
                        inventory, additionalInputs);
                expectedOutputs.reset();
                expectedOutputs.addAll(firstExpectedOutputs);
                expectedContainerItems.reset();
                expectedContainerItems.addAll(
                        firstExpectedContainerItems);
                return null;
            }
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static List<ToolCandidate> collectToolCandidates(
            IPatternDetails.IInput input, AEItemKey firstTool,
            MolecularReusableInputAdapters.Analysis firstAnalysis,
            ICraftingInventory inventory, Level level,
            long requestedCrafts) {
        var keys = new LinkedHashSet<AEKey>();
        keys.add(firstTool);
        try {
            // Prefer the actual stored damage states of the already-selected
            // tool before a very large tag fills the candidate budget.
            for (var fuzzy : inventory.findFuzzyTemplates(firstTool)) {
                if (fuzzy != null) {
                    keys.add(fuzzy);
                    if (keys.size() >= MAX_TOOL_POOL_CANDIDATES) {
                        break;
                    }
                }
            }
            var possibleInputs = keys.size() < MAX_TOOL_POOL_CANDIDATES
                    ? input.getPossibleInputs()
                    : null;
            if (possibleInputs != null) {
                for (var possible : possibleInputs) {
                    if (possible != null && possible.what() != null) {
                        keys.add(possible.what());
                        if (keys.size() >= MAX_TOOL_POOL_CANDIDATES) {
                            break;
                        }
                    }
                }
            }
            var seeds = List.copyOf(keys);
            for (var seed : seeds) {
                for (var fuzzy : inventory.findFuzzyTemplates(seed)) {
                    if (fuzzy != null) {
                        keys.add(fuzzy);
                        if (keys.size() >= MAX_TOOL_POOL_CANDIDATES) {
                            break;
                        }
                    }
                }
                if (keys.size() >= MAX_TOOL_POOL_CANDIDATES) {
                    break;
                }
            }
        } catch (RuntimeException ignored) {
            // The exact first tool can still form a valid conservative batch.
        }

        var result = new ArrayList<ToolCandidate>();
        for (var key : keys) {
            if (!(key instanceof AEItemKey itemKey)
                    || !input.isValid(itemKey, level)) {
                continue;
            }
            var candidate = itemKey.equals(firstTool)
                    ? candidateFromAnalysis(itemKey, firstAnalysis)
                    : probeToolCandidate(input, itemKey, level,
                            requestedCrafts);
            if (candidate == null) {
                continue;
            }
            long available = inventory.extract(itemKey,
                    Long.MAX_VALUE, Actionable.SIMULATE);
            if (itemKey.equals(firstTool)) {
                available = saturatedAdd(available, 1);
            }
            if (available <= 0) {
                continue;
            }
            result.add(new ToolCandidate(itemKey, available,
                    candidate.safeCrafts(), candidate.finalKey()));
        }
        result.sort(Comparator
                .comparing((ToolCandidate candidate) ->
                        !candidate.key().equals(firstTool))
                .thenComparing(Comparator.comparingLong(
                        ToolCandidate::safeCrafts).reversed()));
        return result;
    }

    /**
     * Probes only the first recipe transition for additional damage states.
     * The provider later exhaustively validates every distinct state selected
     * for the batch under one global transition budget. This prevents candidate
     * discovery from multiplying a 2048-step scan by hundreds of stored tools.
     */
    @Nullable
    private static ToolCandidate probeToolCandidate(
            IPatternDetails.IInput input, AEItemKey key, Level level,
            long requestedCrafts) {
        var firstStep = MolecularReusableInputAdapters.analyze(
                input, key, level, 1);
        var firstCandidate = candidateFromAnalysis(key, firstStep);
        if (firstCandidate == null || firstCandidate.finalKey() == null) {
            return firstCandidate;
        }

        ItemStack stack = key.toStack();
        long physicalUses = (long) stack.getMaxDamage()
                - stack.getDamageValue();
        if (physicalUses <= 0) {
            return null;
        }
        long safeCrafts = Math.min(
                Math.min(requestedCrafts, physicalUses),
                MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS);
        AEItemKey finalKey = safeCrafts == physicalUses
                ? null
                : damageKeyAfter(key, safeCrafts);
        return new ToolCandidate(key, 0, safeCrafts, finalKey);
    }

    @Nullable
    private static ToolCandidate candidateFromAnalysis(AEItemKey key,
            MolecularReusableInputAdapters.Analysis analysis) {
        if (analysis == null
                || analysis.mode()
                        != MolecularReusableInputAdapters.Mode.DETERMINISTIC_DAMAGE
                || analysis.safeCrafts() <= 0
                || analysis.finalKey() != null
                        && !(analysis.finalKey() instanceof AEItemKey)) {
            return null;
        }
        return new ToolCandidate(key, 0, analysis.safeCrafts(),
                (AEItemKey) analysis.finalKey());
    }

    private static long toolCapacity(List<ToolCandidate> candidates) {
        long result = 0;
        for (var candidate : candidates) {
            result = saturatedAdd(result, saturatedMultiply(
                    candidate.available(), candidate.safeCrafts()));
        }
        return result;
    }

    private static long limitToolPoolByEnergy(
            IEnergyService energyService, ToolPoolInput[] inputs,
            int damageSlot, List<ToolCandidate> candidates,
            long craftLimit) {
        if (hasToolPoolEnergy(energyService, inputs, damageSlot,
                candidates, craftLimit)) {
            return craftLimit;
        }
        long low = 2;
        long high = craftLimit - 1;
        long best = 1;
        while (low <= high) {
            long candidate = low + (high - low) / 2;
            if (hasToolPoolEnergy(energyService, inputs, damageSlot,
                    candidates, candidate)) {
                best = candidate;
                low = candidate + 1;
            } else {
                high = candidate - 1;
            }
        }
        return best;
    }

    private static boolean hasToolPoolEnergy(
            IEnergyService energyService, ToolPoolInput[] inputs,
            int damageSlot, List<ToolCandidate> candidates,
            long craftCount) {
        var selection = selectTools(candidates, craftCount);
        if (selection == null) {
            return false;
        }
        try {
            var combined = combinedToolPoolInputs(
                    inputs, damageSlot, selection, craftCount);
            return hasEnergyFor(energyService,
                    CraftingCpuHelper.calculatePatternPower(combined));
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Nullable
    private static ToolSelection selectTools(
            List<ToolCandidate> candidates, long craftCount) {
        if (craftCount <= 0 || candidates.isEmpty()) {
            return null;
        }
        long remaining = craftCount;
        var groups = new ArrayList<DamageGroup>();
        var selected = new KeyCounter();
        try {
            for (var candidate : candidates) {
                if (remaining == 0) {
                    break;
                }
                long fullCount = Math.min(candidate.available(),
                        remaining / candidate.safeCrafts());
                if (fullCount > 0) {
                    groups.add(new DamageGroup(candidate.key(), fullCount,
                            candidate.safeCrafts(), candidate.finalKey()));
                    selected.add(candidate.key(), fullCount);
                    remaining -= Math.multiplyExact(
                            fullCount, candidate.safeCrafts());
                }
                if (remaining > 0 && fullCount < candidate.available()) {
                    AEItemKey partialFinal = damageKeyAfter(
                            candidate.key(), remaining);
                    groups.add(new DamageGroup(candidate.key(), 1,
                            remaining, partialFinal));
                    selected.add(candidate.key(), 1);
                    remaining = 0;
                }
            }
            return remaining == 0 && !groups.isEmpty()
                    ? new ToolSelection(List.copyOf(groups), selected)
                    : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static KeyCounter[] combinedToolPoolInputs(
            ToolPoolInput[] inputs, int damageSlot,
            ToolSelection selection, long craftCount) {
        var result = new KeyCounter[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            var holder = result[index] = new KeyCounter();
            if (index == damageSlot) {
                holder.addAll(selection.selectedInputs());
                continue;
            }
            var input = inputs[index];
            long amount = input.analysis().mode()
                    == MolecularReusableInputAdapters.Mode.CONSUMABLE
                            ? Math.multiplyExact(
                                    input.amountPerCraft(), craftCount)
                            : input.amountPerCraft();
            holder.add(input.key(), amount);
        }
        return result;
    }

    @Nullable
    private static KeyCounter[] additionalInputs(
            KeyCounter[] combinedInputs, KeyCounter[] firstInputs) {
        if (combinedInputs.length != firstInputs.length) {
            return null;
        }
        var result = new KeyCounter[combinedInputs.length];
        try {
            for (int index = 0; index < combinedInputs.length; index++) {
                var additional = result[index] = new KeyCounter();
                for (var entry : combinedInputs[index]) {
                    long amount = Math.subtractExact(entry.getLongValue(),
                            firstInputs[index].get(entry.getKey()));
                    if (amount < 0) {
                        return null;
                    }
                    if (amount > 0) {
                        additional.add(entry.getKey(), amount);
                    }
                }
                for (var first : firstInputs[index]) {
                    if (first.getLongValue() > 0
                            && combinedInputs[index].get(first.getKey())
                                    < first.getLongValue()) {
                        return null;
                    }
                }
            }
            return result;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean reserveAdditionalInputs(
            ICraftingInventory inventory, KeyCounter[] additionalInputs) {
        var required = new Object2LongOpenHashMap<AEKey>();
        var extracted = new KeyCounter();
        try {
            for (var holder : additionalInputs) {
                for (var entry : holder) {
                    required.put(entry.getKey(), Math.addExact(
                            required.getLong(entry.getKey()),
                            entry.getLongValue()));
                }
            }
            for (var entry : required.object2LongEntrySet()) {
                if (inventory.extract(entry.getKey(), entry.getLongValue(),
                        Actionable.SIMULATE) != entry.getLongValue()) {
                    return false;
                }
            }
            for (var entry : required.object2LongEntrySet()) {
                long amount = inventory.extract(entry.getKey(),
                        entry.getLongValue(), Actionable.MODULATE);
                if (amount > 0) {
                    extracted.add(entry.getKey(), amount);
                }
                if (amount != entry.getLongValue()) {
                    CraftingCpuHelper.reinjectPatternInputs(
                            inventory, new KeyCounter[] { extracted });
                    return false;
                }
            }
            return true;
        } catch (RuntimeException exception) {
            CraftingCpuHelper.reinjectPatternInputs(
                    inventory, new KeyCounter[] { extracted });
            return false;
        }
    }

    private static AEItemKey damageKeyAfter(AEItemKey initialKey,
            long uses) {
        var stack = initialKey.toStack();
        long damage = Math.addExact(stack.getDamageValue(), uses);
        if (damage > Integer.MAX_VALUE) {
            throw new ArithmeticException("Damage value overflow");
        }
        stack.setDamageValue((int) damage);
        var result = AEItemKey.of(stack);
        if (result == null) {
            throw new IllegalStateException(
                    "Could not advance deterministic tool");
        }
        return result;
    }

    private static long saturatedAdd(long left, long right) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException("Negative saturated add");
        }
        return left > Long.MAX_VALUE - right
                ? Long.MAX_VALUE
                : left + right;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left < 0 || right < 0) {
            throw new IllegalArgumentException("Negative saturated multiply");
        }
        return left != 0 && right > Long.MAX_VALUE / left
                ? Long.MAX_VALUE
                : left * right;
    }

    private record ToolPoolInput(AEKey key, long amountPerCraft,
            MolecularReusableInputAdapters.Analysis analysis) {
    }

    private record ToolCandidate(AEItemKey key, long available,
            long safeCrafts, @Nullable AEItemKey finalKey) {
    }

    private record ToolSelection(List<DamageGroup> groups,
            KeyCounter selectedInputs) {
    }

    @Nullable
    private static BatchExtraction expandConsumableSubstitution(
            IPatternDetails patternDetails, ICraftingInventory inventory,
            IEnergyService energyService, Level level, KeyCounter[] firstInputs,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            long maxCrafts) {
        try {
            if (!hasSubstitutionPossibility(patternDetails)
                    || !isEmpty(expectedContainerItems)) {
                return null;
            }

            var patternInputs = patternDetails.getInputs();
            if (firstInputs == null || patternInputs == null
                    || firstInputs.length != patternInputs.length
                    || !allActualInputsConsumable(
                            patternInputs, firstInputs, level, maxCrafts)) {
                return null;
            }

            var firstExpectedOutputs = copyCounter(expectedOutputs);
            if (!hasOnlyPositiveEntries(firstExpectedOutputs)) {
                return null;
            }

            long low = 1;
            long high = maxCrafts - 1;
            ConsumableSubstitutionProbe best = null;
            while (low <= high) {
                long additionalCrafts = low + (high - low) / 2;
                var candidate = probeConsumableSubstitution(
                        patternDetails, inventory, energyService, level,
                        firstInputs, firstExpectedOutputs, additionalCrafts);
                if (candidate != null) {
                    best = candidate;
                    low = additionalCrafts + 1;
                } else {
                    high = additionalCrafts - 1;
                }
            }
            if (best == null) {
                return null;
            }

            return reserveConsumableSubstitution(
                    inventory, firstInputs, expectedOutputs,
                    expectedContainerItems, firstExpectedOutputs, best);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean hasSubstitutionPossibility(
            IPatternDetails patternDetails) {
        if (patternDetails == null || patternDetails.getInputs() == null) {
            return false;
        }
        for (var input : patternDetails.getInputs()) {
            if (input == null || input.getPossibleInputs() == null) {
                return false;
            }
            if (input.getPossibleInputs().length > 1) {
                return true;
            }
        }
        return false;
    }

    private static boolean allActualInputsConsumable(
            IPatternDetails.IInput[] patternInputs, KeyCounter[] actualInputs,
            Level level, long maxCrafts) {
        for (int index = 0; index < actualInputs.length; index++) {
            var holder = actualInputs[index];
            if (holder == null || holder.isEmpty()) {
                return false;
            }
            for (var entry : holder) {
                if (entry.getKey() == null || entry.getLongValue() <= 0) {
                    return false;
                }
                // This probe only distinguishes consumables from reusable
                // inputs. One transition is sufficient and avoids scanning a
                // high-durability tool before the dedicated tool-pool path.
                var analysis = MolecularReusableInputAdapters.analyze(
                        patternInputs[index], entry.getKey(), level, 1);
                if (analysis.mode()
                        != MolecularReusableInputAdapters.Mode.CONSUMABLE) {
                    return false;
                }
            }
        }
        return true;
    }

    @Nullable
    private static ConsumableSubstitutionProbe probeConsumableSubstitution(
            IPatternDetails patternDetails, ICraftingInventory inventory,
            IEnergyService energyService, Level level, KeyCounter[] firstInputs,
            KeyCounter firstExpectedOutputs, long additionalCrafts) {
        if (additionalCrafts <= 0) {
            return null;
        }

        var additionalExpectedOutputs = new KeyCounter();
        var additionalRemainders = new KeyCounter();
        var overlay = new ReadOnlyCraftingInventory(inventory);
        KeyCounter[] additionalInputs;
        try {
            additionalInputs = CraftingCpuHelper.extractPatternInputs(
                    new MolecularScaledPattern(patternDetails, additionalCrafts),
                    overlay, level, additionalExpectedOutputs,
                    additionalRemainders);
        } catch (RuntimeException exception) {
            return null;
        }
        if (additionalInputs == null || !isEmpty(additionalRemainders)) {
            return null;
        }

        var patternInputs = patternDetails.getInputs();
        long craftCount = Math.addExact(additionalCrafts, 1);
        if (additionalInputs.length != patternInputs.length
                || !allActualInputsConsumable(
                        patternInputs, additionalInputs, level, craftCount)) {
            return null;
        }

        var expectedAdditionalOutputs =
                scaleCounter(firstExpectedOutputs, additionalCrafts);
        if (!countersEqualWithoutMutation(
                expectedAdditionalOutputs, additionalExpectedOutputs)) {
            return null;
        }

        var combinedInputs = combineInputs(firstInputs, additionalInputs);
        double requestedPower =
                CraftingCpuHelper.calculatePatternPower(combinedInputs);
        if (!hasEnergyFor(energyService, requestedPower)) {
            return null;
        }

        return new ConsumableSubstitutionProbe(
                additionalInputs, combinedInputs,
                scaleCounter(firstExpectedOutputs, craftCount), craftCount);
    }

    @Nullable
    private static BatchExtraction reserveConsumableSubstitution(
            ICraftingInventory inventory, KeyCounter[] firstInputs,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
            KeyCounter firstExpectedOutputs,
            ConsumableSubstitutionProbe probe) {
        var requiredPerKey = new Object2LongOpenHashMap<AEKey>();
        var actuallyExtracted = new KeyCounter[] { new KeyCounter() };
        var firstExpectedContainerItems = copyCounter(expectedContainerItems);
        try {
            for (var holder : probe.additionalInputs()) {
                for (var entry : holder) {
                    long total = Math.addExact(
                            requiredPerKey.getLong(entry.getKey()),
                            entry.getLongValue());
                    requiredPerKey.put(entry.getKey(), total);
                }
            }

            for (var entry : requiredPerKey.object2LongEntrySet()) {
                long available = inventory.extract(
                        entry.getKey(), entry.getLongValue(),
                        Actionable.SIMULATE);
                if (available != entry.getLongValue()) {
                    return null;
                }
            }

            for (var entry : requiredPerKey.object2LongEntrySet()) {
                long extracted = inventory.extract(
                        entry.getKey(), entry.getLongValue(),
                        Actionable.MODULATE);
                if (extracted > 0) {
                    actuallyExtracted[0].add(entry.getKey(), extracted);
                }
                if (extracted != entry.getLongValue()) {
                    CraftingCpuHelper.reinjectPatternInputs(
                            inventory, actuallyExtracted);
                    return null;
                }
            }

            expectedOutputs.reset();
            expectedOutputs.addAll(probe.scaledExpectedOutputs());
            expectedContainerItems.reset();
            return new BatchExtraction(
                    probe.combinedInputs(), firstInputs,
                    probe.additionalInputs(), firstExpectedOutputs,
                    firstExpectedContainerItems, probe.craftCount(), null);
        } catch (RuntimeException exception) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, actuallyExtracted);
            expectedOutputs.reset();
            expectedOutputs.addAll(firstExpectedOutputs);
            expectedContainerItems.reset();
            expectedContainerItems.addAll(firstExpectedContainerItems);
            return null;
        }
    }

    private static KeyCounter[] combineInputs(
            KeyCounter[] firstInputs, KeyCounter[] additionalInputs) {
        if (firstInputs.length != additionalInputs.length) {
            throw new IllegalArgumentException("Input holder count changed");
        }
        var combined = new KeyCounter[firstInputs.length];
        for (int index = 0; index < firstInputs.length; index++) {
            var holder = combined[index] = new KeyCounter();
            holder.addAll(firstInputs[index]);
            holder.addAll(additionalInputs[index]);
            if (!hasOnlyPositiveEntries(holder)) {
                throw new IllegalArgumentException("Invalid combined inputs");
            }
        }
        return combined;
    }

    private static KeyCounter scaleCounter(
            KeyCounter source, long multiplier) {
        if (source == null || multiplier <= 0) {
            throw new IllegalArgumentException("Invalid counter multiplier");
        }
        var scaled = new KeyCounter();
        for (var entry : source) {
            if (entry.getKey() == null || entry.getLongValue() <= 0) {
                throw new IllegalArgumentException("Invalid counter entry");
            }
            scaled.add(entry.getKey(),
                    Math.multiplyExact(entry.getLongValue(), multiplier));
        }
        return scaled;
    }

    private static KeyCounter copyCounter(KeyCounter source) {
        if (source == null) {
            throw new IllegalArgumentException("Missing counter");
        }
        var copy = new KeyCounter();
        copy.addAll(source);
        return copy;
    }

    private static boolean hasOnlyPositiveEntries(KeyCounter counter) {
        if (counter == null || counter.isEmpty()) {
            return false;
        }
        for (var entry : counter) {
            if (entry.getKey() == null || entry.getLongValue() <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(KeyCounter counter) {
        if (counter == null) {
            return false;
        }
        for (var entry : counter) {
            if (entry.getLongValue() != 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean countersEqualWithoutMutation(
            KeyCounter left, KeyCounter right) {
        if (left == null || right == null) {
            return false;
        }
        int leftSize = 0;
        for (var entry : left) {
            if (entry.getLongValue() != 0) {
                leftSize++;
                if (entry.getKey() == null
                        || right.get(entry.getKey()) != entry.getLongValue()) {
                    return false;
                }
            }
        }
        int rightSize = 0;
        for (var entry : right) {
            if (entry.getLongValue() != 0) {
                rightSize++;
            }
        }
        return leftSize == rightSize;
    }

    private static boolean hasEnergyFor(
            IEnergyService energyService, double requestedPower) {
        if (energyService == null || !Double.isFinite(requestedPower)
                || requestedPower <= 0) {
            return false;
        }
        double availablePower = energyService.extractAEPower(
                requestedPower, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return availablePower >= requestedPower - POWER_EPSILON;
    }

    private record ConsumableSubstitutionProbe(
            KeyCounter[] additionalInputs, KeyCounter[] combinedInputs,
            KeyCounter scaledExpectedOutputs, long craftCount) {
    }

    /**
     * Gives AE2 a mutable view of a stable inventory snapshot without forwarding
     * any mutation to the real crafting inventory. It is recreated for every
     * binary-search probe.
     */
    private static final class ReadOnlyCraftingInventory
            implements ICraftingInventory {
        private final ICraftingInventory delegate;
        private final Object2LongOpenHashMap<AEKey> baseAmounts =
                new Object2LongOpenHashMap<>();
        private final Object2LongOpenHashMap<AEKey> adjustments =
                new Object2LongOpenHashMap<>();

        private ReadOnlyCraftingInventory(ICraftingInventory delegate) {
            this.delegate = delegate;
        }

        @Override
        public void insert(AEKey key, long amount, Actionable mode) {
            if (key == null || amount <= 0 || mode != Actionable.MODULATE) {
                return;
            }
            adjustments.put(key, Math.addExact(
                    adjustments.getLong(key), amount));
        }

        @Override
        public long extract(AEKey key, long amount, Actionable mode) {
            if (key == null || amount <= 0) {
                return 0;
            }

            long available = available(key);
            long extracted = Math.min(amount, available);
            if (mode == Actionable.MODULATE && extracted > 0) {
                adjustments.put(key, Math.subtractExact(
                        adjustments.getLong(key), extracted));
            }
            return extracted;
        }

        @Override
        public Iterable<AEKey> findFuzzyTemplates(AEKey key) {
            return delegate.findFuzzyTemplates(key);
        }

        private long available(AEKey key) {
            long baseAmount;
            if (baseAmounts.containsKey(key)) {
                baseAmount = baseAmounts.getLong(key);
            } else {
                baseAmount = delegate.extract(
                        key, Long.MAX_VALUE, Actionable.SIMULATE);
                baseAmounts.put(key, baseAmount);
            }
            long adjustment = adjustments.getLong(key);
            if (adjustment >= 0) {
                return adjustment > Long.MAX_VALUE - baseAmount
                        ? Long.MAX_VALUE
                        : baseAmount + adjustment;
            }
            return Math.max(0, baseAmount + adjustment);
        }
    }

    private static long limitByEnergy(IEnergyService energyService, ExpansionPlan plan,
            long craftCount) {
        if (hasEnergyFor(energyService, plan, craftCount)) {
            return craftCount;
        }

        long low = 2;
        long high = craftCount - 1;
        long poweredCrafts = 1;
        while (low <= high) {
            long candidate = low + (high - low) / 2;
            if (hasEnergyFor(energyService, plan, candidate)) {
                poweredCrafts = candidate;
                low = candidate + 1;
            } else {
                high = candidate - 1;
            }
        }
        return poweredCrafts;
    }

    private static boolean hasEnergyFor(IEnergyService energyService, ExpansionPlan plan,
            long craftCount) {
        double requestedPower = plan.calculatePatternPower(craftCount);
        if (!Double.isFinite(requestedPower) || requestedPower <= 0) {
            return false;
        }
        double availablePower = energyService.extractAEPower(requestedPower, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        return availablePower >= requestedPower - POWER_EPSILON;
    }

    private record PlannedInput(AEKey key, long amountPerCraft,
            MolecularReusableInputAdapters.Analysis reusableAnalysis) {
        boolean reusable() {
            return reusableAnalysis.isReusable();
        }
    }

    private record PlannedOutput(AEKey key, long amountPerCraft) {
    }

    private record ExpansionPlan(PlannedInput[] inputs,
            Object2LongOpenHashMap<AEKey> amountsPerKey, PlannedOutput[] outputs,
            long representableCrafts, long reusableCraftLimit,
            boolean hasReusableInputs) {
        static ExpansionPlan fromFirst(IPatternDetails patternDetails, Level level,
                KeyCounter[] firstInputs, KeyCounter expectedOutputs,
                KeyCounter expectedContainerItems, long maxCrafts,
                boolean allowReusableInputs) {
            if (patternDetails == null || firstInputs == null || expectedOutputs == null
                    || expectedContainerItems == null) {
                return null;
            }

            var patternInputs = patternDetails.getInputs();
            if (patternInputs == null || firstInputs.length != patternInputs.length) {
                return null;
            }

            var plannedInputs = new PlannedInput[firstInputs.length];
            var amountsPerKey = new Object2LongOpenHashMap<AEKey>();
            var calculatedFirstRemainders = new KeyCounter();
            long representableCrafts = Long.MAX_VALUE;
            long reusableCraftLimit = Long.MAX_VALUE;
            boolean hasReusableInputs = false;
            try {
                for (int index = 0; index < firstInputs.length; index++) {
                    var input = firstInputs[index];
                    if (input == null || input.size() != 1) {
                        return null;
                    }
                    var entry = input.getFirstEntry();
                    if (entry == null || entry.getKey() == null || entry.getLongValue() <= 0) {
                        return null;
                    }

                    long amount = entry.getLongValue();
                    long analysisCrafts = allowReusableInputs ? maxCrafts : 1;
                    var analysis = MolecularReusableInputAdapters.analyze(
                            patternInputs[index], entry.getKey(), level,
                            analysisCrafts);
                    if (!analysis.isSupported()
                            || analysis.isReusable() && !allowReusableInputs) {
                        return null;
                    }
                    plannedInputs[index] = new PlannedInput(
                            entry.getKey(), amount, analysis);

                    if (analysis.isReusable()) {
                        hasReusableInputs = true;
                        reusableCraftLimit = Math.min(
                                reusableCraftLimit, analysis.safeCrafts());
                        AEKey firstRemainder = keyAfter(
                                plannedInputs[index], 1, analysis.safeCrafts());
                        if (firstRemainder != null) {
                            calculatedFirstRemainders.add(firstRemainder, amount);
                        }
                    } else {
                        long totalAmount = Math.addExact(
                                amountsPerKey.getLong(entry.getKey()), amount);
                        amountsPerKey.put(entry.getKey(), totalAmount);
                        representableCrafts = Math.min(
                                representableCrafts, Long.MAX_VALUE / amount);
                    }
                }
                if (!countersEqual(calculatedFirstRemainders,
                        expectedContainerItems)) {
                    return null;
                }
                // The same AE key may occur in more than one input holder. The provider
                // aggregates those holders before validating a scaled push, so clamp by
                // the aggregate amount as well as by each individual slot.
                for (var entry : amountsPerKey.object2LongEntrySet()) {
                    representableCrafts = Math.min(representableCrafts,
                            Long.MAX_VALUE / entry.getLongValue());
                }

                var plannedOutputs = new ArrayList<PlannedOutput>(expectedOutputs.size());
                for (var entry : expectedOutputs) {
                    if (entry.getKey() == null || entry.getLongValue() <= 0) {
                        return null;
                    }
                    plannedOutputs.add(new PlannedOutput(entry.getKey(), entry.getLongValue()));
                    representableCrafts = Math.min(representableCrafts,
                            Long.MAX_VALUE / entry.getLongValue());
                }
                if (plannedOutputs.isEmpty()) {
                    return null;
                }

                return new ExpansionPlan(plannedInputs, amountsPerKey,
                        plannedOutputs.toArray(PlannedOutput[]::new),
                        representableCrafts, reusableCraftLimit, hasReusableInputs);
            } catch (ArithmeticException exception) {
                return null;
            }
        }

        double calculatePatternPower(long craftCount) {
            if (craftCount <= 0 || craftCount > representableCrafts
                    || craftCount > reusableCraftLimit) {
                throw new IllegalArgumentException("Invalid craft count");
            }

            var craftingContainer = new KeyCounter[inputs.length];
            for (int index = 0; index < inputs.length; index++) {
                var input = inputs[index];
                long amount = input.reusable()
                        ? input.amountPerCraft()
                        : Math.multiplyExact(input.amountPerCraft(), craftCount);
                var counter = craftingContainer[index] = new KeyCounter();
                counter.add(input.key(), amount);
            }
            return CraftingCpuHelper.calculatePatternPower(craftingContainer);
        }

        long limitByInventory(ICraftingInventory inventory, long maxCrafts) {
            long craftCount = Math.min(
                    Math.min(maxCrafts, representableCrafts),
                    reusableCraftLimit);
            if (craftCount <= 1) {
                return 1;
            }

            for (var entry : amountsPerKey.object2LongEntrySet()) {
                long available = inventory.extract(entry.getKey(), Long.MAX_VALUE, Actionable.SIMULATE);
                long additionalCrafts = available / entry.getLongValue();
                if (additionalCrafts < craftCount - 1) {
                    craftCount = additionalCrafts + 1;
                    if (craftCount <= 1) {
                        return 1;
                    }
                }
            }
            return craftCount;
        }

        BatchExtraction extractAdditional(ICraftingInventory inventory,
                KeyCounter expectedOutputs, KeyCounter expectedContainerItems,
                KeyCounter[] firstInputs, long craftCount) {
            long additionalCrafts = craftCount - 1;
            var extraInputs = new KeyCounter[inputs.length];
            var combinedInputs = new KeyCounter[inputs.length];
            var firstExpectedOutputs = new KeyCounter();
            firstExpectedOutputs.addAll(expectedOutputs);
            var firstExpectedContainerItems = new KeyCounter();
            firstExpectedContainerItems.addAll(expectedContainerItems);

            try {
                for (int index = 0; index < inputs.length; index++) {
                    var input = inputs[index];
                    long extraAmount = input.reusable()
                            ? 0
                            : Math.multiplyExact(
                                    input.amountPerCraft(), additionalCrafts);
                    long combinedAmount = input.reusable()
                            ? input.amountPerCraft()
                            : Math.multiplyExact(
                                    input.amountPerCraft(), craftCount);

                    var combined = combinedInputs[index] = new KeyCounter();
                    combined.add(input.key(), combinedAmount);

                    var extra = extraInputs[index] = new KeyCounter();
                    if (extraAmount == 0) {
                        continue;
                    }
                    long simulated = inventory.extract(input.key(), extraAmount, Actionable.SIMULATE);
                    if (simulated != extraAmount) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, extraInputs);
                        return null;
                    }
                    long extracted = inventory.extract(input.key(), extraAmount, Actionable.MODULATE);
                    if (extracted > 0) {
                        extra.add(input.key(), extracted);
                    }
                    if (extracted != extraAmount) {
                        CraftingCpuHelper.reinjectPatternInputs(inventory, extraInputs);
                        return null;
                    }
                }

                var scaledExpectedOutputs = new KeyCounter();
                for (var output : outputs) {
                    scaledExpectedOutputs.add(output.key(),
                            Math.multiplyExact(output.amountPerCraft(), craftCount));
                }
                expectedOutputs.reset();
                expectedOutputs.addAll(scaledExpectedOutputs);

                var finalExpectedContainerItems = new KeyCounter();
                var reusablePlans = new MolecularReusableBatchPlan.InputPlan[inputs.length];
                for (int index = 0; index < inputs.length; index++) {
                    var input = inputs[index];
                    var mode = switch (input.reusableAnalysis().mode()) {
                        case CONSUMABLE ->
                            MolecularReusableBatchPlan.InputMode.CONSUMABLE;
                        case INVARIANT_REUSABLE ->
                            MolecularReusableBatchPlan.InputMode.INVARIANT_REUSABLE;
                        case DETERMINISTIC_DAMAGE ->
                            MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE;
                        case UNSUPPORTED -> throw new IllegalStateException(
                                "Unsupported reusable input escaped validation");
                    };
                    AEKey finalKey = keyAfter(input, craftCount, craftCount);
                    reusablePlans[index] = new MolecularReusableBatchPlan.InputPlan(
                            input.key(), input.amountPerCraft(), mode, finalKey);
                    if (input.reusable() && finalKey != null) {
                        finalExpectedContainerItems.add(
                                finalKey, input.amountPerCraft());
                    }
                }
                expectedContainerItems.reset();
                expectedContainerItems.addAll(finalExpectedContainerItems);

                MolecularReusableBatchPlan reusablePlan = hasReusableInputs
                        ? new MolecularReusableBatchPlan(craftCount,
                                reusablePlans)
                        : null;
                return new BatchExtraction(combinedInputs, firstInputs, extraInputs,
                        firstExpectedOutputs, firstExpectedContainerItems,
                        craftCount, reusablePlan);
            } catch (RuntimeException exception) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, extraInputs);
                expectedOutputs.reset();
                expectedOutputs.addAll(firstExpectedOutputs);
                expectedContainerItems.reset();
                expectedContainerItems.addAll(firstExpectedContainerItems);
                return null;
            }
        }

        @Nullable
        private static AEKey keyAfter(PlannedInput input, long crafts,
                long totalCrafts) {
            var analysis = input.reusableAnalysis();
            if (!analysis.isReusable()) {
                return null;
            }
            if (analysis.mode()
                    == MolecularReusableInputAdapters.Mode.INVARIANT_REUSABLE
                    || crafts == 0) {
                return input.key();
            }
            if (crafts == totalCrafts && analysis.finalKey() == null
                    && crafts == analysis.safeCrafts()) {
                return null;
            }

            var stack = ((appeng.api.stacks.AEItemKey) input.key()).toStack();
            long damage = Math.addExact(stack.getDamageValue(), crafts);
            if (damage > Integer.MAX_VALUE) {
                throw new ArithmeticException("Damage value overflow");
            }
            stack.setDamageValue((int) damage);
            return appeng.api.stacks.AEItemKey.of(stack);
        }

        private static boolean countersEqual(KeyCounter left,
                KeyCounter right) {
            left.removeZeros();
            right.removeZeros();
            if (left.size() != right.size()) {
                return false;
            }
            for (var entry : left) {
                if (entry.getKey() == null || entry.getLongValue() <= 0
                        || right.get(entry.getKey()) != entry.getLongValue()) {
                    return false;
                }
            }
            return true;
        }
    }

    public record BatchExtraction(KeyCounter[] inputs, KeyCounter[] firstInputs,
            KeyCounter[] additionalInputs, KeyCounter firstExpectedOutputs,
            KeyCounter firstExpectedContainerItems, long craftCount,
            @Nullable MolecularReusableBatchPlan reusablePlan) {
        /**
         * Restores the already-valid one-craft extraction when a later runtime wrapper
         * cannot be constructed.
         */
        public void rollbackAdditional(ICraftingInventory inventory,
                KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
            CraftingCpuHelper.reinjectPatternInputs(inventory, additionalInputs);
            expectedOutputs.reset();
            expectedOutputs.addAll(firstExpectedOutputs);
            expectedContainerItems.reset();
            expectedContainerItems.addAll(firstExpectedContainerItems);
        }
    }
}
