package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Recognizes planning-time scaled patterns from optional integrations.
 *
 * <p>ExtendedAE Plus can replace one processing-pattern task with a wrapper
 * whose inputs and outputs already represent many crafts. That is safe only
 * while the whole vector remains one atomic machine operation. Omni normalizes
 * multi-key wrappers back to the original pattern before applying its own
 * provider-aware dispatch policy.</p>
 */
public final class MolecularExternalScaledPattern {
    private static final String EAP_SCALED_PATTERN =
            "com.extendedae_plus.api.crafting.ScaledProcessingPattern";
    private static final int MAX_NESTING = 16;

    private static volatile Lookup lookup;

    private MolecularExternalScaledPattern() {
    }

    public static Unwrapped unwrapMultiInput(IPatternDetails patternDetails) {
        if (patternDetails == null) {
            throw new IllegalArgumentException("Pattern details cannot be null");
        }

        var resolvedLookup = getLookup(patternDetails.getClass().getClassLoader());
        if (resolvedLookup.missing()
                || !resolvedLookup.scaledPatternClass().isInstance(patternDetails)) {
            return new Unwrapped(patternDetails, 1);
        }
        if (resolvedLookup.failure() != null) {
            throw new IllegalStateException(
                    "ExtendedAE Plus scaled-pattern API could not be inspected",
                    resolvedLookup.failure());
        }

        IPatternDetails current = patternDetails;
        long combinedMultiplier = 1;
        int depth = 0;
        var visited = new IdentityHashMap<IPatternDetails, Boolean>();
        while (resolvedLookup.scaledPatternClass().isInstance(current)) {
            if (++depth > MAX_NESTING || visited.put(current, Boolean.TRUE) != null) {
                throw new IllegalStateException(
                        "ExtendedAE Plus scaled-pattern nesting is cyclic or too deep");
            }

            var original = getOriginal(resolvedLookup, current);
            long multiplier = getMultiplier(resolvedLookup, current, original);
            validateScaledLayer(current, original, multiplier);
            combinedMultiplier = Math.multiplyExact(combinedMultiplier, multiplier);
            current = original;
        }

        if (!hasMultipleDistinctInputTemplates(current)) {
            // Retain EAP's high-throughput wrapper when every input resolves to
            // the same key. Sequential routing cannot split that recipe into
            // mutually blocking ingredient waves.
            return new Unwrapped(patternDetails, 1);
        }
        return new Unwrapped(current, combinedMultiplier);
    }

    private static Lookup getLookup(ClassLoader classLoader) {
        var current = lookup;
        if (current != null) {
            return current;
        }
        synchronized (MolecularExternalScaledPattern.class) {
            current = lookup;
            if (current != null) {
                return current;
            }
            Class<?> scaledPatternClass;
            try {
                scaledPatternClass = Class.forName(
                        EAP_SCALED_PATTERN, false, classLoader);
            } catch (ClassNotFoundException exception) {
                current = Lookup.absent();
                lookup = current;
                return current;
            } catch (LinkageError exception) {
                current = new Lookup(
                        Object.class, null, null, false, exception);
                lookup = current;
                return current;
            }

            try {
                var getOriginal = scaledPatternClass.getMethod("getOriginal");
                var multiplier = findField(scaledPatternClass, "multiplier");
                if (multiplier != null) {
                    try {
                        multiplier.setAccessible(true);
                    } catch (RuntimeException exception) {
                        // Public input/output ratios remain sufficient to derive
                        // and validate the multiplier.
                        multiplier = null;
                    }
                }
                current = new Lookup(
                        scaledPatternClass, getOriginal, multiplier,
                        false, null);
            } catch (ReflectiveOperationException | RuntimeException
                    | LinkageError exception) {
                current = new Lookup(
                        scaledPatternClass, null, null, false, exception);
            }
            lookup = current;
            return current;
        }
    }

    private static Field findField(Class<?> type, String name) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Continue through compatibility subclasses and API revisions.
            }
        }
        return null;
    }

    private static IPatternDetails getOriginal(Lookup resolvedLookup,
            IPatternDetails scaledPattern) {
        try {
            var original = resolvedLookup.getOriginal().invoke(scaledPattern);
            if (!(original instanceof IPatternDetails patternDetails)
                    || patternDetails == scaledPattern) {
                throw new IllegalStateException(
                        "ExtendedAE Plus returned an invalid original pattern");
            }
            return patternDetails;
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException(
                    "ExtendedAE Plus original pattern is inaccessible", exception);
        } catch (InvocationTargetException exception) {
            throw invocationFailure(
                    "ExtendedAE Plus original pattern lookup failed", exception);
        }
    }

    private static long getMultiplier(Lookup resolvedLookup,
            IPatternDetails scaledPattern, IPatternDetails original) {
        var multiplierField = resolvedLookup.multiplier();
        if (multiplierField != null) {
            try {
                long multiplier = multiplierField.getLong(scaledPattern);
                if (multiplier > 0) {
                    return multiplier;
                }
            } catch (IllegalAccessException | RuntimeException ignored) {
                // Derive and validate the ratio below for API-compatible versions.
            }
        }
        return deriveMultiplier(scaledPattern, original);
    }

    private static long deriveMultiplier(IPatternDetails scaledPattern,
            IPatternDetails original) {
        long candidate = 0;
        var scaledInputs = scaledPattern.getInputs();
        var originalInputs = original.getInputs();
        if (scaledInputs != null && originalInputs != null
                && scaledInputs.length == originalInputs.length) {
            for (int index = 0; index < scaledInputs.length; index++) {
                var scaledInput = scaledInputs[index];
                var originalInput = originalInputs[index];
                if (scaledInput == null || originalInput == null) {
                    continue;
                }
                candidate = mergeRatio(
                        candidate,
                        scaledInput.getMultiplier(),
                        originalInput.getMultiplier());
            }
        }

        var scaledOutputs = scaledPattern.getOutputs();
        var originalOutputs = original.getOutputs();
        if (scaledOutputs != null && originalOutputs != null
                && scaledOutputs.length == originalOutputs.length) {
            for (int index = 0; index < scaledOutputs.length; index++) {
                var scaledOutput = scaledOutputs[index];
                var originalOutput = originalOutputs[index];
                if (scaledOutput == null || originalOutput == null
                        || !Objects.equals(scaledOutput.what(), originalOutput.what())) {
                    continue;
                }
                candidate = mergeRatio(
                        candidate, scaledOutput.amount(), originalOutput.amount());
            }
        }
        if (candidate <= 0) {
            throw new IllegalStateException(
                    "ExtendedAE Plus scaled-pattern multiplier is unavailable");
        }
        return candidate;
    }

    private static long mergeRatio(long candidate, long scaled, long original) {
        if (scaled <= 0 || original <= 0 || scaled % original != 0) {
            return candidate;
        }
        long ratio = scaled / original;
        if (ratio <= 0) {
            return candidate;
        }
        if (candidate != 0 && candidate != ratio) {
            throw new IllegalStateException(
                    "ExtendedAE Plus scaled-pattern ratios are inconsistent");
        }
        return ratio;
    }

    private static void validateScaledLayer(IPatternDetails scaledPattern,
            IPatternDetails original, long multiplier) {
        if (multiplier <= 0
                || !Objects.equals(
                        scaledPattern.getDefinition(), original.getDefinition())) {
            throw new IllegalStateException(
                    "ExtendedAE Plus scaled-pattern identity is inconsistent");
        }

        var scaledInputs = scaledPattern.getInputs();
        var originalInputs = original.getInputs();
        if (scaledInputs == null || originalInputs == null
                || scaledInputs.length != originalInputs.length) {
            throw new IllegalStateException(
                    "ExtendedAE Plus scaled-pattern inputs are inconsistent");
        }
        for (int index = 0; index < originalInputs.length; index++) {
            var scaledInput = scaledInputs[index];
            var originalInput = originalInputs[index];
            if (scaledInput == null || originalInput == null
                    || scaledInput.getMultiplier()
                            != Math.multiplyExact(
                                    originalInput.getMultiplier(), multiplier)
                    || !Arrays.equals(
                            scaledInput.getPossibleInputs(),
                            originalInput.getPossibleInputs())) {
                throw new IllegalStateException(
                        "ExtendedAE Plus scaled-pattern input ratio is inconsistent");
            }
        }

        validateOutputs(
                java.util.Arrays.asList(scaledPattern.getOutputs()), java.util.Arrays.asList(original.getOutputs()), multiplier);
    }

    private static void validateOutputs(List<GenericStack> scaledOutputs,
            List<GenericStack> originalOutputs, long multiplier) {
        if (scaledOutputs == null || originalOutputs == null
                || scaledOutputs.size() != originalOutputs.size()) {
            throw new IllegalStateException(
                    "ExtendedAE Plus scaled-pattern outputs are inconsistent");
        }
        for (int index = 0; index < originalOutputs.size(); index++) {
            var scaledOutput = scaledOutputs.get(index);
            var originalOutput = originalOutputs.get(index);
            if (scaledOutput == null || originalOutput == null
                    || !Objects.equals(scaledOutput.what(), originalOutput.what())
                    || scaledOutput.amount()
                            != Math.multiplyExact(
                                    originalOutput.amount(), multiplier)) {
                throw new IllegalStateException(
                        "ExtendedAE Plus scaled-pattern output ratio is inconsistent");
            }
        }
    }

    private static boolean hasMultipleDistinctInputTemplates(
            IPatternDetails patternDetails) {
        Set<AEKey> keys = new HashSet<>();
        var inputs = patternDetails.getInputs();
        if (inputs == null) {
            throw new IllegalStateException("Original pattern inputs are missing");
        }
        for (var input : inputs) {
            if (input == null || input.getPossibleInputs() == null) {
                throw new IllegalStateException(
                        "Original pattern input templates are missing");
            }
            for (var possibleInput : input.getPossibleInputs()) {
                if (possibleInput == null || possibleInput.what() == null
                        || possibleInput.amount() <= 0) {
                    throw new IllegalStateException(
                            "Original pattern input template is invalid");
                }
                keys.add(possibleInput.what());
                if (keys.size() > 1) {
                    return true;
                }
            }
        }
        return false;
    }

    private static IllegalStateException invocationFailure(
            String message, InvocationTargetException exception) {
        var cause = exception.getCause();
        return new IllegalStateException(
                message, cause == null ? exception : cause);
    }

    public record Unwrapped(IPatternDetails patternDetails, long multiplier) {
        public Unwrapped {
            Objects.requireNonNull(patternDetails, "patternDetails");
            if (multiplier <= 0) {
                throw new IllegalArgumentException("multiplier must be positive");
            }
        }
    }

    private record Lookup(Class<?> scaledPatternClass, Method getOriginal,
            Field multiplier, boolean missing, Throwable failure) {
        private static Lookup absent() {
            return new Lookup(Object.class, null, null, true, null);
        }
    }
}
