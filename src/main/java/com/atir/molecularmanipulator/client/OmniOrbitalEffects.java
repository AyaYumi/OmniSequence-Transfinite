package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniCrownGeometry;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure.PartType;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import static com.atir.molecularmanipulator.client.render.OmniRenderGeometry.argb;

/** Amethyst celestial orrery. The outer orbit deliberately has no geometric seal markers. */
final class OmniOrbitalEffects {
    private static final int CYAN = 0x87DAFF;
    private static final int VIOLET = 0xC786FF;
    private static final int PEARL = 0xE6DAFF;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final List<Vec3> STATIONS = OmniCrownGeometry.createParts().stream()
            .filter(part -> part.type() == PartType.CRYSTAL_PYLON
                    && (part.y() == OmniCrownGeometry.CENTER_Y + 9 || part.y() == OmniCrownGeometry.CENTER_Y - 5))
            .map(part -> new Vec3(part.x(), part.y() - OmniCrownGeometry.CENTER_Y, part.z())).toList();

    private OmniOrbitalEffects() { }

    static void render(PoseStack stack, VertexConsumer out, double angle, float activity,
            float completion, boolean detailed, boolean glow) {
        float drive = com.atir.molecularmanipulator.util.MathCompat.clamp(activity, 0, 1);
        float pulse = com.atir.molecularmanipulator.util.MathCompat.clamp(completion, 0, 1);
        var pose = stack.last();
        drawOrbits(pose, out, angle, drive, pulse, detailed, glow);
        drawHypercube(pose, out, angle, drive, glow);
        drawStarMap(pose, out, angle, drive, pulse, detailed, glow);
        drawStations(pose, out, angle, drive, detailed, glow);
        if (detailed) drawCoordinateMarkers(pose, out, angle, glow);
    }

    private static void drawOrbits(PoseStack.Pose pose, VertexConsumer out, double angle,
            float drive, float pulse, boolean detailed, boolean glow) {
        int index = 0;
        for (var orbit : OmniCrownGeometry.orbits()) {
            int color = index == 2 ? CYAN : VIOLET;
            int segments = detailed ? 192 : 96;
            for (double offset : new double[] {-0.68, 0.68}) {
                orbitArc(pose, out, orbit, 0, (Math.PI * 2), segments, offset,
                        glow ? 0.115F : 0.05F, color, glow ? 23 : 165);
            }
            if (index == 0) {
                // Twenty-four calibration groups, not twelve closed emblems.
                for (int group = 0; group < 24; group++) {
                    double phase = group * (Math.PI * 2) / 24;
                    for (int tick = -1; tick <= 1; tick++) {
                        double a = phase + tick * 0.018;
                        line(pose, out, orbitPoint(orbit, a, -0.30), orbitPoint(orbit, a, tick == 0 ? 0.38 : 0.08),
                                glow ? 0.065F : 0.024F, pulse > 0.5 ? PEARL : color,
                                Math.round(glow ? 18 + pulse * 16 : 136 + pulse * 72));
                    }
                    double moving = phase + angle * 0.002;
                    orbitArc(pose, out, orbit, moving, 0.095, 4, 0,
                            glow ? 0.085F : 0.028F, color, glow ? 19 : 142);
                }
            } else {
                int panels = detailed ? 8 : 4;
                for (int panel = 0; panel < panels; panel++) {
                    double start = panel * (Math.PI * 2) / panels + angle * (index == 1 ? 0.0018 : -0.0018);
                    double sweep = 0.42;
                    int steps = detailed ? 6 : 4;
                    for (int step = 0; step < steps; step++) {
                        double a = start + sweep * step / steps, b = start + sweep * (step + 1) / steps;
                        if (glow) face(pose, out, orbitPoint(orbit, a, -0.62), orbitPoint(orbit, b, -0.62),
                                orbitPoint(orbit, b, 0.62), orbitPoint(orbit, a, 0.62), argb(color, 30));
                    }
                    for (double edge : new double[] {-0.62, 0.62}) orbitArc(pose, out, orbit, start, sweep, steps, edge,
                            glow ? 0.14F : 0.06F, color, glow ? 24 : 157);
                    for (double end : new double[] {start, start + sweep}) line(pose, out,
                            orbitPoint(orbit, end, -0.62), orbitPoint(orbit, end, 0.62),
                            glow ? 0.13F : 0.05F, color, glow ? 22 : 143);
                    if (detailed) for (int trace = 0; trace < 3; trace++) {
                        double a = start + 0.06 + trace * 0.09;
                        orbitArc(pose, out, orbit, a, 0.06, 2, (trace - 1) * 0.28,
                                glow ? 0.05F : 0.019F, PEARL, glow ? 16 : 118);
                        line(pose, out, orbitPoint(orbit, a, -0.36), orbitPoint(orbit, a, -0.12),
                                glow ? 0.05F : 0.019F, color, glow ? 16 : 119);
                    }
                }
            }
            int packets = detailed ? 6 : 3;
            double direction = index == 1 ? -1 : 1;
            for (int packet = 0; packet < packets; packet++) {
                double head = packet * (Math.PI * 2) / packets + angle * (0.009 + index * 0.001);
                for (int tail = 0; tail < 5; tail++) {
                    double a = direction * (head - tail * 0.025);
                    orbitArc(pose, out, orbit, a, -direction * 0.025, 1, 0,
                            (glow ? 0.20F : 0.09F) * (1 - tail * 0.14F),
                            packet % 2 == 0 ? PEARL : color, glow ? 35 - tail * 5 : 220 - tail * 26);
                }
                spark(pose, out, orbitPoint(orbit, direction * head, 0),
                        0.22 + drive * 0.06, glow, PEARL);
            }
            index++;
        }
    }

    static Vec3 railPoint(OmniCrownGeometry.Orbit orbit, double phase) {
        double offset = orbit.ny() > 0.999 ? 0.66 : 1.9;
        return orbit.point(phase).add(new Vec3(orbit.nx(), orbit.ny(), orbit.nz()).scale(offset));
    }

    static Vec3 orbitPoint(OmniCrownGeometry.Orbit orbit, double phase, double radialOffset) {
        return railPoint(orbit, phase).add(orbit.point(phase).normalize().scale(radialOffset));
    }

    private static void orbitArc(PoseStack.Pose pose, VertexConsumer out, OmniCrownGeometry.Orbit orbit,
            double start, double sweep, int segments, double offset, float width, int color, int alpha) {
        for (int step = 0; step < segments; step++) line(pose, out,
                orbitPoint(orbit, start + sweep * step / segments, offset),
                orbitPoint(orbit, start + sweep * (step + 1) / segments, offset), width, color, alpha);
    }

    /** Rotate a true four-dimensional cube, then project through a bounded 4D perspective. */
    static Vec3[] hypercubeVertices(double angle) {
        var vertices = new Vec3[16];
        double ax = angle * 0.005, ay = angle * 0.0031;
        for (int index = 0; index < 16; index++) {
            double x = (index & 1) == 0 ? -1 : 1, y = (index & 2) == 0 ? -1 : 1;
            double z = (index & 4) == 0 ? -1 : 1, w = (index & 8) == 0 ? -1 : 1;
            double rx = x * Math.cos(ax) - w * Math.sin(ax);
            double rw = x * Math.sin(ax) + w * Math.cos(ax);
            double ry = y * Math.cos(ay) - rw * Math.sin(ay);
            rw = y * Math.sin(ay) + rw * Math.cos(ay);
            double scale = 4.7 / (3.5 - rw);
            vertices[index] = new Vec3(rx * scale, ry * scale, z * scale).yRot((float) (angle * 0.002));
        }
        return vertices;
    }

    private static void drawHypercube(PoseStack.Pose pose, VertexConsumer out, double angle, float drive, boolean glow) {
        var vertices = hypercubeVertices(angle);
        for (int index = 0; index < vertices.length; index++) {
            for (int axis = 0; axis < 4; axis++) {
                int second = index ^ (1 << axis);
                if (second <= index) continue;
                int color = axis == 3 ? VIOLET : CYAN;
                line(pose, out, vertices[index], vertices[second], glow ? 0.14F : 0.065F,
                        color, Math.round(glow ? 28 + drive * 6 : 184 + drive * 42));
            }
            spark(pose, out, vertices[index], glow ? 0.22 : 0.16, glow, PEARL);
        }
    }

    private static Vec3 sphere(double radius, double longitude, double latitude) {
        double horizontal = radius * Math.cos(latitude);
        return new Vec3(Math.cos(longitude) * horizontal, Math.sin(latitude) * radius,
                Math.sin(longitude) * horizontal);
    }

    private static void drawStarMap(PoseStack.Pose pose, VertexConsumer out, double angle, float drive,
            float pulse, boolean detailed, boolean glow) {
        double spin = -angle * 0.0015;
        int segments = detailed ? 96 : 48;
        int latitudeCount = detailed ? 3 : 1;
        for (int band = 0; band < latitudeCount; band++) {
            double latitude = latitudeCount == 1 ? 0 : (band - 1) * 0.64;
            for (int step = 0; step < segments; step++) {
                if (step % 12 == 11) continue;
                line(pose, out, sphere(8.45, spin + step * (Math.PI * 2) / segments, latitude),
                        sphere(8.45, spin + (step + 1) * (Math.PI * 2) / segments, latitude),
                        glow ? 0.085F : 0.032F, CYAN, glow ? 16 : 114);
            }
        }
        for (int plane = 0; plane < (detailed ? 3 : 2); plane++) {
            double longitude = spin + plane * Math.PI / (detailed ? 3 : 2);
            for (int step = 0; step < segments; step++) {
                double a = step * (Math.PI * 2) / segments, b = (step + 1) * (Math.PI * 2) / segments;
                line(pose, out, sphere(8.5, longitude, a), sphere(8.5, longitude, b),
                        glow ? 0.085F : 0.032F, plane % 2 == 0 ? CYAN : VIOLET, glow ? 16 : 114);
            }
        }
        int panels = detailed ? 6 : 3;
        for (int panel = 0; panel < panels; panel++) {
            double lon = panel * (Math.PI * 2) / panels + spin;
            double lat = panel % 2 == 0 ? -0.36 : 0.36;
            int columns = detailed ? 6 : 4;
            for (int column = 0; column < columns; column++) for (int row = 0; row < 3; row++) {
                double a = lon - 0.31 + column * 0.62 / columns, b = lon - 0.31 + (column + 1) * 0.62 / columns;
                double c = lat - 0.23 + row * 0.46 / 3, d = lat - 0.23 + (row + 1) * 0.46 / 3;
                if (glow) face(pose, out, sphere(8.75, a, c), sphere(8.75, b, c),
                        sphere(8.75, b, d), sphere(8.75, a, d), argb(VIOLET, 32));
                if (row == 0 || row == 2) {
                    double edge = row == 0 ? c : d;
                    line(pose, out, sphere(8.76, a, edge), sphere(8.76, b, edge),
                            glow ? 0.12F : 0.045F, VIOLET, glow ? 23 : 152);
                }
                if (column == 0 || column == columns - 1) {
                    double edge = column == 0 ? a : b;
                    line(pose, out, sphere(8.76, edge, c), sphere(8.76, edge, d),
                            glow ? 0.12F : 0.045F, CYAN, glow ? 23 : 152);
                }
            }
            Vec3 previous = null;
            for (int node = 0; node < 3; node++) {
                double a = lon - 0.17 + node * 0.16, b = lat + Math.sin(panel + node * 1.7) * 0.12;
                Vec3 point = sphere(8.79, a, b);
                spark(pose, out, point, 0.10, glow, PEARL);
                if (previous != null) {
                    int alpha = Math.round((glow ? 14 : 90) + drive * (glow ? 12 : 80));
                    line(pose, out, previous, point, glow ? 0.06F : 0.024F, CYAN, alpha);
                }
                previous = point;
                if (detailed) line(pose, out, sphere(8.78, a, lat - 0.15),
                        sphere(8.78, a + 0.075, lat - 0.15), glow ? 0.05F : 0.018F, VIOLET, glow ? 16 : 117);
            }
        }
        if (pulse > 0.001F) {
            double latitude = -Math.PI / 2 + (1 - pulse) * Math.PI;
            for (int step = 0; step < segments; step++) {
                line(pose, out, sphere(8.95, step * (Math.PI * 2) / segments, latitude),
                        sphere(8.95, (step + 1) * (Math.PI * 2) / segments, latitude),
                        glow ? 0.15F : 0.062F, PEARL, Math.round(pulse * (glow ? 35 : 225)));
                double a = step * (Math.PI * 2) / segments, b = (step + 1) * (Math.PI * 2) / segments;
                double radius = 4.8 + (1 - pulse) * 4.25;
                line(pose, out, sphere(radius, spin, a), sphere(radius, spin, b),
                        glow ? 0.11F : 0.045F, VIOLET, Math.round(pulse * (glow ? 29 : 177)));
            }
        }
    }

    private static void drawStations(PoseStack.Pose pose, VertexConsumer out, double angle,
            float drive, boolean detailed, boolean glow) {
        int index = 0;
        for (var station : STATIONS) {
            int count = station.y > 0 ? 2 : 1;
            for (int ring = 0; ring < count; ring++) {
                double radius = ring == 0 ? 1.28 : 0.94;
                double y = ring == 0 ? -0.38 : 0.48;
                int steps = detailed ? 6 : 4;
                for (int sector = 0; sector < 4; sector++) {
                    double start = sector * Math.PI / 2 + angle * (ring == 0 ? 0.006 : -0.005) + index * 0.3;
                    for (int step = 0; step < steps; step++) line(pose, out,
                            station.add(circle(radius, y, start + step * 1.12 / steps)),
                            station.add(circle(radius, y, start + (step + 1) * 1.12 / steps)),
                            glow ? 0.14F : 0.05F, VIOLET, Math.round(glow ? 22 + drive * 7 : 152 + drive * 30));
                }
            }
            Vec3 outward = new Vec3(station.x, 0, station.z).normalize();
            Vec3 right = new Vec3(-outward.z, 0, outward.x);
            for (int side : new int[] {-1, 1}) {
                Vec3 a = station.add(right.scale(side * 1.45)).add(0, -0.5, 0);
                Vec3 b = a.add(0, 0.75, 0), c = b.add(right.scale(-side * 0.4));
                line(pose, out, a, b, glow ? 0.065F : 0.024F, CYAN, glow ? 18 : 124);
                line(pose, out, b, c, glow ? 0.065F : 0.024F, CYAN, glow ? 18 : 124);
            }
            index++;
        }
    }

    private static void drawCoordinateMarkers(PoseStack.Pose pose, VertexConsumer out, double angle, boolean glow) {
        for (int marker = 0; marker < 6; marker++) {
            double phase = marker * (Math.PI * 2) / 6 + 0.29 + angle * 0.0007;
            Vec3 center = circle(17.2, 3 + Math.sin(phase * 2 + 0.7) * 4, phase);
            Vec3 right = new Vec3(-Math.sin(phase), 0, Math.cos(phase));
            for (int sx : new int[] {-1, 1}) for (int sy : new int[] {-1, 1}) {
                Vec3 corner = center.add(right.scale(sx * 0.58)).add(UP.scale(sy * 0.58));
                line(pose, out, corner, corner.add(right.scale(-sx * 0.28)),
                        glow ? 0.065F : 0.025F, VIOLET, glow ? 19 : 127);
                line(pose, out, corner, corner.add(UP.scale(-sy * 0.28)),
                        glow ? 0.065F : 0.025F, VIOLET, glow ? 19 : 127);
            }
            spark(pose, out, center, 0.15, glow, CYAN);
        }
    }

    private static Vec3 circle(double radius, double y, double phase) {
        return new Vec3(Math.cos(phase) * radius, y, Math.sin(phase) * radius);
    }

    private static void spark(PoseStack.Pose pose, VertexConsumer out, Vec3 center, double radius, boolean glow, int color) {
        for (var axis : new Vec3[] {new Vec3(radius, 0, 0), new Vec3(0, radius, 0), new Vec3(0, 0, radius)}) {
            line(pose, out, center.subtract(axis), center.add(axis), glow ? 0.055F : 0.025F,
                    color, glow ? 31 : 208);
        }
    }

    /** Crossed double-sided ribbons read from any angle at one third of a box beam's vertex cost. */
    private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b,
            float width, int color, int alpha) {
        Vec3 delta = b.subtract(a);
        if (delta.lengthSqr() < 1.0E-12 || alpha <= 0) return;
        Vec3 direction = delta.normalize();
        Vec3 side = direction.cross(Math.abs(direction.y) > 0.95 ? new Vec3(1, 0, 0) : UP).normalize().scale(width);
        Vec3 normal = direction.cross(side).normalize().scale(width * 0.65);
        face(pose, out, a.add(side), b.add(side), b.subtract(side), a.subtract(side), argb(color, alpha));
        face(pose, out, a.add(normal), b.add(normal), b.subtract(normal), a.subtract(normal), argb(color, alpha));
    }

    private static void face(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 c, Vec3 d, int color) {
        vertex(pose, out, a, color); vertex(pose, out, b, color); vertex(pose, out, c, color); vertex(pose, out, d, color);
        vertex(pose, out, d, color); vertex(pose, out, c, color); vertex(pose, out, b, color); vertex(pose, out, a, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 point, int color) {
        out.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z).color(color).endVertex();
    }
}
