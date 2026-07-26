package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Creates a runtime-scaled pattern while preserving optional provider-specific
 * pattern interfaces when they are present.
 */
public final class MolecularScaledPatternFactory {
    private static final String ADVANCED_PATTERN_INTERFACE =
            "net.pedroksl.advanced_ae.common.patterns.IAdvPatternDetails";
    private static final String AE2LT_OVERLOADED_PATTERN_INTERFACE =
            "com.moakiee.ae2lt.overload.pattern.OverloadedProviderOnlyPatternDetails";
    private static final String EAP_PATTERN_SCALER =
            "com.extendedae_plus.util.smartDoubling.PatternScaler";
    private static final String AE2_PROCESSING_PATTERN =
            "appeng.crafting.pattern.AEProcessingPattern";
    private static final String ADVANCED_PROCESSING_PATTERN =
            "net.pedroksl.advanced_ae.common.patterns.AdvProcessingPattern";
    private static final List<String> OPTIONAL_PATTERN_INTERFACES = List.of(
            ADVANCED_PATTERN_INTERFACE,
            AE2LT_OVERLOADED_PATTERN_INTERFACE);

    private MolecularScaledPatternFactory() {
    }

    public static IPatternDetails create(IPatternDetails base, long multiplier) {
        // Constructing our checked implementation first validates every scaled
        // input/output multiplication even when an optional compatibility wrapper
        // is selected below.
        IPatternDetails scaled = new MolecularScaledPattern(base, multiplier);
        if (isClassOrSubclass(base, AE2_PROCESSING_PATTERN)
                || isClassOrSubclass(base, ADVANCED_PROCESSING_PATTERN)) {
            var eapScaled = createWithExtendedAePlus(base, multiplier);
            if (eapScaled != null) {
                scaled = eapScaled;
            }
        }
        return preserveOptionalInterfaces(base, scaled);
    }

    private static boolean isClassOrSubclass(
            IPatternDetails patternDetails, String className) {
        for (Class<?> type = patternDetails.getClass();
                type != null; type = type.getSuperclass()) {
            if (className.equals(type.getName())) {
                return true;
            }
        }
        return false;
    }

    private static IPatternDetails createWithExtendedAePlus(
            IPatternDetails base, long multiplier) {
        try {
            var scaler = Class.forName(EAP_PATTERN_SCALER, false,
                    base.getClass().getClassLoader());
            Method factory = scaler.getMethod(
                    "createScaled", IPatternDetails.class, long.class);
            var scaled = factory.invoke(null, base, multiplier);
            return scaled instanceof IPatternDetails details ? details : null;
        } catch (ClassNotFoundException | NoSuchMethodException
                | IllegalAccessException | InvocationTargetException
                | LinkageError exception) {
            return null;
        }
    }

    private static IPatternDetails preserveOptionalInterfaces(
            IPatternDetails base, IPatternDetails scaled) {
        var preservedInterfaces = new ArrayList<Class<?>>();
        for (var interfaceName : OPTIONAL_PATTERN_INTERFACES) {
            var optionalInterface = loadOptionalInterface(base, interfaceName);
            if (optionalInterface != null
                    && optionalInterface.isInstance(base)
                    && Modifier.isPublic(optionalInterface.getModifiers())) {
                preservedInterfaces.add(optionalInterface);
            }
        }
        if (preservedInterfaces.isEmpty()
                || preservedInterfaces.stream().allMatch(type -> type.isInstance(scaled))) {
            return scaled;
        }

        var proxyInterfaces = new Class<?>[preservedInterfaces.size() + 1];
        proxyInterfaces[0] = IPatternDetails.class;
        for (int index = 0; index < preservedInterfaces.size(); index++) {
            proxyInterfaces[index + 1] = preservedInterfaces.get(index);
        }

        try {
            var proxy = Proxy.newProxyInstance(
                    base.getClass().getClassLoader(),
                    proxyInterfaces,
                    (instance, method, arguments) -> {
                        if (method.getDeclaringClass() == Object.class) {
                            return switch (method.getName()) {
                                case "equals" -> instance == arguments[0];
                                case "hashCode" -> System.identityHashCode(instance);
                                case "toString" -> "ProviderCompatible" + scaled;
                                default -> method.invoke(scaled, arguments);
                            };
                        }

                        try {
                            if (method.getDeclaringClass() == IPatternDetails.class
                                    || method.getDeclaringClass().isInstance(scaled)) {
                                return method.invoke(scaled, arguments);
                            }
                            return method.invoke(base, arguments);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            return (IPatternDetails) proxy;
        } catch (IllegalArgumentException | LinkageError exception) {
            return scaled;
        }
    }

    private static Class<?> loadOptionalInterface(
            IPatternDetails patternDetails, String interfaceName) {
        try {
            var optionalInterface = Class.forName(
                    interfaceName, false, patternDetails.getClass().getClassLoader());
            return optionalInterface.isInterface() ? optionalInterface : null;
        } catch (ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }
}
