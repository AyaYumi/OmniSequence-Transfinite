package com.atir.molecularmanipulator.mixin;

import appeng.api.networking.crafting.ICraftingProvider;
import com.atir.molecularmanipulator.api.crafting.OmniBatchAdmission;
import com.atir.molecularmanipulator.api.crafting.OmniBatchProbe;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * ECO 21.2.9's allocated Omni adapter can throw indeterminate before delivery.accept(), making the
 * legacy CPU refund already delivered materials. The independent dispatcher replaces that adapter.
 * Single-copy dispatch remains available when an actual fast-path preparation is unavailable.
 */
@Pseudo
@Mixin(targets = "cn.dancingsnow.neoecoae.compat.omnisequence.ECOOmniSequenceBatchAdapter", remap = false)
public abstract class EcoCpuDispatchAdapterMixin {
    @Inject(method = "prepare", at = @At("HEAD"), cancellable = true, require = 1)
    private static void molecularmanipulator$disableUnsafeAllocatedAdapter(ICraftingProvider provider,
            Level level, OmniBatchProbe probe, CallbackInfoReturnable<OmniBatchAdmission> cir) {
        cir.setReturnValue(null);
    }
}
