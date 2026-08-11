package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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

    /**
     * Expands terminal-only views at the common grid-machine query boundary.
     * Queries for unrelated machine services are returned unchanged.
     *
     * <p>AE2 enumerates concrete grid-machine classes and then queries each class,
     * while terminal callers consume those results through {@link PatternContainer}.
     * For that reason this accepts concrete pattern-container classes as well as the
     * interface itself.</p>
     */
    public static Set<?> expandActiveMachines(Class<?> machineClass, Set<?> machines) {
        if (!PatternContainer.class.isAssignableFrom(machineClass) || machines.isEmpty()) {
            return machines;
        }

        var expanded = new LinkedHashSet<Object>(machines.size());
        boolean changed = false;
        for (Object machine : machines) {
            if (!(machine instanceof SegmentedPatternContainerHost segmentedHost)) {
                expanded.add(machine);
                continue;
            }

            List<PatternContainer> terminalContainers =
                    segmentedHost.molecularmanipulator$getTerminalPatternContainers();
            boolean replacesMachine = terminalContainers.size() != 1
                    || terminalContainers.getFirst() != machine;
            if (!replacesMachine) {
                expanded.add(machine);
                continue;
            }

            changed = true;
            expanded.addAll(terminalContainers);
        }

        return changed ? Collections.unmodifiableSet(expanded) : machines;
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

        int containerCount = segmentCount(inventorySize);
        var rebuilt = new ArrayList<PatternContainer>(containerCount);
        for (int containerIndex = 0; containerIndex < containerCount; containerIndex++) {
            int firstSlot = containerIndex * MAX_PATTERNS_PER_CONTAINER;
            int containerSize = segmentSize(inventorySize, containerIndex);
            rebuilt.add(new Segment(host,
                    inventory.getSubInventory(firstSlot, firstSlot + containerSize),
                    containerIndex));
        }
        containers = List.copyOf(rebuilt);
    }

    static int segmentCount(int inventorySize) {
        return inventorySize <= 0
                ? 0
                : 1 + (inventorySize - 1) / MAX_PATTERNS_PER_CONTAINER;
    }

    static int segmentSize(int inventorySize, int segmentIndex) {
        int firstSlot = segmentIndex * MAX_PATTERNS_PER_CONTAINER;
        return Math.min(MAX_PATTERNS_PER_CONTAINER, inventorySize - firstSlot);
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
