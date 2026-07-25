package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin {
    @Redirect(method = "getAvailableStacks", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private void molecularmanipulator$exposeInfiniteAmounts(MEStorage inventory, KeyCounter output) {
        var available = new KeyCounter();
        inventory.getAvailableStacks(available);

        for (var entry : available) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();
            if (molecularmanipulator$isInfinite(inventory, key, amount)) {
                output.set(key, Long.MAX_VALUE);
            } else {
                output.add(key, amount);
            }
        }
    }

    private static boolean molecularmanipulator$isInfinite(MEStorage inventory, AEKey key, long reportedAmount) {
        if (reportedAmount < Integer.MAX_VALUE) {
            return false;
        }

        try {
            return inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty())
                    == Long.MAX_VALUE;
        } catch (RuntimeException exception) {
            return false;
        }
    }
}
