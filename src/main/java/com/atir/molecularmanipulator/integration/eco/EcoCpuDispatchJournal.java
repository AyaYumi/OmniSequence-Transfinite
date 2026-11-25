package com.atir.molecularmanipulator.integration.eco;

import java.math.BigDecimal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** Imports the old ECO balance and preserves uncertain material ownership as opaque NBT. */
public record EcoCpuDispatchJournal(BigDecimal credit, @Nullable Tag held) {
    public static final String NBT_KEY = "molecularmanipulator:ecoDispatch";
    private static final EcoCpuDispatchJournal EMPTY = new EcoCpuDispatchJournal(BigDecimal.ZERO, null);

    public static EcoCpuDispatchJournal read(@Nullable Tag tag) {
        if (tag == null) return EMPTY;
        if (!(tag instanceof CompoundTag legacy) || legacy.getInt("version") != 1)
            return new EcoCpuDispatchJournal(BigDecimal.ZERO, tag.copy());
        try {
            var credit = legacy.contains("credit") ? new BigDecimal(legacy.getString("credit")) : BigDecimal.ZERO;
            if (credit.signum() < 0) throw new IllegalArgumentException("Negative ECO energy credit");
            if (!legacy.getBoolean("suspended") && !legacy.contains("intent"))
                return new EcoCpuDispatchJournal(credit, null);
            // An interrupted submission is never replayed or converted into refundable CPU stock.
            var held = legacy.copy();
            held.remove("credit");
            return new EcoCpuDispatchJournal(credit, held);
        } catch (IllegalArgumentException failure) {
            return new EcoCpuDispatchJournal(BigDecimal.ZERO, tag.copy());
        }
    }
}
