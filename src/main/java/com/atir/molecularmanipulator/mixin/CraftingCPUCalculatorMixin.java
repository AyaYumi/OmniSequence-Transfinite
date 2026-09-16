package com.atir.molecularmanipulator.mixin;

import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.MBCalculator;
import appeng.me.cluster.implementations.CraftingCPUCalculator;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keep every Omni computation core out of AE2's crafting-CPU multiblock scan.
 *
 * <p>Our cores extend {@link CraftingBlockEntity} but their blocks are not
 * {@code AbstractCraftingUnitBlock}, so AE2 may only ever see them as a
 * standalone 1x1x1 cluster containing themselves. Letting a scan pull one of
 * them into a region with real crafting units - or pulling real crafting units
 * into theirs - makes {@code CraftingBlockEntity#getUnitBlock} cast a non-unit
 * block and crash the server thread.
 */
@Mixin(value = CraftingCPUCalculator.class, remap = false)
public abstract class CraftingCPUCalculatorMixin extends MBCalculator<CraftingBlockEntity, CraftingCPUCluster> {
    protected CraftingCPUCalculatorMixin(CraftingBlockEntity target) {
        super(target);
    }

    private CraftingBlockEntity molecularmanipulator$target() {
        return this.target;
    }

    @Inject(method = "isValidBlockEntity", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$isolateNexus(BlockEntity candidate, CallbackInfoReturnable<Boolean> callback) {
        if (molecularmanipulator$target() instanceof OmniComputationCoreBlockEntity omniTarget) {
            // Only our own 1x1x1 cluster: never absorb a neighbouring AE unit, not
            // even a second Omni core.
            callback.setReturnValue(candidate == omniTarget);
        } else if (candidate instanceof OmniComputationCoreBlockEntity) {
            // The reverse direction: a real AE CPU bordering a controller, storage
            // or nexus must not absorb it.
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "verifyInternalStructure", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$excludeEnclosedNexus(ServerLevel level, BlockPos min, BlockPos max,
            CallbackInfoReturnable<Boolean> callback) {
        var target = molecularmanipulator$target();
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof OmniComputationCoreBlockEntity core && core != target) {
                callback.setReturnValue(false);
                return;
            }
        }
    }
}
