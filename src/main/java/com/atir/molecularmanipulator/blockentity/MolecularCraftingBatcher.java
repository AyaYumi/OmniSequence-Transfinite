package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.menu.AutoCraftingMenu;
import com.atir.molecularmanipulator.crafting.MolecularReusableInputAdapters;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

final class MolecularCraftingBatcher {
    private static final int CRAFTING_GRID_SIZE = 9;
    private static final int MAX_CACHED_PATTERNS = 2048;
    private static final int MAX_REUSABLE_VALIDATION_STATES =
            (int) MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS;

    private final TransientCraftingContainer craftingGrid =
            new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    private final Object2LongOpenHashMap<AEKey> outputAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> primaryOutputAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> remainderOutputAmounts = new Object2LongOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IPatternDetails, CraftPlan> planCache =
            new Reference2ObjectOpenHashMap<>();

    private KeyCounter[] simulatedInputs = new KeyCounter[0];
    private CraftPlan preparedPlan;
    private ItemStack craftedOutput = ItemStack.EMPTY;
    private long craftCount;
    private boolean craftingGridPrepared;

    boolean prepare(IPatternDetails patternDetails, KeyCounter[] inputs, Level level, long maxCrafts) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)) {
            return false;
        }

        var cachedPlan = planCache.get(patternDetails);
        if (cachedPlan != null) {
            craftCount = cachedPlan.getCraftCount(inputs, maxCrafts);
            if (craftCount > 0) {
                preparedPlan = cachedPlan;
                craftingGridPrepared = false;
                return prepareOutputs(cachedPlan);
            }
        }

        prepareSimulationInputs(inputs);
        clearCraftingGrid();
        pattern.fillCraftingGrid(simulatedInputs, craftingGrid::setItem);

        craftCount = calculateCraftCount(inputs, maxCrafts);
        if (craftCount <= 0) {
            return false;
        }

        var plan = createPlan(pattern, inputs, level);
        if (plan == null) {
            return false;
        }
        if (planCache.size() >= MAX_CACHED_PATTERNS) {
            planCache.clear();
        }
        planCache.put(patternDetails, plan);
        preparedPlan = plan;
        craftingGridPrepared = true;

        return prepareOutputs(plan);
    }

    /**
     * Prepares an explicitly expanded AE2 batch from the actual keys selected
     * by extraction. Substitution-enabled patterns may spread an aggregate
     * holder across several keys, so the aggregate cannot be interpreted
     * through the pattern-identity cache or assumed to be one selection scaled
     * by {@code selectedCraftCount}.
     */
    boolean prepareSelected(IPatternDetails patternDetails, KeyCounter[] inputs,
            Level level, long maxCrafts, KeyCounter[] firstInputs,
            long selectedCraftCount) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)
                || inputs == null || firstInputs == null
                || inputs.length != firstInputs.length
                || selectedCraftCount <= 1
                || selectedCraftCount > maxCrafts
                || !containsSelection(inputs, firstInputs)) {
            return false;
        }

        var remainingInputs = copyInputs(inputs);
        var expectedPrimaryPerCraft =
                expectedPrimaryOutputs(patternDetails);
        if (expectedPrimaryPerCraft == null) {
            return false;
        }
        var selectedPrimaryOutputs = new Object2LongOpenHashMap<AEKey>();
        var selectedRemainderOutputs = new Object2LongOpenHashMap<AEKey>();
        ItemStack selectedCraftedOutput = ItemStack.EMPTY;
        ItemStack[] selectedGrid = null;
        long completedCrafts = 0;
        int groupsRemaining;
        try {
            groupsRemaining = Math.addExact(
                    Math.multiplyExact(countPositiveEntries(remainingInputs), 2),
                    1);
        } catch (ArithmeticException exception) {
            return false;
        }

        try {
            while (completedCrafts < selectedCraftCount
                    && groupsRemaining-- > 0) {
                var before = copyInputs(remainingInputs);
                clearCraftingGrid();
                pattern.fillCraftingGrid(
                        remainingInputs, craftingGrid::setItem);

                long craftsRemaining = selectedCraftCount - completedCrafts;
                var consumption = measureConsumption(
                        before, remainingInputs, craftsRemaining);
                if (consumption == null) {
                    return failSelectedPreparation();
                }

                var craftingInput =
                        craftingGrid.asPositionedCraftInput().input();
                ItemStack output = pattern.assemble(craftingInput, level);
                if (output.isEmpty()) {
                    return failSelectedPreparation();
                }
                var crafted = output.copy();
                crafted.onCraftedBySystem(level);
                var actualPrimaryPerCraft =
                        new Object2LongOpenHashMap<AEKey>();
                if (!addOutput(actualPrimaryPerCraft, crafted)
                        || !mapsEqual(expectedPrimaryPerCraft,
                                actualPrimaryPerCraft)) {
                    return failSelectedPreparation();
                }
                if (!addScaledOutput(selectedPrimaryOutputs, crafted,
                        consumption.repeats())) {
                    return failSelectedPreparation();
                }
                for (var remainder : pattern.getRemainingItems(craftingInput)) {
                    // A non-reusable expanded context is admitted only when AE2's
                    // actual-key analysis reported no crafting remainder. Do not
                    // invent untracked byproducts if a contextual recipe disagrees.
                    if (!remainder.isEmpty()) {
                        return failSelectedPreparation();
                    }
                }

                if (selectedGrid == null) {
                    selectedCraftedOutput = crafted;
                    selectedGrid = copyCraftingGrid();
                }
                if (!removeRepeatedConsumption(
                        remainingInputs, consumption)) {
                    return failSelectedPreparation();
                }
                completedCrafts = Math.addExact(
                        completedCrafts, consumption.repeats());
            }
        } catch (RuntimeException exception) {
            return failSelectedPreparation();
        }

        if (completedCrafts != selectedCraftCount
                || !allInputsEmpty(remainingInputs)
                || selectedGrid == null
                || selectedPrimaryOutputs.isEmpty()) {
            return failSelectedPreparation();
        }

        clearCraftingGrid();
        for (int slot = 0; slot < selectedGrid.length; slot++) {
            craftingGrid.setItem(slot, selectedGrid[slot]);
        }
        preparedPlan = null;
        craftingGridPrepared = true;
        craftCount = selectedCraftCount;
        craftedOutput = selectedCraftedOutput;
        primaryOutputAmounts.clear();
        primaryOutputAmounts.putAll(selectedPrimaryOutputs);
        remainderOutputAmounts.clear();
        remainderOutputAmounts.putAll(selectedRemainderOutputs);
        outputAmounts.clear();
        try {
            mergeOutputs(primaryOutputAmounts, outputAmounts);
            mergeOutputs(remainderOutputAmounts, outputAmounts);
        } catch (ArithmeticException exception) {
            return failSelectedPreparation();
        }
        return !outputAmounts.isEmpty();
    }

    private static Object2LongOpenHashMap<AEKey> expectedPrimaryOutputs(
            IPatternDetails patternDetails) {
        var expected = new Object2LongOpenHashMap<AEKey>();
        try {
            var outputs = patternDetails.getOutputs();
            if (outputs == null || outputs.isEmpty()) {
                return null;
            }
            for (var output : outputs) {
                if (output == null || output.what() == null
                        || output.amount() <= 0) {
                    return null;
                }
                expected.put(output.what(), Math.addExact(
                        expected.getLong(output.what()), output.amount()));
            }
            return expected;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    MolecularReusableBatchJob prepareReusable(IPatternDetails patternDetails,
            KeyCounter[] inputs, Level level, UUID craftingId,
            MolecularReusableBatchPlan reusablePlan) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)
                || craftingId == null || reusablePlan == null
                || !reusablePlan.matchesProvidedInputs(inputs)) {
            return null;
        }

        try {
            var firstInputs = createOneCraftInputs(reusablePlan, 0);
            if (firstInputs == null || !fillCraftingGrid(pattern, firstInputs)) {
                return null;
            }

            var firstCraftingInput = craftingGrid.asPositionedCraftInput().input();
            ItemStack output = pattern.assemble(firstCraftingInput, level);
            if (output.isEmpty()) {
                return null;
            }
            var crafted = output.copy();
            crafted.onCraftedBySystem(level);
            var primaryPerCraft = new Object2LongOpenHashMap<AEKey>();
            var expectedPrimaryPerCraft = expectedPrimaryOutputs(
                    patternDetails);
            if (!addOutput(primaryPerCraft, crafted)
                    || expectedPrimaryPerCraft == null
                    || !mapsEqual(expectedPrimaryPerCraft,
                            primaryPerCraft)) {
                return null;
            }
            if (!remainingItemsMatch(pattern,
                    reusablePlan.expectedCraftRemainders(0))) {
                return null;
            }

            var firstGrid = new ItemStack[CRAFTING_GRID_SIZE];
            for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
                firstGrid[slot] = craftingGrid.getItem(slot).copy();
            }

            if (hasTransitioningInput(reusablePlan)
                    && !validateDamagePool(pattern, reusablePlan, level,
                            primaryPerCraft)) {
                return null;
            }

            clearCraftingGrid();
            for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
                craftingGrid.setItem(slot, firstGrid[slot]);
            }
            preparedPlan = null;
            craftingGridPrepared = true;
            craftCount = reusablePlan.craftCount();
            craftedOutput = crafted;
            outputAmounts.clear();
            primaryOutputAmounts.clear();
            remainderOutputAmounts.clear();

            return MolecularReusableBatchJob.create(craftingId,
                    patternDetails.getDefinition(), reusablePlan, inputs,
                    primaryPerCraft);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static boolean containsSelection(
            KeyCounter[] aggregateInputs, KeyCounter[] selectedInputs) {
        if (aggregateInputs.length != selectedInputs.length) {
            return false;
        }
        for (int index = 0; index < aggregateInputs.length; index++) {
            var aggregate = aggregateInputs[index];
            var selected = selectedInputs[index];
            if (aggregate == null || selected == null) {
                return false;
            }

            boolean hasAggregate = false;
            for (var entry : aggregate) {
                if (entry.getKey() == null || entry.getLongValue() < 0) {
                    return false;
                }
                hasAggregate |= entry.getLongValue() > 0;
            }
            boolean hasSelected = false;
            for (var entry : selected) {
                long selectedAmount = entry.getLongValue();
                if (entry.getKey() == null || selectedAmount < 0
                        || selectedAmount > aggregate.get(entry.getKey())) {
                    return false;
                }
                hasSelected |= selectedAmount > 0;
            }
            if (!hasAggregate || !hasSelected) {
                return false;
            }
        }
        return true;
    }

    private static KeyCounter[] copyInputs(KeyCounter[] inputs) {
        var copy = new KeyCounter[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            var holder = copy[index] = new KeyCounter();
            for (var entry : inputs[index]) {
                if (entry.getLongValue() > 0) {
                    holder.add(entry.getKey(), entry.getLongValue());
                }
            }
        }
        return copy;
    }

    private static int countPositiveEntries(KeyCounter[] inputs) {
        int result = 0;
        for (var holder : inputs) {
            for (var entry : holder) {
                if (entry.getLongValue() > 0) {
                    result = Math.incrementExact(result);
                }
            }
        }
        return result;
    }

    private static SelectionConsumption measureConsumption(
            KeyCounter[] before, KeyCounter[] after, long maxRepeats) {
        if (before.length != after.length || maxRepeats <= 0) {
            return null;
        }

        var consumedInputs = new KeyCounter[before.length];
        long repeats = maxRepeats;
        for (int index = 0; index < before.length; index++) {
            var beforeHolder = before[index];
            var afterHolder = after[index];
            var consumedHolder = consumedInputs[index] = new KeyCounter();
            boolean consumedAny = false;

            for (var entry : afterHolder) {
                long afterAmount = entry.getLongValue();
                if (entry.getKey() == null || afterAmount < 0
                        || afterAmount > beforeHolder.get(entry.getKey())) {
                    return null;
                }
            }
            for (var entry : beforeHolder) {
                AEKey key = entry.getKey();
                long beforeAmount = entry.getLongValue();
                long afterAmount = afterHolder.get(key);
                if (key == null || beforeAmount <= 0
                        || afterAmount < 0 || afterAmount > beforeAmount) {
                    return null;
                }
                long consumed = beforeAmount - afterAmount;
                if (consumed <= 0) {
                    continue;
                }
                consumedAny = true;
                consumedHolder.add(key, consumed);
                repeats = Math.min(repeats, beforeAmount / consumed);
            }
            if (!consumedAny) {
                return null;
            }
            afterHolder.removeZeros();
        }
        return repeats > 0
                ? new SelectionConsumption(consumedInputs, repeats)
                : null;
    }

    private static boolean removeRepeatedConsumption(
            KeyCounter[] remainingInputs, SelectionConsumption consumption) {
        long additionalRepeats = consumption.repeats() - 1;
        if (additionalRepeats <= 0) {
            return true;
        }
        for (int index = 0; index < remainingInputs.length; index++) {
            var remaining = remainingInputs[index];
            for (var entry : consumption.inputs()[index]) {
                long additional = Math.multiplyExact(
                        entry.getLongValue(), additionalRepeats);
                if (additional < 0
                        || remaining.get(entry.getKey()) < additional) {
                    return false;
                }
                remaining.remove(entry.getKey(), additional);
            }
            remaining.removeZeros();
        }
        return true;
    }

    private static boolean allInputsEmpty(KeyCounter[] inputs) {
        for (var holder : inputs) {
            holder.removeZeros();
            if (!holder.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private ItemStack[] copyCraftingGrid() {
        var copy = new ItemStack[CRAFTING_GRID_SIZE];
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
            copy[slot] = craftingGrid.getItem(slot).copy();
        }
        return copy;
    }

    private static boolean addScaledOutput(
            Object2LongOpenHashMap<AEKey> outputs, ItemStack stack,
            long repeats) {
        if (stack.isEmpty()) {
            return true;
        }
        var key = AEItemKey.of(stack);
        if (key == null || repeats <= 0) {
            return false;
        }
        try {
            long amount = Math.multiplyExact((long) stack.getCount(), repeats);
            outputs.put(key, Math.addExact(outputs.getLong(key), amount));
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private boolean failSelectedPreparation() {
        preparedPlan = null;
        craftingGridPrepared = false;
        craftedOutput = ItemStack.EMPTY;
        craftCount = 0;
        outputAmounts.clear();
        primaryOutputAmounts.clear();
        remainderOutputAmounts.clear();
        clearCraftingGrid();
        return false;
    }

    private boolean prepareOutputs(CraftPlan plan) {
        outputAmounts.clear();
        primaryOutputAmounts.clear();
        remainderOutputAmounts.clear();
        try {
            scaleOutputs(plan.primaryOutputs, primaryOutputAmounts);
            scaleOutputs(plan.remainderOutputs, remainderOutputAmounts);
            mergeOutputs(primaryOutputAmounts, outputAmounts);
            mergeOutputs(remainderOutputAmounts, outputAmounts);
        } catch (ArithmeticException exception) {
            outputAmounts.clear();
            primaryOutputAmounts.clear();
            remainderOutputAmounts.clear();
            return false;
        }

        craftedOutput = plan.craftedOutput;
        return !outputAmounts.isEmpty();
    }

    private void scaleOutputs(Object2LongOpenHashMap<AEKey> source, Object2LongOpenHashMap<AEKey> target) {
        for (var entry : source.object2LongEntrySet()) {
            target.put(entry.getKey(), Math.multiplyExact(entry.getLongValue(), craftCount));
        }
    }

    private static void mergeOutputs(Object2LongOpenHashMap<AEKey> source,
            Object2LongOpenHashMap<AEKey> target) {
        for (var entry : source.object2LongEntrySet()) {
            target.put(entry.getKey(), Math.addExact(target.getLong(entry.getKey()), entry.getLongValue()));
        }
    }

    Object2LongOpenHashMap<AEKey> getOutputAmounts() {
        return outputAmounts;
    }

    Object2LongOpenHashMap<AEKey> getPrimaryOutputAmounts() {
        return primaryOutputAmounts;
    }

    Object2LongOpenHashMap<AEKey> getRemainderOutputAmounts() {
        return remainderOutputAmounts;
    }

    ItemStack getCraftedOutput() {
        return craftedOutput;
    }

    TransientCraftingContainer getCraftingGrid() {
        if (!craftingGridPrepared && preparedPlan != null) {
            preparedPlan.loadCraftingGrid(craftingGrid);
            craftingGridPrepared = true;
        }
        return craftingGrid;
    }

    long getCraftCount() {
        return craftCount;
    }

    void consumeInputs(KeyCounter[] inputs) {
        for (var input : inputs) {
            input.clear();
        }
    }

    private KeyCounter[] createOneCraftInputs(
            MolecularReusableBatchPlan plan, long completedCrafts) {
        var plannedInputs = plan.inputs();
        var result = new KeyCounter[plannedInputs.length];
        for (int index = 0; index < plannedInputs.length; index++) {
            var input = plannedInputs[index];
            AEKey key = input.mode()
                    == MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE
                            ? input.keyForCraft(completedCrafts)
                            : input.initialKey();
            if (key == null) {
                return null;
            }
            var holder = result[index] = new KeyCounter();
            holder.add(key, input.amountPerCraft());
        }
        return result;
    }

    private boolean fillCraftingGrid(IMolecularAssemblerSupportedPattern pattern,
            KeyCounter[] oneCraftInputs) {
        clearCraftingGrid();
        pattern.fillCraftingGrid(oneCraftInputs, craftingGrid::setItem);
        for (var input : oneCraftInputs) {
            input.removeZeros();
            if (!input.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean remainingItemsMatch(
            IMolecularAssemblerSupportedPattern pattern,
            KeyCounter expectedRemainders) {
        var actual = new KeyCounter();
        var craftingInput = craftingGrid.asPositionedCraftInput().input();
        for (var remainder : pattern.getRemainingItems(craftingInput)) {
            if (remainder.isEmpty()) {
                continue;
            }
            var key = AEItemKey.of(remainder);
            if (key == null) {
                return false;
            }
            actual.add(key, remainder.getCount());
        }
        return countersEqual(actual, expectedRemainders);
    }

    private static boolean hasTransitioningInput(
            MolecularReusableBatchPlan plan) {
        for (var input : plan.inputs()) {
            if (input.mode()
                    == MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE) {
                return true;
            }
        }
        return false;
    }

    /**
     * Validates every distinct damage state once, regardless of how many tools
     * with that same initial key are present in the pool. The validation budget
     * applies to the complete pool, not to each tool or group. Multiple
     * transitioning slots are rejected by the plan before reaching this method.
     */
    private boolean validateDamagePool(
            IMolecularAssemblerSupportedPattern pattern,
            MolecularReusableBatchPlan plan, Level level,
            Object2LongOpenHashMap<AEKey> primaryPerCraft) {
        var longestGroups = new HashMap<AEItemKey,
                MolecularReusableBatchPlan.DamageGroup>();
        for (var input : plan.inputs()) {
            if (input.mode()
                    != MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE) {
                continue;
            }
            for (var group : input.damageGroups()) {
                var previous = longestGroups.get(group.initialKey());
                if (previous == null
                        || group.usesPerTool() > previous.usesPerTool()) {
                    longestGroups.put(group.initialKey(), group);
                } else if (group.usesPerTool() == previous.usesPerTool()
                        && !java.util.Objects.equals(
                                group.finalKey(), previous.finalKey())) {
                    return false;
                }
            }
        }

        try {
            var validatedStates = new HashSet<AEItemKey>();
            for (var entry : longestGroups.entrySet()) {
                var group = entry.getValue();
                for (long used = 0; used < group.usesPerTool(); used++) {
                    AEItemKey stateKey = group.keyAfter(used);
                    if (!validatedStates.add(stateKey)) {
                        continue;
                    }
                    if (validatedStates.size()
                            > MAX_REUSABLE_VALIDATION_STATES) {
                        return false;
                    }
                    var stateInputs = createValidationInputs(
                            plan, group, used);
                    if (stateInputs == null
                            || !fillCraftingGrid(pattern, stateInputs)) {
                        return false;
                    }
                    var stateCraftingInput =
                            craftingGrid.asPositionedCraftInput().input();
                    ItemStack stateOutput =
                            pattern.assemble(stateCraftingInput, level);
                    if (!stateOutput.isEmpty()) {
                        stateOutput = stateOutput.copy();
                        stateOutput.onCraftedBySystem(level);
                    }
                    var statePrimary = new Object2LongOpenHashMap<AEKey>();
                    if (stateOutput.isEmpty()
                            || !addOutput(statePrimary, stateOutput)
                            || !mapsEqual(primaryPerCraft, statePrimary)
                            || !remainingItemsMatch(pattern,
                                    expectedValidationRemainders(
                                            plan, group, used))) {
                        return false;
                    }
                }
            }
            return !longestGroups.isEmpty();
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private KeyCounter[] createValidationInputs(
            MolecularReusableBatchPlan plan,
            MolecularReusableBatchPlan.DamageGroup activeGroup,
            long used) {
        var plannedInputs = plan.inputs();
        var result = new KeyCounter[plannedInputs.length];
        for (int index = 0; index < plannedInputs.length; index++) {
            var input = plannedInputs[index];
            AEKey key = input.mode()
                    == MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE
                            ? activeGroup.keyAfter(used)
                            : input.initialKey();
            if (key == null) {
                return null;
            }
            var holder = result[index] = new KeyCounter();
            holder.add(key, input.amountPerCraft());
        }
        return result;
    }

    private static KeyCounter expectedValidationRemainders(
            MolecularReusableBatchPlan plan,
            MolecularReusableBatchPlan.DamageGroup activeGroup,
            long used) {
        var result = new KeyCounter();
        for (var input : plan.inputs()) {
            if (input.mode()
                    == MolecularReusableBatchPlan.InputMode.INVARIANT_REUSABLE) {
                result.add(input.initialKey(), input.amountPerCraft());
            } else if (input.mode()
                    == MolecularReusableBatchPlan.InputMode.DETERMINISTIC_DAMAGE) {
                AEKey next = activeGroup.keyAfter(used + 1);
                if (next != null) {
                    result.add(next, 1);
                }
            }
        }
        return result;
    }

    private static boolean countersEqual(KeyCounter left, KeyCounter right) {
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

    private static boolean mapsEqual(Object2LongOpenHashMap<AEKey> left,
            Object2LongOpenHashMap<AEKey> right) {
        if (left.size() != right.size()) {
            return false;
        }
        for (var entry : left.object2LongEntrySet()) {
            if (right.getLong(entry.getKey()) != entry.getLongValue()) {
                return false;
            }
        }
        return true;
    }

    private CraftPlan createPlan(IMolecularAssemblerSupportedPattern pattern, KeyCounter[] inputs, Level level) {
        var positionedInput = craftingGrid.asPositionedCraftInput();
        var craftingInput = positionedInput.input();
        var output = pattern.assemble(craftingInput, level);
        if (output.isEmpty()) {
            return null;
        }

        var crafted = output.copy();
        crafted.onCraftedBySystem(level);

        var primaryOutputs = new Object2LongOpenHashMap<AEKey>();
        var remainderOutputs = new Object2LongOpenHashMap<AEKey>();
        if (!addOutput(primaryOutputs, crafted)) {
            return null;
        }
        for (var remainder : pattern.getRemainingItems(craftingInput)) {
            if (!addOutput(remainderOutputs, remainder)) {
                return null;
            }
        }

        var inputSignature = new ItemStack[CRAFTING_GRID_SIZE];
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
            inputSignature[slot] = craftingGrid.getItem(slot).copy();
        }

        var inputRequirements = new InputRequirement[inputs.length];
        for (int index = 0; index < inputs.length; index++) {
            InputRequirement requirement = null;
            for (var entry : inputs[index]) {
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }
                long consumed = amount - simulatedInputs[index].get(entry.getKey());
                if (consumed <= 0 || requirement != null) {
                    return null;
                }
                requirement = new InputRequirement(entry.getKey(), consumed);
            }
            if (requirement == null) {
                return null;
            }
            inputRequirements[index] = requirement;
        }
        return new CraftPlan(inputSignature, inputRequirements, crafted, primaryOutputs, remainderOutputs);
    }

    private long calculateCraftCount(KeyCounter[] inputs, long maxCrafts) {
        long result = 0;
        for (int index = 0; index < inputs.length; index++) {
            boolean hasInput = false;
            for (var entry : inputs[index]) {
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    continue;
                }

                hasInput = true;
                long remaining = simulatedInputs[index].get(entry.getKey());
                long consumed = amount - remaining;
                if (consumed <= 0 || amount % consumed != 0) {
                    return 0;
                }

                long candidate = amount / consumed;
                if (candidate <= 0 || candidate > maxCrafts) {
                    return 0;
                }
                if (result == 0) {
                    result = candidate;
                } else if (result != candidate) {
                    return 0;
                }
            }
            if (!hasInput) {
                return 0;
            }
        }
        return result;
    }

    private void prepareSimulationInputs(KeyCounter[] inputs) {
        if (simulatedInputs.length != inputs.length) {
            simulatedInputs = new KeyCounter[inputs.length];
            for (int index = 0; index < inputs.length; index++) {
                simulatedInputs[index] = new KeyCounter();
            }
        }

        for (int index = 0; index < inputs.length; index++) {
            simulatedInputs[index].clear();
            simulatedInputs[index].addAll(inputs[index]);
        }
    }

    private void clearCraftingGrid() {
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
            craftingGrid.setItem(slot, ItemStack.EMPTY);
        }
    }

    private static boolean addOutput(Object2LongOpenHashMap<AEKey> outputs, ItemStack stack) {
        if (stack.isEmpty()) {
            return true;
        }
        var key = AEItemKey.of(stack);
        if (key == null) {
            return false;
        }
        try {
            outputs.put(key, Math.addExact(outputs.getLong(key), stack.getCount()));
            return true;
        } catch (ArithmeticException exception) {
            return false;
        }
    }

    private record InputRequirement(AEKey key, long amount) {
    }

    private record SelectionConsumption(
            KeyCounter[] inputs, long repeats) {
    }

    private record CraftPlan(ItemStack[] inputs, InputRequirement[] inputRequirements, ItemStack craftedOutput,
            Object2LongOpenHashMap<AEKey> primaryOutputs,
            Object2LongOpenHashMap<AEKey> remainderOutputs) {
        long getCraftCount(KeyCounter[] providedInputs, long maxCrafts) {
            if (providedInputs.length != inputRequirements.length) {
                return 0;
            }

            long result = 0;
            for (int index = 0; index < providedInputs.length; index++) {
                var requirement = inputRequirements[index];
                var providedInput = providedInputs[index];
                providedInput.removeZeros();
                if (providedInput.size() != 1) {
                    return 0;
                }
                long providedAmount = providedInput.get(requirement.key);
                if (providedAmount <= 0 || providedAmount % requirement.amount != 0) {
                    return 0;
                }
                long candidate = providedAmount / requirement.amount;
                if (candidate <= 0 || candidate > maxCrafts) {
                    return 0;
                }
                if (result == 0) {
                    result = candidate;
                } else if (result != candidate) {
                    return 0;
                }
            }
            return result;
        }

        void loadCraftingGrid(TransientCraftingContainer craftingGrid) {
            for (int slot = 0; slot < inputs.length; slot++) {
                craftingGrid.setItem(slot, inputs[slot].copy());
            }
        }
    }
}
