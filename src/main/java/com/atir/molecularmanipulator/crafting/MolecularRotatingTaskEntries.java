package com.atir.molecularmanipulator.crafting;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Presents a rotating snapshot of a task map while preserving
 * {@link Iterator#remove()} against the backing map.
 */
public final class MolecularRotatingTaskEntries {
    private MolecularRotatingTaskEntries() {
    }

    public static <K, V> Set<Map.Entry<K, V>> rotate(
            Map<K, V> backingMap, Set<Map.Entry<K, V>> sourceEntries,
            int startIndex) {
        if (sourceEntries.size() <= 1) {
            return sourceEntries;
        }

        var snapshot = new ArrayList<>(sourceEntries);
        int start = Math.floorMod(startIndex, snapshot.size());
        if (start == 0) {
            return new RotatingEntrySet<>(backingMap, snapshot);
        }

        var ordered = new ArrayList<Map.Entry<K, V>>(snapshot.size());
        ordered.addAll(snapshot.subList(start, snapshot.size()));
        ordered.addAll(snapshot.subList(0, start));
        return new RotatingEntrySet<>(backingMap, ordered);
    }

    private static final class RotatingEntrySet<K, V>
            extends AbstractSet<Map.Entry<K, V>> {
        private final Map<K, V> backingMap;
        private final List<Map.Entry<K, V>> entries;

        private RotatingEntrySet(
                Map<K, V> backingMap, List<Map.Entry<K, V>> entries) {
            this.backingMap = backingMap;
            this.entries = entries;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new RotatingIterator<>(backingMap, entries);
        }

        @Override
        public int size() {
            return entries.size();
        }
    }

    private static final class RotatingIterator<K, V>
            implements Iterator<Map.Entry<K, V>> {
        private final Map<K, V> backingMap;
        private final List<Map.Entry<K, V>> entries;
        private int cursor;
        private Map.Entry<K, V> lastEntry;
        private boolean canRemove;

        private RotatingIterator(
                Map<K, V> backingMap, List<Map.Entry<K, V>> entries) {
            this.backingMap = backingMap;
            this.entries = entries;
        }

        @Override
        public boolean hasNext() {
            return cursor < entries.size();
        }

        @Override
        public Map.Entry<K, V> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            lastEntry = entries.get(cursor++);
            canRemove = true;
            return lastEntry;
        }

        @Override
        public void remove() {
            if (!canRemove) {
                throw new IllegalStateException();
            }
            backingMap.remove(lastEntry.getKey(), lastEntry.getValue());
            canRemove = false;
        }
    }
}
