package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import com.appliedenhancements.runtime.ExactScaledTaskReconciliation.Result;
import com.github.appliedenhancements.integration.ae2.AelisScaledPattern;
import java.math.BigInteger;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Nonthrowing batch reconciliation for Applied Enhancements 1.1.0. */
public final class LegacyAelisTaskReconciliation {
    private static final BigInteger WINDOW = BigInteger.valueOf(Long.MAX_VALUE - 1);

    private LegacyAelisTaskReconciliation() { }

    private record Scale(IPatternDetails original, long multiplier) { }

    public static Result reconcile(Map<IPatternDetails, Long> projected,
            Map<IPatternDetails, BigInteger> exact) {
        // Metadata copied from an already reconciled plan is expressed in pushes.
        if (exact.isEmpty() || exact.keySet().equals(projected.keySet())) {
            return new Result(projected, exact);
        }
        var batches = new LinkedHashMap<IPatternDetails, Map<IPatternDetails, Long>>();
        try {
            for (var candidate : projected.keySet()) {
                if (exact.containsKey(candidate)) continue;
                var scale = resolve(candidate);
                if (scale == null) return originalTasks(exact);
                var original = findOriginal(exact, scale.original());
                if (original == null) return originalTasks(exact);
                batches.computeIfAbsent(original, ignored -> new LinkedHashMap<>())
                        .put(candidate, scale.multiplier());
            }
        } catch (RuntimeException | LinkageError unavailable) {
            // Unknown optional wrappers use complete original work, never a partial plan.
            return originalTasks(exact);
        }
        var corrected = new LinkedHashMap<>(exact);
        batches.forEach((original, wrappers) -> {
            var amount = corrected.remove(original);
            if (amount == null || amount.signum() <= 0) return;
            if (wrappers.keySet().stream().anyMatch(OmniNativeSmartDoubling::isExternallyManaged)) {
                var nativeTasks = new LinkedHashMap<>(wrappers);
                if (projected.containsKey(original)) nativeTasks.put(original, 1L);
                BigInteger nativeWork = BigInteger.ZERO;
                for (var task : nativeTasks.entrySet()) {
                    long pushes = projected.getOrDefault(task.getKey(), 0L);
                    if (pushes > 0) nativeWork = nativeWork.add(BigInteger.valueOf(pushes)
                            .multiply(BigInteger.valueOf(task.getValue())));
                }
                if (nativeWork.equals(amount)) {
                    // Keep native provider splits and their exact wrapper identities.
                    nativeTasks.forEach((pattern, scale) -> {
                        long pushes = projected.getOrDefault(pattern, 0L);
                        if (pushes > 0) corrected.put(pattern, BigInteger.valueOf(pushes));
                    });
                    return;
                }
            }
            var batch = wrappers.entrySet().stream().max(Map.Entry.comparingByValue()).orElse(null);
            if (batch == null) {
                corrected.put(original, amount);
                return;
            }
            var parts = amount.divideAndRemainder(BigInteger.valueOf(batch.getValue()));
            if (parts[0].signum() > 0) corrected.put(batch.getKey(), parts[0]);
            if (parts[1].signum() > 0) corrected.put(original, parts[1]);
        });
        return originalTasks(corrected);
    }

    private static Result originalTasks(Map<IPatternDetails, BigInteger> exact) {
        var tasks = new LinkedHashMap<IPatternDetails, BigInteger>();
        var windows = new LinkedHashMap<IPatternDetails, Long>();
        exact.forEach((pattern, amount) -> {
            if (pattern != null && amount != null && amount.signum() > 0) {
                tasks.put(pattern, amount);
                windows.put(pattern, amount.min(WINDOW).longValueExact());
            }
        });
        return new Result(Map.copyOf(windows), Map.copyOf(tasks));
    }

    private static IPatternDetails findOriginal(Map<IPatternDetails, BigInteger> exact,
            IPatternDetails original) {
        if (exact.containsKey(original)) return original;
        var definition = original.getDefinition();
        if (definition == null) return null;
        IPatternDetails found = null;
        for (var pattern : exact.keySet()) {
            if (definition.equals(pattern.getDefinition())) {
                if (found != null) return null;
                found = pattern;
            }
        }
        return found;
    }

    private static Scale resolve(IPatternDetails pattern) {
        var visited = new IdentityHashMap<IPatternDetails, Boolean>();
        var current = pattern;
        long multiplier = 1;
        while (current != null && visited.put(current, Boolean.TRUE) == null) {
            IPatternDetails original;
            long scale;
            var nativeScale = OmniNativeSmartDoubling.resolveScale(current);
            if (nativeScale == null && OmniNativeSmartDoubling.isNativeScaledPattern(current)) return null;
            if (nativeScale != null) {
                original = nativeScale.original();
                scale = nativeScale.multiplier();
            } else if (current instanceof AelisScaledPattern scaled) {
                original = scaled.appliedenhancements$originalPattern();
                scale = scaled.appliedenhancements$operationsPerPush();
            } else {
                return current == pattern ? null : new Scale(current, multiplier);
            }
            if (original == null || scale <= 0) return null;
            multiplier = Math.multiplyExact(multiplier, scale);
            current = original;
        }
        return null;
    }
}
