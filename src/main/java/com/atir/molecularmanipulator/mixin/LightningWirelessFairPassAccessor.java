package com.atir.molecularmanipulator.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

@Pseudo
@Mixin(targets = "com.moakiee.ae2lt.logic.provider.DispatchFairnessScheduler$Pass", remap = false)
public interface LightningWirelessFairPassAccessor {
    @Invoker("raiseAllowance")
    long molecularmanipulator$raiseAllowance(Object target, long minimumAllowance);
}
