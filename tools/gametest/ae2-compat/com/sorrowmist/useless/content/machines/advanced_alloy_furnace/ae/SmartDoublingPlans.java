package com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.Level;

/** Test-only optional entry-point ABI; actual Applied Mixin must transform both overloads. */
public final class SmartDoublingPlans {
    public static Map<IPatternDetails, Long> observed;
    public static ICraftingPlan rewriteForSubmission(ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providers) {
        observed = plan.patternTimes();
        return plan;
    }
    public static ICraftingPlan rewriteForSubmission(ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providers, Level level) {
        observed = plan.patternTimes();
        return plan;
    }
}
