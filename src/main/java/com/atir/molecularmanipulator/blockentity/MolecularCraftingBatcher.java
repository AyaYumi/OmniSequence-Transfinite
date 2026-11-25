package com.atir.molecularmanipulator.blockentity;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AECraftingPattern;
import appeng.menu.AutoCraftingMenu;
import com.atir.molecularmanipulator.crafting.MolecularBatchCancellationData;
import com.atir.molecularmanipulator.crafting.MolecularBatchDispatchContext;
import com.atir.molecularmanipulator.crafting.MolecularRemainderContext;
import com.atir.molecularmanipulator.crafting.MolecularReusableInputAdapters;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputMode;
import com.atir.molecularmanipulator.crafting.MolecularReusableBatchPlan.InputPlan;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.integration.ae2.MolecularBatchCraftingProvider.ReusableValidation;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.google.common.collect.Range;
import com.google.common.collect.TreeRangeSet;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;

final class MolecularCraftingBatcher {
    private static final int CRAFTING_GRID_SIZE = 9;
    private static final int MAX_CACHED_PATTERNS = 2048;
    static final int WEAR_STATES_PER_TICK = 64;

    private final TransientCraftingContainer craftingGrid =
            new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
    private final Object2LongOpenHashMap<AEKey> outputAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> primaryOutputAmounts = new Object2LongOpenHashMap<>();
    private final Object2LongOpenHashMap<AEKey> remainderOutputAmounts = new Object2LongOpenHashMap<>();
    private final Reference2ObjectOpenHashMap<IPatternDetails, CraftPlan> planCache =
            new Reference2ObjectOpenHashMap<>();
    // The CPU retains its job UUID. An abandoned or completed order owns no machine resources.
    private final Map<UUID, Map<IPatternDetails, ReusableProof>> reusableProofs = new WeakHashMap<>();
    private final LinkedHashSet<ReusableProof> requestedProofs = new LinkedHashSet<>();
    private Level proofLevel;
    private Object proofRecipeView;
    private long proofGeneration;
    private long validationTick = Long.MIN_VALUE;
    private int validationStepsLeft;
    private boolean validatingReusable;
    private TransientCraftingContainer validationGrid;
    private KeyCounter[] validationInputs = new KeyCounter[0];
    private final KeyCounter validationRemainders = new KeyCounter();

    private KeyCounter[] simulatedInputs = new KeyCounter[0];
    private CraftPlan preparedPlan;
    private ItemStack craftedOutput = ItemStack.EMPTY;
    private long craftCount;
    private boolean craftingGridPrepared;
    private Object planRecipeView;

    static long outputCapacity(long crafts, int maxTypes, KeyCounter unitOutputs,
            Object2LongOpenHashMap<AEKey> buffered,
            @Nullable Object2LongOpenHashMap<AEKey> additionalBuffered) {
        if (crafts <= 0) {
            return 0;
        }

        int bufferedTypes = buffered.size();
        int additionalTypes = additionalBuffered == null ? 0 : additionalBuffered.size();
        long types = (long) bufferedTypes + additionalTypes;
        if (additionalBuffered != null && bufferedTypes != 0 && additionalTypes != 0) {
            // Center splits its buffer by output kind, but both count against one capacity.
            var smaller = bufferedTypes <= additionalTypes ? buffered : additionalBuffered;
            var larger = smaller == buffered ? additionalBuffered : buffered;
            var entries = smaller.object2LongEntrySet().fastIterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                long otherAmount = larger.getLong(entry.getKey());
                if (otherAmount > 0) {
                    if (entry.getLongValue() > Long.MAX_VALUE - otherAmount) {
                        return 0;
                    }
                    types--;
                }
            }
        }
        if (types > maxTypes) {
            return 0;
        }

        boolean hasOutput = false;
        for (var entry : unitOutputs) {
            long amount = entry.getLongValue();
            if (amount <= 0) {
                return 0;
            }
            hasOutput = true;
            var key = entry.getKey();
            long queued = buffered.getLong(key);
            if (additionalBuffered != null) {
                queued += additionalBuffered.getLong(key);
            }
            if (queued == 0 && ++types > maxTypes) {
                return 0;
            }
            crafts = Math.min(crafts, (Long.MAX_VALUE - queued) / amount);
            if (crafts == 0) {
                return 0;
            }
        }
        return hasOutput ? crafts : 0;
    }

    boolean prepareDispatch(BlockEntity machine, IPatternDetails patternDetails, KeyCounter[] inputs,
            long maxCrafts, @Nullable MolecularBatchDispatchContext.Context context) {
        if (context == null) {
            return prepare(machine, patternDetails, inputs, maxCrafts);
        }
        var unitOutputs = context.fixedUnitOutputs();
        if (unitOutputs == null) {
            return prepareSelected(machine, patternDetails, inputs, maxCrafts, context.firstInputs(), context.craftCount());
        }
        return prepareFixed(machine, patternDetails, inputs, maxCrafts, context.firstInputs(),
                unitOutputs, context.craftCount());
    }

    /** Validates the quoted one-craft selection once before scaling its output. */
    private boolean prepareFixed(BlockEntity machine, IPatternDetails patternDetails, KeyCounter[] inputs,
            long maxCrafts, KeyCounter[] unitInputs, KeyCounter unitOutputs, long count) {
        var level = machine.getLevel();
        if (level == null || level.isClientSide() || !(patternDetails instanceof IMolecularAssemblerSupportedPattern pattern)
                || count <= 0 || count > maxCrafts || !matchesFixedInputs(inputs, unitInputs, count)
                || !RecipeLocks.allowedPattern(machine, patternDetails)) {
            return false;
        }

        try (var scope = RecipeExecutionContext.open(machine)) {
            prepareSimulationInputs(unitInputs);
            if (!fillCraftingGrid(pattern, simulatedInputs)) {
                return false;
            }
            var craftingInput = craftingGrid.asPositionedCraftInput().input();
            var output = pattern.assemble(craftingInput, level);
            if (output.isEmpty()) {
                return false;
            }
            var crafted = output.copy();
            crafted.onCraftedBySystem(level);
            var key = AEItemKey.of(crafted);
            if (key == null || unitOutputs.size() != 1 || unitOutputs.get(key) != crafted.getCount()) {
                return false;
            }
            for (var remainder : pattern.getRemainingItems(craftingInput)) {
                if (!remainder.isEmpty()) {
                    return false;
                }
            }

            long total = Math.multiplyExact((long) crafted.getCount(), count);
            primaryOutputAmounts.clear();
            primaryOutputAmounts.put(key, total);
            remainderOutputAmounts.clear();
            outputAmounts.clear();
            outputAmounts.put(key, total);
            preparedPlan = null;
            craftingGridPrepared = true;
            craftedOutput = crafted;
            craftCount = count;
            return true;
        } catch (RuntimeException exception) {
            return failSelectedPreparation();
        }
    }

    private static boolean matchesFixedInputs(KeyCounter[] inputs, KeyCounter[] unitInputs, long count) {
        if (inputs.length != unitInputs.length) {
            return false;
        }
        long maxUnitAmount = Long.MAX_VALUE / count;
        for (int slot = 0; slot < inputs.length; slot++) {
            var aggregate = inputs[slot];
            var unit = unitInputs[slot];
            int keys = 0;
            for (var entry : unit) {
                long amount = entry.getLongValue();
                if (amount <= 0 || amount > maxUnitAmount || aggregate.get(entry.getKey()) != amount * count) {
                    return false;
                }
                keys++;
            }
            if (aggregate.size() != keys) {
                return false;
            }
        }
        return true;
    }

    /** Runtime entry: even a warm plan cache requires a fresh machine/grid authorization. */
    boolean prepare(BlockEntity machine, IPatternDetails patternDetails, KeyCounter[] inputs, long maxCrafts) {
        var level = machine.getLevel();
        if (level == null || level.isClientSide() || !RecipeLocks.allowedPattern(machine, patternDetails)) return false;
        try (var scope = RecipeExecutionContext.open(machine)) {
            var recipes = level.getRecipeManager().getRecipes();
            if (planRecipeView != recipes) {
                planCache.clear();
                planRecipeView = recipes;
            }
            return prepare(patternDetails, inputs, level, maxCrafts);
        }
    }

    boolean prepareSelected(BlockEntity machine, IPatternDetails patternDetails, KeyCounter[] inputs,
            long maxCrafts, KeyCounter[] firstInputs, long selectedCraftCount) {
        var level = machine.getLevel();
        if (level == null || level.isClientSide() || !RecipeLocks.allowedPattern(machine, patternDetails)) return false;
        try (var scope = RecipeExecutionContext.open(machine)) {
            return prepareSelected(patternDetails, inputs, level, maxCrafts, firstInputs, selectedCraftCount);
        }
    }

    ReusableValidation validateReusable(BlockEntity machine, IPatternDetails pattern,
            MolecularReusableBatchPlan plan, UUID jobId) {
        var level = machine.getLevel();
        if (level == null || level.isClientSide() || machine.isRemoved() || jobId == null
                || !canValidate(machine, pattern, jobId)) {
            return ReusableValidation.REJECTED;
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            return validateReusable(pattern, plan, jobId, level, level.getRecipeManager().getRecipes(), level.getGameTime(), machine);
        }
    }

    private boolean canValidate(BlockEntity machine, IPatternDetails pattern, UUID jobId) {
        if (!MolecularBatchCancellationData.isCanceled(machine.getLevel(), jobId)) return RecipeLocks.allowedPattern(machine, pattern);
        var canceled = reusableProofs.remove(jobId);
        if (canceled != null) canceled.values().forEach(requestedProofs::remove);
        return false;
    }

    MolecularReusableBatchJob prepareReusable(BlockEntity machine, IPatternDetails patternDetails,
            KeyCounter[] inputs, UUID craftingId, MolecularReusableBatchPlan reusablePlan) {
        var level = machine.getLevel();
        if (level == null || level.isClientSide() || machine.isRemoved() || craftingId == null) return null;
        try (var scope = RecipeExecutionContext.open(machine)) {
            var context = MolecularBatchDispatchContext.current(patternDetails, inputs);
            if (context != null) {
                if (!canValidate(machine, patternDetails, craftingId)) return null;
                if (context.requiresReusableProof()) {
                    return takeVerifiedReusable(patternDetails, inputs, context.firstInputs(), craftingId, reusablePlan,
                            level, level.getRecipeManager().getRecipes(), level.getGameTime());
                }
            } else if (!RecipeLocks.allowedPattern(machine, patternDetails)) {
                return null;
            }
            return prepareReusable(patternDetails, inputs, level, craftingId, reusablePlan);
        }
    }

    // Pure preparation overloads below are also exercised by arithmetic/ownership
    // unit tests without a running server. Runtime hosts must use the machine overloads.
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
        var expectedRemainders = MolecularRemainderContext.expected(
                patternDetails, inputs);
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
                    // Only an explicit aggregate remainder contract may carry
                    // linear containers. Verify the complete actual remainder total
                    // before any machine acquires ownership.
                    if (!remainder.isEmpty()
                            && (expectedRemainders == null
                                    || !addScaledOutput(selectedRemainderOutputs,
                                            remainder, consumption.repeats()))) {
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
                || selectedPrimaryOutputs.isEmpty()
                || expectedRemainders != null
                        && !MolecularRemainderContext.matches(expectedRemainders,
                                selectedRemainderOutputs.object2LongEntrySet())) {
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

    ReusableValidation validateReusable(IPatternDetails pattern, MolecularReusableBatchPlan plan, UUID jobId,
            Level level, Object recipeView, long gameTime) {
        return validateReusable(pattern, plan, jobId, level, recipeView, gameTime, null);
    }

    private ReusableValidation validateReusable(IPatternDetails pattern, MolecularReusableBatchPlan plan, UUID jobId,
            Level level, Object recipeView, long gameTime, @Nullable BlockEntity machine) {
        if (!(pattern instanceof IMolecularAssemblerSupportedPattern) || plan == null || jobId == null) {
            return ReusableValidation.REJECTED;
        }
        if (validatingReusable) return ReusableValidation.PENDING;
        selectProofView(level, recipeView);
        if (validationTick != gameTime) {
            validationTick = gameTime;
            validationStepsLeft = WEAR_STATES_PER_TICK;
        }

        var order = reusableProofs.computeIfAbsent(jobId, ignored -> new HashMap<>());
        var proof = order.get(pattern);
        if (proof == null || !proof.matches(pattern, plan)) {
            if (proof != null) requestedProofs.remove(proof);
            proof = new ReusableProof(pattern, plan, jobId);
            order.put(pattern, proof);
        }
        if (proof.status == ReusableValidation.PENDING) requestedProofs.add(proof);
        // Give each request one turn in arrival order. A caller that goes away leaves at most
        // one slice of speculative work. No other CPU can evict its partially checked recipe.
        validatingReusable = true;
        try {
            while (validationStepsLeft > 0 && !requestedProofs.isEmpty()) {
                var next = requestedProofs.removeFirst();
                var owner = next == proof ? jobId : next.job.get();
                if (owner == null || next != proof && machine != null && !canValidate(machine, next.requested, owner)) {
                    // A different CPU may have lost its license or canceled since it requested this turn.
                    validationStepsLeft--;
                    continue;
                }
                validationStepsLeft -= advanceProof(next, level, validationStepsLeft);
            }
        } finally {
            validatingReusable = false;
        }
        if (proof.status == ReusableValidation.READY) {
            proof.quotedPlan = plan;
            proof.quotedTick = gameTime;
        }
        return proof.status;
    }

    private void selectProofView(Level level, Object recipes) {
        long generation = PatternRecipeReloads.generation();
        if (proofLevel != level || proofRecipeView != recipes || proofGeneration != generation) {
            reusableProofs.clear();
            requestedProofs.clear();
            proofLevel = level;
            proofRecipeView = recipes;
            proofGeneration = generation;
        }
    }

    MolecularReusableBatchJob takeVerifiedReusable(IPatternDetails pattern, KeyCounter[] inputs, KeyCounter[] firstInputs,
            UUID jobId, MolecularReusableBatchPlan plan, Level level, Object recipeView, long gameTime) {
        selectProofView(level, recipeView);
        var order = reusableProofs.get(jobId);
        var proof = order == null ? null : order.get(pattern);
        if (proof == null || proof.requested != pattern || proof.quotedPlan != plan || proof.quotedTick != gameTime
                || proof.status != ReusableValidation.READY) return null;
        order.remove(pattern);
        if (order.isEmpty()) reusableProofs.remove(jobId);
        if (firstInputs.length != proof.inputs.length) return null;
        for (int slot = 0; slot < firstInputs.length; slot++) {
            var selected = proof.inputs[slot];
            var holder = firstInputs[slot];
            if (holder == null || holder.size() != 1 || holder.get(selected.initialKey()) != selected.amountPerCraft()) return null;
        }
        return finishReusable(proof, inputs, jobId);
    }

    // Passive crafting and external CPUs deliver synchronously. Their existing bounded contracts
    // still require a complete real recipe check before their extracted inputs can be acquired.
    MolecularReusableBatchJob prepareReusable(IPatternDetails patternDetails,
            KeyCounter[] inputs, Level level, UUID craftingId, MolecularReusableBatchPlan plan) {
        if (!(patternDetails instanceof IMolecularAssemblerSupportedPattern) || validatingReusable
                || craftingId == null || plan == null || !plan.matchesProvidedInputs(inputs)) return null;
        var proof = new ReusableProof(patternDetails, plan, craftingId);
        validatingReusable = true;
        try {
            advanceProof(proof, level, (int) MolecularReusableInputAdapters.MAX_DETERMINISTIC_TRANSITIONS);
            return proof.status == ReusableValidation.READY ? finishReusable(proof, inputs, craftingId) : null;
        } finally {
            validatingReusable = false;
        }
    }

    private MolecularReusableBatchJob finishReusable(ReusableProof proof, KeyCounter[] inputs, UUID craftingId) {
        var expected = expectedPrimaryOutputs(proof.requested);
        if (expected == null || !mapsEqual(proof.primary, expected)) return null;
        var job = MolecularReusableBatchJob.create(craftingId, proof.definition, proof.plan,
                inputs, proof.primary);
        if (job == null) return null;
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) craftingGrid.setItem(slot, proof.firstGrid[slot]);
        preparedPlan = null;
        craftingGridPrepared = true;
        craftCount = proof.plan.craftCount();
        craftedOutput = proof.firstOutput;
        outputAmounts.clear();
        primaryOutputAmounts.clear();
        remainderOutputAmounts.clear();
        return job;
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

    /**
     * Finalizes an already validated reusable batch for passive auto-crafting.
     * The normal AE crafting-provider path persists the reusable job and advances
     * it separately; the auto-crafter owns the complete extracted batch and can
     * commit its aggregate outputs immediately.
     */
    boolean prepareReusableOutputsForAuto(MolecularReusableBatchJob job,
            Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders) {
        if (job == null || primary == null || primary.isEmpty()
                || remainders == null) {
            return false;
        }
        try {
            primaryOutputAmounts.clear();
            primaryOutputAmounts.putAll(primary);
            remainderOutputAmounts.clear();
            remainderOutputAmounts.putAll(remainders);
            outputAmounts.clear();
            mergeOutputs(primaryOutputAmounts, outputAmounts);
            mergeOutputs(remainderOutputAmounts, outputAmounts);
            craftCount = job.totalCrafts();
            return !outputAmounts.isEmpty();
        } catch (RuntimeException exception) {
            outputAmounts.clear();
            primaryOutputAmounts.clear();
            remainderOutputAmounts.clear();
            craftCount = 0;
            return false;
        }
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

    private static final class ReusableProof {
        final IPatternDetails requested;
        final MolecularReusableBatchPlan plan;
        final InputPlan[] inputs;
        final WeakReference<UUID> job;
        long quotedTick;
        MolecularReusableBatchPlan quotedPlan;
        ReusableValidation status = ReusableValidation.PENDING;
        IMolecularAssemblerSupportedPattern recipe;
        Object2LongOpenHashMap<AEKey> primary;
        AEItemKey definition;
        List<WearRange> ranges = List.of();
        int wearSlot = -1;
        int rangeIndex;
        long damage;
        AEItemKey currentTool;
        ItemStack[] firstGrid;
        ItemStack firstOutput;

        ReusableProof(IPatternDetails requested, MolecularReusableBatchPlan plan, UUID jobId) {
            this.requested = requested;
            this.plan = plan;
            this.inputs = plan.inputs();
            this.job = new WeakReference<>(jobId);
        }

        boolean matches(IPatternDetails pattern, MolecularReusableBatchPlan candidate) {
            return requested == pattern && (plan == candidate || plan.craftCount() == candidate.craftCount()
                    && Arrays.equals(inputs, candidate.inputs()));
        }

        boolean initialize(Level level) {
            definition = requested.getDefinition();
            // AE2 decoded patterns retain their original Recipe instance across a datapack reload.
            var live = PatternDetailsHelper.isEncodedPattern(definition.toStack())
                    ? PatternDetailsHelper.decodePattern(definition, level) : requested;
            if (!(live instanceof IMolecularAssemblerSupportedPattern actual) || actual.getInputs().length != inputs.length) {
                return false;
            }
            recipe = actual;
            primary = expectedPrimaryOutputs(requested);
            if (primary == null || primary.size() != 1) return false;
            for (int slot = 0; slot < inputs.length; slot++) {
                if (inputs[slot].mode() == InputMode.DETERMINISTIC_DAMAGE) {
                    wearSlot = slot;
                    ranges = wearRanges(inputs[slot]);
                    if (ranges.isEmpty()) return false;
                    damage = ranges.getFirst().first();
                }
            }
            return true;
        }
    }

    private record WearRange(AEItemKey template, long first, long end, boolean breaks) {}

    private static AEItemKey damageKeyAt(AEItemKey template, long damage) {
        var stack = template.toStack();
        stack.setDamageValue(Math.toIntExact(damage));
        if (stack.getDamageValue() != damage) throw new IllegalArgumentException("Invalid tool damage");
        return Objects.requireNonNull(AEItemKey.of(stack));
    }

    private static final class WearCoverage {
        final TreeRangeSet<Long> advances = TreeRangeSet.create();
        final TreeRangeSet<Long> breaks = TreeRangeSet.create();
    }

    /** Store intervals per component-identical tool family, independent of durability and tool count. */
    private static List<WearRange> wearRanges(InputPlan input) {
        var families = new HashMap<AEItemKey, WearCoverage>();
        for (var group : input.damageGroups()) {
            var stack = group.initialKey().toStack();
            long start = stack.getDamageValue();
            if (!MolecularReusableInputAdapters.isDeterministicDamageCandidate(group.initialKey())
                    || group.usesPerTool() > MolecularReusableInputAdapters.physicalUses(group.initialKey())) return List.of();
            long end = Math.addExact(start, group.usesPerTool());
            stack.setDamageValue(0);
            var template = Objects.requireNonNull(AEItemKey.of(stack));
            if (!group.initialKey().equals(damageKeyAt(template, start))
                    || group.finalKey() != null && !group.finalKey().equals(damageKeyAt(template, end))) return List.of();
            var family = families.computeIfAbsent(template, ignored -> new WearCoverage());
            long advanceEnd = group.finalKey() == null ? end - 1 : end;
            if (start < advanceEnd) {
                var advances = Range.closedOpen(start, advanceEnd);
                if (family.breaks.intersects(advances)) return List.of();
                family.advances.add(advances);
            }
            if (group.finalKey() == null) {
                var terminal = Range.closedOpen(end - 1, end);
                if (family.advances.intersects(terminal)) return List.of();
                family.breaks.add(terminal);
            }
        }
        var ranges = new ArrayList<WearRange>();
        families.forEach((key, family) -> {
            for (var range : family.advances.asRanges()) {
                ranges.add(new WearRange(key, range.lowerEndpoint(), range.upperEndpoint(), false));
            }
            for (var range : family.breaks.asRanges()) {
                ranges.add(new WearRange(key, range.lowerEndpoint(), range.upperEndpoint(), true));
            }
        });
        return ranges;
    }

    private int advanceProof(ReusableProof proof, Level level, int allowance) {
        int spent = 0;
        try {
            if (proof.recipe == null && !proof.initialize(level)) {
                proof.status = ReusableValidation.REJECTED;
                return 1;
            }
            while (spent < allowance && proof.status == ReusableValidation.PENDING) {
                spent++;
                WearRange range = proof.wearSlot < 0 ? null : proof.ranges.get(proof.rangeIndex);
                if (range != null && proof.currentTool == null) proof.currentTool = damageKeyAt(range.template(), proof.damage);
                var nextTool = range == null || range.breaks() ? null : damageKeyAt(range.template(), proof.damage + 1);
                if (!validateReusableState(proof, level, nextTool)) {
                    proof.status = ReusableValidation.REJECTED;
                } else if (range == null) {
                    proof.status = ReusableValidation.READY;
                } else if (++proof.damage < range.end()) {
                    proof.currentTool = nextTool;
                } else if (++proof.rangeIndex < proof.ranges.size()) {
                    proof.damage = proof.ranges.get(proof.rangeIndex).first();
                    proof.currentTool = null;
                } else {
                    proof.status = proof.firstGrid == null ? ReusableValidation.REJECTED : ReusableValidation.READY;
                }
            }
        } catch (RuntimeException invalid) {
            proof.status = ReusableValidation.REJECTED;
        }
        return Math.max(1, spent);
    }

    private boolean validateReusableState(ReusableProof proof, Level level, @Nullable AEItemKey nextTool) {
        if (validationGrid == null) validationGrid = new TransientCraftingContainer(new AutoCraftingMenu(), 3, 3);
        if (validationInputs.length != proof.inputs.length) {
            validationInputs = new KeyCounter[proof.inputs.length];
            Arrays.setAll(validationInputs, ignored -> new KeyCounter());
        }
        validationRemainders.clear();
        var rules = proof.recipe.getInputs();
        for (int slot = 0; slot < proof.inputs.length; slot++) {
            var input = proof.inputs[slot];
            var key = slot == proof.wearSlot ? proof.currentTool : input.initialKey();
            var expected = slot == proof.wearSlot ? nextTool : input.finalKey();
            if (input.mode() == InputMode.INVARIANT_REUSABLE) {
                var actual = MolecularReusableInputAdapters.analyze(rules[slot], key, level, 1);
                if (actual.mode() != MolecularReusableInputAdapters.Mode.INVARIANT_REUSABLE) return false;
            } else if (!rules[slot].isValid(key, level) || !Objects.equals(rules[slot].getRemainingKey(key), expected)) {
                return false;
            }
            var holder = validationInputs[slot];
            holder.clear();
            holder.add(key, input.amountPerCraft());
            if (expected != null) validationRemainders.add(expected, input.amountPerCraft());
        }

        validationGrid.clearContent();
        proof.recipe.fillCraftingGrid(validationInputs, validationGrid::setItem);
        if (!allInputsEmpty(validationInputs)) return false;
        for (int slot = 0; slot < CRAFTING_GRID_SIZE; slot++) {
            var stack = validationGrid.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!proof.recipe.isSlotEnabled(slot)) return false;
            if (proof.recipe instanceof AECraftingPattern crafting) {
                var wrapped = GenericStack.unwrapItemStack(stack);
                if (wrapped != null && wrapped.equals(crafting.getValidFluid(slot))) continue;
            }
            if (!proof.recipe.isItemValid(slot, AEItemKey.of(stack), level)) return false;
        }
        var grid = validationGrid.asPositionedCraftInput().input();
        var assembled = proof.recipe.assemble(grid, level);
        if (assembled.isEmpty()) return false;
        var crafted = assembled.copy();
        crafted.onCraftedBySystem(level);
        if (crafted.isEmpty() || proof.primary.getLong(AEItemKey.of(crafted)) != crafted.getCount()) return false;
        for (var remainder : proof.recipe.getRemainingItems(grid)) {
            if (remainder.isEmpty()) continue;
            var key = AEItemKey.of(remainder);
            if (validationRemainders.get(key) < remainder.getCount()) return false;
            validationRemainders.remove(key, remainder.getCount());
        }
        validationRemainders.removeZeros();
        if (!validationRemainders.isEmpty()) return false;
        if (proof.firstGrid == null && (proof.wearSlot < 0 || proof.currentTool.equals(proof.inputs[proof.wearSlot].initialKey()))) {
            proof.firstGrid = new ItemStack[CRAFTING_GRID_SIZE];
            Arrays.setAll(proof.firstGrid, slot -> validationGrid.getItem(slot).copy());
            proof.firstOutput = crafted;
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
