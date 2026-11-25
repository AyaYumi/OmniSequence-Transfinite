package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.crafting.IPatternDetails;
import com.fish_dan_.data_energistics.common.crafting.virtual.VirtualCraftingOutputAdapters;
import com.moakiee.thunderbolt.core.crafting.support.CraftingPatternDelegates;
import com.moakiee.thunderbolt.core.crafting.pattern.IProviderLookupPattern;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** Patterns whose optional native hooks must accompany a bounded single-copy push. */
public final class OmniNativeSinglePatterns {
    private static final boolean HAS_LIGHTNING = loaded("ae2lt");
    private static final boolean HAS_DATA_ENERGISTICS = loaded("data_energistics");

    private OmniNativeSinglePatterns() {
    }

    public static boolean requiresNative(Object cpu, IPatternDetails pattern) {
        return (HAS_LIGHTNING && Lightning.requiresNative(cpu, pattern))
                || (HAS_DATA_ENERGISTICS && DataEnergistics.requiresNative(pattern));
    }

    private static boolean loaded(String modId) {
        var mods = ModList.get();
        return mods != null && mods.isLoaded(modId);
    }

    private static final class Lightning {
        private static final ClassValue<Boolean> OVERLOAD_PATTERN = new ClassValue<>() {
            @Override protected Boolean computeValue(Class<?> type) {
                return isOverloadPattern(type);
            }
        };
        private static final MethodHandle PENDING_COLLISION = collisionMethod();

        private static boolean requiresNative(Object cpu, IPatternDetails pattern) {
            var providerPattern = pattern instanceof IProviderLookupPattern
                    ? CraftingPatternDelegates.forProviderLookup(pattern) : pattern;
            if (OVERLOAD_PATTERN.get(providerPattern.getClass())) return true;
            if (PENDING_COLLISION == null) return false;
            try {
                return (boolean) PENDING_COLLISION.invokeExact(cpu, pattern);
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Throwable failure) {
                throw new IllegalStateException("Could not check Lightning output ownership", failure);
            }
        }

        private static boolean isOverloadPattern(@Nullable Class<?> type) {
            if (type == null) return false;
            String name = type.getName();
            if (name.startsWith("com.moakiee.ae2lt.overload.")
                    && name.endsWith(".OverloadedProviderOnlyPatternDetails")) return true;
            for (var contract : type.getInterfaces()) {
                if (isOverloadPattern(contract)) return true;
            }
            return isOverloadPattern(type.getSuperclass());
        }

        private static @Nullable MethodHandle collisionMethod() {
            // Lightning moved its overload package between releases. The marker check above
            // still keeps overload patterns on the native path if this helper is unavailable.
            for (String namespace : new String[] { "overload.runtime.cpu", "overload.cpu" }) {
                try {
                    var api = Class.forName("com.moakiee.ae2lt." + namespace + ".OverloadCpuInsertSupport", false,
                            Lightning.class.getClassLoader());
                    return MethodHandles.publicLookup().findStatic(api, "hasPendingCollisionWithOrdinaryPattern",
                            MethodType.methodType(boolean.class, Object.class, IPatternDetails.class));
                } catch (ReflectiveOperationException unavailable) {
                    // Try the other known namespace.
                }
            }
            return null;
        }
    }

    private static final class DataEnergistics {
        private static boolean requiresNative(IPatternDetails pattern) {
            return VirtualCraftingOutputAdapters.project(pattern).hasVirtualOutputs();
        }
    }
}
