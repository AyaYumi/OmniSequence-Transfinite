package com.atir.molecularmanipulator.crafting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class OmniDispatchLaneRotationTest {
    @Test
    void roundRobinsByLeastRecentlyServedIdentity() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var a = new Object();
        var b = new Object();
        var c = new Object();

        assertSame(a, rotation.select(List.of(a, b, c)));
        assertSame(b, rotation.select(List.of(a, b, c)));
        assertSame(c, rotation.select(List.of(a, b, c)));
        assertSame(a, rotation.select(List.of(a, b, c)));
        assertSame(b, rotation.select(List.of(a, b, c)));
    }

    @Test
    void addingAndRemovingLanesDoesNotSkipALaneThatStayedActive() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var a = new Object();
        var b = new Object();
        var c = new Object();

        assertSame(a, rotation.select(List.of(a, b)));
        assertSame(b, rotation.select(List.of(a, b)));
        // c joins behind both lanes instead of displacing the rotation index.
        assertSame(a, rotation.select(List.of(a, b, c)));
        assertSame(b, rotation.select(List.of(a, b, c)));
        assertSame(c, rotation.select(List.of(a, b, c)));
        // b leaves; the remaining lanes continue from their own last service.
        assertSame(a, rotation.select(List.of(a, c)));
        assertSame(c, rotation.select(List.of(a, c)));
        // b returns as a new lane and has to wait for the lanes that stayed.
        assertSame(a, rotation.select(List.of(a, b, c)));
        assertSame(b, rotation.select(List.of(a, b, c)));
        assertSame(c, rotation.select(List.of(a, b, c)));
    }

    @Test
    void continuousNewLanesCannotStarveStableLanes() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var a = new Object();
        var b = new Object();
        var c = new Object();
        int servedA = 0;
        int servedB = 0;
        int servedC = 0;

        for (int tick = 0; tick < 45; tick++) {
            var active = new ArrayList<Object>(4);
            active.add(a);
            active.add(b);
            active.add(c);
            active.add(new Object());
            var selected = rotation.select(active);
            if (selected == a) servedA++;
            if (selected == b) servedB++;
            if (selected == c) servedC++;
        }

        // The old identity-hash index could repeatedly land on the same lane.
        // Every stable lane should keep getting turns despite the churn, while
        // transient lanes join behind them.
        assertEquals(15, servedA);
        assertEquals(15, servedB);
        assertEquals(15, servedC);
    }

    @Test
    void coldLaneGetsAProgressTurnEvenWhenDemandLanesStayFirst() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var hot = new Object();
        var cold = new Object();
        assertSame(hot, rotation.select(List.of(hot, cold)));
        assertSame(cold, rotation.select(List.of(hot, cold)));
        assertSame(hot, rotation.select(List.of(hot, cold)));
        assertSame(cold, rotation.select(List.of(hot, cold)));
    }

    @Test
    void zeroQuotaDoesNotEraseWaitingHistoryOrConsumeAProgressTurn() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var hot = new Object();
        var cold = new Object();
        var active = List.of(hot, cold);

        assertSame(hot, rotation.select(active, lane -> lane == hot));
        assertNull(rotation.select(active, lane -> false));
        assertEquals(2, rotation.trackedLanes());
        assertSame(hot, rotation.select(active, lane -> lane == hot));
        assertSame(cold, rotation.select(active, lane -> true));
        assertSame(hot, rotation.select(List.of(hot), lane -> true));
        assertEquals(1, rotation.trackedLanes(), "Only departed lanes lose their history");
    }

    @Test
    void rotatingScarceQuotasCannotStarveBehindAnAlwaysEligibleCore() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var hot = new Object();
        int crowdedLanes = 257;
        var active = new ArrayList<Object>();
        active.add(hot);
        for (int lane = 0; lane < crowdedLanes; lane++) active.add(new Object());
        var served = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
        int workRotation = 0;
        int attemptRotation = 0;

        for (int tick = 0; tick < crowdedLanes * 4; tick++) {
            var eligible = Collections.newSetFromMap(new IdentityHashMap<Object, Boolean>());
            eligible.add(hot);
            for (int lane = 0; lane < crowdedLanes; lane++) {
                if (OmniCpuResources.dispatchLaneShare(64, 256, crowdedLanes, lane,
                        workRotation, attemptRotation).canProgress()) {
                    eligible.add(active.get(lane + 1));
                }
            }
            var selected = rotation.select(active, eligible::contains);
            assertTrue(eligible.contains(selected), "Zero-quota lanes cannot use the progress turn");
            // After the shared deadline expires, only this lane can dispatch.
            served.add(selected);
            int attemptStart = workRotation;
            workRotation = OmniCpuResources.nextDispatchWorkRotation(64, 256, crowdedLanes, workRotation);
            attemptRotation = (attemptStart + 256) % crowdedLanes;
        }

        assertEquals(active.size(), served.size(), "Every busy lane must get a real progress turn");
        assertEquals(active.size(), rotation.trackedLanes());
    }

    @Test
    void emptySelectionClearsRememberedLanes() {
        var rotation = new OmniDispatchLaneRotation<Object>();
        var a = new Object();
        assertSame(a, rotation.select(List.of(a)));
        assertEquals(1, rotation.trackedLanes());
        assertNull(rotation.select(List.of()));
        assertEquals(0, rotation.trackedLanes());
        assertSame(a, rotation.select(List.of(a)));
    }

    @Test
    void lanesAreTrackedByObjectIdentityNotEquals() {
        var first = new AlwaysEqual();
        var second = new AlwaysEqual();
        var rotation = new OmniDispatchLaneRotation<AlwaysEqual>();

        assertSame(first, rotation.select(List.of(first)));
        assertSame(second, rotation.select(List.of(second, first)));
        assertEquals(2, rotation.trackedLanes());
        // Both distinct identities then get fair turns.
        assertSame(first, rotation.select(List.of(second, first)));
    }

    private static final class AlwaysEqual {
        @Override
        public boolean equals(Object other) {
            return other instanceof AlwaysEqual;
        }

        @Override
        public int hashCode() {
            return 1;
        }
    }
}
