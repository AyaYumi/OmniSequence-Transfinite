package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class MolecularCenterEffectRendererTest {
    private static final int FIELD = 0x123456;
    private static final int CORE = 0x234567;
    private static final int PRIMARY = 0x345678;
    private static final int SECONDARY = 0x456789;
    private static final int LATTICE = 0x56789A;

    @Test
    void disabledEffectsSubmitNoGeometry() {
        assertTrue(draw(0, false, 35, 0).vertices().isEmpty());
        assertTrue(draw(0, true, 35, 0).vertices().isEmpty());
    }

    @Test
    void translucentFieldIsNeverSubmittedToTheDepthPass() {
        var depth = draw(2, false, 37, 3);
        var glow = draw(2, true, 37, 3);
        assertEquals(Set.of(CORE, PRIMARY, SECONDARY, LATTICE), colors(depth));
        assertEquals(Set.of(FIELD, CORE, PRIMARY, SECONDARY, LATTICE), colors(glow));
        assertTrue(depth.vertices().stream().noneMatch(v -> v.rgb() == FIELD));
        assertTrue(select(glow, FIELD).stream().allMatch(v -> v.alpha() == 42));
    }

    @Test
    void lowEffectLevelKeepsPrimaryRingAndCoreButOmitsSecondaryAndLattice() {
        var depth = draw(1, false, 51, 2);
        var glow = draw(1, true, 51, 2);
        assertEquals(Set.of(CORE, PRIMARY), colors(depth));
        assertEquals(Set.of(FIELD, CORE, PRIMARY), colors(glow));
        assertTrue(depth.vertices().size() < draw(2, false, 51, 2).vertices().size());
    }

    @Test
    void originalRadiiAndModeDependentFieldPulseArePreserved() {
        for (int mode = 0; mode <= 3; mode++) {
            float angle = 37.0F;
            var depth = draw(2, false, angle, mode);
            for (var vertex : select(depth, CORE)) assertEquals(1.82D, vertex.radius(), 0.00002D);
            assertRadiusRange(select(depth, PRIMARY), 14.05D, 14.45D);
            assertRadiusRange(select(depth, SECONDARY).stream().filter(v -> v.alpha() == 200).toList(),
                    12.08D, 12.42D);
            assertRadiusRange(select(depth, SECONDARY).stream().filter(v -> v.alpha() == 180).toList(),
                    11.50D, 11.80D);
            assertRadiusRange(select(depth, LATTICE), 4.345D, 4.455D);
            float strength = switch (mode) {
                case 1 -> 0.055F;
                case 2 -> 0.045F;
                case 3 -> 0.08F;
                default -> 0.035F;
            };
            double radius = 6.35F * (1.0F + (float) Math.sin(angle * 0.085F) * strength);
            for (var vertex : select(draw(2, true, angle, mode), FIELD)) {
                assertEquals(radius, vertex.radius(), 0.00002D);
            }
        }
    }

    @Test
    void passesBalancePoseStackAndKeepControlledGlowBudget() {
        var pose = new PoseStack();
        pose.translate(2, 4, -7);
        pose.mulPose(Axis.YP.rotationDegrees(19));
        var before = new Matrix4f(pose.last().pose());
        for (boolean glow : new boolean[] { false, true }) {
            MolecularCenterRenderer.renderPass(pose, new RecordingColorConsumer(),
                    50, 1, 2, glow, FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
            assertEquals(before, pose.last().pose());
        }
        var depth = draw(2, false, 50, 1);
        var halo = draw(2, true, 50, 1);
        assertTrue(depth.vertices().size() + halo.vertices().size() < 70000);
        assertTrue(halo.vertices().stream().allMatch(v -> v.alpha() <= 42));
        assertTrue(halo.vertices().stream().allMatch(v -> v.radius() < 14.62D));
    }

    private static RecordingColorConsumer draw(int level, boolean glow, float angle, int mode) {
        var recorder = new RecordingColorConsumer();
        MolecularCenterRenderer.renderPass(new PoseStack(), recorder,
                angle, mode, level, glow, FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
        return recorder;
    }

    private static Set<Integer> colors(RecordingColorConsumer recorder) {
        return recorder.vertices().stream().map(RecordingColorConsumer.Vertex::rgb)
                .collect(Collectors.toSet());
    }

    private static List<RecordingColorConsumer.Vertex> select(RecordingColorConsumer recorder, int rgb) {
        return recorder.vertices().stream().filter(v -> v.rgb() == rgb).toList();
    }

    private static void assertRadiusRange(List<RecordingColorConsumer.Vertex> vertices,
            double minimum, double maximum) {
        assertFalse(vertices.isEmpty());
        assertEquals(minimum, vertices.stream().mapToDouble(RecordingColorConsumer.Vertex::radius)
                .min().orElseThrow(), 0.00003D);
        assertEquals(maximum, vertices.stream().mapToDouble(RecordingColorConsumer.Vertex::radius)
                .max().orElseThrow(), 0.00003D);
    }
}
