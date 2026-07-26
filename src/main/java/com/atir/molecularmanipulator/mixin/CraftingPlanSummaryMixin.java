package com.atir.molecularmanipulator.mixin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftingPlanSummary;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingPlanSummary.class, remap = false)
public abstract class CraftingPlanSummaryMixin {
    @Unique
    private static final int MOLECULARMANIPULATOR_MISSING = 0;
    @Unique
    private static final int MOLECULARMANIPULATOR_STORED = 1;
    @Unique
    private static final int MOLECULARMANIPULATOR_CRAFTING = 2;

    @Inject(method = "fromJob", at = @At("RETURN"))
    private static void molecularmanipulator$useCalculatedMissingItems(
            IGrid grid, IActionSource actionSource, ICraftingPlan job,
            CallbackInfoReturnable<CraftingPlanSummary> callback) {
        if (!job.simulation() || !molecularmanipulator$hasMaterialCalculationCore(grid)) {
            return;
        }

        var plan = new HashMap<AEKey, long[]>();
        for (var used : job.usedItems()) {
            var stats = molecularmanipulator$stats(plan, used.getKey());
            stats[MOLECULARMANIPULATOR_STORED] = molecularmanipulator$saturatedAdd(
                    stats[MOLECULARMANIPULATOR_STORED], used.getLongValue());
        }
        for (var missing : job.missingItems()) {
            var stats = molecularmanipulator$stats(plan, missing.getKey());
            stats[MOLECULARMANIPULATOR_MISSING] = molecularmanipulator$saturatedAdd(
                    stats[MOLECULARMANIPULATOR_MISSING], missing.getLongValue());
        }
        for (var emitted : job.emittedItems()) {
            var stats = molecularmanipulator$stats(plan, emitted.getKey());
            stats[MOLECULARMANIPULATOR_STORED] = molecularmanipulator$saturatedAdd(
                    stats[MOLECULARMANIPULATOR_STORED], emitted.getLongValue());
            stats[MOLECULARMANIPULATOR_CRAFTING] = molecularmanipulator$saturatedAdd(
                    stats[MOLECULARMANIPULATOR_CRAFTING], emitted.getLongValue());
        }
        for (var pattern : job.patternTimes().entrySet()) {
            for (var output : pattern.getKey().getOutputs()) {
                var crafted = molecularmanipulator$saturatedMultiply(
                        output.amount(), pattern.getValue());
                var stats = molecularmanipulator$stats(plan, output.what());
                stats[MOLECULARMANIPULATOR_CRAFTING] = molecularmanipulator$saturatedAdd(
                        stats[MOLECULARMANIPULATOR_CRAFTING], crafted);
            }
        }

        var entries = new ArrayList<CraftingPlanSummaryEntry>(plan.size());
        for (var entry : plan.entrySet()) {
            var stats = entry.getValue();
            entries.add(new CraftingPlanSummaryEntry(
                    entry.getKey(),
                    stats[MOLECULARMANIPULATOR_MISSING],
                    stats[MOLECULARMANIPULATOR_STORED],
                    stats[MOLECULARMANIPULATOR_CRAFTING]));
        }
        Collections.sort(entries);

        // Keep the instance returned by AE2 so other Mixins can retain state attached to it.
        ((CraftingPlanSummaryAccessor) (Object) callback.getReturnValue())
                .molecularmanipulator$setEntries(List.copyOf(entries));
    }

    @Unique
    private static boolean molecularmanipulator$hasMaterialCalculationCore(IGrid grid) {
        for (var controller : grid.getMachines(OmniComputationCoreBlockEntity.class)) {
            if (controller.isMaterialCalculationEnabled()) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private static long[] molecularmanipulator$stats(
            HashMap<AEKey, long[]> plan, AEKey key) {
        return plan.computeIfAbsent(key, ignored -> new long[3]);
    }

    @Unique
    private static long molecularmanipulator$saturatedAdd(long left, long right) {
        if (right > 0 && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    @Unique
    private static long molecularmanipulator$saturatedMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }
}
