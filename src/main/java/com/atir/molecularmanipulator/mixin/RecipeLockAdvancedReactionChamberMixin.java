package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import com.atir.molecularmanipulator.crafting.PatternRecipeReloads;
import com.atir.molecularmanipulator.recipelock.RecipeExecutionContext;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Optional: verified against AdvancedAE 1.3.6-1.20.1; register only for that version. */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.entities.ReactionChamberEntity", remap = false)
public abstract class RecipeLockAdvancedReactionChamberMixin {
    @Shadow public abstract void setWorking(boolean working);
    @Unique private long recipeLock$recipeGeneration = Long.MIN_VALUE;

    @WrapOperation(method = "getTask()Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
            target = "Lnet/pedroksl/advanced_ae/common/entities/ReactionChamberEntity;cachedTask:Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;"))
    @Coerce
    private Object recipeLock$refreshStaleQuery(@Coerce Object instance, Operation<Object> original) {
        var level = ((BlockEntity) instance).getLevel();
        if (level != null && !level.isClientSide) {
            long generation = PatternRecipeReloads.generation();
            if (this.recipeLock$recipeGeneration != generation) {
                // Only the first read is the cache null-guard. Returning null here makes the
                // original getTask overwrite cachedTask via findRecipe, even with unchanged inputs.
                // No optional field-type Shadow, inventory mutation or progress reset is needed.
                return null;
            }
        }
        return original.call(instance);
    }

    // Callback erasure deliberately avoids any descriptor dependency on an optional recipe class.
    @Inject(method = "getTask()Lnet/pedroksl/advanced_ae/recipes/ReactionChamberRecipe;",
            at = @At("RETURN"), cancellable = true)
    private void recipeLock$checkQueryAndCachedTask(CallbackInfoReturnable<Recipe<?>> cir) {
        var machine = (BlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return;
        }
        // Record only a completed lookup; if findRecipe throws, the next call must retry.
        this.recipeLock$recipeGeneration = PatternRecipeReloads.generation();
        var recipe = cir.getReturnValue();
        if (recipe == null || RecipeLocks.hasRules(level) && !RecipeLocks.allowed(machine, recipe)) {
            // hasCraftWork's null-task branch returns isWorking(). Leaving it true would let the
            // power lambda dereference getTask().getEnergy() after we returned null.
            this.setWorking(false);
            cir.setReturnValue(null);
        }
    }

    @WrapMethod(method = "getTickingRequest")
    private TickingRequest recipeLock$pollAfterLoad(IGridNode node, Operation<TickingRequest> original) {
        var machine = (BlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return original.call(node);
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            var request = original.call(node);
            return RecipeLocks.hasRules(level) && request.isSleeping()
                    ? new TickingRequest(request.minTickRate(), request.maxTickRate(), false,
                            request.canBeAlerted(), request.initialTickRate())
                    : request;
        }
    }

    @WrapMethod(method = "tickingRequest")
    private TickRateModulation recipeLock$executeInMachineContext(IGridNode node, int elapsed,
            Operation<TickRateModulation> original) {
        var machine = (BlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return TickRateModulation.SLEEP;
        }
        try (var scope = RecipeExecutionContext.open(machine)) {
            // getTask is checked before energy/progress and before either item or fluid output.
            var rate = original.call(node, elapsed);
            return rate == TickRateModulation.SLEEP && RecipeLocks.hasRules(level)
                    ? TickRateModulation.SLOWER : rate;
        }
    }
}
