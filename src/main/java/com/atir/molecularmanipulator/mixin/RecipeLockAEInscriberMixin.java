package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.recipes.handlers.InscriberRecipe;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** getTask is used before power/progress, before starting the smash, and at smash step eight. */
@Mixin(value = InscriberBlockEntity.class, remap = false)
public abstract class RecipeLockAEInscriberMixin {
    @Shadow private InscriberRecipe cachedTask;
    @Unique private long recipeLock$recipeGeneration = Long.MIN_VALUE;

    @WrapMethod(method = "getTask")
    private InscriberRecipe recipeLock$checkQueryAndCache(Operation<InscriberRecipe> original) {
        var machine = (InscriberBlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return original.call();
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            long generation = PatternRecipeReloads.generation();
            if (this.recipeLock$recipeGeneration != generation) {
                // A reload replaces recipe objects without changing these inventory stacks. Only
                // invalidate the lookup: keep progress, smash state, inputs and finished output.
                this.cachedTask = null;
                this.recipeLock$recipeGeneration = generation;
            }
            var recipe = original.call();
            // Check every cached return, not just InscriberRecipes.findRecipe's first lookup.
            return recipe == null || !RecipeLocks.hasRules(level) || RecipeLocks.allowed(machine, recipe)
                    ? recipe : null;
        }
    }

    @WrapMethod(method = "getTickingRequest")
    private TickingRequest recipeLock$pollAfterLoad(IGridNode node, Operation<TickingRequest> original) {
        var machine = (InscriberBlockEntity) (Object) this;
        var request = original.call(node);
        var level = machine.getLevel();
        if (level != null && !level.isClientSide && RecipeLocks.hasRules(level) && request.isSleeping()) {
            return new TickingRequest(request.minTickRate(), request.maxTickRate(), false,
                    request.canBeAlerted(), request.initialTickRate());
        }
        return request;
    }

    @WrapMethod(method = "tickingRequest")
    private TickRateModulation recipeLock$executeInMachineContext(IGridNode node, int elapsed,
            Operation<TickRateModulation> original) {
        var machine = (InscriberBlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return TickRateModulation.SLEEP;
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            var rate = original.call(node, elapsed);
            // Unlocks and the disappearance/reappearance of an online engine do not change inventory.
            return rate == TickRateModulation.SLEEP && RecipeLocks.hasRules(level)
                    ? TickRateModulation.SLOWER : rate;
        }
    }
}
