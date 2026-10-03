package com.atir.molecularmanipulator.crafting;

import appeng.api.crafting.IPatternDetails;

import java.lang.reflect.InvocationTargetException;
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
        return preserveOptionalInterfaces(base, scaled, multiplier);
    }

    private static IPatternDetails preserveOptionalInterfaces(
            IPatternDetails base, IPatternDetails scaled, long multiplier) {
        var preservedInterfaces = new ArrayList<Class<?>>();
        // Any optional proxy is still our local wrapper and must retain its exact scale.
        preservedInterfaces.add(com.github.appliedenhancements.integration.ae2.AelisScaledPattern.class);
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
                            if (method.getDeclaringClass()
                                    == com.github.appliedenhancements.integration.ae2.AelisScaledPattern.class) {
                                return switch (method.getName()) {
                                    case "appliedenhancements$originalPattern" -> base;
                                    case "appliedenhancements$operationsPerPush" -> multiplier;
                                    default -> throw new UnsupportedOperationException(
                                            "Unknown AelisScaledPattern method: " + method);
                                };
                            }
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
            // Keep the AppliedEnhancements contract even when an optional
            // integration uses an incompatible class loader. The local
            // implementation already carries the same scaled recipe and is
            // safer than returning an unmarked external wrapper.
            return new MolecularScaledPattern(base, multiplier);
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
