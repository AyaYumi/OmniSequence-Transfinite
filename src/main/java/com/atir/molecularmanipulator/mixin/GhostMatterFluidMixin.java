package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.block.GhostMatterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Let vanilla recalculate and drain flowing water while retaining its crystal deposit. */
@Mixin(FlowingFluid.class)
public abstract class GhostMatterFluidMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean omnisequence$retainDeposit(Level level, BlockPos pos, BlockState replacement, int flags) {
        var current = level.getBlockState(pos);
        return level.setBlock(pos, current.getBlock() instanceof GhostMatterBlock
                ? GhostMatterBlock.withFluid(current, replacement.getFluidState()) : replacement, flags);
    }
}
