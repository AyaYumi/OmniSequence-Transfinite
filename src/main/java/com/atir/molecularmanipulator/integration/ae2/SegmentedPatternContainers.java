package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Maintains stable terminal-only views over a larger pattern inventory.
 */
public final class SegmentedPatternContainers {
    public static final int MAX_PATTERNS_PER_CONTAINER = 128;
    private static final long SORT_BUCKET_SIZE = 1024L;

    private final PatternContainer host;
    private int inventorySize = -1;
    private List<PatternContainer> containers = List.of();

    public SegmentedPatternContainers(PatternContainer host) {
        this.host = Objects.requireNonNull(host);
    }

    public static PatternContainer unwrap(PatternContainer container) {
        return container instanceof Segment segment ? segment.host : container;
    }

    public List<PatternContainer> getContainers() {
        InternalInventory inventory = host.getTerminalPatternInventory();
        if (inventory.size() != inventorySize) {
            rebuild(inventory);
        }
        return containers;
    }

    private void rebuild(InternalInventory inventory) {
        inventorySize = inventory.size();
        if (inventorySize <= MAX_PATTERNS_PER_CONTAINER) {
            containers = List.of(host);
            return;
        }

        int containerCount = (inventorySize + MAX_PATTERNS_PER_CONTAINER - 1)
                / MAX_PATTERNS_PER_CONTAINER;
        var rebuilt = new ArrayList<PatternContainer>(containerCount);
        for (int containerIndex = 0; containerIndex < containerCount; containerIndex++) {
            int firstSlot = containerIndex * MAX_PATTERNS_PER_CONTAINER;
            int containerSize = Math.min(MAX_PATTERNS_PER_CONTAINER, inventorySize - firstSlot);
            rebuilt.add(new Segment(host,
                    inventory.getSubInventory(firstSlot, firstSlot + containerSize),
                    containerIndex));
        }
        containers = List.copyOf(rebuilt);
    }

    private static final class Segment implements PatternContainer {
        private final PatternContainer host;
        private final InternalInventory inventory;
        private final int index;

        private Segment(PatternContainer host, InternalInventory inventory, int index) {
            this.host = host;
            this.inventory = inventory;
            this.index = index;
        }

        @Override
        public IGrid getGrid() {
            return host.getGrid();
        }

        @Override
        public boolean isVisibleInTerminal() {
            return host.isVisibleInTerminal();
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return inventory;
        }

        @Override
        public long getTerminalSortOrder() {
            return host.getTerminalSortOrder() * SORT_BUCKET_SIZE + index;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return host.getTerminalGroup();
        }
    }
}
