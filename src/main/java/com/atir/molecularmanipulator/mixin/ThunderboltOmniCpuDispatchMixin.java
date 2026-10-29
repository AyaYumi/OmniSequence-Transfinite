package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.energy.IEnergyService;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.integration.ae2.OmniNativeBatchHost;
import com.atir.molecularmanipulator.integration.thunderbolt.OmniThunderboltDispatch;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Optional native executor; the shared mixin decides when accounting may enter it. */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class ThunderboltOmniCpuDispatchMixin implements OmniNativeBatchHost.Runner {
    @Unique private OmniThunderboltDispatch molecularmanipulator$thunderbolt;

    @Override public int omniNative$run(int maximum, CraftingService crafting,
            IEnergyService energy, Level level) {
        if (molecularmanipulator$thunderbolt == null)
            molecularmanipulator$thunderbolt = new OmniThunderboltDispatch();
        return molecularmanipulator$thunderbolt.run((OmniNativeBatchHost) this,
                maximum, crafting, energy, level);
    }
}
