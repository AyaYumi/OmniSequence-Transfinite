package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class SegmentedPatternContainersTest {
    @Test
    void expandsConcretePatternContainerQueriesIntoBoundedStableViews() {
        var views = List.of(new TestView(), new TestView(), new TestView());
        var host = new TestHost(views);
        Set<TestHost> machines = Set.of(host);

        Set<?> first = SegmentedPatternContainers.expandActiveMachines(TestHost.class, machines);
        Set<?> second = SegmentedPatternContainers.expandActiveMachines(TestHost.class, machines);

        assertEquals(3, first.size());
        assertIterableEquals(views, first);
        assertIterableEquals(first, second);
    }

    @Test
    void leavesUnrelatedMachineQueriesUntouched() {
        var host = new TestHost(List.of(new TestView(), new TestView()));
        Set<Object> machines = Set.of(host);

        assertSame(machines,
                SegmentedPatternContainers.expandActiveMachines(Object.class, machines));
    }

    @Test
    void leavesAlreadyBoundedContainersUntouched() {
        var host = new TestHost();
        Set<TestHost> machines = Set.of(host);

        assertSame(machines,
                SegmentedPatternContainers.expandActiveMachines(TestHost.class, machines));
    }

    @Test
    void calculatesStrict128SlotBoundaries() {
        assertEquals(3, SegmentedPatternContainers.segmentCount(300));
        assertEquals(List.of(128, 128, 44), List.of(
                SegmentedPatternContainers.segmentSize(300, 0),
                SegmentedPatternContainers.segmentSize(300, 1),
                SegmentedPatternContainers.segmentSize(300, 2)));
    }

    private static final class TestHost implements PatternContainer, SegmentedPatternContainerHost {
        private final List<PatternContainer> views;

        private TestHost() {
            views = List.of(this);
        }

        private TestHost(List<? extends PatternContainer> views) {
            this.views = List.copyOf(views);
        }

        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return null;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return null;
        }

        @Override
        public List<PatternContainer> molecularmanipulator$getTerminalPatternContainers() {
            return views;
        }
    }

    private static final class TestView implements PatternContainer {
        @Override
        public IGrid getGrid() {
            return null;
        }

        @Override
        public InternalInventory getTerminalPatternInventory() {
            return null;
        }

        @Override
        public PatternContainerGroup getTerminalGroup() {
            return null;
        }
    }
}
