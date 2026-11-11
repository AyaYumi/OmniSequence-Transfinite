package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.ticking.TickRateModulation;
import appeng.recipes.handlers.InscriberRecipe;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.glodblock.github.extendedae.common.tileentities.TileExInscriber;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

/** EAE uses four independent InscriberThreads; it does not extend InscriberBlockEntity. */
@Mixin(targets = "com.glodblock.github.extendedae.common.me.InscriberThread", remap = false)
public abstract class RecipeLockExtendedInscriberThreadMixin {
    @Shadow @Final private TileExInscriber host;
    @Shadow private InscriberRecipe cachedTask;
    @Unique private long recipeLock$recipeGeneration = Long.MIN_VALUE;

    @WrapMethod(method = "getTask")
    private InscriberRecipe recipeLock$checkQueryAndCache(Operation<InscriberRecipe> original) {
        var level = this.host.getLevel();
        if (level == null || level.isClientSide) {
            return original.call();
        }
        try (var scope = RecipeExecutionContext.open(this.host)) {
            long generation = PatternRecipeReloads.generation();
            if (this.recipeLock$recipeGeneration != generation) {
                // Each thread owns a raw Recipe cache, not a holder/ID. Re-query after /reload
                // without touching this thread's inventory, output, progress or smash state.
                this.cachedTask = null;
                this.recipeLock$recipeGeneration = generation;
            }
            var recipe = original.call();
            return recipe == null || !RecipeLocks.hasRules(level) || RecipeLocks.allowed(this.host, recipe)
                    ? recipe : null;
        }
    }

    @WrapMethod(method = "tick")
    private TickRateModulation recipeLock$executeInHostContext(Operation<TickRateModulation> original) {
        var level = this.host.getLevel();
        if (level == null || level.isClientSide) {
            return TickRateModulation.SLEEP;
        }
        try (var scope = RecipeExecutionContext.open(this.host)) {
            // getTask is rechecked before power, smash start, and smash step eight in this method.
            var rate = original.call();
            return rate == TickRateModulation.SLEEP && RecipeLocks.hasRules(level)
                    ? TickRateModulation.SLOWER : rate;
        }
    }
}
