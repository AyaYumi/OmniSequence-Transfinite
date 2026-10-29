package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.atir.molecularmanipulator.integration.eco.EcoCpuDispatchOutput;
import com.google.common.collect.ImmutableSet;
import java.math.BigInteger;
import java.util.UUID;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply after ECO's priority-1000 CraftingService mixin has introduced these router methods. */
@Mixin(value = CraftingService.class, remap = false, priority = 900)
public abstract class EcoCpuDispatchOutputMixin {
    @Shadow public abstract ImmutableSet<ICraftingCPU> getCpus();

    @Inject(method = "neoecoae$insertExactIntoCpuForJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$receiveExactEco(UUID job, AEKey key, BigInteger amount,
            Actionable mode, CallbackInfoReturnable<BigInteger> cir) {
        var accepted = EcoCpuDispatchOutput.insert(getCpus(), job, key, amount, mode);
        if (accepted != null) cir.setReturnValue(accepted);
    }
    @Inject(method = "neoecoae$insertIntoCpuForJob", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$receiveEco(UUID job, AEKey key, long amount,
            Actionable mode, CallbackInfoReturnable<Long> cir) {
        var accepted = EcoCpuDispatchOutput.insert(getCpus(), job, key, BigInteger.valueOf(amount), mode);
        if (accepted != null) cir.setReturnValue(accepted.longValueExact());
    }
}
