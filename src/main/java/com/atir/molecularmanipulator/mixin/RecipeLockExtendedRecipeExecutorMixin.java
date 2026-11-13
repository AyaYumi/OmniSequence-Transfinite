package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.ticking.TickRateModulation;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.glodblock.github.extendedae.api.IRecipeMachine;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** EAE 1.4.20's Circuit Cutter uses this executor; EAEP's replacement tick is guarded at its host. */
@Mixin(targets = "com.glodblock.github.extendedae.util.recipe.RecipeExecutor", remap = false)
public abstract class RecipeLockExtendedRecipeExecutorMixin {
    @Shadow @Final private IRecipeMachine<?, ?> machine;

    @WrapMethod(method = "execute")
    private TickRateModulation recipeLock$beforeProgressAndConsumption(int speed, boolean usePower,
            Operation<TickRateModulation> original) {
        // Recipe executors are authorized through their real block entity.
        if (!(this.machine instanceof BlockEntity blockEntity)) {
            return original.call(speed, usePower);
        }
        var level = blockEntity.getLevel();
        if (level == null || level.isClientSide) {
            return TickRateModulation.SLEEP;
        }
        if (!RecipeLocks.hasRules(level)) {
            return original.call(speed, usePower);
        }
        try (var scope = RecipeExecutionContext.open(blockEntity)) {
            var context = this.machine.getContext();
            // execute reads currentRecipe directly, including loaded and lastRecipe-derived caches.
            if (context.currentRecipe != null && !RecipeLocks.allowed(blockEntity, context.currentRecipe)) {
                this.machine.setWorking(false);
                this.machine.setProgress(0);
                context.currentRecipe = null;
                context.lastRecipe = null;
            }
            // EAE otherwise remembers "no matching recipe" forever, even after a team unlocks.
            if (context.currentRecipe == null) {
                context.stuck = false;
            }
            var rate = original.call(speed, usePower);
            return context.currentRecipe == null && rate == TickRateModulation.FASTER
                    ? TickRateModulation.SLOWER : rate;
        }
    }
}
