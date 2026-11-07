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

    @WrapMethod(method = "onContentsChanged")
    private void molecularmanipulator$releaseArrayNotificationGuard(int slot, Operation<Void> original) {
        // Leave other inventories and nested notifications alone. A nested call must not
        // release the guard still owned by its outer notification.
        if (notifyingChanges || !(getHost() instanceof MolecularCenterLogic logic)) {
            original.call(slot);
            return;
        }
        try {
            original.call(slot);
        } catch (RuntimeException failure) {
            // The write already happened. The transaction will restore it; do not let a
            // partially updated index survive if that restoration itself also fails.
            logic.invalidateUploadIndex();
            throw failure;
        } finally {
            // AE2 19.2.x does not clear this latch in a finally of its own.
            notifyingChanges = false;
        }
    }
}
