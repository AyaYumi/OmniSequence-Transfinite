package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;

public final class MolecularBatchCraftingExtractor {
    private static final double POWER_EPSILON = 0.01;

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
                    var analysis = MolecularReusableInputAdapters.analyze(
                            patternInputs[index], entry.getKey(), level, maxCrafts);
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
