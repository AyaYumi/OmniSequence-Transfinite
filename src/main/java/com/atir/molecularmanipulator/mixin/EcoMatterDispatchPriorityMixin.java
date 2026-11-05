package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.crafting.ICraftingProvider;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep the well's native transaction ahead of ECO's generic AE2 processing ramp. */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.crafting.execution.ECOProcessingPatternDispatcher", remap = false)
public abstract class EcoMatterDispatchPriorityMixin {
    @Inject(method = "supportsScaledDispatch", at = @At("HEAD"), cancellable = true, require = 1)
    private static void molecularmanipulator$preferWellFastPath(@Coerce Object request,
            ICraftingProvider provider, CallbackInfoReturnable<Boolean> callback) {
        // The well inherits PatternProviderLogic but owns a durable long-sized queue,
        // not AE2's send buffer. The generic ramp runs before FastPath and can defer it
        // after a small accepted chunk, leaving the native preparation unreachable.
        // Only skip that optimization: ECO retains its material/energy transaction,
        // exact path, blocking checks and ordinary one-copy fallback.
        if (provider instanceof MatterFabricationPatternLogic) callback.setReturnValue(false);
    }
}
