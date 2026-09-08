package com.atir.molecularmanipulator.mixin;

import appeng.me.Grid;
import com.atir.molecularmanipulator.integration.ae2.SegmentedPatternContainers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * Makes terminal-only pattern segmentation available to every consumer of the
 * standard AE2 active-machine query, including third-party pattern terminals.
 */
@Mixin(value = Grid.class, remap = false)
public abstract class GridActiveMachinesMixin {
    @Inject(method = "getActiveMachines", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("unchecked")
    private <T> void molecularmanipulator$expandPatternContainers(Class<T> machineClass,
            CallbackInfoReturnable<Set<T>> callback) {
        Set<T> machines = callback.getReturnValue();
        Set<?> expanded = SegmentedPatternContainers.expandActiveMachines(machineClass, machines);
        if (expanded != machines) {
            callback.setReturnValue((Set<T>) expanded);
        }
    }
}
