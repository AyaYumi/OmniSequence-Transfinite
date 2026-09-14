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

/** Keep each nexus independent, including when enclosed inside a normal CPU cuboid. */
@Mixin(value = CraftingCPUCalculator.class, remap = false)
public abstract class CraftingCPUCalculatorMixin extends MBCalculator<CraftingBlockEntity, CraftingCPUCluster> {
    protected CraftingCPUCalculatorMixin(CraftingBlockEntity target) {
        super(target);
    }

    @Inject(method = "isValidBlockEntity", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$isolateNexus(BlockEntity candidate, CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof OmniComputationCoreBlockEntity core && core.isSingleBlock()) {
            callback.setReturnValue(candidate == target);
        } else if (candidate instanceof OmniComputationCoreBlockEntity core && core.isSingleBlock()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "verifyInternalStructure", at = @At("HEAD"), cancellable = true)
    private void molecularmanipulator$excludeEnclosedNexus(ServerLevel level, BlockPos min, BlockPos max,
            CallbackInfoReturnable<Boolean> callback) {
        for (var pos : BlockPos.betweenClosed(min, max)) {
            if (level.getBlockEntity(pos) instanceof OmniComputationCoreBlockEntity core
                    && core.isSingleBlock() && core != target) {
                callback.setReturnValue(false);
                return;
            }
        }
    }
}
