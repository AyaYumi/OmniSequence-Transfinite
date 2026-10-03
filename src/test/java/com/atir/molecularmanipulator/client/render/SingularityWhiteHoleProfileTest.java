package com.atir.molecularmanipulator.client.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SingularityWhiteHoleProfileTest {
    @Test void assemblyExpandsThenSettlesWithoutCrossingTheInfluenceBoundary() {
        assertEquals(3.06F, radius(0), .001);
        assertEquals(6.8F, radius(84), .001);
        assertEquals(3.8F, radius(160), .001);
        for (int age = -20; age < 240; age++) {
            assertTrue(radius(age) > 0 && radius(age) < SingularityWhiteHoleProfile.FIELD_RADIUS);
            assertTrue(Math.abs(radius(age + 1) - radius(age)) < .26);
            if (age >= 88) assertTrue(radius(age + 1) <= radius(age));
        }
    }

    @Test void settledHorizonStaysRoundAndQuietAcrossClockWrap() {
        for (int time = 0; time < 400; time++) {
            float a = SingularityWhiteHoleProfile.radius(SingularityStructureEffects.Frame.steady(time));
            float b = SingularityWhiteHoleProfile.radius(SingularityStructureEffects.Frame.steady(time + 24000));
            assertEquals(a, b);
            assertTrue(a >= 3.764F && a <= 3.836F);
        }
    }

    private static float radius(float age) {
        return SingularityWhiteHoleProfile.radius(new SingularityStructureEffects.Frame(0, age, 1));
    }
}
