package com.atir.molecularmanipulator.mixin;

import appeng.util.inv.AppEngInternalInventory;
import appeng.util.inv.InternalInventoryHost;
import com.atir.molecularmanipulator.blockentity.MolecularCenterLogic;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Keep the Array's event-maintained index and crystal rollback live after a host callback fails. */
@Mixin(value = AppEngInternalInventory.class, remap = false)
public abstract class MolecularCenterInventoryNotificationsMixin {
    @Shadow private boolean notifyingChanges;
    @Shadow public abstract InternalInventoryHost getHost();

    @WrapMethod(method = "onContentsChanged(I)V")
    private void molecularmanipulator$releaseArrayNotificationGuard(int slot, Operation<Void> original) {
        // Do not release a nested notification's outer latch or change unrelated inventories.
        if (notifyingChanges || !(getHost() instanceof MolecularCenterLogic logic)) {
            original.call(slot);
            return;
        }
        try {
            original.call(slot);
        } catch (RuntimeException failure) {
            logic.invalidateUploadIndex();
            throw failure;
        } finally {
            // AE2 15.4.10 also leaves this latch set if onChangeInventory(InternalInventory, int)
            // or saveChanges() throws. WrapMethod preserves both original calls and their ABI.
            notifyingChanges = false;
        }
    }
}
