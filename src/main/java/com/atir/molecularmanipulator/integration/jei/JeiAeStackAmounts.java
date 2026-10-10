package com.atir.molecularmanipulator.integration.jei;

import appeng.api.stacks.GenericStack;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;

/** Carries recipe quantities alongside native JEI ingredients that only describe an AE key. */
public final class JeiAeStackAmounts {
    private static final String PREFIX = "molecularmanipulator:ae_stack_amount/";

    private JeiAeStackAmounts() {}

    static String slotName(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("AE ingredient amount must be positive");
        return PREFIX + amount;
    }

    public static long amount(IRecipeSlotView slot, long fallback) {
        var name = slot.getSlotName().orElse("");
        if (!name.startsWith(PREFIX)) return fallback;
        try {
            long amount = Long.parseLong(name.substring(PREFIX.length()));
            return amount > 0 ? amount : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static GenericStack restore(IRecipeSlotView slot, GenericStack stack) {
        if (stack == null) return null;
        long amount = amount(slot, stack.amount());
        return amount == stack.amount() ? stack : new GenericStack(stack.what(), amount);
    }
}
