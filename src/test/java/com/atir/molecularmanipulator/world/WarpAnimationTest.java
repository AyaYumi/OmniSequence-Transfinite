package com.atir.molecularmanipulator.world;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WarpAnimationTest {
    @Test
    void darkTransitionJoinsContinuouslyAcrossTheActualTransfer() {
        assertEquals(0, WarpAnimation.screenOpacity(WarpAnimation.CHARGING,
                WarpAnimation.CHARGE_TICKS - WarpAnimation.FADE_TICKS));
        float previous = 0;
        for (float age = 22; age <= WarpAnimation.CHARGE_TICKS; age += .125F) {
            float opacity = WarpAnimation.screenOpacity(WarpAnimation.CHARGING, age);
            assertTrue(opacity >= previous && opacity <= .96F);
            previous = opacity;
        }
        assertEquals(previous, WarpAnimation.screenOpacity(WarpAnimation.ARRIVING, 0), .00001F);
        for (float age = 0; age <= WarpAnimation.ARRIVAL_TICKS; age += .125F) {
            float opacity = WarpAnimation.screenOpacity(WarpAnimation.ARRIVING, age);
            assertTrue(opacity <= previous && opacity >= 0);
            previous = opacity;
        }
        assertEquals(0, previous);
        assertEquals(0, WarpAnimation.screenOpacity(WarpAnimation.IDLE, 25));
    }

    @Test
    void keysLockLeftToRightBeforeDarkeningAndEffectsExpire() {
        for (float age = 0; age < 22; age += .25F) {
            assertTrue(WarpAnimation.keyLock(age, 0) >= WarpAnimation.keyLock(age, 1));
            assertTrue(WarpAnimation.keyLock(age, 1) >= WarpAnimation.keyLock(age, 2));
        }
        for (int key = 0; key < 3; key++) assertEquals(1, WarpAnimation.keyLock(22, key));
        assertFalse(WarpAnimation.active(WarpAnimation.IDLE, 5));
        assertFalse(WarpAnimation.active(WarpAnimation.CHARGING, -1));
        assertTrue(WarpAnimation.active(WarpAnimation.CHARGING, WarpAnimation.CHARGE_TICKS - .01F));
        assertFalse(WarpAnimation.active(WarpAnimation.CHARGING, WarpAnimation.CHARGE_TICKS));
        assertTrue(WarpAnimation.active(WarpAnimation.ARRIVING, WarpAnimation.ARRIVAL_TICKS - .01F));
        assertFalse(WarpAnimation.active(WarpAnimation.ARRIVING, WarpAnimation.ARRIVAL_TICKS));
    }
}
