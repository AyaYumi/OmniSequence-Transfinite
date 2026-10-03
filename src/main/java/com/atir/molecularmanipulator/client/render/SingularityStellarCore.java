package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

/** Orbital collector nodes around the white horizon; the horizon itself is a scene effect. */
public final class SingularityStellarCore {
    private static final double TAU = Math.PI * 2;
    private static final int WHITE = 0xF5FDFF;
    private static final Vec3[] NODES = quantumNodes();
    private static final Vec3[][] COLLECTOR_NORMALS = collectorNormals();
    private final PoseStack poses;
    private final VertexConsumer out;
    private final double time, power;
    private final boolean detailed;

    private SingularityStellarCore(PoseStack poses, VertexConsumer out, SingularityStructureEffects.Frame frame, boolean detailed) {
        this.poses = poses; this.out = out; this.detailed = detailed;
        time = ((frame.time() % 24000) + 24000) % 24000;
        power = .55 + .45 * Math.clamp(frame.power(), 0, 1);
    }

    public static void renderStar(PoseStack poses, VertexConsumer out, SingularityStructureEffects.Frame frame, boolean detailed) {
        var core = new SingularityStellarCore(poses, out, frame, detailed);
        core.begin(frame);
        core.collectors();
        poses.popPose();
    }

    public static void renderFlux(PoseStack poses, VertexConsumer out, SingularityStructureEffects.Frame frame, boolean detailed) {
        var core = new SingularityStellarCore(poses, out, frame, detailed);
        core.begin(frame);
        core.quantumMarkers();
        poses.popPose();
    }

    private void begin(SingularityStructureEffects.Frame frame) {
        double t = Math.clamp((frame.age() - 65) / 75, 0, 1);
        float size = (float) (.45 + .55 * t * t * (3 - 2 * t));
        poses.pushPose(); poses.translate(0, 12.5, 0); poses.scale(size, size, size);
        poses.mulPose(Axis.ZP.rotationDegrees(18));
    }

    private void collectors() {
        int count = detailed ? 6 : 4;
        for (int lane = 0; lane < 3; lane++) {
            poses.pushPose();
            poses.mulPose(Axis.YP.rotationDegrees(lane * 60));
            poses.mulPose(Axis.XP.rotationDegrees(24 + lane * 52));
            double phase = time * TAU / (lane == 1 ? -600 : 800);
            for (int node = 0; node < count; node++) {
                double a = phase + node * TAU / count;
                var p = circle(SingularityWhiteHoleProfile.CORE_SCALE * (3.22 + .12 * lane), a);
                collectorNode(p);
            }
            poses.popPose();
        }
    }

    private void collectorNode(Vec3 center) {
        // Compact, closed spheres stay volumetric from every angle, including through refraction.
        // The former wide, thin collector tiles looked like the discarded floating lens fragments.
        for (int y = 0; y < 4; y++) for (int x = 0; x < 8; x++) {
            collectorVertex(center, COLLECTOR_NORMALS[y][x]);
            collectorVertex(center, COLLECTOR_NORMALS[y + 1][x]);
            collectorVertex(center, COLLECTOR_NORMALS[y + 1][x + 1]);
            collectorVertex(center, COLLECTOR_NORMALS[y][x + 1]);
        }
    }

    private void collectorVertex(Vec3 center, Vec3 normal) {
        double light = Math.clamp(.60 - .18 * normal.x + .30 * normal.y - .15 * normal.z, 0, 1);
        double gold = Math.pow(Math.max(0, normal.y), 4);
        int r = (int) (42 + 56 * light + 86 * gold);
        int g = (int) (94 + 75 * light + 26 * gold);
        int b = (int) (111 + 75 * light - 28 * gold);
        vertex(center.add(normal.scale(.12)), 0xFF000000 | r << 16 | g << 8 | b);
    }

    private static Vec3[][] collectorNormals() {
        var normals = new Vec3[5][9];
        for (int y = 0; y <= 4; y++) for (int x = 0; x <= 8; x++) {
            double latitude = -Math.PI / 2 + y * Math.PI / 4;
            double longitude = (x % 8) * TAU / 8;
            normals[y][x] = new Vec3(Math.cos(latitude) * Math.cos(longitude), Math.sin(latitude),
                    Math.cos(latitude) * Math.sin(longitude));
        }
        return normals;
    }

    private void quantumMarkers() {
        poses.pushPose(); poses.mulPose(Axis.YP.rotationDegrees((float) (-time * 360 / 1200)));
        poses.mulPose(Axis.XP.rotationDegrees(15));
        for (int i = 0; i < NODES.length; i++) {
            var p = NODES[i];
            double pulse = .5 + .5 * Math.sin(time * TAU / 160 + i * Math.PI / 3);
            OmniRenderGeometry.octahedron(poses.last(), out, p, .06F, .11F, .06F,
                    (float) (time * 360 / 400), color(WHITE, 120 + pulse * 90));
        }
        poses.popPose();
    }

    private static Vec3[] quantumNodes() {
        double phi = (1 + Math.sqrt(5)) * .5;
        var nodes = new Vec3[12]; int i = 0;
        for (int a : new int[]{-1, 1}) for (int b : new int[]{-1, 1}) {
            nodes[i++] = new Vec3(0, a, b * phi).normalize().scale(4.15 * SingularityWhiteHoleProfile.CORE_SCALE);
            nodes[i++] = new Vec3(a, b * phi, 0).normalize().scale(4.15 * SingularityWhiteHoleProfile.CORE_SCALE);
            nodes[i++] = new Vec3(a * phi, 0, b).normalize().scale(4.15 * SingularityWhiteHoleProfile.CORE_SCALE);
        }
        return nodes;
    }
    private static Vec3 circle(double radius, double angle) {
        return new Vec3(radius * Math.cos(angle), 0, radius * Math.sin(angle));
    }
    private void vertex(Vec3 p, int color) {
        out.addVertex(poses.last().pose(), (float) p.x, (float) p.y, (float) p.z).setColor(color);
    }
    private int color(int rgb, double alpha) { return OmniRenderGeometry.argb(rgb, (int) Math.round(alpha * power)); }
}
