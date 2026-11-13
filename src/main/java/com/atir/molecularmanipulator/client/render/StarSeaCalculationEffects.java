package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;

/** Orrery-style pixel code falling around the four fixed facades of an operating mainframe. */
public final class StarSeaCalculationEffects {
    public static final double BOUNDS_MARGIN = .35D;
    private static final double SIDE = 2.72D;
    private static final double TOP = 8.8D;
    private static final double BOTTOM = .5D;
    private static final double CYCLE_SECONDS = 8D;
    private static final int ICE = 0xD7F8ED;
    private static final int MINT = 0x8AD9C8;
    private static final int TEAL = 0x398D79;

    private StarSeaCalculationEffects() {}

    /** Block-space centred on the building, before the miniature baked model's 8x scale. */
    public static void render(PoseStack poses, VertexConsumer out, boolean running, int quality,
            double distanceSqr, double time, boolean glow) {
        if (!running || quality <= 0 || out == null) return;
        boolean detailed = StarOrreryEffects.isDetailed(quality, distanceSqr);
        int lanes = detailed ? 3 : 2;
        int rows = glow ? 1 : detailed ? 6 : 3;
        double pixel = glow ? .078D : .065D;
        for (int side = 0; side < 4; side++) {
            Vec3 tangent = switch (side) {
                case 0 -> new Vec3(1, 0, 0);
                case 1 -> new Vec3(0, 0, 1);
                case 2 -> new Vec3(-1, 0, 0);
                default -> new Vec3(0, 0, -1);
            };
            for (int lane = 0; lane < lanes; lane++) {
                double along = -1.8D + 3.6D * lane / (lanes - 1);
                int column = side * lanes + lane;
                double phase = time / CYCLE_SECONDS + column * .1618D;
                double head = TOP - (phase - Math.floor(phase)) * (TOP - BOTTOM);
                for (int row = 0; row < rows; row++) {
                    // A bright falling head leads a short, dimmer tail ABOVE it.
                    double y = head + row * .58D;
                    if (y <= BOTTOM + 5D * pixel || y >= TOP) continue;
                    double edge = Math.min(1D, Math.min(TOP - y, y - BOTTOM - 5D * pixel) / .65D);
                    int alpha = (int) ((glow ? 24 : 220 - row * 28) * edge);
                    if (alpha <= 0) continue;
                    Vec3 origin = switch (side) {
                        case 0 -> new Vec3(along, y, -SIDE);
                        case 1 -> new Vec3(SIDE, y, along);
                        case 2 -> new Vec3(-along, y, SIDE);
                        default -> new Vec3(-SIDE, y, -along);
                    };
                    int mask = StarOrreryCoreEffects.glyphMask(column * 7 + row * 11 + (int) Math.floor(time * 2D));
                    int color = row == 0 ? ICE : row == 1 ? MINT : TEAL;
                    StarOrreryCoreEffects.glyph(poses.last(), out, origin, tangent, mask, pixel, color, alpha);
                }
            }
        }
    }
}
