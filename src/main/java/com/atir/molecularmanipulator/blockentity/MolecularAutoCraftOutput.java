package com.atir.molecularmanipulator.blockentity;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import com.atir.molecularmanipulator.blockentity.MolecularAutoCrafterHost.OutputDestination;
import com.atir.molecularmanipulator.util.MathCompat;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import java.math.BigInteger;

/** Bounded capacity probes and same-tick delivery for passive crafting only. */
final class MolecularAutoCraftOutput {
    static OutputDestination network(MEStorage storage, IActionSource source) {
        return new OutputDestination() {
            @Override
            public long insert(AEKey key, long amount, Actionable mode) {
                return storage.insert(key, amount, mode, source);
            }

            @Override
            public long extract(AEKey key, long amount) {
                return storage.extract(key, amount, Actionable.MODULATE, source);
            }
        };
    }

    /** One probe per distinct key, regardless of the number of crafts. */
    static long capacity(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders, long crafts,
            long maximum, OutputDestination destination) {
        var outputs = combine(primary, remainders);
        long result = maximum;
        for (var entry : outputs.object2LongEntrySet()) {
            long amount = entry.getLongValue();
            if (amount <= 0 || crafts <= 0) return 0;
            long requested = scaled(amount, maximum, crafts);
            long accepted;
            try {
                accepted = MathCompat.clamp(destination.insert(entry.getKey(), requested,
                        Actionable.SIMULATE), 0, requested);
            } catch (RuntimeException exception) {
                return 0;
            }
            result = Math.min(result, scaled(accepted, crafts, amount));
            if (result == 0) break;
        }
        return result;
    }

    /**
     * Simulations do not reserve shared storage. Roll back a partial delivery
     * before refunding ingredients. If a one-way or changed destination refuses
     * rollback, the batch must instead commit and retain ONLY its missing outputs.
     */
    static Delivery deliver(Object2LongOpenHashMap<AEKey> primary,
            Object2LongOpenHashMap<AEKey> remainders, OutputDestination destination) {
        var outputs = combine(primary, remainders);
        var inserted = new Object2LongOpenHashMap<AEKey>();
        boolean complete = true;
        for (var entry : outputs.object2LongEntrySet()) {
            long accepted;
            try {
                accepted = MathCompat.clamp(destination.insert(entry.getKey(),
                        entry.getLongValue(), Actionable.MODULATE), 0, entry.getLongValue());
            } catch (RuntimeException exception) {
                accepted = 0;
            }
            if (accepted > 0) inserted.put(entry.getKey(), accepted);
            if (accepted != entry.getLongValue()) {
                complete = false;
                break;
            }
        }
        if (complete) return new Delivery(true, new Object2LongOpenHashMap<>());

        var irreversible = new Object2LongOpenHashMap<AEKey>();
        for (var entry : inserted.object2LongEntrySet()) {
            long recovered;
            try {
                recovered = MathCompat.clamp(destination.extract(entry.getKey(),
                        entry.getLongValue()), 0, entry.getLongValue());
            } catch (RuntimeException exception) {
                recovered = 0;
            }
            if (recovered < entry.getLongValue()) {
                irreversible.put(entry.getKey(), entry.getLongValue() - recovered);
            }
        }
        if (irreversible.isEmpty()) return new Delivery(false, new Object2LongOpenHashMap<>());

        var recovery = new Object2LongOpenHashMap<AEKey>();
        for (var entry : outputs.object2LongEntrySet()) {
            long missing = entry.getLongValue() - irreversible.getLong(entry.getKey());
            if (missing > 0) recovery.put(entry.getKey(), missing);
        }
        return new Delivery(true, recovery);
    }

    private static Object2LongOpenHashMap<AEKey> combine(
            Object2LongOpenHashMap<AEKey> primary, Object2LongOpenHashMap<AEKey> remainders) {
        var result = new Object2LongOpenHashMap<AEKey>(primary);
        for (var entry : remainders.object2LongEntrySet()) {
            result.put(entry.getKey(), Math.addExact(result.getLong(entry.getKey()), entry.getLongValue()));
        }
        return result;
    }

    private static long scaled(long amount, long multiplier, long divisor) {
        try {
            return Math.multiplyExact(amount, multiplier) / divisor;
        } catch (ArithmeticException exception) {
            return BigInteger.valueOf(amount).multiply(BigInteger.valueOf(multiplier))
                    .divide(BigInteger.valueOf(divisor)).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        }
    }

    record Delivery(boolean committed, Object2LongOpenHashMap<AEKey> recovery) {}
}
