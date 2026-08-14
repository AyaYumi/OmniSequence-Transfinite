package com.atir.molecularmanipulator.mixin;

import java.util.IdentityHashMap;
import java.util.Map;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.StorageCell;
import appeng.me.cells.BasicCellInventory;
import appeng.me.storage.DriveWatcher;
import appeng.me.storage.NetworkStorage;
import com.atir.molecularmanipulator.runtime.NetworkStorageCollectionDepth;
import com.atir.molecularmanipulator.runtime.NetworkStorageDetectionCache;
import com.atir.molecularmanipulator.storage.InfiniteStorageAmounts;
import com.atir.molecularmanipulator.storage.InfiniteStorageDetector;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Saturates network listings for cells that can supply more than they advertise. */
@Mixin(value = NetworkStorage.class, remap = false)
public abstract class NetworkStorageMixin {
    @Unique
    private final Map<MEStorage, NetworkStorageDetectionCache<AEKey>>
            molecularmanipulator$infiniteKeys = new IdentityHashMap<>();

    @WrapOperation(method = "getAvailableStacks", at = @At(value = "INVOKE",
            target = "Lappeng/api/storage/MEStorage;getAvailableStacks(Lappeng/api/stacks/KeyCounter;)V"))
    private void molecularmanipulator$collectAvailableStacks(
            MEStorage storage,
            KeyCounter output,
            Operation<Void> original) {
        Integer collectionDepth = NetworkStorageCollectionDepth.current();
        if (collectionDepth != null && collectionDepth > 0) {
            // The outer cell boundary performs the infinity check. Nested
            // NetworkStorage instances must only contribute their raw listing,
            // otherwise every layer copies and probes the same keys again.
            original.call(storage, output);
            return;
        }

        StorageCell storageCell;
        if (storage instanceof StorageCell directCell) {
            storageCell = directCell;
        } else if (storage instanceof DriveWatcher driveWatcher) {
            storageCell = driveWatcher.getCell();
        } else {
            original.call(storage, output);
            return;
        }
        if (storageCell == null || storageCell instanceof BasicCellInventory) {
            // AE2 basic cells have an explicit finite capacity. They neither
            // need a local counter nor a simulated Long.MAX_VALUE extraction.
            original.call(storage, output);
            return;
        }

        // Collect each mount into its own counter. Letting mounts write directly
        // to the network counter makes mount order observable when long addition
        // overflows before an infinite provider is recognized.
        var local = new KeyCounter();
        NetworkStorageCollectionDepth.enter(collectionDepth);
        try {
            original.call(storage, local);

            NetworkStorageDetectionCache<AEKey> detectedKeys =
                    molecularmanipulator$infiniteKeys.computeIfAbsent(
                            storage, ignored -> new NetworkStorageDetectionCache<>());
            detectedKeys.beginRefresh();
            try {
                for (var entry : local) {
                    var key = entry.getKey();
                    long amount = entry.getLongValue();

                    // Some legacy third-party cells advertise a negative infinity
                    // sentinel. Normalize it at the network boundary as well so no
                    // negative count or infinity glyph leaks into AE2's UI/math.
                    if (amount < 0) {
                        output.set(key, InfiniteStorageAmounts.DISPLAY_AMOUNT);
                        continue;
                    }
                    if (amount == 0) {
                        if (output.get(key) != InfiniteStorageAmounts.DISPLAY_AMOUNT) {
                            output.add(key, amount);
                        }
                        continue;
                    }

                    boolean infinite = amount == InfiniteStorageAmounts.DISPLAY_AMOUNT;
                    if (!infinite) {
                        infinite = detectedKeys.resolve(
                                key,
                                amount,
                                () -> InfiniteStorageDetector.probeUnbounded(
                                        storageCell, key, amount));
                    }
                    output.set(key, InfiniteStorageAmounts.mergeAvailable(
                            output.get(key), amount, infinite));
                }
            } finally {
                detectedKeys.endRefresh();
            }
        } finally {
            NetworkStorageCollectionDepth.exit(collectionDepth);
        }
    }

    @Inject(method = "unmount", at = @At("HEAD"))
    private void molecularmanipulator$forgetUnmountedStorage(
            MEStorage storage, CallbackInfo callback) {
        molecularmanipulator$infiniteKeys.remove(storage);
    }
}
