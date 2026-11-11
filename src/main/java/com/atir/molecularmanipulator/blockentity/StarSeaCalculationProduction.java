package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.security.IActionSource;
import appeng.api.storage.MEStorage;
import com.atir.molecularmanipulator.ae2.StarSignalKey;

/** Configurable output batches, paid in full before insertion. Unspent energy survives reloads and price changes. */
public final class StarSeaCalculationProduction {
    public static final double MAX_ENERGY_PER_TICK = Integer.MAX_VALUE;

    public enum Result { OUTPUT_FULL, NO_POWER, RUNNING }

    private double paidEnergy;

    public double energyCredit() {
        return paidEnergy;
    }

    /** A saved payment may exceed a newly lowered price, but never the largest supported payment. */
    public void restoreEnergyCredit(double savedEnergy) {
        paidEnergy = Double.isFinite(savedEnergy) ? Math.max(0, Math.min(MAX_ENERGY_PER_TICK, savedEnergy)) : 0;
    }

    public Result tick(MEStorage storage, IEnergySource energy, IActionSource source, long bitsPerTick, double energyPerTick) {
        if (!canInsert(storage, source, bitsPerTick)) return Result.OUTPUT_FULL;

        double demand = energyPerTick - paidEnergy;
        if (demand > 0) {
            double extracted = energy.extractAEPower(demand, Actionable.MODULATE, PowerMultiplier.CONFIG);
            if (Double.isFinite(extracted) && extracted > 0) {
                paidEnergy += Math.min(demand, extracted);
            }
        }
        if (paidEnergy < energyPerTick) return Result.NO_POWER;

        long inserted = storage.insert(StarSignalKey.INSTANCE, bitsPerTick, Actionable.MODULATE, source);
        if (inserted <= 0) return Result.OUTPUT_FULL;
        // Storage can accept less than its simulation promised. Spend only the accepted output's share.
        paidEnergy -= energyPerTick * ((double) inserted / bitsPerTick);
        return Result.RUNNING;
    }

    /** A right-click probe must neither buy energy nor generate a signal. */
    public Result inspect(MEStorage storage, IEnergySource energy, IActionSource source, long bitsPerTick, double energyPerTick) {
        if (!canInsert(storage, source, bitsPerTick)) return Result.OUTPUT_FULL;
        double demand = energyPerTick - paidEnergy;
        if (demand <= 0) return Result.RUNNING;
        double available = energy.extractAEPower(demand, Actionable.SIMULATE, PowerMultiplier.CONFIG);
        return Double.isFinite(available) && available >= demand ? Result.RUNNING : Result.NO_POWER;
    }

    private static boolean canInsert(MEStorage storage, IActionSource source, long bitsPerTick) {
        return storage.insert(StarSignalKey.INSTANCE, bitsPerTick, Actionable.SIMULATE, source) == bitsPerTick;
    }
}
