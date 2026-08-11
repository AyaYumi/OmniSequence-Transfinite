package com.atir.molecularmanipulator.storage;

import java.util.Optional;

import org.jetbrains.annotations.ApiStatus;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.cells.StorageCell;
import appeng.me.helpers.BaseActionSource;

/** Detects storage cells that can extract more than the amount they advertise. */
public final class InfiniteStorageDetector {
    private static final BaseActionSource PROBE_SOURCE = new BaseActionSource();

    private InfiniteStorageDetector() {
    }

    public static boolean isUnbounded(MEStorage storage, AEKey key, long advertisedAmount) {
        return probeUnbounded(storage, key, advertisedAmount).orElse(false);
    }

    @ApiStatus.Internal
    public static Optional<Boolean> probeUnbounded(
            MEStorage storage, AEKey key, long advertisedAmount) {
        if (!(storage instanceof StorageCell) || advertisedAmount <= 0) {
            return Optional.of(false);
        }
        if (advertisedAmount == InfiniteStorageAmounts.DISPLAY_AMOUNT) {
            return Optional.of(true);
        }
        try {
            long requestedAmount = InfiniteStorageAmounts.probeAmount(
                    advertisedAmount,
                    Math.max(1, key.getAmountPerUnit()));
            long extractedAmount = storage.extract(
                    key,
                    requestedAmount,
                    Actionable.SIMULATE,
                    PROBE_SOURCE);
            return Optional.of(InfiniteStorageAmounts.isInfiniteResponse(
                    advertisedAmount,
                    requestedAmount,
                    extractedAmount));
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }
}
