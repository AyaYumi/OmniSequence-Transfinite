package com.atir.molecularmanipulator.blockentity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequenceCrownAnimationTest {
    @Test void idleKeepsMovingAndWorkingStateEasesInAndOut() {
        var animation = new SequenceCrownAnimation();
        animation.sample(0);
        for (int tick = 1; tick <= 20; tick++) animation.sample(tick);
        assertEquals(16, animation.angle(), 1.0E-4);
        assertEquals(0, animation.activity());
        animation.receive(true, false, 20);
        animation.sample(21);
        assertTrue(animation.activity() > 0 && animation.activity() < 0.2);
        for (int tick = 22; tick <= 80; tick++) animation.sample(tick);
        assertTrue(animation.activity() > 0.99);
        animation.receive(false, false, 80);
        for (int tick = 81; tick <= 140; tick++) animation.sample(tick);
        assertTrue(animation.activity() < 0.01);
    }

    @Test void OnlyExplicitSuccessfulBatchEventsStartACompletionWave() {
        var animation = new SequenceCrownAnimation();
        animation.receive(true, false, 100);
        animation.receive(false, false, 105);
        assertEquals(0, animation.completion(105));
        animation.receive(false, true, 110);
        assertEquals(1, animation.completion(110));
        animation.receive(false, false, 114);
        assertEquals(0.5, animation.completion(122));
        assertEquals(0, animation.completion(134));
        assertEquals(0, animation.completion(10000));
    }

    @Test void DuplicateFramesAndLongAbsencesDoNotLeapTheAnimation() {
        var animation = new SequenceCrownAnimation();
        animation.sample(200);
        animation.sample(200.5);
        float angle = animation.angle();
        animation.sample(200.5);
        assertEquals(angle, animation.angle());
        animation.sample(100000);
        assertEquals(angle, animation.angle());
        animation.sample(100000.5);
        assertTrue(animation.angle() > angle);
    }
}
