package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.blockentity.FrostFeatherGeometry;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure.StructureLayout;
import com.atir.molecularmanipulator.client.render.RecordingColorConsumer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class MolecularCenterFeatherRenderTest {
    private static final int FIELD = 0x123456;
    private static final int CORE = 0x234567;
    private static final int PRIMARY = 0x345678;
    private static final int SECONDARY = 0x456789;
    private static final int LATTICE = 0x56789A;

    @Test
    void ColoredCoreFacetsAreLocalAndPreserveTheCallerPose() {
        var stack = new PoseStack();
        var before = new Matrix4f(stack.last().pose());
        var surface = new RecordingColorConsumer();
        FeatherResonanceEffects.renderCoreSurface(stack, surface, 67, CORE, SECONDARY);
        assertEquals(before, stack.last().pose());
        assertEquals(Set.of(CORE, SECONDARY), colors(surface));
        assertTrue(surface.vertices().stream().allMatch(v -> v.radius() <= 3.401 && v.alpha() <= 96));
        assertEquals(96, surface.vertices().size());
    }

    @Test
    void SixPetalsCloseTowardTheSeedDuringWorkAndCompletionAddsAnExpandingWave() {
        for (int petal = 0; petal < 6; petal++) {
            double phase = petal * Math.TAU / 6;
            for (int sample = 0; sample <= 12; sample++) {
                var idle = FeatherResonanceEffects.petalPoint(phase, sample / 12.0, 0);
                var working = FeatherResonanceEffects.petalPoint(phase, sample / 12.0, 1);
                assertTrue(Math.hypot(working.x, working.z) < Math.hypot(idle.x, idle.z));
                assertTrue(working.y <= idle.y && idle.y < 5.4);
            }
        }
        var early = new RecordingColorConsumer();
        var late = new RecordingColorConsumer();
        var idle = new RecordingColorConsumer();
        MolecularCenterRenderer.renderFeatherPass(new PoseStack(), early, 67, 0, 0.9F, 2, true,
                FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
        MolecularCenterRenderer.renderFeatherPass(new PoseStack(), late, 67, 0, 0.2F, 2, true,
                FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
        MolecularCenterRenderer.renderFeatherPass(new PoseStack(), idle, 67, 0, 0, 2, true,
                FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
        assertTrue(early.vertices().size() > idle.vertices().size());
        var e = early.vertices().getLast();
        var l = late.vertices().getLast();
        assertTrue(Math.hypot(l.x(), l.z()) > Math.hypot(e.x(), e.z()));
        assertTrue(l.alpha() < e.alpha());
    }

    @Test
    void effectLevelsAndAllFiveCustomColorsRemainAvailable() {
        assertTrue(draw(0, false, 43, 0).vertices().isEmpty());
        assertTrue(draw(0, true, 43, 0).vertices().isEmpty());
        assertEquals(Set.of(CORE, PRIMARY), colors(draw(1, false, 43, 0)));
        assertEquals(Set.of(FIELD, CORE, PRIMARY), colors(draw(1, true, 43, 0)));
        assertEquals(Set.of(CORE, PRIMARY, SECONDARY, LATTICE), colors(draw(2, false, 43, 0)));
        assertEquals(Set.of(FIELD, CORE, PRIMARY, SECONDARY, LATTICE), colors(draw(2, true, 43, 0)));
        assertTrue(draw(1, false, 43, 0).vertices().size() < draw(2, false, 43, 0).vertices().size());
    }

    @Test
    void mainTrailStaysHorizontalBelowTheCoreWithoutFollowingAnimatedInnerOrbits() {
        var first = mainTrail(draw(2, false, 0, 0));
        var later = mainTrail(draw(2, false, 91, 3));
        assertFalse(first.isEmpty());
        assertEquals(first, later);
        assertEquals(1, FrostFeatherGeometry.orbits().size());
        double height = FrostFeatherGeometry.RING_Y - FrostFeatherGeometry.CORE_Y;
        double radius = FrostFeatherGeometry.orbits().getFirst().radius() - 2.5;
        assertEquals(-5, height);
        for (var vertex : first) {
            assertTrue(Math.abs(vertex.y() - height) <= 0.0421,
                    "Main trail must stay on the structure's horizontal ring plane");
            assertTrue(Math.abs(Math.hypot(vertex.x(), vertex.z()) - radius) <= 0.0421,
                    "Main trail must remain thin and exposed inside the physical border");
            assertEquals(PRIMARY, vertex.rgb());
        }

    }

    @Test
    void facetsAndLightFeathersRemainTranslucentAndWithinTheStructureEnvelope() {
        for (int mode = 0; mode <= 4; mode++) {
            var depth = draw(2, false, 59, mode);
            var glow = draw(2, true, 59, mode);
            assertTrue(depth.vertices().stream().noneMatch(vertex -> vertex.rgb() == FIELD));
            assertTrue(depth.vertices().stream().filter(vertex -> vertex.rgb() == CORE)
                    .allMatch(vertex -> vertex.radius() >= 0.75 && vertex.radius() <= 3.46));
            assertTrue(glow.vertices().stream().allMatch(vertex -> vertex.alpha() <= 34));
            assertTrue(glow.vertices().stream().filter(vertex -> vertex.rgb() == FIELD)
                    .allMatch(vertex -> vertex.alpha() <= 25));
            assertTrue(glow.vertices().stream().allMatch(vertex -> Math.abs(vertex.x()) < 31
                    && Math.abs(vertex.z()) < 31 && vertex.y() > -6 && vertex.y() < 15));
            assertTrue(glow.vertices().stream().anyMatch(vertex -> Math.abs(vertex.x()) > 26),
                    "Wing highlights must reach the far feathers");
            int totalVertices = depth.vertices().size() + glow.vertices().size();
            assertTrue(totalVertices < 100000, "Combined feather pass budget exceeded: " + totalVertices);
        }
    }

    @Test
    void featherHighlightsFollowMirroredPhysicalLeavesAndAnimateWithoutLongWraparoundBeams() {
        for (var feather : FrostFeatherGeometry.feathers()) {
            for (int sample = 0; sample <= 100; sample++) {
                double t = sample / 100.0;
                var physical = feather.point(t);
                var highlight = FeatherResonanceEffects.featherPoint(feather, t, -1, 1);
                assertEquals(-physical.x, highlight.x, 1.0E-9);
                assertEquals(physical.z, highlight.z, 1.0E-9);
                assertEquals(1.7, highlight.y + FrostFeatherGeometry.CORE_Y - physical.y, 1.0E-9);
            }
        }
        assertNotEquals(draw(2, false, 1, 0).vertices(), draw(2, false, 81, 0).vertices());
        assertNotEquals(draw(2, false, 81, 0).vertices(), draw(2, false, 81, 3).vertices());
        for (float angle : new float[] {0, 1, 180, 360, 720}) {
            var consumer = new RecordingColorConsumer();
            FeatherResonanceEffects.render(new PoseStack(), consumer, angle, 1, 0,
                    true, false, FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
            var vertices = consumer.vertices();
            for (int i = 0; i < vertices.size(); i += 4) {
                var first = vertices.get(i);
                var opposite = vertices.get(i + 2);
                double length = Math.sqrt(Math.pow(first.x() - opposite.x(), 2)
                        + Math.pow(first.y() - opposite.y(), 2) + Math.pow(first.z() - opposite.z(), 2));
                if (Math.hypot(first.x(), first.z()) <= 18) continue;
                assertTrue(length < 5.1, "A feather gleam must not bridge the entire wing");
            }
        }
    }



    @Test
    void bothFeatherPassesPreserveTheCallersPose() {
        var pose = new PoseStack();
        pose.translate(-3, 6, 12);
        pose.mulPose(Axis.YP.rotationDegrees(41));
        var before = new Matrix4f(pose.last().pose());
        for (boolean glow : new boolean[] { false, true }) {
            MolecularCenterRenderer.renderFeatherPass(pose, new RecordingColorConsumer(),
                    50, 1, 2, glow, FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
            assertEquals(before, pose.last().pose());
        }
    }

    private static RecordingColorConsumer draw(int level, boolean glow, float angle, int mode) {
        var recorder = new RecordingColorConsumer();
        MolecularCenterRenderer.renderFeatherPass(new PoseStack(), recorder,
                angle, mode, level, glow, FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
        return recorder;
    }

    private static RecordingColorConsumer drawLayout(StructureLayout layout) {
        var recorder = new RecordingColorConsumer();
        MolecularCenterRenderer.renderLayoutPass(layout, new PoseStack(), recorder,
                50, 1, 2, false, FIELD, CORE, PRIMARY, SECONDARY, LATTICE);
        return recorder;
    }

    private static List<RecordingColorConsumer.Vertex> mainTrail(RecordingColorConsumer recorder) {
        return recorder.vertices().stream().filter(vertex -> vertex.alpha() == 158 && vertex.radius() > 10).toList();
    }

    private static Set<Integer> colors(RecordingColorConsumer recorder) {
        return recorder.vertices().stream().map(RecordingColorConsumer.Vertex::rgb)
                .collect(Collectors.toSet());
    }
}
