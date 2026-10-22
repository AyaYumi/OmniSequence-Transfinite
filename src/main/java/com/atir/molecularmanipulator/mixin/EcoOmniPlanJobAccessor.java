package com.atir.molecularmanipulator.mixin;

import appeng.crafting.execution.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Retains the original submitting player's storage permissions for deferred material pulls. */
@Mixin(value = ExecutingCraftingJob.class, remap = false)
public interface EcoOmniPlanJobAccessor {
    @Accessor("playerId") Integer ecoOmni$playerId();
}
