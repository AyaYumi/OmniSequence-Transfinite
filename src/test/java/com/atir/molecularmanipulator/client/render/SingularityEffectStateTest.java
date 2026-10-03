package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SingularityEffectStateTest {
    @Test
    void pauseAndResumeEaseWithoutReplayingTheOpening() {
        var state = new SingularityEffectState();
        var running = state.sample(1000, 1, 500);
        var paused = state.sample(1001, 2, 501);
        assertTrue(paused.power() < running.power() && paused.power() > .9);
        var settled = state.sample(1041, 2, 501);
        assertTrue(settled.power() < paused.power() && settled.power() > .48);
        assertTrue(settled.time() > paused.time());
        var resumed = state.sample(1042, 1, 502);
        assertTrue(resumed.power() > settled.power());
        assertEquals(160, resumed.age());
    }

    @Test
    void loadingAnAlreadyRunningAssemblySkipsOpening() {
        var old = new SingularityEffectState().sample(100000, 1, 50000);
        assertEquals(160, old.age());
        assertEquals(1, old.power());
        var fresh = new SingularityEffectState().sample(100000, 1, .5);
        assertEquals(.5, fresh.age());
    }

    @Test
    void repeatedAndShadowPassesDoNotAdvanceOrResetTheClock() {
        var state = new SingularityEffectState();
        var first = state.sample(100.75, 1, 500);
        assertEquals(first, state.sample(100.75, 1, 500));
        assertEquals(first.time(), state.sample(100, 1, 500).time());
        assertEquals(first.time() + 1, state.sample(101.75, 1, 501).time(), .0001);
    }
}
