package com.atir.molecularmanipulator.client.render;

import static org.junit.jupiter.api.Assertions.*;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.junit.jupiter.api.Test;

class ArmillaryRainGeometryTest {
    @Test void immutableColumnsRetainTheFullFixedEmitterLayoutAndStorage() {
        assertTrue(ArmillaryRainGeometry.columns(0, true).isEmpty());
        for (int lit = 1; lit <= 9; lit++) for (boolean detailed : new boolean[]{false, true}) {
            var columns = ArmillaryRainGeometry.columns(lit, detailed);
            int core = detailed ? 12 : 6;
            assertEquals(core + lit * (detailed ? 4 : 2) + (detailed ? 24 : 12), columns.size());
            assertThrows(UnsupportedOperationException.class, () -> columns.add(columns.getFirst()));
            var data = ByteBuffer.allocate(columns.size() * ArmillaryRainGeometry.COLUMN_BYTES).order(ByteOrder.nativeOrder());
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
                assertEquals(ArmillaryRainGeometry.COLUMN_BYTES, data.position() - from);
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
        var data = ByteBuffer.allocate(ArmillaryRainGeometry.TEMPLATE_BYTES).order(ByteOrder.nativeOrder());
        ArmillaryRainGeometry.writeTemplates(data);
        assertEquals(data.capacity(), data.position());
        double[] pixels = {.105D, .13D, .09D, .115D};
        for (int mode = 0; mode < pixels.length; mode++) for (int glyph = 0; glyph < 20; glyph++) {
            int first = (mode * 20 + glyph) * ArmillaryRainGeometry.GLYPH_SLOTS * 32;
            int count = (int) data.getDouble(first), mask = EntropyArmillaryCoreEffects.glyphMask(glyph);
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
            assertTrue(count > 0 && count <= ArmillaryRainGeometry.MAX_STROKES);
        }
    }

    @Test void residentCapacityBoundsBothCompleteOriginalPassesAtAllTiersAndLods() {
        for (int lit : new int[]{1, 3, 6, 9}) for (boolean detailed : new boolean[]{false, true}) {
            for (float time : new float[]{0, .1875F, 3.5F, 12, 11999}) for (boolean glow : new boolean[]{false, true}) {
                var out = new RecordingColorConsumer();
                int quality = detailed ? 2 : 1;
                EntropyArmillaryCoreEffects.renderRain(new PoseStack(), out, lit, quality, 0, time, glow);
                EntropyArmillaryCoreEffects.renderBuildingRain(new PoseStack(), out, lit, quality, 0, time, 0, glow);
                assertEquals(0, out.vertices().size() % 4);
                assertTrue(out.vertices().size() / 4 <= ArmillaryRainGeometry.maxQuads(lit, detailed, glow));
            }
        }
        assertEquals(0, ArmillaryRainGeometry.maxQuads(0, true, false));
    }
}
