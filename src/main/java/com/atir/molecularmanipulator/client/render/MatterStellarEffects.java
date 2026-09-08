package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.research.ResearchVisualState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/** Bounded star-ring geometry, shared by live rendering and GPU verification. */
public final class MatterStellarEffects {
    private static final int ICE = 0xA1ECFF;
    private static final int GOLD = 0xFFE4A0;
    private static final int WHITE = 0xF3FCFF;
    private static final int[] COLORS = {0x76DFFF, 0x7BF4D4, 0xBE9BFF, 0xFFAFCD};
    private static final int[] ACCENTS = {0xE1FAFF, GOLD, 0xF0DEFF, 0xFFDC91};
    private static final double TAU = Math.PI * 2;

    private MatterStellarEffects() {}

    public record Frame(float activity, float productionTime, ResearchVisualState research,
            double researchElapsed, float researchCompletion) {
        public static final Frame IDLE = new Frame(0, 0, ResearchVisualState.EMPTY, 0, 0);
    }

    public static final class Animation {
        private double previousTime = Double.NaN;
        private float activity;
        private float productionTime;

        public Frame sample(float time, boolean running, float completion, ResearchVisualState research,
                double elapsed, float researchCompletion) {
            float delta = Double.isNaN(previousTime) ? 1 : (float) com.atir.molecularmanipulator.util.MathCompat.clamp(time - previousTime, 0, 5);
            previousTime = time;
            // Coast through packet boundaries and one-tick crafts without restarting the orbit.
            float target = running || completion > 0 ? 1 : 0;
            activity += (target - activity) * (1 - (float) Math.exp(-delta / 6));
            productionTime += delta * activity;
            return new Frame(activity, productionTime, research, elapsed, researchCompletion);
        }
    }

    public static void render(PoseStack stack, VertexConsumer out, Frame frame, boolean detailed, boolean glow) {
        if (frame.activity() > 0.015F) production(stack, out, frame, detailed, glow);
        List<ResearchVisualState.Task> tasks = frame.research().tasks();
        for (int lane = 0; lane < tasks.size(); lane++) {
            research(stack, out, tasks.get(lane), lane, tasks.size(), frame.researchElapsed(), detailed, glow);
        }
        if (frame.researchCompletion() > 0) breakthrough(stack, out, frame, detailed, glow);
    }

    private static void production(PoseStack stack, VertexConsumer out, Frame frame, boolean detailed, boolean glow) {
        var pose = stack.last();
        float time = frame.productionTime(), strength = frame.activity();
        int segments = detailed ? 72 : 36;
        for (int orbit = 0; orbit < 3; orbit++) {
            double phase = time * (orbit == 1 ? -0.014 : 0.011) + orbit * 2.1;
            double radius = 3.72 + orbit * 0.23;
            float tilt = (float) Math.toRadians(orbit == 0 ? 22 : orbit == 1 ? 66 : -52);
            float yaw = orbit * 1.05F;
            int color = orbit == 1 ? GOLD : ICE;
            for (int part = 0; part < segments; part++) {
                if (part % 12 >= 9) continue;
                double angle = phase + TAU * part / segments;
                Vec3 a = orbit(radius, angle, tilt, yaw), b = orbit(radius, angle + TAU / segments, tilt, yaw);
                line(pose, out, a, b, glow ? 0.075F : 0.025F, color, (glow ? 24 : 185) * strength);
                if (part % 12 == 0) line(pose, out, a.scale(0.975), a.scale(1.035),
                        glow ? 0.085F : 0.025F, WHITE, (glow ? 28 : 220) * strength);
            }
            for (int satellite = 0; satellite < (detailed ? 4 : 2); satellite++) {
                double angle = phase * 2 + satellite * TAU / (detailed ? 4 : 2);
                Vec3 point = orbit(radius, angle, tilt, yaw);
                star(pose, out, point, glow ? 0.15 : 0.085, WHITE, (glow ? 38 : 240) * strength);
                int tail = detailed ? 6 : 3;
                for (int step = 0; step < tail; step++) {
                    double start = angle - (step + 1) * 0.045;
                    line(pose, out, orbit(radius, start, tilt, yaw), orbit(radius, start + 0.04, tilt, yaw),
                            glow ? 0.06F : 0.02F, color, (glow ? 22 : 155) * strength * (1F - step / (float) tail));
                }
            }
        }
        // Independent of individual completion pulses, so one-tick production still draws incoming matter.
        for (int stream = 0; stream < 4; stream++) {
            int packets = detailed ? 6 : 3;
            for (int packet = 0; packet < packets; packet++) {
                double travel = fraction(time * 0.018 + packet / (double) packets + stream * 0.137);
                Vec3 point = incomingMatter(stream, travel, time);
                star(pose, out, point, glow ? 0.1 : 0.055, GOLD, (glow ? 28 : 230) * strength);
                line(pose, out, incomingMatter(stream, Math.max(0, travel - 0.065), time), point,
                        glow ? 0.045F : 0.014F, ICE, (glow ? 20 : 165) * strength);
            }
        }
        // Upward waves make continuous production legible even when recipes complete every tick.
        for (int wave = 0; wave < 2; wave++) {
            float age = (float) fraction(time / 65 + wave * 0.5);
            float alpha = (float) Math.sin(age * Math.PI) * strength;
            OmniRenderGeometry.ring(stack, out, 0, -3.8F + age * 7.4F, 0, 3.5F,
                    glow ? 0.06F : 0.018F, detailed ? 72 : 36, 0, 0, 0,
                    OmniRenderGeometry.argb(ICE, Math.round(alpha * (glow ? 15 : 100))));
        }
    }

    private static Vec3 incomingMatter(int stream, double travel, float time) {
        double radius = 4.05 * (1 - travel) + 0.25;
        double angle = stream * Math.PI / 2 + time * 0.004 + travel * 1.4;
        return new Vec3(Math.cos(angle) * radius, (stream % 2 == 0 ? 2.2 : -2.2) * (1 - travel),
                Math.sin(angle) * radius);
    }

    private static void research(PoseStack stack, VertexConsumer out, ResearchVisualState.Task task,
            int lane, int count, double elapsed, boolean detailed, boolean glow) {
        double activeTime = task.progress() + (task.running() ? com.atir.molecularmanipulator.util.MathCompat.clamp(elapsed, 0, 5) : 0);
        double seedPhase = (task.identity() & 0xFFFF) / 65536.0 * TAU;
        float progress = task.fraction(elapsed);
        float strength = task.running() ? 1 : 0.26F;
        int style = task.style(), color = COLORS[style], accent = ACCENTS[style];
        double radius = count == 1 ? 2.25 : count == 2 ? 1.55 : 1.23;
        Vec3 center = count == 1 ? new Vec3(0, 5.35, 0)
                : count == 2 ? new Vec3(lane == 0 ? -2.15 : 2.15, 5.2, 0)
                : new Vec3(lane % 2 == 0 ? -2 : 2, 5.3, lane / 2 == 0 ? -2 : 2);
        stack.pushPose();
        stack.translate(center.x, center.y, center.z);
        var pose = stack.last();
        double phase = activeTime * 0.008 + seedPhase;
        int segments = detailed && count <= 2 ? 64 : 32;
        for (int segment = 0; segment < segments; segment++) {
            if (segment % 8 == 7) continue;
            double a = TAU * segment / segments;
            boolean solved = segment / (float) segments < progress;
            line(pose, out, orbit(radius * 1.12, a, 0.25F, 0), orbit(radius * 1.12, a + TAU / segments, 0.25F, 0),
                    glow ? 0.065F : 0.022F, solved ? accent : color,
                    (solved ? glow ? 30 : 225 : glow ? 8 : 65) * strength);
        }
        int nodes = detailed && count <= 2 ? 16 : 8;
        for (int node = 0; node < nodes; node++) {
            double u = node / (double) nodes;
            Vec3 point = researchNode(style, u, phase).scale(radius);
            Vec3 next = researchNode(style, (node + 1.0) / nodes, phase).scale(radius);
            boolean solved = u <= progress;
            line(pose, out, point, next, glow ? 0.045F : 0.014F, color, (glow ? 14 : 110) * strength);
            if (detailed || node % 2 == 0) {
                Vec3 across = researchNode(style, (node + (style == 1 ? 4.0 : 5.0)) / nodes, phase).scale(radius);
                line(pose, out, point, across, glow ? 0.027F : 0.009F, color, (glow ? 7 : 48) * strength);
                if (task.running()) {
                    double travel = fraction(activeTime * 0.025 + node * 0.173);
                    star(pose, out, point.lerp(across, travel), glow ? 0.075 : 0.037,
                            accent, (glow ? 30 : 225) * strength);
                }
            }
            float pulse = task.running() ? (float) (0.75 + 0.25 * Math.sin(activeTime * 0.09 - node * 0.9)) : 1;
            star(pose, out, point, (glow ? 0.12 : 0.065) * pulse,
                    solved ? accent : color, (solved ? glow ? 35 : 230 : glow ? 13 : 110) * strength);
        }
        // Extra crown arcs indicate depth without increasing geometry without bound.
        int crowns = Math.min(3, 1 + (task.round() - 1) / 3);
        for (int crown = 0; crown < crowns; crown++) {
            double r = radius * (0.36 + crown * 0.13);
            for (int segment = 0; segment < 16; segment++) {
                double a = phase * -1.7 + segment * TAU / 24;
                var first = orbit(r, a, 0, 0).add(0, radius * 0.9 + crown * 0.12, 0);
                var second = orbit(r, a + TAU / 24, 0, 0).add(0, radius * 0.9 + crown * 0.12, 0);
                line(pose, out, first, second, glow ? 0.05F : 0.015F, accent, (glow ? 18 : 160) * strength);
            }
        }
        stack.popPose();
    }

    /** Four distinct star maps: crystal lattice, array, orbital sphere and double helix. */
    public static Vec3 researchNode(int style, double u, double phase) {
        double a = u * TAU;
        return switch (Math.floorMod(style, ResearchVisualState.STYLE_COUNT)) {
            case 0 -> new Vec3(Math.cos(a), Math.sin(a * 2) * 0.52, Math.sin(a)).yRot((float) phase);
            case 1 -> new Vec3(Math.cos(a), Math.sin(a * 4) * 0.36, Math.sin(a))
                    .scale(1 / Math.max(Math.abs(Math.cos(a)), Math.abs(Math.sin(a))))
                    .yRot((float) -phase);
            case 2 -> new Vec3(Math.cos(a) * Math.cos(a * 2), Math.sin(a * 2) * 0.8, Math.sin(a) * Math.cos(a * 2))
                    .yRot((float) phase).zRot(0.38F);
            default -> new Vec3(Math.cos(a * 2) * 0.8, Math.sin(a) * 0.8, Math.sin(a * 2) * 0.8)
                    .yRot((float) -phase).xRot(0.35F);
        };
    }

    private static void breakthrough(PoseStack stack, VertexConsumer out, Frame frame, boolean detailed, boolean glow) {
        float remaining = frame.researchCompletion(), age = 1 - remaining;
        int accent = ACCENTS[frame.research().completionStyle()];
        float envelope = (float) Math.sin(Math.PI * age);
        for (int ring = 0; ring < 3; ring++) {
            float radius = 0.7F + age * 3.4F + ring * 0.16F;
            OmniRenderGeometry.ring(stack, out, 0, 4.4F + age * 2.2F - ring * 0.25F, 0, radius,
                    glow ? 0.095F : 0.028F, detailed ? 80 : 40, 0, 0, 0,
                    OmniRenderGeometry.argb(accent, Math.round(envelope * (glow ? 32 : 205))));
        }
        for (int ray = 0; ray < (detailed ? 12 : 6); ray++) {
            double a = TAU * ray / (detailed ? 12 : 6);
            Vec3 point = new Vec3(Math.cos(a) * (0.5 + age * 2), 5.3 + age, Math.sin(a) * (0.5 + age * 2));
            line(stack.last(), out, point, point.add(0, 0.4 + remaining, 0), glow ? 0.075F : 0.02F,
                    WHITE, envelope * (glow ? 22 : 175));
        }
    }

    private static Vec3 orbit(double radius, double angle, float tilt, float yaw) {
        return new Vec3(Math.cos(angle) * radius, 0, Math.sin(angle) * radius).xRot(tilt).yRot(yaw);
    }

    private static void star(PoseStack.Pose pose, VertexConsumer out, Vec3 point, double radius, int rgb, float alpha) {
        OmniRenderGeometry.orientedBox(pose, out, point, new Vec3(radius, 0, 0), new Vec3(0, radius, 0),
                new Vec3(0, 0, radius), OmniRenderGeometry.argb(rgb, Math.round(alpha)));
    }

    private static void line(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, float width, int rgb, float alpha) {
        Vec3 direction = b.subtract(a).normalize();
        if (direction.lengthSqr() < 0.5) return;
        Vec3 reference = Math.abs(direction.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 right = direction.cross(reference).normalize().scale(width);
        Vec3 up = direction.cross(right).normalize().scale(width);
        int color = OmniRenderGeometry.argb(rgb, Math.round(alpha));
        ribbon(pose, out, a, b, right, color);
        ribbon(pose, out, a, b, up, color);
    }

    // Two crossed, double-sided ribbons keep thin light trails visible without six-sided beam boxes.
    private static void ribbon(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b, Vec3 offset, int color) {
        Vec3 p = a.add(offset), q = a.subtract(offset), r = b.subtract(offset), s = b.add(offset);
        vertex(pose, out, p, color); vertex(pose, out, q, color); vertex(pose, out, r, color); vertex(pose, out, s, color);
        vertex(pose, out, s, color); vertex(pose, out, r, color); vertex(pose, out, q, color); vertex(pose, out, p, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer out, Vec3 point, int color) {
        out.vertex(pose.pose(), (float) point.x, (float) point.y, (float) point.z).color(color).endVertex();
    }

    private static double fraction(double value) { return value - Math.floor(value); }
}
