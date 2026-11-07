package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.integration.extendedaeplus.MatterFabricationProviderUpload;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Verified against EAEP 1.6.4.1. Called only after EAEP's auto-upload toggle and Shift opt-out checks. */
@Pseudo
@Mixin(targets = "com.extendedae_plus.client.ProviderListCache", remap = false)
public abstract class ExtendedAEPlusFabricationProviderCacheMixin {
    @Inject(method = "findUniqueMatch", at = @At("HEAD"), cancellable = true)
    private static void molecularmanipulator$useFabricationMachineIdentity(String searchKey,
            CallbackInfoReturnable<Component> callback) {
        if (MatterFabricationProviderUpload.isFabricationCategory(searchKey)) {
            callback.setReturnValue(MatterFabricationProviderUpload.automaticTarget());
        }
    }
}
