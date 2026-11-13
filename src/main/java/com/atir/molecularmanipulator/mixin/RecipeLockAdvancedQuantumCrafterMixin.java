package com.atir.molecularmanipulator.mixin;

import appeng.crafting.pattern.AECraftingPattern;
import com.atir.molecularmanipulator.recipelock.RecipeLocks;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

/** Optional: AdvancedAE 1.3.6-1.20.1 executes cached pattern outputs without Recipe.assemble. */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.entities.QuantumCrafterEntity", remap = false)
public abstract class RecipeLockAdvancedQuantumCrafterMixin {
    @WrapOperation(method = {
            "maximumCraftableAmount(Lnet/pedroksl/advanced_ae/common/entities/QuantumCrafterEntity$CraftingJob;)I",
            "performCraft(Lnet/pedroksl/advanced_ae/common/entities/QuantumCrafterEntity$CraftingJob;I)V"
    },
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD, ordinal = 0,
                    target = "Lnet/pedroksl/advanced_ae/common/entities/QuantumCrafterEntity$CraftingJob;pattern:Lappeng/crafting/pattern/AECraftingPattern;"))
    private AECraftingPattern recipeLock$beforeCachedCraft(@Coerce Object job, Operation<AECraftingPattern> original) {
        var machine = (BlockEntity) (Object) this;
        var level = machine.getLevel();
        if (level == null || level.isClientSide) {
            return null;
        }
        var pattern = original.call(job);
        // Both first field reads are null-guards, before powered extraction or cached output insertion.
        // Only suppress this invocation's view; do not erase the job, its pattern, or team progress.
        return pattern == null || !RecipeLocks.hasRules(level) || RecipeLocks.allowedPattern(machine, pattern)
                ? pattern : null;
    }
}
