package com.atir.molecularmanipulator.integration.cpu;

import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import net.minecraftforge.fml.ModList;

/** Keeps optional Data Energistics classes out of the base provider's class hierarchy. */
public final class MatterFabricationCpuCompat {
    private MatterFabricationCpuCompat() { }

    public static Runnable registerDataProvider(MatterFabricationPatternLogic logic) {
        if (!ModList.get().isLoaded("data_energistics")) return null;
        try { return DataEnergisticsMatterPatternAdapter.register(logic); }
        catch (RuntimeException | LinkageError unavailable) {
            com.atir.molecularmanipulator.diagnostics.RateLimitedLog.warn("Data Energistics counted provider API is unavailable", unavailable);
            return null;
        }
    }
}
