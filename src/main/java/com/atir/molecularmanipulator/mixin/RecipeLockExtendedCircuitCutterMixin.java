package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.glodblock.github.extendedae.common.tileentities.TileCircuitCutter;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;

/** EAEP 1.6.4's super cutter replaces the native tick and bypasses RecipeExecutor. */
@Mixin(value = TileCircuitCutter.class, remap = false)
public abstract class RecipeLockExtendedCircuitCutterMixin {
    @WrapMethod(method = "tickingRequest")
    private TickRateModulation recipeLock$checkNativeAndParallelExecution(IGridNode node, int elapsed,
            Operation<TickRateModulation> original) {
        var machine = (TileCircuitCutter) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) return TickRateModulation.SLEEP;
        if (!RecipeLocks.hasRules(level)) return original.call(node, elapsed);
        try (var scope = RecipeExecutionContext.open(machine)) {
            var context = machine.getContext();
            if (context.currentRecipe != null && !RecipeLocks.allowed(machine, context.currentRecipe)) {
                machine.setWorking(false);
                machine.setProgress(0);
                context.currentRecipe = null;
                context.lastRecipe = null;
            }
            // A new grant must retry even when no inventory callback has cleared stuck.
            if (context.currentRecipe == null) context.stuck = false;
            // WrapMethod surrounds EAEP's injected parallel body as well as the native body.
            // Both can still export already completed output while a recipe is locked.
            var rate = original.call(node, elapsed);
            return context.currentRecipe == null && (rate == TickRateModulation.FASTER || rate == TickRateModulation.SLEEP)
                    ? TickRateModulation.SLOWER : rate;
        }
    }
}
