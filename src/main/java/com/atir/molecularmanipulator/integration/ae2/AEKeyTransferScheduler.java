package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.stacks.AEKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class AEKeyTransferScheduler {
    public static final int DEFAULT_MAX_KEYS_PER_CYCLE = 64;
    public static final int DEFAULT_MAX_CONSECUTIVE_FAILURES = 5;

    private final ObjectArrayList<AEKey> keyOrder = new ObjectArrayList<>();
    private int nextKeyIndex;

    public static TransferBudget defaultBudget() {
        return new TransferBudget(DEFAULT_MAX_KEYS_PER_CYCLE);
    }

    public FlushResult flush(Object2LongMap<AEKey> source, TransferBudget budget, Inserter inserter) {
        if (source.isEmpty() || !budget.hasRemaining()) {
            return FlushResult.EMPTY;
        }

        boolean changed = snapshotKeys(source);
        if (keyOrder.isEmpty()) {
            keyOrder.clear();
            return new FlushResult(changed, false, 0);
        }

        if (nextKeyIndex >= keyOrder.size()) {
            nextKeyIndex = 0;
        }

        int index = nextKeyIndex;
        int visited = 0;
        int consecutiveFailures = 0;
        boolean blocked = false;
        long transferred = 0;

        while (visited < keyOrder.size() && budget.tryConsume()) {
            AEKey key = keyOrder.get(index);
            index = (index + 1) % keyOrder.size();
            visited++;

            long requested = source.getLong(key);
            if (requested <= 0) {
                if (source.removeLong(key) != 0) {
                    changed = true;
                }
                continue;
            }

            long inserted = com.atir.molecularmanipulator.util.MathCompat.clamp(inserter.insert(key, requested), 0, requested);
            if (inserted > 0) {
                long remaining = requested - inserted;
                if (remaining == 0) {
                    source.removeLong(key);
                } else {
                    source.put(key, remaining);
                    blocked = true;
                }
                changed = true;
                transferred = saturatedAdd(transferred, inserted);
                consecutiveFailures = 0;
            } else {
                blocked = true;
                consecutiveFailures++;
                if (consecutiveFailures >= DEFAULT_MAX_CONSECUTIVE_FAILURES) {
                    break;
                }
            }
        }

        nextKeyIndex = index;
        keyOrder.clear();
        return new FlushResult(changed, blocked, transferred);
    }

    private boolean snapshotKeys(Object2LongMap<AEKey> source) {
        keyOrder.clear();
        boolean changed = false;
        for (var iterator = Object2LongMaps.fastIterator(source); iterator.hasNext();) {
            var entry = iterator.next();
            if (entry.getKey() == null || entry.getLongValue() <= 0) {
                iterator.remove();
                changed = true;
            } else {
                keyOrder.add(entry.getKey());
            }
        }
        return changed;
    }

    private static long saturatedAdd(long first, long second) {
        if (Long.MAX_VALUE - first < second) {
            return Long.MAX_VALUE;
        }
        return first + second;
    }

    @FunctionalInterface
    public interface Inserter {
        long insert(AEKey key, long amount);
    }

    public static final class TransferBudget {
        private int remainingKeys;

        private TransferBudget(int remainingKeys) {
            this.remainingKeys = Math.max(0, remainingKeys);
        }

        public boolean hasRemaining() {
            return remainingKeys > 0;
        }

        private boolean tryConsume() {
            if (remainingKeys <= 0) {
                return false;
            }
            remainingKeys--;
            return true;
        }
    }

    public record FlushResult(boolean changed, boolean blocked, long transferred) {
        public static final FlushResult EMPTY = new FlushResult(false, false, 0);
    }
}
