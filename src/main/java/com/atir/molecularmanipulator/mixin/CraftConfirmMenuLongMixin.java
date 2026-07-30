package com.atir.molecularmanipulator.mixin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.stacks.AEKey;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.atir.molecularmanipulator.integration.ae2.LongCraftingConfirmMenuBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Bridge for AE2 implementations such as AE2-UELM that natively use a long
 * crafting amount. The mixin plugin only enables this path when the target
 * {@code amount} field has the JVM descriptor {@code J}.
 */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuLongMixin implements LongCraftingConfirmMenuBridge {
    @Unique
    private static final Method molecularmanipulator$planJobLong =
            molecularmanipulator$findPlanJobLong();

    @Override
    public boolean molecularmanipulator$planLong(AEKey what, long amount, CalculationStrategy strategy) {
        try {
            return (boolean) molecularmanipulator$planJobLong.invoke(this, what, amount, strategy);
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Unable to invoke AE2-UELM CraftConfirmMenu.planJob(long)", exception);
        } catch (InvocationTargetException exception) {
            var cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("AE2-UELM CraftConfirmMenu.planJob(long) failed", cause);
        }
    }

    @Unique
    private static Method molecularmanipulator$findPlanJobLong() {
        try {
            return CraftConfirmMenu.class.getMethod(
                    "planJob",
                    AEKey.class,
                    long.class,
                    CalculationStrategy.class);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(
                    "AE2 CraftConfirmMenu stores long amounts but has no planJob(AEKey, long, CalculationStrategy)",
                    exception);
        }
    }
}
