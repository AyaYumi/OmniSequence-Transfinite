package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.crafting.CraftingPlan;
import com.atir.molecularmanipulator.integration.ae2.OmniSmartDoublingProvider;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.function.Function;

/** Rewrites an ordinary AE2 plan into exact scaled pushes for OmniSequence providers. */
public final class OmniSmartDoublingPlanner {
    private OmniSmartDoublingPlanner() { }

    public static ICraftingPlan rewriteForSubmission(ICraftingPlan plan,
            Function<IPatternDetails, Iterable<ICraftingProvider>> providerLookup) {
        if (plan == null || plan.simulation()) return plan;
        var rewritten = new LinkedHashMap<IPatternDetails, Long>();
        boolean changed = false;
        for (var entry : plan.patternTimes().entrySet()) {
            long operations = entry.getValue() == null ? 0 : entry.getValue();
            if (operations > 1
                    && !(entry.getKey() instanceof com.github.appliedenhancements.integration.ae2.AelisScaledPattern)
                    && !com.atir.molecularmanipulator.crafting.OmniNativeSmartDoubling.isExternallyManaged(entry.getKey())
                    && hasSmartProvider(providerLookup.apply(entry.getKey()))) {
                try {
                    rewritten.put(MolecularScaledPatternFactory.create(entry.getKey(), operations), 1L);
                    changed = true;
                    continue;
                } catch (RuntimeException | LinkageError ignored) { }
            }
            rewritten.merge(entry.getKey(), operations, OmniSmartDoublingPlanner::saturatedAdd);
        }
        if (!changed) return plan;
        var rewrittenPlan = new CraftingPlan(plan.finalOutput(), plan.bytes(), plan.simulation(), plan.multiplePaths(),
                plan.usedItems(), plan.emittedItems(), plan.missingItems(),
                Collections.unmodifiableMap(rewritten));
        try {
            return com.appliedenhancements.api.AelisCycleExecutionApi.copyMetadata(plan, rewrittenPlan);
        } catch (RuntimeException | LinkageError unavailable) {
            // Older Applied versions may not reconcile a mixed external rewrite.
            // Retain the complete original job instead of submitting partial metadata.
            return plan;
        }
    }

    private static boolean hasSmartProvider(Iterable<ICraftingProvider> providers) {
        if (providers == null) return false;
        boolean local = false;
        for (var provider : providers) {
            if (com.atir.molecularmanipulator.crafting.OmniNativeSmartDoubling.isExternallyManagedProvider(provider)) return false;
            if (provider instanceof OmniSmartDoublingProvider) local = true;
        }
        return local;
    }

    private static long saturatedAdd(long left, long right) {
        return right > 0 && left > Long.MAX_VALUE - right ? Long.MAX_VALUE : left + right;
    }
}
