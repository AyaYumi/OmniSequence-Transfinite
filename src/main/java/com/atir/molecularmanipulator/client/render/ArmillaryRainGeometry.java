package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Immutable layout columns and merged glyph strokes, shared by every frame and both rain passes. */
final class ArmillaryRainGeometry {
    static final int COLUMN_BYTES = 64;
    static final int GLYPHS = 20;
    static final int MAX_STROKES = 10;
    static final int GLYPH_SLOTS = MAX_STROKES + 1;
    static final int TEMPLATE_BYTES = 4 * GLYPHS * GLYPH_SLOTS * 32;
    private static final double TAU = Math.PI * 2D;

    private ArmillaryRainGeometry() {}

    record Column(double x, double z, double tangentX, double tangentZ, double top, double bottom,
            int building, int column, int sequence, int rgb) {
        void write(ByteBuffer target) {
            target.putDouble(x).putDouble(z).putDouble(tangentX).putDouble(tangentZ)
                    .putDouble(top).putDouble(bottom).putInt(building).putInt(column).putInt(sequence).putInt(rgb);
        }
    }

    static List<Column> columns(int lit, boolean detailed) {
        if (lit <= 0) return List.of();
        int columns = detailed ? 12 : 6;
        var result = new ArrayList<Column>();
        for (int column = 0; column < columns; column++) {
            double angle = TAU * column / columns;
            double radius = 3.25D + .24D * Math.sin(column * 2.3D);
            result.add(new Column(Math.cos(angle) * radius, Math.sin(angle) * radius,
                    -Math.sin(angle), Math.cos(angle), EntropyArmillaryCoreEffects.RAIN_TOP,
                    EntropyArmillaryCoreEffects.RAIN_BOTTOM, 0, column, column,
                    column % 3 == 0 ? 0xBF91FF : 0x71BFFF));
        }
        int ringColumns = detailed ? 4 : 2, sequence = 0;
        for (int ring = 0; ring < Math.min(lit, EntropyArmillaryLayout.RINGS); ring++) {
            var plane = EntropyArmillaryMotion.restRotation(new Quaternionf(),
                    EntropyArmillaryLayout.yaw(ring), EntropyArmillaryLayout.pitch(ring));
            double radius = EntropyArmillaryLayout.radius(ring) + .72D;
            for (int column = 0; column < ringColumns; column++) {
                double angle = TAU * (column + (ring % 2) * .25D) / ringColumns;
                var point = plane.transform(new Vector3f((float) (Math.cos(angle) * radius), 0F,
                        (float) (Math.sin(angle) * radius)));
                double radial = Math.hypot(point.x, point.z);
                double floor = radial <= 6.7D ? -34D : radial <= 10D ? -36.75D : -38.25D;
                double top = Math.max(floor + 5D, point.y + 3.6D);
                double bottom = Math.max(floor, top - 12D - ring * .3D);
                result.add(building(point.x, point.z, top, bottom, sequence++, column,
                        EntropyArmillaryEffects.tierColor(ring)));
            }
        }
        double[] radii = {9D, 13.1D, 16.55D};
        double[] heights = {-36.75D, EntropyArmillaryPedestalMotion.DIAL_Y + .12D, -36.1D};
        int pedestalColumns = detailed ? 8 : 4;
        for (int ring = 0; ring < radii.length; ring++) {
            int color = EntropyArmillaryEffects.tierColor(Math.min(ring * 3, lit - 1));
            for (int column = 0; column < pedestalColumns; column++) {
                double angle = TAU * column / pedestalColumns;
                double x = Math.cos(angle) * radii[ring], z = Math.sin(angle) * radii[ring];
                double floor = heights[ring];
                if (ring == 1) {
                    floor -= z * Math.sin(EntropyArmillaryPedestalMotion.DIAL_TILT);
                    z *= Math.cos(EntropyArmillaryPedestalMotion.DIAL_TILT);
                }
                result.add(building(x, z, floor + 10D, floor, sequence++, column, color));
            }
        }
        return List.copyOf(result);
    }

    private static Column building(double x, double z, double top, double bottom,
            int sequence, int column, int color) {
        var tangent = new Vec3(-z, 0D, x).normalize();
        return new Column(x, z, tangent.x, tangent.z, top, bottom, 1, column, sequence, color);
    }

    static int maxQuads(int lit, boolean detailed, boolean glow) {
        if (lit <= 0) return 0;
        int core = detailed ? 12 : 6;
        int building = Math.min(lit, EntropyArmillaryLayout.RINGS) * (detailed ? 4 : 2) + (detailed ? 24 : 12);
        return (core * (glow ? 1 : detailed ? 12 : 7)
                + building * (glow ? 1 : detailed ? 6 : 3)) * MAX_STROKES;
    }

    /** Four pixel sizes retain the original double arithmetic before each local vertex becomes float. */
    static void writeTemplates(ByteBuffer target) {
        for (double pixel : new double[]{.105D, .13D, .09D, .115D}) {
            for (int glyph = 0; glyph < GLYPHS; glyph++) {
                int start = target.position(), count = 0;
                target.position(start + 32);
                int mask = EntropyArmillaryCoreEffects.glyphMask(glyph);
                for (int row = 0; row < 5; row++) {
                    int bits = mask >> ((4 - row) * 3) & 7;
                    for (int column = 0; column < 3; column++) {
                        if ((bits & 1 << (2 - column)) == 0) continue;
                        int end = column + 1;
                        while (end < 3 && (bits & 1 << (2 - end)) != 0) end++;
                        target.putDouble((column - 1.5D) * pixel).putDouble((end - 1.5D) * pixel - .018D)
                                .putDouble(-row * pixel).putDouble(-pixel + .018D);
                        count++;
                        column = end - 1;
                    }
                }
                target.putDouble(start, count);
                target.position(start + GLYPH_SLOTS * 32);
            }
        }
    }
}
