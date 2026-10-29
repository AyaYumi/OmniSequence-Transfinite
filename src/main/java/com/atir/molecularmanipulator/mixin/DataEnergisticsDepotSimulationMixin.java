package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import com.atir.molecularmanipulator.integration.ae2.MolecularItemCapacity;
import com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotBlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The original SIMULATE loop repeatedly counts the same unmodified slot space. */
@Pseudo
@Mixin(targets = "com.fish_dan_.data_energistics.blockentity.storage.DigitalStorageDepotBlockEntity$DepotStorage", remap = false)
public abstract class DataEnergisticsDepotSimulationMixin {
    @Shadow @Final private DigitalStorageDepotBlockEntity this$0;

    @Inject(method = "insertItem", at = @At("HEAD"), cancellable = true, require = 1)
    private void molecularmanipulator$simulateActualItemRoom(AEItemKey key, long amount,
            Actionable mode, CallbackInfoReturnable<Long> callback) {
        if (mode == Actionable.SIMULATE) {
            callback.setReturnValue(MolecularItemCapacity.simulate(this$0.getStorageInventory(), key, amount));
        }
    }
}
