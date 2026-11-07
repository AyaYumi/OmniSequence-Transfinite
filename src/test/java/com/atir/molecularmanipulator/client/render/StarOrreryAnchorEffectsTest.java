package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class StarOrreryAnchorEffectsTest {
    private static final int FULL_VERTICES = 240;

    private static RecordingColorConsumer render(int lit, int effects, Vec3 camera, float time, boolean glow) {
        var out = new RecordingColorConsumer();
        StarOrreryAnchorEffects.render(new PoseStack(), out, lit, effects, camera, time, glow);
        assertEquals(0, out.vertices().size() % 4);
        return out;
    }

    @Test
    void everyMarkerIsCentredOnTheRealFunctionalSlotAndFitsTheExistingShape() {
        for (float time : new float[]{0F, .8F, 1.6F, 2.7F}) {
            var vertices = render(9, 2, Vec3.ZERO, time, false).vertices();
            assertEquals(504 * FULL_VERTICES, vertices.size());
            int cursor = 0;
            for (int ring = 0; ring < 9; ring++) {
                for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                    var p = StarOrreryLayout.offset(Direction.NORTH, ring, slot);
                    assertEquals(p, StarOrreryAnchorEffects.marker(ring, slot));
                    for (int i = 0; i < FULL_VERTICES; i++) {
                        var v = vertices.get(cursor++);
                        assertTrue(Math.abs(v.x() - p.getX()) <= .211D);
                        assertTrue(Math.abs(v.y() - p.getY()) <= .211D);
                        assertTrue(Math.abs(v.z() - p.getZ()) <= .211D);
                        assertEquals(255, v.alpha(), "No translucent square curtain or full-anchor glow");
                    }
                }
            }
        }
    }

    @Test
    void noMarkersAreInventedForUnbuiltRings() {
        assertTrue(render(0, 2, Vec3.ZERO, 2F, false).vertices().isEmpty());
        assertTrue(render(0, 2, Vec3.ZERO, 2F, true).vertices().isEmpty());
        assertEquals((24 + 32 + 40) * FULL_VERTICES, render(3, 2, Vec3.ZERO, 2F, false).vertices().size());
    }

    @Test
    void fixedFramesStayInPlaceWhileNucleiScansAndSegmentColoursAnimateInTheDepthPass() {
        var first = render(9, 2, Vec3.ZERO, .1F, false).vertices();
        var later = render(9, 2, Vec3.ZERO, .9F, false).vertices();
        int changedFrames = 0;
        for (int marker = 0; marker < 504; marker++) {
            int start = marker * FULL_VERTICES;
            for (int i = 24; i < 224; i++) {
                var a = first.get(start + i);
                var b = later.get(start + i);
                assertEquals(a.x(), b.x());
                assertEquals(a.y(), b.y());
                assertEquals(a.z(), b.z());
                if (a.rgb() != b.rgb()) changedFrames++;
            }
            assertNotEquals(first.subList(start + 224, start + 240), later.subList(start + 224, start + 240),
                    "Opaque scan bars must move, not depend on additive glow");
            assertNotEquals(first.subList(start, start + 24), later.subList(start, start + 24),
                    "The centred coordinate nucleus pulses in size/brightness");
            for (int axis = 0; axis < 3; axis++) {
                double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
                for (int i = 0; i < 24; i++) {
                    var v = later.get(start + i);
                    double value = axis == 0 ? v.x() : axis == 1 ? v.y() : v.z();
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
                assertEquals(Math.rint((min + max) / 2D), (min + max) / 2D, 1E-5D,
                        "Nucleus remains centred on an integer layout slot");
            }
        }
        assertTrue(changedFrames > 504 * 24, "Travelling segment lights are visibly different over time");
        assertEquals(first, render(9, 1, Vec3.ZERO, .1F, false).vertices(),
                "Readable animation also works without the optional glow pass");
    }

    @Test
    void disablingEffectsRetainsColouredFramesButStopsEveryPartOfTheAnimation() {
        var first = render(9, 0, Vec3.ZERO, 1F, false).vertices();
        assertEquals(504 * 224, first.size());
        assertEquals(first, render(9, 0, Vec3.ZERO, 50F, false).vertices());
        assertTrue(render(9, 0, Vec3.ZERO, 2F, true).vertices().isEmpty());
    }

    @Test
    void eachAnchorTierUsesTheSameDistinctIdentityAsItsDataRail() {
        var vertices = render(9, 2, Vec3.ZERO, .5F, false).vertices();
        int cursor = 0;
        for (int ring = 0; ring < 9; ring++) {
            int color = StarOrreryAnchorEffects.tierColor(ring);
            assertEquals(StarOrreryEffects.tierColor(ring), color);
            for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                assertEquals(color, vertices.get(cursor + 216).rgb(), "Tier-coloured opaque locator");
                cursor += FULL_VERTICES;
            }
        }
        assertNotEquals(StarOrreryAnchorEffects.tierColor(0), StarOrreryAnchorEffects.tierColor(3));
        assertNotEquals(StarOrreryAnchorEffects.tierColor(3), StarOrreryAnchorEffects.tierColor(6));
        assertNotEquals(StarOrreryAnchorEffects.tierColor(0), StarOrreryAnchorEffects.tierColor(6));
    }

    @Test
    void perAnchorDistanceSimplifiesDenseArraysWithoutRemovingNearFrames() {
        assertEquals(2, StarOrreryAnchorEffects.detailLevel(47.99D * 47.99D));
        assertEquals(1, StarOrreryAnchorEffects.detailLevel(48D * 48D));
        assertEquals(0, StarOrreryAnchorEffects.detailLevel(112D * 112D));
        assertEquals(0, StarOrreryAnchorEffects.detailLevel(224D * 224D));
        int near = render(9, 2, Vec3.ZERO, 2F, false).vertices().size();
        int middle = render(9, 2, new Vec3(0, 0, 82), 2F, false).vertices().size();
        int far = render(9, 2, new Vec3(0, 0, 160), 2F, false).vertices().size();
        assertTrue(near > middle && middle > far);
        assertTrue(middle <= 504 * 48 && middle > 504 * 24,
                "Some nodes on the far side cross the per-anchor 112-block boundary");
        assertEquals(504 * 24, far);
        assertEquals(504 * 24, render(9, 2, new Vec3(0, 0, 300), 2F, false).vertices().size());
        var p = StarOrreryAnchorEffects.marker(8, 0);
        var camera = new Vec3(p.getX(), p.getY(), p.getZ() + 2);
        assertTrue(render(9, 2, camera, 2F, false).vertices().size() > 504 * 48,
                "LOD uses distance to each node, not only the controller");
    }

    @Test
    void scansRunContinuouslyOnStaggeredCyclesAndGlowRemainsSmallAndCloseOnly() {
        for (int ring = 0; ring < 9; ring++) {
            int count = StarOrreryLayout.slotCount(ring);
            for (int sample = 0; sample < 32; sample++) {
                float time = sample / 10F;
                int phaseBins = 0;
                for (int slot = 0; slot < count; slot++) {
                    float phase = StarOrreryAnchorEffects.scanProgress(ring, slot, time);
                    float repeat = StarOrreryAnchorEffects.scanProgress(ring, slot,
                            time + StarOrreryAnchorEffects.CYCLE_SECONDS);
                    assertEquals(phase, repeat, 1E-5F);
                    assertTrue(phase >= 0F && phase < 1F, "No 7.6-second invisible waiting period");
                    phaseBins |= 1 << Math.min(7, (int) (phase * 8F));
                }
                assertEquals(255, phaseBins, "Each ring's phases spread across the whole cycle");
            }
        }
        assertTrue(render(9, 1, Vec3.ZERO, 2F, true).vertices().isEmpty());
        assertTrue(render(9, 2, new Vec3(0, 0, 82), 2F, true).vertices().isEmpty());
        var glow = render(9, 2, Vec3.ZERO, 2F, true).vertices();
        assertEquals(504 * 16, glow.size(), "Only two narrow crossing bars, no filled scanning planes");
        assertTrue(glow.stream().allMatch(v -> v.alpha() == 28));
    }

    @Test
    void completeL9BatchHasABoundedVertexBudgetAndRestoresThePose() {
        int depth = render(9, 2, Vec3.ZERO, 2F, false).vertices().size();
        int glow = render(9, 2, Vec3.ZERO, 2F, true).vertices().size();
        assertTrue(depth + glow < 132_000, "Explicit two-pass ceiling for the complete near L9 array");
        var poses = new PoseStack();
        poses.translate(2, 3, 4);
        var before = new org.joml.Matrix4f(poses.last().pose());
        StarOrreryAnchorEffects.render(poses, new RecordingColorConsumer(), 9, 2, Vec3.ZERO, 2F, false);
        assertEquals(before, poses.last().pose());
        StarOrreryAnchorEffects.render(poses, null, 9, 2, Vec3.ZERO, 2F, false);
    }

    @Test
    void rendererDistanceIncludesEveryAnchorUntilTheTerrainFogEnclosesIt() {
        double maxRadius = 0;
        for (int ring = 0; ring < 9; ring++) {
            for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                var p = StarOrreryAnchorEffects.marker(ring, slot);
                maxRadius = Math.max(maxRadius, Math.sqrt(p.getX() * p.getX()
                        + p.getY() * p.getY() + p.getZ() * p.getZ()));
            }
        }
        for (int chunks : new int[] {2, 4, 8, 12, 16, 24, 32}) {
            double terrainDistance = chunks * 16D;
            assertTrue(StarOrreryRenderer.viewDistance(terrainDistance)
                    >= Math.sqrt(2D) * terrainDistance + maxRadius);
        }
    }

    @Test
    void rendererNoLongerDrawsOrRegistersTheFixedGemModels() throws Exception {
        String source = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/render/StarOrreryRenderer.java"));
        String events = Files.readString(Path.of("src/main/java/com/atir/molecularmanipulator/client/ClientEvents.java"));
        assertFalse(source.contains("\"gems_\""));
        assertFalse(events.contains("\"gems_\""));
        assertTrue(source.contains("StarOrreryAnchorEffects.render(poses, crystal"));
        assertTrue(source.contains("StarOrreryAnchorEffects.render(poses, glow"));
    }
}
