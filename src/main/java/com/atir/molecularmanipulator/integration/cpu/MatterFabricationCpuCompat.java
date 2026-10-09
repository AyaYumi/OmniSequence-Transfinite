package com.atir.molecularmanipulator.integration.cpu;

import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import net.neoforged.fml.ModList;

/** Keeps optional Data Energistics classes out of the base provider's class hierarchy. */
public final class MatterFabricationCpuCompat {
    private MatterFabricationCpuCompat() { }

    public static Runnable registerDataProvider(MatterFabricationPatternLogic logic) {
        return ModList.get().isLoaded("data_energistics") ? DataEnergisticsMatterPatternAdapter.register(logic) : null;
    }
}
