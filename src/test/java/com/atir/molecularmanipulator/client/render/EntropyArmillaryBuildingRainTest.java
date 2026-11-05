package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EntropyArmillaryBuildingRainTest {
    @Test
    void disabledOrUnlitBuildingsEmitNothingButEnabledRainReachesTheWorldFog() {
        for (boolean glow : new boolean[] {false, true}) {
            assertTrue(draw(0, 2, 0, 3, 4, glow).vertices().isEmpty());
            assertTrue(draw(-1, 2, 0, 3, 4, glow).vertices().isEmpty());
            assertTrue(draw(9, 0, 0, 3, 4, glow).vertices().isEmpty());
            for (double distance : new double[] {160, 224, 257, 384, 512}) {
                assertFalse(draw(9, 2, distance * distance, 3, 4, glow).vertices().isEmpty());
            }
            assertFalse(draw(9, 1, 159D * 159D, 3, 4, glow).vertices().isEmpty());
        }
    }

    @Test
    void codeCoversTheMovingBeltsAndFloatingPedestalNotOnlyTheCentralAxis() {
        for (float ringTime : new float[] {0, 3, 14, 40, 83}) {
            var vertices = draw(9, 2, 0, 6, ringTime, false).vertices();
            assertFalse(vertices.isEmpty());
            assertEquals(0, vertices.size() % 4);
            assertTrue(vertices.stream().anyMatch(v -> Math.hypot(v.x(), v.z()) > 29));
            assertTrue(vertices.stream().anyMatch(v -> v.y() > 10));
            assertTrue(vertices.stream().anyMatch(v -> v.y() < -30));
            assertTrue(vertices.stream().allMatch(v -> Math.hypot(v.x(), v.z()) < 33));
            assertTrue(vertices.stream().allMatch(v -> v.y() > -39 && v.y() < 37));
            assertTrue(vertices.stream().allMatch(v -> v.alpha() > 0 && v.alpha() <= 196));
        }
    }

    @Test
    void pedestalTrailsUseTheNewNarrowRingsInsteadOfTheRemovedSolidTerraces() {
        for (float time : new float[] {0, 3, 6, 19}) {
            var low = draw(1, 2, 0, time, 0, false).vertices().stream()
                    .filter(v -> v.y() < -30).toList();
            assertFalse(low.isEmpty());
            assertTrue(low.stream().allMatch(v -> Math.hypot(v.x(), v.z()) > 8.7
                    && Math.hypot(v.x(), v.z()) < 17));
            for (double radius : new double[] {9, 13.1, 16.55}) {
                assertTrue(low.stream().anyMatch(v -> Math.abs(Math.hypot(v.x(), v.z()) - radius) < .3));
            }
        }
    }

    @Test
    void activeRingTiersColorTheirOwnTrailsWithoutLightingFutureRings() {
        int base = EntropyArmillaryEffects.tierColor(0);
        int quantum = EntropyArmillaryEffects.tierColor(3);
        int terminal = EntropyArmillaryEffects.tierColor(6);
        var first = draw(1, 2, 0, 3, 4, false).vertices();
        assertTrue(first.stream().allMatch(v -> v.rgb() == base));
        var all = draw(9, 2, 0, 3, 4, false).vertices();
        assertTrue(all.stream().anyMatch(v -> v.rgb() == base));
        assertTrue(all.stream().anyMatch(v -> v.rgb() == quantum));
        assertTrue(all.stream().anyMatch(v -> v.rgb() == terminal));
        assertTrue(all.size() > first.size());
    }

    @Test
    void trailsFallWithTheFlowClockWhileTheirOriginsStayFixed() {
        for (boolean glow : new boolean[] {false, true}) {
            var first = draw(9, 2, 0, 3.1F, 4, glow).vertices();
            var later = draw(9, 2, 0, 3.2F, 4, glow).vertices();
            assertEquals(first.getFirst().x(), later.getFirst().x(), 1E-5);
            assertEquals(first.getFirst().z(), later.getFirst().z(), 1E-5);
            assertTrue(first.getFirst().y() > later.getFirst().y());
            assertEquals(first, draw(9, 2, 0, 3.1F, 6, glow).vertices());
            assertEquals(first, draw(9, 2, 0, 3.1F, 4, glow).vertices());
        }
    }

    @Test
    void qualityAndDistanceBoundTheAdditionalGeometry() {
        for (float time : new float[] {0, 1, 3, 8, 15, 11999}) {
            int full = 0, reduced = 0;
            for (boolean glow : new boolean[] {false, true}) {
                full += draw(9, 2, 0, time, time, glow).vertices().size();
                reduced += draw(9, 1, 0, time, time, glow).vertices().size();
                assertEquals(draw(9, 1, 0, time, time, glow).vertices(),
                        draw(9, 2, 113D * 113D, time, time, glow).vertices());
            }
            assertTrue(full < 20000, "Building code vertex budget: " + full);
            assertTrue(reduced * 2 < full);
        }
    }

    @Test
    void theSharedClockWrapDoesNotChangePositionsOrGlyphs() {
        for (boolean glow : new boolean[] {false, true}) {
            var a = draw(9, 2, 0, 3, 4, glow).vertices();
            var b = draw(9, 2, 0, 12003, 4, glow).vertices();
            assertEquals(a.size(), b.size());
            for (int i = 0; i < a.size(); i++) {
                assertEquals(a.get(i).x(), b.get(i).x(), 1E-4);
                assertEquals(a.get(i).y(), b.get(i).y(), 1E-4);
                assertEquals(a.get(i).z(), b.get(i).z(), 1E-4);
                assertEquals(a.get(i).argb(), b.get(i).argb());
            }
        }
    }

    @Test
    void everyFacingUsesTheCallerPoseWithoutMutatingIt() {
        for (int angle : new int[] {0, 90, 180, 270}) {
            var poses = new PoseStack();
            poses.translate(3, 12, -9);
            poses.mulPose(Axis.YP.rotationDegrees(angle));
            var before = new Matrix4f(poses.last().pose());
            var local = draw(9, 2, 0, 3, 4, false).vertices();
            var out = new RecordingColorConsumer();
            EntropyArmillaryCoreEffects.renderBuildingRain(poses, out, 9, 2, 0, 3, 4, false);
            assertEquals(before, poses.last().pose());
            assertEquals(local.size(), out.vertices().size());
            for (int i = 0; i < local.size(); i++) {
                var source = local.get(i);
                var actual = out.vertices().get(i);
                var expected = before.transformPosition(new Vector3f(source.x(), source.y(), source.z()));
                assertEquals(expected.x, actual.x(), 1E-5);
                assertEquals(expected.y, actual.y(), 1E-5);
                assertEquals(expected.z, actual.z(), 1E-5);
                assertEquals(source.argb(), actual.argb());
            }
        }
    }

    private static RecordingColorConsumer draw(int lit, int quality, double distance,
            float flowTime, float ringTime, boolean glow) {
        var out = new RecordingColorConsumer();
        EntropyArmillaryCoreEffects.renderBuildingRain(new PoseStack(), out, lit, quality,
                distance, flowTime, ringTime, glow);
        return out;
    }
}
