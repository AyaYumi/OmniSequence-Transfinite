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
            ICraftingInventory inventory, IEnergyService energyService, KeyCounter[] firstInputs,
            KeyCounter expectedOutputs, KeyCounter expectedContainerItems, long maxCrafts) {
        if (maxCrafts <= 1) {
            return null;
        }

        var plan = ExpansionPlan.fromFirst(patternDetails, firstInputs, expectedOutputs,
                expectedContainerItems);
        if (plan == null) {
            return null;
        }

        try {
            long craftCount = plan.limitByInventory(inventory, maxCrafts);
            if (craftCount <= 1) {
                return null;
            }

            craftCount = limitByEnergy(energyService, plan.powerPerCraft(), craftCount);
            if (craftCount <= 1) {
                return null;
            }

            return plan.extractAdditional(inventory, expectedOutputs, firstInputs, craftCount);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static long limitByEnergy(IEnergyService energyService, double powerPerCraft,
            long craftCount) {
        if (!Double.isFinite(powerPerCraft) || powerPerCraft <= 0) {
            return 1;
        }

        double requestedPower = powerPerCraft * craftCount;
        if (!Double.isFinite(requestedPower)) {
            requestedPower = Double.MAX_VALUE;
        }
        double availablePower = energyService.extractAEPower(requestedPower, Actionable.SIMULATE,
                PowerMultiplier.CONFIG);
        if (availablePower >= requestedPower - POWER_EPSILON) {
            return craftCount;
        }
        if (!Double.isFinite(availablePower) || availablePower <= 0) {
            return 1;
        }

        double poweredCrafts = Math.floor((availablePower + POWER_EPSILON) / powerPerCraft);
        if (poweredCrafts < 2) {
            return 1;
        }
        if (poweredCrafts >= craftCount) {
            return craftCount;
        }
        return (long) poweredCrafts;
    }

    private record PlannedInput(AEKey key, long amountPerCraft) {
    }

    private record PlannedOutput(AEKey key, long amountPerCraft) {
    }

    private record ExpansionPlan(PlannedInput[] inputs,
            Object2LongOpenHashMap<AEKey> amountsPerKey, PlannedOutput[] outputs,
            double powerPerCraft, long representableCrafts) {
        static ExpansionPlan fromFirst(IPatternDetails patternDetails, KeyCounter[] firstInputs,
                KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
            if (patternDetails == null || firstInputs == null || expectedOutputs == null
                    || expectedContainerItems == null || !expectedContainerItems.isEmpty()) {
                return null;
            }

            var patternInputs = patternDetails.getInputs();
            if (patternInputs == null || firstInputs.length != patternInputs.length) {
                return null;
            }

            var plannedInputs = new PlannedInput[firstInputs.length];
            var amountsPerKey = new Object2LongOpenHashMap<AEKey>();
            long representableCrafts = Long.MAX_VALUE;
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
                    plannedInputs[index] = new PlannedInput(entry.getKey(), amount);
                    long totalAmount = Math.addExact(amountsPerKey.getLong(entry.getKey()), amount);
                    amountsPerKey.put(entry.getKey(), totalAmount);
                    representableCrafts = Math.min(representableCrafts, Long.MAX_VALUE / amount);
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

                double powerPerCraft = CraftingCpuHelper.calculatePatternPower(firstInputs);
                return new ExpansionPlan(plannedInputs, amountsPerKey,
                        plannedOutputs.toArray(PlannedOutput[]::new), powerPerCraft,
                        representableCrafts);
            } catch (ArithmeticException exception) {
                return null;
            }
        }

        long limitByInventory(ICraftingInventory inventory, long maxCrafts) {
            long craftCount = Math.min(maxCrafts, representableCrafts);
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

        BatchExtraction extractAdditional(ICraftingInventory inventory, KeyCounter expectedOutputs,
                KeyCounter[] firstInputs, long craftCount) {
            long additionalCrafts = craftCount - 1;
            var extraInputs = new KeyCounter[inputs.length];
            var combinedInputs = new KeyCounter[inputs.length];

            try {
                for (int index = 0; index < inputs.length; index++) {
                    var input = inputs[index];
                    long extraAmount = Math.multiplyExact(input.amountPerCraft(), additionalCrafts);
                    long combinedAmount = Math.multiplyExact(input.amountPerCraft(), craftCount);

                    var combined = combinedInputs[index] = new KeyCounter();
                    combined.add(input.key(), combinedAmount);

                    var extra = extraInputs[index] = new KeyCounter();
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

                expectedOutputs.reset();
                for (var output : outputs) {
                    expectedOutputs.add(output.key(), Math.multiplyExact(output.amountPerCraft(), craftCount));
                }
                return new BatchExtraction(combinedInputs, firstInputs, craftCount);
            } catch (RuntimeException exception) {
                CraftingCpuHelper.reinjectPatternInputs(inventory, extraInputs);
                return null;
            }
        }
    }

    public record BatchExtraction(KeyCounter[] inputs, KeyCounter[] firstInputs, long craftCount) {
    }
}
