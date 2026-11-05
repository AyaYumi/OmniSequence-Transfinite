package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.nio.file.Files;
import java.nio.file.Path;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class EntropyArmillaryPedestalMotionTest {
    @Test
    void dialUsesTheAuthoredSlowSpeedForEveryActiveTierAndQuality() {
        for (int lit : new int[] {1, 3, 6, 9}) for (int quality : new int[] {1, 2}) {
            float before = EntropyArmillaryPedestalMotion.dialRotation(20, 0, lit, quality);
            float after = EntropyArmillaryPedestalMotion.dialRotation(100, 0, lit, quality);
            assertEquals(.05F, before, 1E-7F);
            assertEquals(.25F, after, 1E-7F);
            assertEquals(.05F / 20F * .5F, EntropyArmillaryPedestalMotion.dialRotation(20, .5F, lit, quality)
                    - before, 1E-7F);
        }
    }

    @Test
    void unlitAndDisabledMachinesKeepTheDialAtRest() {
        assertEquals(0F, EntropyArmillaryPedestalMotion.dialRotation(5000, .5F, 0, 2));
        assertEquals(0F, EntropyArmillaryPedestalMotion.dialRotation(5000, .5F, 9, 0));
    }

    @Test
    void rotationStaysContinuousAcrossTheRingClockSeamAndLongWorldTimes() {
        for (long tick : new long[] {239999L, 479999L, 1_000_000_000L}) {
            float before = EntropyArmillaryPedestalMotion.dialRotation(tick, .9F, 9, 2);
            float after = EntropyArmillaryPedestalMotion.dialRotation(tick + 1, .1F, 9, 2);
            var a = new Vector3f(1, 0, 0).rotateY(before);
            var b = new Vector3f(1, 0, 0).rotateY(after);
            assertEquals(.0005F, a.distance(b), 1E-6F);
            assertTrue(after >= 0 && after <= Math.PI * 2D);
        }
    }

    @Test
    void tiltAndSpinAreLocalToTheDialWithoutMovingItsCenterOrLeakingThePose() {
        var poses = new PoseStack();
        poses.translate(.5, .5, .5);
        var before = new Matrix4f(poses.last().pose());
        for (long tick : new long[] {0, 80, 600, 240000}) {
            poses.pushPose();
            poses.translate(0, EntropyArmillaryPedestalMotion.DIAL_Y, 0);
            poses.mulPose(Axis.XP.rotation(EntropyArmillaryPedestalMotion.DIAL_TILT));
            poses.mulPose(Axis.YP.rotation(EntropyArmillaryPedestalMotion.dialRotation(tick, .5F, 9, 2)));
            var center = poses.last().pose().transformPosition(new Vector3f());
            assertEquals(.5F, center.x, 1E-5F);
            assertEquals(-35F, center.y, 1E-5F);
            assertEquals(.5F, center.z, 1E-5F);
            var edge = poses.last().pose().transformPosition(new Vector3f(13.7F, 0, 0));
            assertEquals(13.7F, edge.distance(center), 1E-4F);
            assertTrue(Math.abs(edge.y - center.y) <= 13.7D * Math.sin(.12D) + 1E-4D);
            poses.popPose();
            assertEquals(before, poses.last().pose());
        }
    }

    @Test
    void dialAndItsGlowShareOneLocalPoseWhileTheOldSolidPedestalIsNotDrawn() throws Exception {
        var root = Path.of("src/main/java/com/atir/molecularmanipulator/client");
        String renderer = Files.readString(root.resolve("render/EntropyArmillaryRenderer.java"));
        String events = Files.readString(root.resolve("ClientEvents.java"));
        int start = renderer.indexOf("poses.translate(0, EntropyArmillaryPedestalMotion.DIAL_Y, 0)");
        int end = renderer.indexOf("poses.popPose()", start);
        assertTrue(start >= 0 && end > start);
        String dial = renderer.substring(start, end);
        assertTrue(dial.contains("Axis.XP.rotation(EntropyArmillaryPedestalMotion.DIAL_TILT)"));
        assertTrue(dial.contains("EntropyArmillaryPedestalMotion.dialRotation("));
        assertTrue(dial.contains("\"floating_pedestal_dial\""));
        assertTrue(dial.contains("\"floating_pedestal_dial_glow\""));
        assertFalse(dial.contains("\"floating_pedestal_petals\""));
        assertFalse(renderer.contains("\"pedestal_skin\""));
        assertFalse(renderer.contains("PEDESTAL_ARCS"));
        assertTrue(events.contains("EntropyArmillaryRenderer.PARTS"));
        assertFalse(events.contains("PEDESTAL_ARCS"));
        for (String form : EntropyArmillaryRenderer.FORMS) {
            for (String part : EntropyArmillaryRenderer.PARTS) {
                assertTrue(Files.isRegularFile(Path.of("src/main/resources/assets/molecularmanipulator/models/block/entropy_armillary")
                        .resolve(form).resolve(part + ".json")), form + "/" + part);
            }
        }
    }
}
