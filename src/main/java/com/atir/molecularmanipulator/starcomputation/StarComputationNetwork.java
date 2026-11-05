package com.atir.molecularmanipulator.starcomputation;

import appeng.api.networking.IGrid;
import com.atir.molecularmanipulator.blockentity.StarComputationEngineCoreBlockEntity;
import org.jetbrains.annotations.Nullable;

/** Network-local selection: one engine supplies the effective capability; engines never multiply. */
public final class StarComputationNetwork {
    private StarComputationNetwork() {}

    @Nullable
    public static StarComputationEngineCoreBlockEntity best(@Nullable IGrid grid) {
        if (grid == null) return null;
        StarComputationEngineCoreBlockEntity best = null;
        for (var engine : grid.getMachines(StarComputationEngineCoreBlockEntity.class)) {
            if (engine.isRemoved() || !engine.isFormed() || !engine.getMainNode().isActive()) continue;
            if (best == null || compare(engine, best) > 0) best = engine;
        }
        return best;
    }

    private static int compare(StarComputationEngineCoreBlockEntity a, StarComputationEngineCoreBlockEntity b) {
        // getBenefits is the runtime-validated level (armillary rings must be
        // verified now, never inferred from a stale raw entropy value).
        int level = Integer.compare(a.getBenefits().level(), b.getBenefits().level());
        if (level != 0) return level;
        int clock = Boolean.compare(a.isOverclocked(), b.isOverclocked());
        return clock != 0 ? clock : Long.compare(b.getBlockPos().asLong(), a.getBlockPos().asLong());
    }

    public static StarComputationBenefits forGrid(@Nullable IGrid grid) {
        var engine = best(grid);
        if (engine == null) return StarComputationBenefits.NONE;
        engine.payOverclockForCurrentTick();
        return engine.getBenefits();
    }

    public static int unlockMask(@Nullable IGrid grid) {
        if (grid == null) return 0;
        int mask = 0;
        for (var engine : grid.getMachines(StarComputationEngineCoreBlockEntity.class)) {
            if (!engine.isRemoved() && engine.isFormed() && engine.getMainNode().isActive()) mask |= engine.getUnlockMask();
        }
        return mask;
    }
}
