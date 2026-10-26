package com.atir.molecularmanipulator.entropy;

import appeng.api.networking.IGrid;
import com.atir.molecularmanipulator.blockentity.NegativeEntropyEngineCoreBlockEntity;
import org.jetbrains.annotations.Nullable;

/** Network-local selection: one engine supplies the effective capability; engines never multiply. */
public final class NegativeEntropyNetwork {
    private NegativeEntropyNetwork() {}

    @Nullable
    public static NegativeEntropyEngineCoreBlockEntity best(@Nullable IGrid grid) {
        if (grid == null) return null;
        NegativeEntropyEngineCoreBlockEntity best = null;
        for (var engine : grid.getMachines(NegativeEntropyEngineCoreBlockEntity.class)) {
            if (engine.isRemoved() || !engine.isFormed() || !engine.getMainNode().isActive()) continue;
            if (best == null || compare(engine, best) > 0) best = engine;
        }
        return best;
    }

    private static int compare(NegativeEntropyEngineCoreBlockEntity a, NegativeEntropyEngineCoreBlockEntity b) {
        int level = Integer.compare(NegativeEntropyBenefits.levelFor(a.getEffectiveEntropy()),
                NegativeEntropyBenefits.levelFor(b.getEffectiveEntropy()));
        if (level != 0) return level;
        int clock = Boolean.compare(a.isOverclocked(), b.isOverclocked());
        return clock != 0 ? clock : Long.compare(b.getBlockPos().asLong(), a.getBlockPos().asLong());
    }

    public static NegativeEntropyBenefits forGrid(@Nullable IGrid grid) {
        var engine = best(grid);
        if (engine == null) return NegativeEntropyBenefits.NONE;
        engine.payOverclockForCurrentTick();
        return engine.getBenefits();
    }

    public static int unlockMask(@Nullable IGrid grid) {
        if (grid == null) return 0;
        int mask = 0;
        for (var engine : grid.getMachines(NegativeEntropyEngineCoreBlockEntity.class)) {
            if (!engine.isRemoved() && engine.isFormed() && engine.getMainNode().isActive()) mask |= engine.getUnlockMask();
        }
        return mask;
    }
}
