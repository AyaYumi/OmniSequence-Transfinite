package com.atir.molecularmanipulator.blockentity;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import java.util.Map;

/** Keeps newly produced items out of network insertion until a later game tick. */
final class MatterOutputStaging {
    private final Object2LongLinkedOpenHashMap<AEKey> pending = new Object2LongLinkedOpenHashMap<>();
    private long readyTick = Long.MIN_VALUE;

    boolean isEmpty() { return pending.isEmpty(); }

    long storedAmount(Object2LongMap<AEKey> ready, AEKey key) {
        return Math.addExact(ready.getLong(key), pending.getLong(key));
    }

    long store(Object2LongMap<AEKey> ready, AEKey key, long amount, long gameTime) {
        release(ready, gameTime);
        long inserted = Math.min(amount, Long.MAX_VALUE - storedAmount(ready, key));
        if (inserted > 0) {
            pending.addTo(key, inserted);
            readyTick = MolecularOutputScheduling.scheduleNextTick(readyTick, gameTime);
        }
        return Math.max(0, inserted);
    }

    void release(Object2LongMap<AEKey> ready, long gameTime) {
        if (pending.isEmpty()) return;
        if (readyTick == Long.MIN_VALUE) {
            // Restored outputs wait too: block entities can tick before their CPU is restored.
            readyTick = MolecularOutputScheduling.scheduleNextTick(readyTick, gameTime);
            return;
        }
        if (gameTime < readyTick) return;
        pending.forEach((key, amount) -> ready.put(key, Math.addExact(ready.getLong(key), amount)));
        clear();
    }

    void addTo(Map<AEKey, Long> amounts) {
        pending.forEach((key, amount) -> amounts.merge(key, amount, Math::addExact));
    }

    void restore(Map<AEKey, Long> amounts) {
        clear();
        pending.putAll(amounts);
    }

    void clear() { pending.clear(); readyTick = Long.MIN_VALUE; }
}
