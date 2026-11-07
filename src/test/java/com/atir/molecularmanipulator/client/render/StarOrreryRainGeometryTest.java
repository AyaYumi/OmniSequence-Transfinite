package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

class StarOrreryRainGeometryTest {
    @Test void immutableColumnsRetainTheFullFixedEmitterLayoutAndStorage() {
        assertTrue(StarOrreryRainGeometry.columns(0, true).isEmpty());
        for (int lit = 1; lit <= 9; lit++) for (boolean detailed : new boolean[]{false, true}) {
            var columns = StarOrreryRainGeometry.columns(lit, detailed);
            int core = detailed ? 12 : 6;
            assertEquals(core + lit * (detailed ? 4 : 2) + (detailed ? 24 : 12), columns.size());
            assertTrue(columns.size() <= 72, "The GPU per-pass row cache must cover the complete layout");
            assertThrows(UnsupportedOperationException.class, () -> columns.add(columns.getFirst()));
            var data = ByteBuffer.allocate(columns.size() * StarOrreryRainGeometry.COLUMN_BYTES).order(ByteOrder.nativeOrder());
            for (int index = 0; index < columns.size(); index++) {
                var column = columns.get(index);
                assertTrue(column.top() > column.bottom());
                double length = Math.hypot(column.x(), column.z());
                // Vec3.normalize preserves a zero tangent when a rest-plane column lies on the axis.
                assertEquals(column.building() != 0 && length < 1E-4 ? 0D : 1D,
                        Math.hypot(column.tangentX(), column.tangentZ()), 1E-12);
                assertEquals(index < core ? 0 : 1, column.building());
                assertEquals(index < core ? index : index - core, column.sequence());
                int from = data.position();
                column.write(data);
                assertEquals(StarOrreryRainGeometry.COLUMN_BYTES, data.position() - from);
                assertEquals(column.x(), data.getDouble(from));
                assertEquals(column.z(), data.getDouble(from + 8));
                assertEquals(column.tangentX(), data.getDouble(from + 16));
                assertEquals(column.tangentZ(), data.getDouble(from + 24));
                assertEquals(column.top(), data.getDouble(from + 32));
                assertEquals(column.bottom(), data.getDouble(from + 40));
                assertEquals(column.building(), data.getInt(from + 48));
                assertEquals(column.column(), data.getInt(from + 52));
                assertEquals(column.sequence(), data.getInt(from + 56));
                assertEquals(column.rgb(), data.getInt(from + 60));
            }
            assertEquals(data.capacity(), data.position());
        }
    }

    @Test void everyResidentStrokePreservesTheOriginalMergedGlyphPixelsAndDoubleOffsets() {
        var data = ByteBuffer.allocate(StarOrreryRainGeometry.TEMPLATE_BYTES).order(ByteOrder.nativeOrder());
        StarOrreryRainGeometry.writeTemplates(data);
        assertEquals(data.capacity(), data.position());
        double[] pixels = {.105D, .13D, .09D, .115D};
        for (int mode = 0; mode < pixels.length; mode++) for (int glyph = 0; glyph < 20; glyph++) {
            int first = (mode * 20 + glyph) * StarOrreryRainGeometry.GLYPH_SLOTS * 32;
            int count = (int) data.getDouble(first), mask = StarOrreryCoreEffects.glyphMask(glyph);
            double pixel = pixels[mode];
            int cursor = 0;
            for (int row = 0; row < 5; row++) {
                int bits = mask >> ((4 - row) * 3) & 7;
                for (int col = 0; col < 3; col++) {
                    if ((bits & 1 << (2 - col)) == 0) continue;
                    int end = col + 1;
                    while (end < 3 && (bits & 1 << (2 - end)) != 0) end++;
                    int at = first + (++cursor) * 32;
                    assertEquals((col - 1.5D) * pixel, data.getDouble(at));
                    assertEquals((end - 1.5D) * pixel - .018D, data.getDouble(at + 8));
                    assertEquals(-row * pixel, data.getDouble(at + 16));
                    assertEquals(-pixel + .018D, data.getDouble(at + 24));
                    col = end - 1;
                }
            }
            assertEquals(cursor, count);
            assertTrue(count > 0 && count <= StarOrreryRainGeometry.MAX_STROKES);
        }
    }

    @Test void residentCapacityBoundsBothCompleteOriginalPassesAtAllTiersAndLods() {
        for (int lit : new int[]{1, 3, 6, 9}) for (boolean detailed : new boolean[]{false, true}) {
            for (float time : new float[]{0, .1875F, 3.5F, 12, 11999}) for (boolean glow : new boolean[]{false, true}) {
                var out = new RecordingColorConsumer();
                int quality = detailed ? 2 : 1;
                StarOrreryCoreEffects.renderRain(new PoseStack(), out, lit, quality, 0, time, glow);
                StarOrreryCoreEffects.renderBuildingRain(new PoseStack(), out, lit, quality, 0, time, 0, glow);
                assertEquals(0, out.vertices().size() % 4);
                assertTrue(out.vertices().size() / 4 <= StarOrreryRainGeometry.maxQuads(lit, detailed, glow));
            }
        }
        assertEquals(0, StarOrreryRainGeometry.maxQuads(0, true, false));
    }

    @Test void singleSampleRowCacheRetainsEveryVertexColorAndOriginalOrderAtBoundaries() {
        var templates = ByteBuffer.allocate(StarOrreryRainGeometry.TEMPLATE_BYTES).order(ByteOrder.nativeOrder());
        StarOrreryRainGeometry.writeTemplates(templates);
        var poses = new PoseStack();
        poses.translate(3.25, -17.5, 41.5);
        poses.mulPose(Axis.YP.rotationDegrees(37));
        poses.mulPose(Axis.XP.rotationDegrees(-24));
        poses.scale(-.75F, 1.25F, .4F);
        float[] times = {-12F, -.5F, 0F, Math.nextUp(0F), Math.nextDown(.5F), .5F, Math.nextUp(.5F),
                Math.nextDown(12F), 12F, Math.nextUp(12F), 11999.5F, 12000F};
        for (int lit = 1; lit <= 9; lit++) for (boolean detailed : new boolean[]{false, true}) {
            var columns = StarOrreryRainGeometry.columns(lit, detailed);
            for (boolean glow : new boolean[]{false, true}) for (float time : times) {
                var expected = new RecordingColorConsumer();
                int quality = detailed ? 2 : 1;
                StarOrreryCoreEffects.renderRain(poses, expected, lit, quality, 0, time, glow);
                StarOrreryCoreEffects.renderBuildingRain(poses, expected, lit, quality, 0, time, 0, glow);
                var cached = cachedPass(columns, templates, poses.last().pose(), detailed, glow, time);
                int core = detailed ? 12 : 6;
                assertEquals(core * (glow ? 1 : detailed ? 12 : 7)
                        + (columns.size() - core) * (glow ? 1 : detailed ? 6 : 3), cached.samples);
                assertEquals(expected.vertices().size(), cached.vertices.size());
                for (int index = 0; index < cached.vertices.size(); index++) {
                    var a = expected.vertices().get(index);
                    var b = cached.vertices.get(index);
                    assertEquals(a.argb(), b.argb(), "Ordered vertex color " + index);
                    assertEquals(a.x(), b.x(), 2E-5);
                    assertEquals(a.y(), b.y(), 2E-5);
                    assertEquals(a.z(), b.z(), 2E-5);
                }
            }
        }
    }

    @Test void cachedShaderKeepsTheOriginalSamplingStrokeAndPoseMath() throws IOException {
        String cached = shader("star_orrery_rain.comp"), reference = shader("star_orrery_rain_reference.comp");
        assertEquals(reference.substring(reference.indexOf("uint templateStart("), reference.indexOf("void main()")),
                cached.substring(cached.indexOf("uint templateStart("), cached.indexOf("void main()")));
        assertEquals(1L, cached.substring(cached.indexOf("void main()")).lines()
                .filter(line -> line.contains("sampleRow(")).count());
    }

    private static String shader(String name) throws IOException {
        try (var input = StarOrreryRainGeometryTest.class.getResourceAsStream(
                "/assets/molecularmanipulator/shaders/core/" + name)) {
            assertNotNull(input, name);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private record CachedRow(double y, int start, int runs, int argb) {}
    private record CachedPass(List<RecordingColorConsumer.Vertex> vertices, int samples) {}

    /** CPU reference of count -> cached rows -> stable column prefix -> emit, not a second rain renderer. */
    private static CachedPass cachedPass(List<StarOrreryRainGeometry.Column> columns, ByteBuffer templates,
            Matrix4f pose, boolean detailed, boolean glow, float time) {
        var cache = new CachedRow[columns.size()][];
        int[] counts = new int[columns.size()], offsets = new int[columns.size()];
        int samples = 0, total = 0;
        for (int lane = 0; lane < columns.size(); lane++) {
            var column = columns.get(lane);
            int rows = glow ? 1 : column.building() == 0 ? (detailed ? 12 : 7) : (detailed ? 6 : 3);
            cache[lane] = new CachedRow[rows];
            for (int row = 0; row < rows; row++) {
                var sample = cache[lane][row] = sampleRow(column, templates, row, glow, time);
                samples++;
                if (sample != null) counts[lane] += sample.runs;
            }
            offsets[lane] = total;
            total += counts[lane];
        }
        var vertices = new RecordingColorConsumer.Vertex[total * 4];
        for (int lane = 0; lane < columns.size(); lane++) {
            var column = columns.get(lane);
            int quad = offsets[lane];
            for (var row : cache[lane]) {
                if (row == null) continue;
                for (int run = 0; run < row.runs; run++) {
                    int at = row.start + (1 + run) * 32;
                    double ax = column.x() + column.tangentX() * templates.getDouble(at);
                    double az = column.z() + column.tangentZ() * templates.getDouble(at);
                    double bx = column.x() + column.tangentX() * templates.getDouble(at + 8);
                    double bz = column.z() + column.tangentZ() * templates.getDouble(at + 8);
                    double upper = row.y + templates.getDouble(at + 16);
                    double lower = upper + templates.getDouble(at + 24);
                    int first = quad++ * 4;
                    vertices[first] = vertex(pose, ax, upper, az, row.argb);
                    vertices[first + 1] = vertex(pose, bx, upper, bz, row.argb);
                    vertices[first + 2] = vertex(pose, bx, lower, bz, row.argb);
                    vertices[first + 3] = vertex(pose, ax, lower, az, row.argb);
                }
            }
            assertEquals(offsets[lane] + counts[lane], quad);
        }
        return new CachedPass(Arrays.asList(vertices), samples);
    }

    /** Mirrors sampleRow's double operation order, alpha truncation and signed glyph modulo. */
    private static CachedRow sampleRow(StarOrreryRainGeometry.Column column, ByteBuffer templates,
            int row, boolean glow, float time) {
        double top = column.top(), bottom = column.bottom(), span = top - bottom, y;
        int rgb = column.rgb(), key;
        if (column.building() == 0) {
            double travel = (double) time * (span / 16D);
            travel = travel + column.column() * 5.713D;
            travel = travel - row * .94D;
            double phase = travel / span;
            double fallen = (phase - Math.floor(phase)) * span;
            y = top - fallen;
            if (row == 0) rgb = 0xDBF6FF;
            key = column.column() * 7 + row * 11;
        } else {
            double phase = (double) time / 12D;
            phase = phase + column.sequence() * .1618D;
            double head = top - (phase - Math.floor(phase)) * span;
            y = head - row * .82D;
            if (y <= bottom + .12D || y >= top) return null;
            key = column.sequence() * 11 + column.column() * 7 + row * 13;
        }
        double edge = Math.min(1D, Math.min(top - y, y - bottom) / 1.2D);
        int level = column.building() == 0 ? (glow ? 32 : 240 - row * 12) : (glow ? 20 : 196 - row * 17);
        int alpha = (int) (level * Math.max(0D, edge));
        if (alpha == 0) return null;
        int glyph = Math.floorMod(key + (int) Math.floor((double) time * 2D), 20);
        int mode = column.building() * 2 + (glow ? 1 : 0);
        int start = (mode * 20 + glyph) * StarOrreryRainGeometry.GLYPH_SLOTS * 32;
        return new CachedRow(y, start, (int) templates.getDouble(start), alpha << 24 | rgb);
    }

    private static RecordingColorConsumer.Vertex vertex(Matrix4f pose, double x, double y, double z, int argb) {
        var p = pose.transformPosition((float) x, (float) y, (float) z, new Vector3f());
        return new RecordingColorConsumer.Vertex(p.x, p.y, p.z, argb);
    }
}
