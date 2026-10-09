package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Optional native doubling contracts, independent of newer AppliedEnhancements APIs. */
public final class OmniNativeSmartDoubling {
    private static final String EAP_PATTERN = "com.extendedae_plus.api.crafting.ScaledProcessingPattern";
    private static final String USELESS_PATTERN =
            "com.sorrowmist.useless.content.machines.advanced_alloy_furnace.ae.ScaledProcessingPattern";
    private static final String EAP_AWARE = "com.extendedae_plus.api.smartDoubling.ISmartDoublingAwarePattern";
    private static final String USELESS_PROVIDER = "com.sorrowmist.useless.api.crafting.SmartDoublingCraftingProvider";

    private record Access(boolean external, Method enabled) { }
    public record Scale(IPatternDetails original, long multiplier) { }
    private record ScaleAccess(boolean wrapper, Method original, Method multiplier, Field multiplierField) { }

    private static final ClassValue<ScaleAccess> SCALES = new ClassValue<>() {
        @Override protected ScaleAccess computeValue(Class<?> type) {
            boolean wrapper = inherits(type, USELESS_PATTERN) || inherits(type, EAP_PATTERN);
            try {
                if (inherits(type, USELESS_PATTERN)) {
                    return new ScaleAccess(true, type.getMethod("getOriginal"), type.getMethod("getOperationsPerPush"), null);
                }
                if (inherits(type, EAP_PATTERN)) {
                    for (var current = type; current != null; current = current.getSuperclass()) {
                        try {
                            var field = current.getDeclaredField("multiplier");
                            if (field.trySetAccessible()) return new ScaleAccess(true, type.getMethod("getOriginal"), null, field);
                            break;
                        } catch (NoSuchFieldException absent) { /* inherited optional wrapper */ }
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) { }
            return new ScaleAccess(wrapper, null, null, null);
        }
    };

    private static final ClassValue<Access> PATTERNS = new ClassValue<>() {
        @Override protected Access computeValue(Class<?> type) {
            if (inherits(type, EAP_PATTERN) || inherits(type, USELESS_PATTERN)) {
                return new Access(true, null);
            }
            if (!hasInterface(type, EAP_AWARE)) return new Access(false, null);
            try {
                return new Access(false, type.getMethod("eap$allowScaling"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
                // An unreadable native contract must not receive a second multiplier.
                return new Access(true, null);
            }
        }
    };
    private static final ClassValue<Boolean> PROVIDERS = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return hasInterface(type, USELESS_PROVIDER);
        }
    };

    private OmniNativeSmartDoubling() { }

    public static boolean isNativeScaledPattern(IPatternDetails pattern) {
        return SCALES.get(pattern.getClass()).wrapper();
    }

    /** Reads one known native wrapper layer without requiring the Applied scaled-pattern interface. */
    public static Scale resolveScale(IPatternDetails pattern) {
        var access = SCALES.get(pattern.getClass());
        if (access.original() == null) return null;
        try {
            var original = access.original().invoke(pattern);
            long multiplier = access.multiplierField() != null ? access.multiplierField().getLong(pattern)
                    : ((Number) access.multiplier().invoke(pattern)).longValue();
            if (original instanceof IPatternDetails base && base != pattern && multiplier > 0) {
                return new Scale(base, multiplier);
            }
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError unavailable) { }
        return null;
    }

    public static boolean isExternallyManaged(IPatternDetails pattern) {
        if (pattern == null) return false;
        var access = PATTERNS.get(pattern.getClass());
        if (access.external()) return true;
        if (access.enabled() == null) return false;
        try {
            // Cache discovery only: this flag can change on an existing pattern.
            return !(access.enabled().invoke(pattern) instanceof Boolean enabled) || enabled;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException | LinkageError unavailable) {
            return true;
        }
    }

    public static boolean isExternallyManagedProvider(Object provider) {
        return provider != null && PROVIDERS.get(provider.getClass());
    }

    private static boolean inherits(Class<?> type, String name) {
        for (var current = type; current != null; current = current.getSuperclass()) {
            if (name.equals(current.getName())) return true;
        }
        return false;
    }

    private static boolean hasInterface(Class<?> type, String name) {
        if (type == null) return false;
        if (name.equals(type.getName())) return true;
        for (var contract : type.getInterfaces()) {
            if (hasInterface(contract, name)) return true;
        }
        return hasInterface(type.getSuperclass(), name);
    }
}
