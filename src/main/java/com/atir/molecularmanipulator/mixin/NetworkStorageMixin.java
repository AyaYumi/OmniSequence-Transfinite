package com.atir.molecularmanipulator.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin {
    @Inject(method = "getAvailableStacks", at = @At("RETURN"))
    private void molecularmanipulator$exposeInfiniteAmounts(KeyCounter output, CallbackInfo callback) {
        var candidates = new ArrayList<AEKey>();

        for (var entry : output) {
            if (entry.getLongValue() >= Integer.MAX_VALUE) {
                candidates.add(entry.getKey());
            }
        }

        var networkStorage = (MEStorage) (Object) this;
        for (var key : candidates) {
            if (molecularmanipulator$isInfinite(networkStorage, key, output.get(key))) {
                output.set(key, Long.MAX_VALUE);
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
