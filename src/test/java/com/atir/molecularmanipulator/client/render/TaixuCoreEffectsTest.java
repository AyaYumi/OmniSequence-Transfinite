package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.blaze3d.vertex.PoseStack;
import org.junit.jupiter.api.Test;

class TaixuCoreEffectsTest {
    @Test
    void animatedGeometryStaysInsideTheBlockAndRestoresThePose() {
        for (boolean detail : new boolean[] {false, true}) {
            for (boolean glow : new boolean[] {false, true}) {
                for (float time : new float[] {0, 17, 120, 4000, 23999.5F}) {
                    var poses = new PoseStack();
                    var original = new org.joml.Matrix4f(poses.last().pose());
                    var recorder = new RecordingColorConsumer();
                    TaixuCoreEffects.render(poses, recorder, time, 217, detail, glow);
                    assertEquals(original, poses.last().pose());
                    assertEquals(0, recorder.vertices().size() % 4);
                    assertTrue(recorder.vertices().size() < 5500, "Bound the per-core vertex cost");
                    for (var v : recorder.vertices()) {
                        assertTrue(Math.abs(v.x()) < 0.5 && Math.abs(v.y()) < 0.5 && Math.abs(v.z()) < 0.5,
                                "Normal block bounds must contain every glow and mote");
                        assertTrue(v.alpha() > 0 && v.alpha() <= 255);
                    }
                }
            }
        }
    }

    @Test
    void reducedModeActuallyReducesGeometryAndAnimationClosesAtClockWrap() {
        var reduced = new RecordingColorConsumer();
        var full = new RecordingColorConsumer();
        TaixuCoreEffects.render(new PoseStack(), reduced, 0, 0, false, false);
        TaixuCoreEffects.render(new PoseStack(), full, 0, 0, true, false);
        assertTrue(reduced.vertices().size() * 2 <= full.vertices().size());
        var wrapped = new RecordingColorConsumer();
        TaixuCoreEffects.render(new PoseStack(), wrapped, 24000, 0, true, false);
        for (int i = 0; i < full.vertices().size(); i++) {
            var a = full.vertices().get(i);
            var b = wrapped.vertices().get(i);
            assertEquals(a.x(), b.x(), 0.0001);
            assertEquals(a.y(), b.y(), 0.0001);
            assertEquals(a.z(), b.z(), 0.0001);
        }
    }
}
