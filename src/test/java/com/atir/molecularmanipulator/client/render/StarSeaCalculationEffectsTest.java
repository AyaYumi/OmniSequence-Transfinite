package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarSeaCalculationEffectsTest {
    @Test
    void stoppedMachinesAndDisabledEffectsSubmitNoCharactersInEitherPass() {
        for (boolean glow : new boolean[] {false, true}) {
            for (int quality : new int[] {0, 1, 2}) {
                assertTrue(draw(false, quality, 0, 3, glow).vertices().isEmpty());
            }
            assertTrue(draw(true, 0, 0, 3, glow).vertices().isEmpty());
            assertFalse(draw(true, 1, 0, 3, glow).vertices().isEmpty());
        }
    }

    @Test
    void shortTealCharacterTrailsCoverAllFourFacadesOutsideThePhysicalBuilding() {
        var palette = Set.of(0xD7F8ED, 0x8AD9C8, 0x398D79);
        for (double time : new double[] {0, .125, 3, 7.99, 19}) {
            for (boolean glow : new boolean[] {false, true}) {
                var vertices = draw(true, 2, 0, time, glow).vertices();
                assertFalse(vertices.isEmpty());
                assertEquals(0, vertices.size() % 4);
                for (var v : vertices) {
                    double side = Math.max(Math.abs(v.x()), Math.abs(v.z()));
                    assertEquals(2.72, side, 1E-5);
                    assertTrue(v.y() >= .5 && v.y() <= 8.8);
                    assertTrue(palette.contains(v.rgb()));
                    assertTrue(v.alpha() > 0 && v.alpha() <= (glow ? 24 : 220));
                }
                assertTrue(vertices.stream().anyMatch(v -> v.x() > 2.7));
                assertTrue(vertices.stream().anyMatch(v -> v.x() < -2.7));
                assertTrue(vertices.stream().anyMatch(v -> v.z() > 2.7));
                assertTrue(vertices.stream().anyMatch(v -> v.z() < -2.7));
                assertTrue(vertices.stream().anyMatch(v -> v.rgb() == 0xD7F8ED));
                if (!glow) assertTrue(vertices.stream().anyMatch(v -> v.rgb() == 0x398D79));
            }
        }
    }

    @Test
    void glyphsFallWithFractionalTicksAndKeepDimmerTailsAboveTheirBrightHeads() {
        for (boolean glow : new boolean[] {false, true}) {
            var first = draw(true, 2, 0, 3.1, glow).vertices();
            var later = draw(true, 2, 0, 3.15, glow).vertices();
            assertEquals(first.getFirst().x(), later.getFirst().x(), 1E-5);
            assertEquals(first.getFirst().z(), later.getFirst().z(), 1E-5);
            assertEquals(.05 * 8.3 / 8, first.getFirst().y() - later.getFirst().y(), 1E-5);
        }
        var column = draw(true, 2, 0, 3.1, false).vertices().stream()
                .filter(v -> Math.abs(v.z() + 2.72) < 1E-5 && v.x() < -1.5).toList();
        double headTop = column.stream().filter(v -> v.rgb() == 0xD7F8ED)
                .mapToDouble(RecordingColorConsumer.Vertex::y).max().orElseThrow();
        assertTrue(column.stream().filter(v -> v.rgb() != 0xD7F8ED).allMatch(v -> v.y() > headTop));
    }

    @Test
    void fourFacingTransformsKeepEveryVertexInsideTheExpandedRendererBoundsWithoutScalingTheRain() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var poses = new PoseStack();
            StarSeaCalculationModel.transformBuilding(poses, facing);
            var before = new Matrix4f(poses.last().pose());
            var expected = draw(true, 2, 0, 3, false).vertices();
            var actual = new RecordingColorConsumer();
            StarSeaCalculationEffects.render(poses, actual, true, 2, 0, 3, false);
            assertEquals(before, poses.last().pose());
            assertEquals(expected.size(), actual.vertices().size());
            var rendererBounds = StarSeaCalculationMainframeBlock.bounds(BlockPos.ZERO, facing)
                    .inflate(StarSeaCalculationEffects.BOUNDS_MARGIN, 0, StarSeaCalculationEffects.BOUNDS_MARGIN);
            for (int index = 0; index < expected.size(); index++) {
                var v = expected.get(index);
                var transformed = before.transformPosition(new Vector3f(v.x(), v.y(), v.z()));
                var a = actual.vertices().get(index);
                assertEquals(transformed.x, a.x(), 1E-5);
                assertEquals(transformed.y, a.y(), 1E-5);
                assertEquals(transformed.z, a.z(), 1E-5);
                assertTrue(rendererBounds.contains(a.x(), a.y(), a.z()));
            }
        }
    }

    @Test
    void reducedDetailCutsGeometryAndTheBoundedTimeClockWrapsWithoutAVisibleJump() {
        for (double time : new double[] {0, 1, 3.125, 7.9, 11999.9}) {
            int full = 0, reduced = 0;
            for (boolean glow : new boolean[] {false, true}) {
                var detailed = draw(true, 2, 0, time, glow).vertices();
                var low = draw(true, 1, 0, time, glow).vertices();
                full += detailed.size();
                reduced += low.size();
                assertEquals(low, draw(true, 2, 113D * 113D, time, glow).vertices());
                var wrapped = draw(true, 2, 0, time + 12000, glow).vertices();
                assertEquals(detailed.size(), wrapped.size());
                for (int index = 0; index < detailed.size(); index++) {
                    var a = detailed.get(index);
                    var b = wrapped.get(index);
                    assertEquals(a.x(), b.x(), 1E-5);
                    assertEquals(a.y(), b.y(), 1E-5);
                    assertEquals(a.z(), b.z(), 1E-5);
                    assertEquals(a.argb(), b.argb());
                }
            }
            assertTrue(full < 4000, "One complete building's code rain stays below 4000 vertices");
            assertTrue(reduced * 2 < full);
        }
    }

    private static RecordingColorConsumer draw(boolean running, int quality, double distance, double time, boolean glow) {
        var out = new RecordingColorConsumer();
        StarSeaCalculationEffects.render(new PoseStack(), out, running, quality, distance, time, glow);
        return out;
    }
}
