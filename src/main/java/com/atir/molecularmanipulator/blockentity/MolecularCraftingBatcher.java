package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.menu.AutoCraftingMenu;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

final class MolecularCraftingBatcher {
    private static final int CRAFTING_GRID_SIZE = 9;
    private static final int MAX_CACHED_PATTERNS = 2048;

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

            ItemStack output = pattern.assemble(craftingGrid, level);
            if (output.isEmpty()) {
                return null;
            }
            var primaryPerCraft = new Object2LongOpenHashMap<AEKey>();
            if (!addOutput(primaryPerCraft, output)) {
                return null;
            }
            if (!remainingItemsMatch(pattern, reusablePlan.expectedRemainders(1))) {
                return null;
            }

            var firstGrid = new ItemStack[CRAFTING_GRID_SIZE];
            for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
                firstGrid[slot] = craftingGrid.getItem(slot).copy();
            }

            if (hasTransitioningInput(reusablePlan)) {
                // A finite adapter is only safe for aggregate execution when
                // every intermediate damage state produces exactly the same
                // primary output and the predicted next tool key.
                for (long completedBefore = 1;
                        completedBefore < reusablePlan.craftCount();
                        completedBefore++) {
                    var stateInputs = createOneCraftInputs(
                            reusablePlan, completedBefore);
                    if (stateInputs == null
                            || !fillCraftingGrid(pattern, stateInputs)) {
                        return null;
                    }
                    ItemStack stateOutput = pattern.assemble(craftingGrid, level);
                    var statePrimary = new Object2LongOpenHashMap<AEKey>();
                    if (stateOutput.isEmpty()
                            || !addOutput(statePrimary, stateOutput)
                            || !mapsEqual(primaryPerCraft, statePrimary)
                            || !remainingItemsMatch(pattern,
                                    reusablePlan.expectedRemainders(
                                            completedBefore + 1))) {
                        return null;
                    }
                }
            }

            clearCraftingGrid();
            for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
                craftingGrid.setItem(slot, firstGrid[slot]);
            }
            preparedPlan = null;
            craftingGridPrepared = true;
            craftCount = reusablePlan.craftCount();
            craftedOutput = output.copy();
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
                    == MolecularReusableBatchPlan.InputMode.CONSUMABLE
                            ? input.initialKey()
                            : input.keyAfter(
                                    completedCrafts, plan.craftCount());
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
        for (var remainder : pattern.getRemainingItems(craftingGrid)) {
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
        var craftingInput = craftingGrid;
        var output = pattern.assemble(craftingInput, level);
        if (output.isEmpty()) {
            return null;
        }

        var crafted = output.copy();

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
