package com.atir.molecularmanipulator.util;

import net.minecraftforge.fluids.FluidStack;

public final class ForgeFluids {
    private ForgeFluids() {}
    public static FluidStack copyWithAmount(FluidStack source, int amount) {
        if (source.isEmpty() || amount <= 0) return FluidStack.EMPTY;
        var copy = source.copy();
        copy.setAmount(amount);
        return copy;
    }
}
