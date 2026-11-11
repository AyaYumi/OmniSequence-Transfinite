package com.atir.molecularmanipulator.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Optional early rejection at actual dispatch, not getAvailablePatterns or pattern encoding. */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class RecipeLockAEPatternProviderMixin {
    @Shadow @Final private PatternProviderLogicHost host;

    @WrapMethod(method = "pushPattern")
    private boolean recipeLock$beforeDispatch(IPatternDetails pattern, KeyCounter[] inputs, Operation<Boolean> original) {
        var machine = this.host.getBlockEntity();
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return false;
        }
        // Unidentified processing patterns must still be checked by their real target executor.
        if (RecipeLocks.hasRules(level) && !RecipeLocks.allowedPattern(machine, pattern)) {
            return false;
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            return original.call(pattern, inputs);
        }
    }
}
