package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarOrreryMotionTest {
    @Test
    void inactiveRingLayoutKeepsItsOriginalPlaneAtEveryLevelAndTime() {
        for (int lit = 0; lit <= 9; lit++) {
            for (int ring = lit; ring < 9; ring++) {
                var rest = rest(ring);
                for (float time : new float[] {0F, .05F, 7F, 41F, 137.5F, 11999.9F}) {
                    assertRotation(rest, rotation(ring, lit, time), 1E-6F);
                    assertEquals(0F, StarOrreryEffects.beltSpin(ring, lit, time));
                }
            }
        }
    }

    @Test
    void activeRingsChangeTheirEntirePlaneAndNotJustTheirInPlaneAngle() {
        for (int ring = 0; ring < 9; ring++) {
            var originalNormal = rest(ring).transform(new Vector3f(0, 1, 0));
            var movingNormal = rotation(ring, 9, 7F).transform(new Vector3f(0, 1, 0));
            assertTrue(originalNormal.dot(movingNormal) < .99F,
                    "The whole ring must visibly tilt around the core, ring " + ring);
            // An in-plane belt spin alone cannot satisfy the new requirement.
            var spinOnly = rest(ring).rotateY(StarOrreryEffects.beltSpin(ring, 7F))
                    .transform(new Vector3f(0, 1, 0));
            assertVector(originalNormal, spinOnly, 1E-6F);
        }
    }

    @Test
    void onlyItsOwnActivationStartsAPlaneAndDeactivationRestoresTheRestPose() {
        for (int ring = 0; ring < 9; ring++) {
            assertRotation(rest(ring), rotation(ring, ring, 7F), 1E-6F);
            var active = rotation(ring, ring + 1, 7F);
            assertNotEquals(rotation(ring, ring + 1, 8F), active);
            assertRotation(active, rotation(ring, 9, 7F), 1E-6F);
            assertRotation(rest(ring), rotation(ring, ring, 8F), 1E-6F);
        }
        assertRotation(rest(0), rotation(0, -1, 7F), 1E-6F);
        assertRotation(rotation(8, 9, 7F), rotation(8, 99, 7F), 1E-6F);
        assertThrows(IndexOutOfBoundsException.class, () -> rotation(-1, 9, 7F));
        assertThrows(IndexOutOfBoundsException.class, () -> rotation(9, 9, 7F));
    }

    @Test
    void ringsRemainConcentricAndRigidForEveryFacingAndTime() {
        for (int facing : new int[] {0, 90, 180, 270}) {
            for (int ring = 0; ring < 9; ring++) {
                float radius = (float) StarOrreryLayout.radius(ring);
                for (float time : new float[] {0F, 7F, 41F, 137.5F, 11999.9F}) {
                    var plane = new Quaternionf().rotationY((float) Math.toRadians(facing))
                            .mul(rotation(ring, 9, time));
                    assertVector(new Vector3f(), plane.transform(new Vector3f()), 0F);
                    var normal = plane.transform(new Vector3f(0, 1, 0));
                    for (int slot = 0; slot < 16; slot++) {
                        double a = slot * Math.PI / 8D;
                        var point = plane.transform(new Vector3f((float) Math.cos(a) * radius, 0,
                                (float) Math.sin(a) * radius));
                        assertEquals(radius, point.length(), 1E-5F, "No orbit offset or radius drift");
                        assertEquals(0F, point.dot(normal), 2E-5F, "Entire ring stays in one moving plane");
                        assertTrue(Math.abs(point.y) < 32F, "Ring stays clear of the pedestal below y=-33");
                    }
                }
            }
        }
    }

    @Test
    void bothRailPassesUseExactlyTheSameMovingPlaneAsEveryPhysicalRing() {
        for (boolean glow : new boolean[] {false, true}) {
            for (float time : new float[] {0F, 7F, 41F}) {
                var poses = new PoseStack();
                poses.translate(3, 12, -9);
                poses.mulPose(Axis.YP.rotationDegrees(90));
                var parent = new Matrix4f(poses.last().pose());
                var batch = new RecordingColorConsumer();
                StarOrreryEffects.render(poses, batch, 9, time, true, glow);
                assertEquals(parent, poses.last().pose());
                int offset = 0;
                for (int ring = 0; ring < 9; ring++) {
                    var local = new RecordingColorConsumer();
                    StarOrreryEffects.renderRing(new PoseStack(), local, ring, time, true, glow);
                    var transform = new Matrix4f(parent).rotate(rotation(ring, 9, time));
                    for (var source : local.vertices()) {
                        var actual = batch.vertices().get(offset++);
                        var expected = transform.transformPosition(new Vector3f(source.x(), source.y(), source.z()));
                        assertEquals(expected.x, actual.x(), 3E-5F);
                        assertEquals(expected.y, actual.y(), 3E-5F);
                        assertEquals(expected.z, actual.z(), 3E-5F);
                        assertEquals(source.argb(), actual.argb());
                    }
                }
                assertEquals(offset, batch.vertices().size());
            }
        }
    }

    @Test
    void reusableQuaternionsResetAndTheClockWrapHasNoPlaneJump() {
        var reused = new Quaternionf();
        for (int ring = 0; ring < 9; ring++) {
            for (float time : new float[] {0F, 7F, 41F, 11999.9F}) {
                StarOrreryMotion.planeRotation(reused, ring, 9, time);
                assertRotation(rotation(ring, 9, time), reused, 1E-6F);
            }
            assertRotation(rest(ring), rotation(ring, 9, 0F), 1E-6F);
            assertRotation(rotation(ring, 9, 0F), rotation(ring, 9, 12000F), 1E-6F);
            assertRotation(rotation(ring, 9, 7F), rotation(ring, 9, 12007F), 1E-6F);
            assertRotation(rotation(ring, 9, 11999.99F), rotation(ring, 9, .01F), .004F);
        }
    }

    @Test
    void onlyDisablingAnimationStopsTheModelClockAndEffectLodNeverResetsIt() {
        float time = StarOrreryMotion.timeSeconds(800, .5F, 2);
        assertEquals(40.025F, time, 1E-5F);
        assertEquals(time, StarOrreryMotion.timeSeconds(800, .5F, 1));
        assertEquals(0F, StarOrreryMotion.timeSeconds(800, .5F, 0));
        assertEquals(time, StarOrreryMotion.timeSeconds(240800, .5F, 2));
        assertTrue(StarOrreryEffects.effectsVisible(2, 159D * 159D));
        assertTrue(StarOrreryEffects.effectsVisible(2, 161D * 161D));
        // Model time has no distance argument: crossing light-effect LOD leaves
        // every still-visible ring's rigid pose on the same animation clock.
        assertNotEquals(rotation(2, 9, time), rotation(2, 9, 0F));
        assertRotation(rotation(2, 9, time), rotation(2, 9,
                StarOrreryMotion.timeSeconds(800, .5F, 1)), 1E-6F);
    }

    private static Quaternionf rest(int ring) {
        return StarOrreryRenderer.normalRotation(StarOrreryLayout.yaw(ring),
                StarOrreryLayout.pitch(ring));
    }

    private static Quaternionf rotation(int ring, int lit, float time) {
        return StarOrreryMotion.planeRotation(new Quaternionf(), ring, lit, time);
    }

    private static void assertRotation(Quaternionf expected, Quaternionf actual, float tolerance) {
        for (var axis : new Vector3f[] {new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), new Vector3f(0, 0, 1)}) {
            assertVector(expected.transform(new Vector3f(axis)), actual.transform(new Vector3f(axis)), tolerance);
        }
    }

    private static void assertVector(Vector3f expected, Vector3f actual, float tolerance) {
        assertEquals(expected.x, actual.x, tolerance);
        assertEquals(expected.y, actual.y, tolerance);
        assertEquals(expected.z, actual.z, tolerance);
    }
}
