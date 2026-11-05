package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.world.WarpAnimation;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;

class WarpPadEffectsTest {
    @Test
    void idleCancelledAndExpiredAnimationsEmitNoGeometry() {
        var out = new RecordingColorConsumer();
        var poses = new PoseStack();
        WarpPadEffects.render(poses, out, WarpAnimation.IDLE, 12);
        WarpPadEffects.render(poses, out, WarpAnimation.CHARGING, -1);
        WarpPadEffects.render(poses, out, WarpAnimation.CHARGING, WarpAnimation.CHARGE_TICKS);
        WarpPadEffects.render(poses, out, WarpAnimation.ARRIVING, WarpAnimation.ARRIVAL_TICKS);
        assertTrue(out.vertices().isEmpty());
    }

    @Test
    void bothPhasesUseFiniteColorOnlyQuadsWithinTheApprovedFootprint() {
        for (int phase : new int[] {WarpAnimation.CHARGING, WarpAnimation.ARRIVING}) {
            int duration = phase == WarpAnimation.CHARGING ? WarpAnimation.CHARGE_TICKS : WarpAnimation.ARRIVAL_TICKS;
            for (float age = 1; age < duration; age += 2.5F) {
                var out = new RecordingColorConsumer();
                WarpPadEffects.render(new PoseStack(), out, phase, age);
                assertFalse(out.vertices().isEmpty());
                assertEquals(0, out.vertices().size() % 4);
                assertTrue(out.vertices().stream().anyMatch(vertex -> vertex.alpha() > 0));
                for (var vertex : out.vertices()) {
                    assertTrue(vertex.x() >= -1 && vertex.x() <= 2, "X escaped the pad");
                    assertTrue(vertex.z() >= -1 && vertex.z() <= 2, "Z escaped the pad");
                    assertTrue(vertex.y() >= -.3 && vertex.y() <= 1.55, "Y escaped the pad and keys");
                }
            }
        }
    }
}
