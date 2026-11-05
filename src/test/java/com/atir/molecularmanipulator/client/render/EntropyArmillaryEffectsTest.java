package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EntropyArmillaryEffectsTest {
    private static final float TIME = 137.5F;

    @Test
    void effectsOffOrZeroLitRingsSubmitNothing() {
        for (boolean glow : new boolean[] {false, true}) {
            for (boolean detailed : new boolean[] {false, true}) {
                var out = new RecordingColorConsumer();
                EntropyArmillaryEffects.render(new PoseStack(), out, 0, TIME, detailed, glow);
                assertTrue(out.vertices().isEmpty());
            }
        }
        assertEquals(0, EntropyArmillaryEffects.visibleRings(9, 0, 0));
        assertEquals(0, EntropyArmillaryEffects.visibleRings(0, 2, 0));
        assertFalse(EntropyArmillaryEffects.effectsVisible(0, 0));

        for (boolean glow : new boolean[] {false, true}) {
            var gated = new RecordingColorConsumer();
            EntropyArmillaryEffects.render(new PoseStack(), gated, 9, 0, 0D,
                    TIME, true, glow);
            assertTrue(gated.vertices().isEmpty(), "Effect level zero must submit no rail geometry");

            var visible = new RecordingColorConsumer();
            EntropyArmillaryEffects.render(new PoseStack(), visible, 9, 1, 100D * 100D,
                    TIME, false, glow);
            assertFalse(visible.vertices().isEmpty());
        }
    }

    @Test
    void distanceSelectsRailDetailWithoutCuttingOffVisibleBelts() {
        assertEquals(9, EntropyArmillaryEffects.visibleRings(9, 1, 100D * 100D));
        assertEquals(6, EntropyArmillaryEffects.visibleRings(6, 1, 159D * 159D));
        assertEquals(0, EntropyArmillaryEffects.visibleRings(9, 0, 0));
        for (double distance : new double[] {160, 224, 257, 384, 512}) {
            assertEquals(9, EntropyArmillaryEffects.visibleRings(9, 1, distance * distance));
        }
        assertFalse(EntropyArmillaryEffects.isDetailed(1, 0));
        assertTrue(EntropyArmillaryEffects.isDetailed(2, 0));
        assertFalse(EntropyArmillaryEffects.isDetailed(2, 113D * 113D));
    }

    @Test
    void railsUseThePhysicalRingPlaneAndStayOutsideTheBelt() {
        for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
            var plane = EntropyArmillaryRenderer.normalRotation(
                    EntropyArmillaryLayout.yaw(ring), EntropyArmillaryLayout.pitch(ring));
            var normal = plane.transform(new Vector3f(0, 1, 0));
            double rail = EntropyArmillaryEffects.railRadius(ring);
            assertTrue(rail >= EntropyArmillaryLayout.radius(ring) + .18D,
                    "Rail must sit outside the imported belt rim");
            for (boolean glow : new boolean[] {false, true}) {
                var poses = new PoseStack();
                poses.mulPose(plane);
                var out = new RecordingColorConsumer();
                EntropyArmillaryEffects.renderRing(poses, out, ring, TIME, true, glow);
                assertFalse(out.vertices().isEmpty());
                assertEquals(0, out.vertices().size() % 8,
                        "Whole two-sided QUADS keep the shader-compatible layers aligned");
                double tolerance = glow ? .10D : .06D;
                for (var vertex : out.vertices()) {
                    var point = new Vector3f(vertex.x(), vertex.y(), vertex.z());
                    double height = point.dot(normal);
                    assertTrue(Math.abs(height) <= EntropyArmillaryEffects.BELT_HALF_HEIGHT,
                            "Rail thickness must stay within the belt body");
                    var planar = new Vector3f(point).sub(new Vector3f(normal).mul((float) height));
                    assertEquals(rail, planar.length(), tolerance,
                            "Rail radius must follow the layout radius");
                }
            }
        }
    }

    @Test
    void beltsCarryInsetTangentCrystalsInsteadOfHangingPendants() {
        for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
            double beltCenter = EntropyArmillaryLayout.radius(ring);
            double beltOuter = beltCenter + .14D;
            int count = EntropyArmillaryEffects.embeddedCrystalCount(ring);
            assertTrue(count >= 3);
            for (int index = 0; index < count; index++) {
                double x = EntropyArmillaryEffects.embeddedCrystalX(ring, index);
                double z = EntropyArmillaryEffects.embeddedCrystalZ(ring, index);
                double radius = Math.hypot(x, z);
                assertEquals(EntropyArmillaryEffects.embeddedCrystalRadius(ring), radius, 1.0E-5D);
                assertTrue(radius >= beltCenter - .02D && radius <= beltOuter + .06D,
                        "Ornament must be anchored inside/just proud of the belt, not on a chain");
                assertEquals(0F, EntropyArmillaryEffects.embeddedCrystalY(ring, index), 0F,
                        "Ornaments stay on the belt symmetry plane");

                var orientation = new Matrix4f()
                        .rotationY(EntropyArmillaryEffects.embeddedCrystalYaw(ring, index))
                        .rotateZ((float) (-Math.PI * .5D));
                var longAxis = orientation.transformDirection(new Vector3f(0, 1, 0));
                double angle = EntropyArmillaryEffects.embeddedCrystalAngle(ring, index);
                var tangent = new Vector3f((float) -Math.sin(angle), 0, (float) Math.cos(angle));
                assertEquals(1D, longAxis.dot(tangent), 1.0E-5D,
                        "The crystal's long axis follows the belt tangent");
                assertEquals(0D, longAxis.y, 1.0E-6D);
            }

            var local = new RecordingColorConsumer();
            EntropyArmillaryEffects.renderRing(new PoseStack(), local, ring, 41F, true, false);
            for (var vertex : local.vertices()) {
                assertTrue(Math.abs(vertex.y()) <= EntropyArmillaryEffects.BELT_HALF_HEIGHT,
                        "No luminous geometry may hang below or above the belt body: " + vertex);
            }
        }
    }

    @Test
    void tierColorsAreDistinctAndAdditiveOutputStaysRestrained() {
        int[] expected = {0x63F2C4, 0xB88CFF, 0xFFD27D};
        for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
            int rgb = EntropyArmillaryEffects.tierColor(ring);
            assertEquals(expected[EntropyArmillaryLayout.tierOf(ring)], rgb);
            for (boolean glow : new boolean[] {false, true}) {
                var out = drawRing(ring, TIME, glow);
                assertFalse(out.vertices().isEmpty());
                assertTrue(out.vertices().stream().allMatch(v -> v.rgb() == rgb),
                        "Every rail component must use its tier color");
                if (glow) {
                    assertTrue(out.vertices().stream().allMatch(v -> v.alpha() <= 32),
                            "The additive halo must stay restrained");
                } else {
                    assertTrue(out.vertices().stream().allMatch(v -> v.alpha() >= 120));
                }
            }
        }

        var depth = drawAll(EntropyArmillaryEffects.RINGS, true, false);
        var glow = drawAll(EntropyArmillaryEffects.RINGS, true, true);
        int budget = depth.vertices().size() + glow.vertices().size();
        assertTrue(budget < 70000, "Batched rail vertex budget: " + budget);
        assertTrue(glow.vertices().stream().allMatch(v -> v.alpha() <= 32));
        assertTrue(depth.vertices().stream().allMatch(v -> v.alpha() >= 120));

        assertTrue(drawAll(EntropyArmillaryEffects.RINGS, false, false).vertices().size()
                < depth.vertices().size(), "Low effect quality must reduce rail detail");
        assertTrue(drawAll(EntropyArmillaryEffects.RINGS, false, true).vertices().size()
                < glow.vertices().size());
    }

    @Test
    void onlyActiveRingsAreBatchedInTheFinalPasses() {
        int expected = 0;
        for (int ring = 0; ring < 3; ring++) {
            expected += drawRing(ring, TIME, false).vertices().size();
            expected += drawRing(ring, TIME, true).vertices().size();
        }
        var active = new RecordingColorConsumer();
        EntropyArmillaryEffects.render(new PoseStack(), active, 3, TIME, true, false);
        EntropyArmillaryEffects.render(new PoseStack(), active, 3, TIME, true, true);
        assertEquals(expected, active.vertices().size());

        var dormant = drawAll(4, true, false);
        var activeFour = drawAll(3, true, false);
        assertTrue(dormant.vertices().size() > activeFour.vertices().size());
    }

    @Test
    void callerPoseIsAppliedExactlyAndRestoredForBothPasses() {
        var poses = new PoseStack();
        poses.translate(3, 12, -9);
        poses.mulPose(Axis.YP.rotationDegrees(37));
        var before = new Matrix4f(poses.last().pose());
        var local = drawRing(4, 55.5F, false);
        var transformed = new RecordingColorConsumer();
        EntropyArmillaryEffects.renderRing(poses, transformed, 4, 55.5F, true, false);
        assertEquals(before, poses.last().pose(), "The caller pose must be restored");
        assertEquals(local.vertices().size(), transformed.vertices().size());
        for (int index = 0; index < local.vertices().size(); index++) {
            var source = local.vertices().get(index);
            var target = transformed.vertices().get(index);
            var expected = before.transformPosition(new Vector3f(source.x(), source.y(), source.z()));
            assertEquals(expected.x(), target.x(), 1.0E-5D);
            assertEquals(expected.y(), target.y(), 1.0E-5D);
            assertEquals(expected.z(), target.z(), 1.0E-5D);
            assertEquals(source.argb(), target.argb());
        }
    }

    @Test
    void batchedFirstRingMatchesTheMovingRingTransform() {
        var ringPose = new PoseStack();
        ringPose.mulPose(EntropyArmillaryMotion.planeRotation(new Quaternionf(), 0, 1, 88F));
        var expected = new RecordingColorConsumer();
        EntropyArmillaryEffects.renderRing(ringPose, expected, 0, 88F, true, true);

        var batched = new RecordingColorConsumer();
        EntropyArmillaryEffects.render(new PoseStack(), batched, 1, 88F, true, true);
        assertEquals(expected.vertices().size(), batched.vertices().size());
        for (int index = 0; index < expected.vertices().size(); index++) {
            var a = expected.vertices().get(index);
            var b = batched.vertices().get(index);
            assertEquals(a.x(), b.x(), 1.0E-6D);
            assertEquals(a.y(), b.y(), 1.0E-6D);
            assertEquals(a.z(), b.z(), 1.0E-6D);
            assertEquals(a.argb(), b.argb());
        }

        var caller = new PoseStack();
        caller.translate(-2, 4, 7);
        var before = new Matrix4f(caller.last().pose());
        EntropyArmillaryEffects.render(caller, new RecordingColorConsumer(), 1, 88F, true, true);
        assertEquals(before, caller.last().pose());
    }

    @Test
    void pulsesTravelAndOppositeBeltsSpinInOppositeDirections() {
        assertNotEquals(drawRing(2, 0, false).vertices(), drawRing(2, 4, false).vertices(),
                "Pulses and belt details must animate");
        assertEquals(drawRing(2, 0, false).vertices(), drawRing(2, 0, false).vertices(),
                "The same frame must be deterministic");
        assertTrue(EntropyArmillaryEffects.beltSpin(0, 10F) > 0);
        assertTrue(EntropyArmillaryEffects.beltSpin(1, 10F) < 0);
    }

    @Test
    void inactiveBeltsHaveZeroSpinAtEveryUpgradeLevel() {
        for (int lit = 0; lit <= EntropyArmillaryEffects.RINGS; lit++) {
            for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
                for (float time : new float[] {0F, .05F, 10F, 137.5F, 11999.9F}) {
                    float expected = ring < lit ? EntropyArmillaryEffects.beltSpin(ring, time) : 0F;
                    assertEquals(expected, EntropyArmillaryEffects.beltSpin(ring, lit, time), 0F,
                            "L" + lit + " ring " + ring + " time " + time);
                }
            }
        }
    }

    @Test
    void aBeltStartsOnlyAfterItsOwnActivationAndStopsAfterLosingIt() {
        for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
            assertEquals(0F, EntropyArmillaryEffects.beltSpin(ring, ring, 40F));
            assertEquals(0F, EntropyArmillaryEffects.beltSpin(ring, ring, 80F));
            assertNotEquals(EntropyArmillaryEffects.beltSpin(ring, ring + 1, 40F),
                    EntropyArmillaryEffects.beltSpin(ring, ring + 1, 80F));
            assertEquals(0F, EntropyArmillaryEffects.beltSpin(ring, ring, 81F));
        }
        assertEquals(0F, EntropyArmillaryEffects.beltSpin(0, -1, 20F));
        assertEquals(EntropyArmillaryEffects.beltSpin(8, 20F),
                EntropyArmillaryEffects.beltSpin(8, 99, 20F));
        assertThrows(IndexOutOfBoundsException.class, () -> EntropyArmillaryEffects.beltSpin(-1, 0, 1F));
        assertThrows(IndexOutOfBoundsException.class, () -> EntropyArmillaryEffects.beltSpin(9, 9, 1F));
    }

    @Test
    void scanningPanelsAndCalibrationMarksStayOnTheActiveBeltFace() {
        for (boolean glow : new boolean[] {false, true}) {
            for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
                var out = drawRing(ring, 4F, glow);
                double radius = EntropyArmillaryEffects.railRadius(ring);
                assertTrue(out.vertices().stream().anyMatch(v -> Math.abs(v.y()) > .35F),
                        "Full quality includes the taller calibration marks");
                assertTrue(out.vertices().stream().anyMatch(v -> Math.abs(v.y()) == (glow ? .30F : .21F)),
                        "Full quality includes short luminous scanning panels");
                assertTrue(out.vertices().stream().allMatch(v -> Math.hypot(v.x(), v.z()) < radius + .10D));
            }
        }
    }

    @Test
    void calibrationMarksRotateWithThePhysicalBeltInsteadOfSlidingBackwards() {
        for (int ring = 0; ring < EntropyArmillaryEffects.RINGS; ring++) {
            for (boolean glow : new boolean[] {false, true}) {
                var rest = drawRing(ring, 0F, glow).vertices().stream()
                        .filter(v -> Math.abs(v.y()) > .35F).toList();
                float time = 8F;
                var rotated = drawRing(ring, time, glow).vertices().stream()
                        .filter(v -> Math.abs(v.y()) > .35F).toList();
                var spin = new Quaternionf().rotationY(EntropyArmillaryEffects.beltSpin(ring, time));
                assertFalse(rest.isEmpty());
                assertEquals(rest.size(), rotated.size());
                for (int index = 0; index < rest.size(); index++) {
                    var source = rest.get(index);
                    var expected = spin.transform(new Vector3f(source.x(), source.y(), source.z()));
                    var actual = rotated.get(index);
                    assertEquals(expected.x, actual.x(), 2E-5);
                    assertEquals(expected.y, actual.y(), 2E-5);
                    assertEquals(expected.z, actual.z(), 2E-5);
                }
            }
        }
    }

    private static RecordingColorConsumer drawRing(int ring, float time, boolean glow) {
        var out = new RecordingColorConsumer();
        EntropyArmillaryEffects.renderRing(new PoseStack(), out, ring, time, true, glow);
        return out;
    }

    private static RecordingColorConsumer drawAll(int rings, boolean detailed, boolean glow) {
        var out = new RecordingColorConsumer();
        EntropyArmillaryEffects.render(new PoseStack(), out, rings, TIME, detailed, glow);
        return out;
    }
}
