package com.atir.molecularmanipulator.crafting;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.execution.CraftingCpuHelper;
import appeng.crafting.execution.InputTemplate;
import appeng.crafting.inv.ICraftingInventory;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.minecraft.world.level.Level;

public final class MolecularBatchCraftingExtractor {
    private MolecularBatchCraftingExtractor() {
    }

    public static BatchExtraction extractLargest(IPatternDetails patternDetails, ICraftingInventory inventory,
            Level level, IEnergyService energyService, KeyCounter expectedOutputs,
            KeyCounter expectedContainerItems, long maxCrafts) {
        long craftCount = maxCrafts;
        while (craftCount > 0) {
            var plan = planExact(patternDetails, inventory, level, craftCount);
            if (plan == null) {
                craftCount /= 2;
                continue;
            }

            double availablePower = energyService.extractAEPower(plan.power(), Actionable.SIMULATE,
                    PowerMultiplier.CONFIG);
            if (availablePower < plan.power() - 0.01) {
                if (plan.power() <= 0 || availablePower <= 0) {
                    craftCount = 0;
                } else {
                    long energyLimitedCrafts = (long) Math.floor(craftCount * (availablePower / plan.power()));
                    craftCount = energyLimitedCrafts >= craftCount ? craftCount / 2 : energyLimitedCrafts;
                }
                continue;
            }

            var inputs = plan.extract(patternDetails, inventory, expectedOutputs, expectedContainerItems);
            if (inputs != null) {
                return new BatchExtraction(inputs, craftCount, plan.power());
            }
            craftCount /= 2;
        }

        expectedOutputs.reset();
        expectedContainerItems.reset();
        return null;
    }

    private static BatchPlan planExact(IPatternDetails patternDetails, ICraftingInventory inventory,
            Level level, long craftCount) {
        var patternInputs = patternDetails.getInputs();
        var plannedInputs = new PlannedInput[patternInputs.length];
        var reservedAmounts = new Object2LongOpenHashMap<AEKey>();
        double power = 0;

        try {
            for (int index = 0; index < patternInputs.length; index++) {
                var patternInput = patternInputs[index];
                long requiredTemplates = Math.multiplyExact(patternInput.getMultiplier(), craftCount);
                PlannedInput plannedInput = null;

                for (var template : CraftingCpuHelper.getValidItemTemplates(inventory, patternInput, level)) {
                    long requiredAmount = Math.multiplyExact(template.amount(), requiredTemplates);
                    long totalReserved = Math.addExact(reservedAmounts.getLong(template.key()), requiredAmount);
                    long availableAmount = inventory.extract(template.key(), totalReserved, Actionable.SIMULATE);
                    if (availableAmount < totalReserved) {
                        continue;
                    }

                    plannedInput = new PlannedInput(template, requiredTemplates, requiredAmount);
                    reservedAmounts.put(template.key(), totalReserved);
                    power += (double) requiredAmount / template.key().getAmountPerOperation();
                    break;
                }

                if (plannedInput == null) {
                    return null;
                }
                plannedInputs[index] = plannedInput;
            }

            for (var output : patternDetails.getOutputs()) {
                Math.multiplyExact(output.amount(), craftCount);
            }
            return new BatchPlan(plannedInputs, craftCount, power);
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private record PlannedInput(InputTemplate template, long templateCount, long amount) {
    }

    private record BatchPlan(PlannedInput[] plannedInputs, long craftCount, double power) {
        KeyCounter[] extract(IPatternDetails patternDetails, ICraftingInventory inventory,
                KeyCounter expectedOutputs, KeyCounter expectedContainerItems) {
            expectedOutputs.reset();
            expectedContainerItems.reset();
            var extractedInputs = new KeyCounter[plannedInputs.length];

            for (int index = 0; index < plannedInputs.length; index++) {
                var plannedInput = plannedInputs[index];
                var extractedInput = extractedInputs[index] = new KeyCounter();
                long extractedAmount = inventory.extract(plannedInput.template().key(), plannedInput.amount(),
                        Actionable.MODULATE);
                if (extractedAmount > 0) {
                    extractedInput.add(plannedInput.template().key(), extractedAmount);
                }
                if (extractedAmount != plannedInput.amount()) {
                    CraftingCpuHelper.reinjectPatternInputs(inventory, extractedInputs);
                    expectedOutputs.reset();
                    expectedContainerItems.reset();
                    return null;
                }

                var remainingKey = patternDetails.getInputs()[index]
                        .getRemainingKey(plannedInput.template().key());
                if (remainingKey != null) {
                    expectedContainerItems.add(remainingKey, plannedInput.templateCount());
                }
            }

            for (var output : patternDetails.getOutputs()) {
                expectedOutputs.add(output.what(), Math.multiplyExact(output.amount(), craftCount));
            }
            return extractedInputs;
        }
    }

    public record BatchExtraction(KeyCounter[] inputs, long craftCount, double power) {
    }
}
