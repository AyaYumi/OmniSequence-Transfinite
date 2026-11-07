package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Shared activity clock, local phase glitches and core/building code rain; the galaxy is a shader volume. */
public final class StarOrreryCoreEffects {
    public static final double RAIN_TOP = 5.8D;
    public static final double RAIN_BOTTOM = -31.5D;
    public static final float CYCLE_SECONDS = 8F;
    private static final double TAU = Math.PI * 2D;
    private static final int ICE = 0xDBF6FF;
    private static final int BLUE = 0x71BFFF;
    private static final int VIOLET = 0xBF91FF;
    // 3x5 hexadecimal characters followed by four geometric runes. Row-major masks.
    private static final int[] GLYPHS = {
        0b111_101_101_101_111, 0b010_110_010_010_111, 0b111_001_111_100_111,
        0b111_001_111_001_111, 0b101_101_111_001_001, 0b111_100_111_001_111,
        0b111_100_111_101_111, 0b111_001_010_010_010, 0b111_101_111_101_111,
        0b111_101_111_001_111, 0b010_101_111_101_101, 0b110_101_110_101_110,
        0b111_100_100_100_111, 0b110_101_101_101_110, 0b111_100_110_100_111,
        0b111_100_110_100_100, 0b010_111_010_101_010, 0b101_010_111_010_101,
        0b010_101_010_101_010, 0b100_010_111_010_001
    };

    private StarOrreryCoreEffects() {}

    /** State, not installed ring tier: idle / selected online ME engine / paid overclock. */
    public static float accretionSpeed(int activity) {
        return switch (Math.clamp(activity, 0, 2)) {
            case 1 -> 1F;
            case 2 -> 2F;
            default -> .35F;
        };
    }

    /** Integrate elapsed client time so switching speed never jumps the shader flow or falling code. */
    public static final class AccretionClock {
        private double last = Double.NaN;
        private double elapsed;
        private int activity;
        private boolean running;

        public float sample(double seconds, int nextActivity, boolean enabled) {
            if (!Double.isNaN(last) && running) {
                elapsed = (elapsed + Math.max(0D, seconds-last) * accretionSpeed(activity)) % 12000D;
            }
            last = seconds;
            activity = Math.clamp(nextActivity, 0, 2);
            running = enabled;
            return (float) elapsed;
        }
    }

    public static float cyclePhase(float time) { return (float) fract(time / (double) CYCLE_SECONDS); }

    public static float glitch(int part, float time) {
        double phase = fract(time / 4D + part * .271D);
        if (phase < .78D || phase >= .87D) return 0F;
        return (float) Math.sin((phase-.78D)/.09D*Math.PI);
    }

    public static double rainY(int column, int row, float flowTime) {
        double travel = flowTime * ((RAIN_TOP-RAIN_BOTTOM)/16D) + column * 5.713D - row * .94D;
        return RAIN_TOP - fract(travel / (RAIN_TOP-RAIN_BOTTOM)) * (RAIN_TOP-RAIN_BOTTOM);
    }

    public static int glyphMask(int glyph) { return GLYPHS[Math.floorMod(glyph, GLYPHS.length)]; }

    /** Code falls on a fixed axial cylinder, not on tumbling belts or a newly added physical tower. */
    public static void renderRain(PoseStack poses, VertexConsumer out, int lit, int quality,
            double distanceSqr, float flowTime, boolean glow) {
        if (out == null || lit <= 0 || !StarOrreryEffects.effectsVisible(quality, distanceSqr)) return;
        boolean detailed = StarOrreryEffects.isDetailed(quality, distanceSqr);
        int columns = detailed ? 12 : 6;
        int rows = glow ? 1 : detailed ? 12 : 7;
        for (int column = 0; column < columns; column++) {
            double angle = TAU*column/columns;
            Vec3 tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
            double radius = 3.25D + .24D*Math.sin(column*2.3D);
            for (int row = 0; row < rows; row++) {
                double y = rainY(column, row, flowTime);
                double edge = Math.min(1D, Math.min(RAIN_TOP-y, y-RAIN_BOTTOM)/1.2D);
                int alpha = (int) ((glow ? 32 : 240-row*12)*Math.max(0, edge));
                if (alpha <= 0) continue;
                int mask = glyphMask(column*7+row*11+(int)Math.floor(flowTime*2D));
                Vec3 origin = new Vec3(Math.cos(angle)*radius, y, Math.sin(angle)*radius);
                int color = row == 0 ? ICE : column%3 == 0 ? VIOLET : BLUE;
                glyph(poses.last(), out, origin, tangent, mask, glow ? .13D : .105D, color, alpha);
            }
        }
    }

    /**
     * Code falls beside active belts and around the floating pedestal's three narrow rings.
     * The emitters are pinned to the initial layout pose: only the glyph sequence and
     * vertical head advance with {@code flowTime}; ring motion never moves their origins.
     */
    public static void renderBuildingRain(PoseStack poses, VertexConsumer out, int lit, int quality,
            double distanceSqr, float flowTime, float ringTime, boolean glow) {
        if (out == null || lit <= 0 || !StarOrreryEffects.effectsVisible(quality, distanceSqr)) return;
        boolean detailed = StarOrreryEffects.isDetailed(quality, distanceSqr);
        int ringColumns = detailed ? 4 : 2;
        int pedestalColumns = detailed ? 8 : 4;
        int rows = glow ? 1 : detailed ? 6 : 3;
        var pose = poses.last();
        int sequence = 0;

        for (int ring = 0; ring < Math.min(lit, StarOrreryLayout.RINGS); ring++) {
            // Keep the rain in the world/layout frame instead of attaching it to
            // the active belt's tumble, precession or local self-spin.
            Quaternionf plane = StarOrreryMotion.restRotation(new Quaternionf(),
                    StarOrreryLayout.yaw(ring), StarOrreryLayout.pitch(ring));
            double radius = StarOrreryLayout.radius(ring) + 0.72D;
            int color = StarOrreryEffects.tierColor(ring);
            for (int column = 0; column < ringColumns; column++) {
                double angle = TAU * (column + (ring % 2) * 0.25D) / ringColumns;
                Vector3f point = plane.transform(new Vector3f(
                        (float) (Math.cos(angle) * radius), 0F, (float) (Math.sin(angle) * radius)));
                double floor = floorHeight(Math.hypot(point.x, point.z));
                double top = Math.max(floor + 5D, point.y + 3.6D);
                double bottom = Math.max(floor, top - 12D - ring * 0.3D);
                rainColumn(pose, out, point.x, point.z, top, bottom, flowTime, sequence++,
                        column, rows, color, glow);
            }
        }

        double[] pedestalRadii = {9D, 13.1D, 16.55D};
        double[] pedestalHeights = {-36.75D, StarOrreryPedestalMotion.DIAL_Y + .12D, -36.1D};
        for (int ring = 0; ring < pedestalRadii.length; ring++) {
            // The pedestal dial may rotate independently, but its code columns
            // remain at fixed world/layout angles.  Their fall is still animated
            // by flowTime in rainColumn below.
            int color = StarOrreryEffects.tierColor(Math.min(ring * 3, lit - 1));
            for (int column = 0; column < pedestalColumns; column++) {
                double angle = TAU * column / pedestalColumns;
                double radius = pedestalRadii[ring];
                double x = Math.cos(angle) * radius, z = Math.sin(angle) * radius;
                double floor = pedestalHeights[ring];
                if (ring == 1) {
                    floor -= z * Math.sin(StarOrreryPedestalMotion.DIAL_TILT);
                    z *= Math.cos(StarOrreryPedestalMotion.DIAL_TILT);
                }
                rainColumn(pose, out, x, z, floor + 10D, floor,
                        flowTime, sequence++, column, rows, color, glow);
            }
        }
    }

    private static double floorHeight(double radius) {
        if (radius <= 6.7D) return -34D;
        if (radius <= 10D) return -36.75D;
        return -38.25D;
    }

    private static void rainColumn(PoseStack.Pose pose, VertexConsumer out, double x, double z,
            double top, double bottom, float flowTime, int sequence, int column, int rows, int color, boolean glow) {
        double span = top - bottom;
        double head = top - fract(flowTime / 12D + sequence * 0.1618D) * span;
        Vec3 tangent = new Vec3(-z, 0D, x).normalize();
        for (int row = 0; row < rows; row++) {
            double y = head - row * 0.82D;
            if (y <= bottom + 0.12D || y >= top) continue;
            double edge = Math.min(1D, Math.min(top - y, y - bottom) / 1.2D);
            int alpha = (int) ((glow ? 20 : 196 - row * 17) * Math.max(0D, edge));
            if (alpha <= 0) continue;
            int glyph = glyphMask(sequence * 11 + column * 7 + row * 13 + (int) Math.floor(flowTime * 2D));
            Vec3 origin = new Vec3(x, y, z);
            glyph(pose, out, origin, tangent, glyph, glow ? 0.115D : 0.09D, color, alpha);
        }
    }

    private static void glyph(PoseStack.Pose pose, VertexConsumer out, Vec3 origin, Vec3 tangent,
            int mask, double pixel, int color, int alpha) {
        for (int row = 0; row < 5; row++) {
            int bits = mask >> ((4-row)*3) & 7;
            for (int col = 0; col < 3; col++) {
                if ((bits & 1 << (2-col)) == 0) continue;
                int end = col+1;
                while (end < 3 && (bits & 1 << (2-end)) != 0) end++;
                Vec3 a = origin.add(tangent.scale((col-1.5D)*pixel)).add(0, -row*pixel, 0);
                Vec3 b = origin.add(tangent.scale((end-1.5D)*pixel-.018D)).add(0, -row*pixel, 0);
                quad(pose, out, a, b, b.add(0, -pixel+.018D, 0), a.add(0, -pixel+.018D, 0), color, alpha);
                col = end-1;
            }
        }
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int rgb, int alpha) {
        int color = OmniRenderGeometry.argb(rgb, alpha);
        vertex(pose, out, a, color); vertex(pose, out, b, color);
        vertex(pose, out, c, color); vertex(pose, out, d, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 point, int color) {
        out.addVertex(pose, (float)point.x, (float)point.y, (float)point.z).setColor(color);
    }

    private static double fract(double value) { return value-Math.floor(value); }
}
