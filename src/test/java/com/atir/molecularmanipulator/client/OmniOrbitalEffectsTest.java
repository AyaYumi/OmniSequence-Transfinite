package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniCrownGeometry;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.StructureLayout;
import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OmniOrbitalEffectsTest {
    @Test
    void projectedHypercubeHasSixteenMovingCornersInsideTheGuaranteedAirCore() {
        for (double angle : new double[] {0, 50, 360, 1100, 3600, 10000000}) {
            var vertices = OmniOrbitalEffects.hypercubeVertices(angle);
            assertEquals(16, vertices.length);
            assertEquals(16, java.util.Arrays.stream(vertices).distinct().count());
            assertTrue(java.util.Arrays.stream(vertices).allMatch(v -> Double.isFinite(v.length()) && v.length() < 3.31));
        }
        var before = OmniOrbitalEffects.hypercubeVertices(3599.9);
        var after = OmniOrbitalEffects.hypercubeVertices(3600.1);
        for (int i = 0; i < 16; i++) assertTrue(before[i].distanceTo(after[i]) < 0.02);
        assertFalse(java.util.Arrays.equals(OmniOrbitalEffects.hypercubeVertices(0),
                OmniOrbitalEffects.hypercubeVertices(100)));
    }

    @Test
    void pairedRailsAndPanelEdgesStayParallelToTheOriginalPhysicalRings() {
        for (var orbit : OmniCrownGeometry.orbits()) {
            var normal = new Vec3(orbit.nx(), orbit.ny(), orbit.nz());
            for (double offset : new double[] {-0.68, -0.62, 0, 0.62, 0.68}) {
                for (int sample = 0; sample < 48; sample++) {
                    var point = OmniOrbitalEffects.orbitPoint(orbit, sample * (Math.PI * 2) / 48, offset);
                    double plane = orbit.ny() > 0.999 ? 0.66 : 1.9;
                    assertEquals(plane, point.dot(normal), 1.0E-8);
                    assertEquals(orbit.radius() + offset, point.subtract(normal.scale(plane)).length(), 1.0E-8);
                }
            }
        }
    }

    @Test
    void exposedRailsRetainTheThreePhysicalOrbitPlanesAndRadii() {
        assertEquals(3, OmniCrownGeometry.orbits().size());
        for (var orbit : OmniCrownGeometry.orbits()) {
            Vec3 normal = new Vec3(orbit.nx(), orbit.ny(), orbit.nz());
            double offset = orbit.ny() > 0.999 ? 0.66 : 1.9;
            for (int step = 0; step < 360; step++) {
                Vec3 point = OmniOrbitalEffects.railPoint(orbit, Math.toRadians(step));
                assertEquals(offset, point.dot(normal), 1.0E-8);
                assertEquals(orbit.radius(), point.subtract(normal.scale(offset)).length(), 1.0E-8);
            }
        }
    }

    @Test
    void idleIsVisibleAnimatedAndBoundedWhileWorkingAndCompletionAreDistinct() {
        var idle = draw(0, 0, 0, true, false);
        assertTrue(idle.vertices().stream().anyMatch(v -> v.radius() > 30 && v.alpha() >= 150));
        assertNotEquals(idle.vertices(), draw(45, 0, 0, true, false).vertices());
        assertNotEquals(idle.vertices(), draw(0, 1, 0, true, false).vertices());
        assertNotEquals(idle.vertices(), draw(0, 0, 1, true, false).vertices());
        assertTrue(draw(0, 0, 0, false, false).vertices().size() < idle.vertices().size());
        var glow = draw(0, 0, 0, true, true);
        int budget = idle.vertices().size() + glow.vertices().size();
        assertTrue(budget < 150000, "Orbital vertex budget: " + budget);
        assertTrue(glow.vertices().stream().allMatch(v -> v.alpha() <= 38 && v.radius() < 33));
    }

    @Test
    void callerPoseIsPreservedAndUnknownLayoutEmitsNothing() {
        var stack = new PoseStack();
        stack.translate(3, 12, -9);
        stack.mulPose(Axis.YP.rotationDegrees(90));
        var before = new Matrix4f(stack.last().pose());
        for (boolean glow : new boolean[] {false, true}) {
            OmniOrbitalEffects.render(stack, new RecordingColorConsumer(), 67, 1, 0.5F, true, glow);
            assertEquals(before, stack.last().pose());
        }
        var unknown = new RecordingColorConsumer();
        OmniComputationRenderer.renderCrownLayoutPass(StructureLayout.INCOMPLETE, stack, unknown,
                67, 1, 1, true, false);
        assertTrue(unknown.vertices().isEmpty());
    }

    private static RecordingColorConsumer draw(float angle, float activity, float completion,
            boolean detailed, boolean glow) {
        var consumer = new RecordingColorConsumer();
        OmniComputationRenderer.renderCrownPass(new PoseStack(), consumer, angle, activity, completion,
                detailed, glow);
        return consumer;
    }
}
