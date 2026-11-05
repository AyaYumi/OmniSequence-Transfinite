package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import com.atir.molecularmanipulator.block.LabPlatformGeometry;

/** Surrounding filaments and experiment supply link; the hole bodies use the shared cosmic lens shader. */
public final class HighEnergyLabEffects {
    private static final double TAU = Math.PI * 2;
    private static final int VIOLET = 0xB858FA;
    private static final int GOLD = 0xFFE0AD;
    private static final int FEED = 0xA9F9E4;
    private static final int[][] BEACONS = {
        {44,22,0}, {38,20,22}, {22,16,38}, {-22,16,38}, {-38,20,22},
        {-44,23,0}, {-38,24,-22}, {-22,32,-38}, {22,32,-38}, {38,25,-22},
        {66,14,11}, {46,14,54}, {-1,14,74}, {-45,14,51}, {-70,14,8},
        {-58,14,-44}, {-10,14,-65}, {44,14,-53}, {69,14,-23}
    };
    private static final Vec3[] FEED_POINTS = {
        new Vec3(0,8,39), new Vec3(0,9,28), new Vec3(0,10,15), new Vec3(0,11,2)
    };

    private HighEnergyLabEffects() {}

    public static void render(PoseStack poses, VertexConsumer out,
            boolean powered, float seconds, boolean detailed, boolean glow) {
        render(poses, out, powered, seconds, detailed, glow, false);
    }

    public static void render(PoseStack poses, VertexConsumer out,
            boolean powered, float seconds, boolean detailed, boolean glow, boolean platform) {
        if (!powered) return;
        Vec3[] feed = platform ? LabPlatformGeometry.SUPPLY_PATH : FEED_POINTS;
        if (!glow) {
            supply(poses.last(), out, seconds, false, feed);
            return;
        }
        holeLight(poses, out, seconds, detailed);
        supply(poses.last(), out, seconds, true, feed);
        for (int[] beacon : BEACONS) {
            for (int step = 0; step < 24; step++) {
                float fade = (float) Math.pow(1 - step / 24D, 1.7);
                Vec3 a = new Vec3(beacon[0], beacon[1] + step * 2.8, beacon[2]);
                line(poses.last(), out, a, a.add(0,2.8,0), .025, 0x9CECEB, (int) (60 * fade));
            }
        }
    }

    /** No polar jets, and no connection runs through either dark aperture. */
    static void holeLight(PoseStack poses, VertexConsumer out, float seconds, boolean detailed) {
        int steps = detailed ? 100 : 48;
        for (boolean white : new boolean[] {false, true}) {
            int color = white ? GOLD : VIOLET;
            double cx = white ? 13 : -13;
            for (int strand = 0; strand < 9; strand++) {
                for (int step = 0; step < steps; step++) {
                    line(poses.last(), out, accretion(cx, strand, step * TAU / steps),
                            accretion(cx, strand, (step + 1) * TAU / steps),
                            strand < 3 ? .034 : .022, color, strand < 3 ? 165 : 92);
                }
                // Animated cubic motes remain a client effect, not blocks or game entities.
                double a = (seconds * .16 + strand * .871) % TAU;
                Vec3 mote = accretion(cx, strand, a);
                OmniRenderGeometry.sphere(poses.last(), out, mote, .12F, 4, 8,
                        OmniRenderGeometry.argb(white ? 0xFFF5DC : 0xE9CBFF, 240));
            }
        }
        for (int strand = 0; strand < 7; strand++) {
            for (int step = 0; step < 40; step++) {
                double t = step / 40D;
                line(poses.last(), out, coupling(t, strand), coupling((step + 1) / 40D, strand),
                        .023, mix(VIOLET, GOLD, t), 126);
            }
        }
    }

    private static Vec3 accretion(double cx, int strand, double a) {
        double r = 7 + strand * .86, phase = strand * 1.19;
        return new Vec3(cx + Math.cos(a)*r + Math.sin(a*3 + phase)*.6,
                12 - Math.sin(a)*r*.1 + Math.sin(a*3 + phase)*(.2 + strand*.13),
                Math.sin(a)*r*.82 + Math.cos(a*2 + phase)*(.2 + strand*.12));
    }

    private static Vec3 coupling(double t, int strand) {
        return new Vec3(-6.5 + t*12.8,12 + Math.sin(t*Math.PI*4 + strand)*1.25,
                2 + Math.cos(t*Math.PI*3 + strand)*1.1);
    }

    private static void supply(PoseStack.Pose pose, VertexConsumer out, float seconds, boolean glow, Vec3[] feed) {
        for (int side = -1; side <= 1; side++) {
            if (!glow && side != 0) continue;
            for (int i = 0; i < feed.length - 1; i++) {
                Vec3 shift = new Vec3(side*.35,side == 0 ? 0 : .12,0);
                line(pose, out, feed[i].add(shift), feed[i+1].add(shift),
                        glow ? .08 : .045, side == 0 ? FEED : 0xB6A3EC, glow ? 50 : 255);
            }
        }
        if (glow) return;
        for (int i = 0; i < 5; i++) {
            double t = ((seconds*.3 + i/5D) % 1)*(feed.length - 1);
            int segment = (int) t;
            Vec3 mote = feed[segment].lerp(feed[segment+1], t - segment);
            OmniRenderGeometry.sphere(pose, out, mote, .17F, 4, 8, OmniRenderGeometry.argb(FEED,240));
        }
    }

    private static int mix(int a, int b, double t) {
        int rgb = 0;
        for (int shift : new int[] {16,8,0}) {
            int first = a >> shift & 255, second = b >> shift & 255;
            rgb |= (int) (first + (second - first)*t) << shift;
        }
        return rgb;
    }

    /** Crossed ribbons keep the thin lines visible from every viewing direction. */
    private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b,
            double width, int rgb, int alpha) {
        Vec3 direction = b.subtract(a).normalize();
        Vec3 axis = Math.abs(direction.y) > .9 ? new Vec3(1,0,0) : new Vec3(0,1,0);
        Vec3 first = direction.cross(axis).normalize().scale(width);
        Vec3 second = direction.cross(first).normalize().scale(width);
        int color = OmniRenderGeometry.argb(rgb, alpha);
        for (Vec3 offset : new Vec3[] {first, second}) {
            vertex(pose,out,a.add(offset),color); vertex(pose,out,a.subtract(offset),color);
            vertex(pose,out,b.subtract(offset),color); vertex(pose,out,b.add(offset),color);
        }
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 point, int color) {
        out.addVertex(pose,(float)point.x,(float)point.y,(float)point.z).setColor(color);
    }
}
