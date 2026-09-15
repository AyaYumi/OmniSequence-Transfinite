package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.FrostFeatherGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;
import static com.atir.molecularmanipulator.client.render.OmniRenderGeometry.argb;

/** Faceted seed, six open petals, three sigil rings and twelve light-feathers. */
final class FeatherResonanceEffects {
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final double[] RADII = {11.5, 14.1, 16.25};
    private static final double[] HEIGHTS = {-5.0, -2.5, -4.25};
    private static final double DIAL_Y = -4.95;
    private static final double BAND_INNER = 10.55;
    private static final double BAND_OUTER = 10.95;
    private static final double NUMERAL_RADIUS = 9.05;
    private static final double SECOND_HAND = 9.95;
    private static final double MINUTE_HAND = 7.15;
    private static final String[] NUMERALS = {
        "XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"
    };
    private static final int[] DEPTH_BANDS = {120, 152, 176, 196, 215, 235, 250};
    private static final int[] GLOW_BANDS = {13, 17, 21, 25, 28, 31, 34};

    private FeatherResonanceEffects() { }

    /** Ordinary alpha blending gives the gemstone readable colored facets, without white additive washout. */
    static void renderCoreSurface(PoseStack stack, VertexConsumer out, float angle, int core, int secondary) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(angle * 0.24F));
        Vec3 top = new Vec3(0, 3.4, 0), bottom = new Vec3(0, -3.4, 0);
        for (int side = 0; side < 6; side++) {
            Vec3 a = circle(2.35, 0, side * Math.TAU / 6), b = circle(2.35, 0, (side + 1) * Math.TAU / 6);
            face(stack.last(), out, top, a, b, b, argb(side % 2 == 0 ? core : secondary, 96));
            face(stack.last(), out, bottom, b, a, a, argb(side % 2 == 0 ? secondary : core, 76));
        }
        stack.popPose();
    }

    static void render(PoseStack stack, VertexConsumer out, float angle, float activity,
            float completion, boolean detailed, boolean glow, int field, int core,
            int primary, int secondary, int lattice) {
        render(stack, out, angle, activity, completion, detailed, glow, 0, field, core,
                primary, secondary, lattice);
    }

    /** {@code clockTicks} is real client time in ticks; it alone drives the one-second dial beat. */
    static void render(PoseStack stack, VertexConsumer out, float angle, float activity,
            float completion, boolean detailed, boolean glow, double clockTicks, int field, int core,
            int primary, int secondary, int lattice) {
        activity = Math.clamp(activity, 0, 1);
        completion = Math.clamp(completion, 0, 1);
        if (!detailed) { secondary = primary; lattice = primary; }
        crown(stack, out, angle, activity, detailed, glow, field, core, primary, secondary, lattice);
        rings(stack, out, angle, activity, detailed, glow, primary, secondary, lattice, clockTicks);
        feathers(stack, out, angle, activity, detailed, glow, field, primary, secondary, lattice);
        starAndShards(stack, out, angle, detailed, glow, field, primary, secondary, lattice);
        if (completion > 0) {
            double radius = 3.2 + (1 - completion) * 13.3;
            arc(stack.last(), out, radius, -2.6, 0, Math.TAU, detailed ? 128 : 64,
                    glow ? 0.13F : 0.055F, lattice, Math.round(completion * (glow ? 32 : 185)));
        }
    }

    private static void crown(PoseStack stack, VertexConsumer out, float angle, float activity,
            boolean detailed, boolean glow, int field, int core, int primary, int secondary, int lattice) {
        stack.pushPose();
        stack.mulPose(Axis.YP.rotationDegrees(angle * 0.24F));
        jewel(stack.last(), out, 2.35, 3.4, core, secondary, glow, false);
        stack.mulPose(Axis.YP.rotationDegrees(-angle * 0.61F));
        jewel(stack.last(), out, 0.82, 1.5, core, primary, glow, true);
        stack.popPose();
        int samples = detailed ? 12 : 7;
        for (int petal = 0; petal < 6; petal++) {
            double phase = petal * Math.TAU / 6 - angle * 0.0024;
            var tangent = new Vec3(-Math.sin(phase), 0, Math.cos(phase));
            for (int step = 0; step < samples; step++) {
                double t = step / (double) samples, next = (step + 1.0) / samples;
                Vec3 a = petalPoint(phase, t, activity), b = petalPoint(phase, next, activity);
                double wa = Math.pow(Math.sin(t * Math.PI), 0.8) * 0.92;
                double wb = Math.pow(Math.sin(next * Math.PI), 0.8) * 0.92;
                Vec3 l = a.add(tangent.scale(wa)), r = a.subtract(tangent.scale(wa));
                Vec3 ln = b.add(tangent.scale(wb)), rn = b.subtract(tangent.scale(wb));
                if (glow) face(stack.last(), out, l, ln, rn, r,
                        argb(step % 2 == 0 ? field : secondary, detailed ? 25 : 16));
                int color = petal % 2 == 0 ? primary : secondary;
                stroke(stack.last(), out, l, ln, glow ? 0.14F : 0.065F, color, glow ? 28 : 178);
                stroke(stack.last(), out, r, rn, glow ? 0.14F : 0.065F, color, glow ? 28 : 178);
                if (detailed) {
                    stroke(stack.last(), out, a, b, glow ? 0.065F : 0.024F, lattice, glow ? 18 : 126);
                    if (step % 3 == 1) {
                        stroke(stack.last(), out, a, ln, glow ? 0.06F : 0.022F, color, glow ? 17 : 112);
                        stroke(stack.last(), out, a, rn, glow ? 0.06F : 0.022F, color, glow ? 17 : 112);
                    }
                }
            }
        }
        for (int ring = 0; ring < (detailed ? 2 : 1); ring++) {
            double phase = angle * (ring == 0 ? 0.007 : -0.005);
            for (int sector = 0; sector < 6; sector++) {
                arc(stack.last(), out, 4.1 + ring * 0.6, -0.25 + ring * 0.65,
                        phase + sector * Math.TAU / 6, Math.PI / 4, detailed ? 10 : 6,
                        glow ? 0.085F : 0.035F, ring == 0 ? primary : secondary, glow ? 24 : 155);
            }
        }
    }

    static Vec3 petalPoint(double phase, double t, float activity) {
        double radius = 3.7 + 2.5 * Math.sin(t * Math.PI) - activity * (0.3 + t * 1.1);
        return circle(radius, -2.15 + t * 7.45 - activity * t * 0.35, phase);
    }

    private static void rings(PoseStack stack, VertexConsumer out, float angle, float activity,
            boolean detailed, boolean glow, int primary, int secondary, int lattice, double clockTicks) {
        var pose = stack.last();
        for (int ring = 0; ring < 3; ring++) {
            double radius = RADII[ring], y = HEIGHTS[ring];
            int color = ring == 1 ? secondary : primary;
            // Keep one fixed rail on the original exposed inner ring plane.
            if (ring == 0) arc(pose, out, radius, y, 0, Math.TAU, detailed ? 128 : 64,
                    glow ? 0.09F : 0.042F, color, glow ? 21 : 158);
            double spin = angle * (ring % 2 == 0 ? 0.0025 : -0.003);
            int sectors = detailed ? 12 : 6;
            for (int sector = 0; sector < sectors; sector++) {
                double phase = spin + sector * Math.TAU / sectors;
                arc(pose, out, radius + (ring == 0 ? 0.32 : 0), y, phase, Math.TAU / sectors * 0.72,
                        detailed ? 9 : 10, glow ? 0.115F : 0.052F, color, glow ? 26 : 176);
            }
            int motes = detailed ? 6 : 3;
            for (int mote = 0; mote < motes; mote++) {
                double phase = angle * (ring == 1 ? -0.014 : 0.012) + mote * Math.TAU / motes;
                for (int tail = 0; tail < 4; tail++) {
                    arc(pose, out, radius, y + 0.08, phase - tail * 0.027, 0.027, 1,
                            (glow ? 0.19F : 0.095F) * (1 - tail * 0.2F), lattice,
                            glow ? 32 - tail * 6 : 215 - tail * 32);
                }
            }
        }
        for (int sigil = 0; sigil < 12; sigil++) {
            double phase = sigil * Math.TAU / 12;
            var radial = circle(1, 0, phase);
            var tangent = new Vec3(-radial.z, 0, radial.x);
            var center = circle(14.1, -2.42, phase);
            double flash = Math.pow(Math.max(0, Math.cos(angle * 0.025 - sigil * Math.TAU / 12)), 6);
            int alpha = (int) (glow ? 23 + flash * 11 : 165 + flash * (35 + activity * 20));
            diamond(pose, out, center, radial.scale(0.65), tangent.scale(0.88),
                    glow ? 0.125F : 0.055F, lattice, alpha);
            if (detailed) diamond(pose, out, center, radial.scale(0.25), tangent.scale(0.35),
                    glow ? 0.075F : 0.025F, primary, glow ? 18 : 142);
        }
        if (detailed) {
            clock(pose, out, activity, glow, primary, secondary, lattice, clockTicks);
        }
    }

    /**
     * A classical dial for the controller's central ring: a railway band that fills one divider per
     * second, twelve Roman hour numerals, four-point ornaments between them, a stepped minute and
     * second hand, and a rosette at the hub.
     */
    private static void clock(PoseStack.Pose pose, VertexConsumer out, float activity,
            boolean glow, int primary, int secondary, int lattice, double clockTicks) {
        var state = clockState(clockTicks);
        double twelve = -Math.PI / 2;

        // Two fixed rails carry the band; the dividers between them are the seconds.
        for (double rail : new double[] {BAND_INNER, BAND_OUTER}) {
            arc(pose, out, rail, DIAL_Y, 0, Math.TAU, 64, glow ? 0.045F : 0.02F, primary,
                    dialAlpha(glow, 0.34));
        }
        for (int tick = 0; tick < 60; tick++) {
            double phase = twelve + tick * Math.TAU / 60;
            boolean leading = tick == state.secondOfMinute();
            double intensity = leading ? 0.78 + state.pulse() * 0.22 + activity * 0.1
                    : tick < state.secondOfMinute() ? 0.44 + activity * 0.1 : 0;
            stroke(pose, out, dialPoint(phase, BAND_INNER, 0, 0), dialPoint(phase, BAND_OUTER, 0, 0),
                    leading ? (glow ? 0.08F : 0.032F) : (glow ? 0.055F : 0.022F),
                    primary, dialAlpha(glow, intensity));
        }
        // Hour dividers cross both rails; the numerals and ornaments sit just inside them.
        for (int hour = 0; hour < 12; hour++) {
            double phase = twelve + hour * Math.TAU / 12;
            stroke(pose, out, dialPoint(phase, BAND_INNER - 0.38, 0, 0),
                    dialPoint(phase, BAND_OUTER + 0.30, 0, 0), glow ? 0.075F : 0.03F, secondary,
                    dialAlpha(glow, 0.52));
            numeral(pose, out, phase, NUMERAL_RADIUS, NUMERALS[hour], glow ? 0.075F : 0.032F,
                    lattice, dialAlpha(glow, 0.62 + activity * 0.16));
            ornament(pose, out, twelve + (hour + 0.5) * Math.TAU / 12, NUMERAL_RADIUS,
                    glow ? 0.05F : 0.019F, secondary, dialAlpha(glow, 0.42));
        }
        hand(pose, out, twelve + Math.toRadians(state.minuteDegrees()), MINUTE_HAND,
                glow ? 0.075F : 0.03F, primary, dialAlpha(glow, 0.72));
        hand(pose, out, twelve + Math.toRadians(state.secondDegrees()), SECOND_HAND,
                glow ? 0.085F : 0.034F, lattice,
                dialAlpha(glow, 0.7 + state.pulse() * 0.3 + activity * 0.1));
        hub(pose, out, glow, primary, secondary);
    }

    /** A tapered hand with a spade shoulder and a counterweight tail, drawn as line art. */
    private static void hand(PoseStack.Pose pose, VertexConsumer out, double theta, double length,
            float width, int color, int alpha) {
        double shoulder = length * 0.62, base = length * 0.30, tail = -length * 0.30;
        var tip = dialPoint(theta, 0, 0, length);
        var leftShoulder = dialPoint(theta, 0, -0.11, shoulder);
        var rightShoulder = dialPoint(theta, 0, 0.11, shoulder);
        var leftSpade = dialPoint(theta, 0, -0.30, base);
        var rightSpade = dialPoint(theta, 0, 0.30, base);
        var leftNeck = dialPoint(theta, 0, -0.15, 0.24);
        var rightNeck = dialPoint(theta, 0, 0.15, 0.24);
        var counter = dialPoint(theta, 0, 0, tail);
        stroke(pose, out, tip, leftShoulder, width, color, alpha);
        stroke(pose, out, tip, rightShoulder, width, color, alpha);
        stroke(pose, out, leftShoulder, leftSpade, width, color, alpha);
        stroke(pose, out, rightShoulder, rightSpade, width, color, alpha);
        stroke(pose, out, leftSpade, leftNeck, width, color, alpha);
        stroke(pose, out, rightSpade, rightNeck, width, color, alpha);
        stroke(pose, out, leftNeck, counter, width, color, alpha);
        stroke(pose, out, rightNeck, counter, width, color, alpha);
        stroke(pose, out, dialPoint(theta, 0, 0, 0.30), tip, width * 0.45F, color, alpha);
    }

    /** Roman numerals built from I, V and X strokes, each rotated to face away from the hub. */
    private static void numeral(PoseStack.Pose pose, VertexConsumer out, double phase, double radius,
            String text, float width, int color, int alpha) {
        double total = 0;
        for (int i = 0; i < text.length(); i++) total += numeralAdvance(text.charAt(i));
        double cursor = -total / 2;
        for (int i = 0; i < text.length(); i++) {
            char glyph = text.charAt(i);
            double centre = cursor + numeralAdvance(glyph) / 2;
            switch (glyph) {
                case 'I' -> stroke(pose, out, dialPoint(phase, radius, centre, -0.7),
                        dialPoint(phase, radius, centre, 0.7), width, color, alpha);
                case 'V' -> {
                    stroke(pose, out, dialPoint(phase, radius, centre - 0.28, 0.7),
                            dialPoint(phase, radius, centre, -0.7), width, color, alpha);
                    stroke(pose, out, dialPoint(phase, radius, centre + 0.28, 0.7),
                            dialPoint(phase, radius, centre, -0.7), width, color, alpha);
                }
                case 'X' -> {
                    stroke(pose, out, dialPoint(phase, radius, centre - 0.28, 0.7),
                            dialPoint(phase, radius, centre + 0.28, -0.7), width, color, alpha);
                    stroke(pose, out, dialPoint(phase, radius, centre + 0.28, 0.7),
                            dialPoint(phase, radius, centre - 0.28, -0.7), width, color, alpha);
                }
                default -> { }
            }
            cursor += numeralAdvance(glyph);
        }
    }

    private static double numeralAdvance(char glyph) {
        return glyph == 'I' ? 0.34 : 0.66;
    }

    /** The small four-point star that sits between two hour numerals. */
    private static void ornament(PoseStack.Pose pose, VertexConsumer out, double phase, double radius,
            float width, int color, int alpha) {
        var center = dialPoint(phase, radius, 0, 0);
        for (int spoke = 0; spoke < 4; spoke++) {
            double angle = spoke * Math.TAU / 4;
            stroke(pose, out, center,
                    dialPoint(phase, radius, Math.cos(angle) * 0.26, Math.sin(angle) * 0.26),
                    width, color, alpha);
        }
    }

    private static void hub(PoseStack.Pose pose, VertexConsumer out, boolean glow, int primary, int secondary) {
        arc(pose, out, 0.62, DIAL_Y, 0, Math.TAU, 20, glow ? 0.05F : 0.021F, primary, dialAlpha(glow, 0.56));
        arc(pose, out, 0.28, DIAL_Y, 0, Math.TAU, 12, glow ? 0.05F : 0.021F, secondary, dialAlpha(glow, 0.46));
        for (int spoke = 0; spoke < 4; spoke++) {
            double angle = spoke * Math.TAU / 4 + Math.PI / 4;
            stroke(pose, out, dialPoint(angle, 0.28, 0, 0), dialPoint(angle, 0.62, 0, 0),
                    glow ? 0.05F : 0.021F, primary, dialAlpha(glow, 0.5));
        }
    }

    /** Places a point in the dial plane using a radial frame: {@code along} is outward. */
    private static Vec3 dialPoint(double phase, double radius, double lateral, double along) {
        double r = radius + along;
        return new Vec3(Math.cos(phase) * r - Math.sin(phase) * lateral, DIAL_Y,
                Math.sin(phase) * r + Math.cos(phase) * lateral);
    }

    // The rail regression selects vertices by alpha 158 above radius 10, so the bands straddle that
    // value instead of ever landing on it. 3D radius is larger than the dial radius, so the whole
    // face is inside the window that check watches.
    private static int dialAlpha(boolean glow, double intensity) {
        int band = (int) Math.round(Math.clamp(intensity, 0, 1) * (DEPTH_BANDS.length - 1));
        return glow ? GLOW_BANDS[band] : DEPTH_BANDS[band];
    }

    /**
     * Discrete one-second beat for the dial. The seconds index steps by one per 20 ticks and wraps
     * each minute, while the pulse and the second hand's brief overshoot decay inside the second.
     * Stepping rather than sweeping is what gives the ring its clock-like stutter.
     */
    record ClockState(int secondOfMinute, int minuteOfHour, double pulse,
            double secondDegrees, double minuteDegrees) { }

    static ClockState clockState(double clockTicks) {
        if (!Double.isFinite(clockTicks)) return new ClockState(0, 0, 0, 0, 0);
        double seconds = Math.max(0, clockTicks) / 20.0;
        long whole = (long) Math.floor(seconds);
        double fraction = seconds - whole;
        int second = (int) Math.floorMod(whole, 60L);
        int minute = (int) Math.floorMod(whole / 60L, 60L);
        return new ClockState(second, minute, Math.pow(1 - fraction, 5),
                second * 6.0 + 1.7 * Math.pow(1 - fraction, 12), minute * 6.0 + second * 0.1);
    }

    private static void feathers(PoseStack stack, VertexConsumer out, float angle, float activity,
            boolean detailed, boolean glow, int field, int primary, int secondary, int lattice) {
        int quadrant = 0;
        for (int sx : new int[] {-1, 1}) for (int sz : new int[] {-1, 1}) {
            int index = 0;
            for (var feather : FrostFeatherGeometry.feathers()) {
                double phase = Math.toRadians(feather.degrees());
                Vec3 tangent = new Vec3(-Math.sin(phase) * sx, 0.16, Math.cos(phase) * sz).normalize();
                boolean lightFeather = index >= 1 && index <= 3;
                int segments = detailed ? 12 : 7;
                int color = index % 2 == 0 ? primary : secondary;
                for (int step = 0; step < segments; step++) {
                    double t = step / (double) segments, next = (step + 1.0) / segments;
                    Vec3 a = featherPoint(feather, t, sx, sz), b = featherPoint(feather, next, sx, sz);
                    stroke(stack.last(), out, a, b, glow ? 0.07F : 0.027F, color, glow ? 19 : 130);
                    if (lightFeather) {
                        double wa = Math.pow(Math.sin(t * Math.PI), 0.8) * 0.88;
                        double wb = Math.pow(Math.sin(next * Math.PI), 0.8) * 0.88;
                        Vec3 l = a.add(tangent.scale(wa)), r = a.subtract(tangent.scale(wa));
                        Vec3 ln = b.add(tangent.scale(wb)), rn = b.subtract(tangent.scale(wb));
                        if (glow) face(stack.last(), out, l, ln, rn, r, argb(field, 24));
                        stroke(stack.last(), out, l, ln, glow ? 0.14F : 0.06F, color, glow ? 27 : 175);
                        stroke(stack.last(), out, r, rn, glow ? 0.14F : 0.06F, color, glow ? 27 : 175);
                        if (detailed && step % 2 == 0) {
                            stroke(stack.last(), out, a, ln, glow ? 0.06F : 0.022F, lattice, glow ? 20 : 139);
                            stroke(stack.last(), out, a, rn, glow ? 0.06F : 0.022F, lattice, glow ? 20 : 139);
                        }
                    }
                }
                double cycle = angle * 0.006 - index * 0.13 + quadrant * 0.23;
                double head = cycle - Math.floor(cycle);
                // Work gathers light toward the roots; idle feathers light outward.
                double direction = activity > 0.5 ? -1 : 1;
                if (direction < 0) head = 1 - head;
                for (int tail = 0; tail < 5; tail++) {
                    double a = head - direction * tail * 0.025, b = head - direction * (tail + 1) * 0.025;
                    if (a < 0 || a > 1 || b < 0 || b > 1) continue;
                    stroke(stack.last(), out, featherPoint(feather, a, sx, sz), featherPoint(feather, b, sx, sz),
                            (glow ? 0.16F : 0.08F) * (1 - tail * 0.14F), lattice,
                            glow ? 34 - tail * 5 : 220 - tail * 27);
                }
                Vec3 tip = featherPoint(feather, 1, sx, sz);
                diamond(stack.last(), out, tip, tangent.scale(0.30), UP.scale(0.48),
                        glow ? 0.09F : 0.036F, lattice, glow ? 25 : 180);
                index++;
            }
            quadrant++;
        }
    }

    static Vec3 featherPoint(FrostFeatherGeometry.Feather feather, double t, int sx, int sz) {
        var point = feather.point(t);
        return new Vec3(point.x * sx, point.y + 1.7 - FrostFeatherGeometry.CORE_Y, point.z * sz);
    }

    private static void starAndShards(PoseStack stack, VertexConsumer out, float angle,
            boolean detailed, boolean glow, int field, int primary, int secondary, int lattice) {
        stack.pushPose();
        stack.translate(0, 10, 0);
        stack.mulPose(Axis.YP.rotationDegrees(angle * 0.16F));
        diamond(stack.last(), out, Vec3.ZERO, new Vec3(4.3, 0, 0), new Vec3(0, 4.3, 0),
                glow ? 0.085F : 0.031F, secondary, glow ? 23 : 147);
        diamond(stack.last(), out, Vec3.ZERO, new Vec3(0, 0, 4.3), new Vec3(0, 4.3, 0),
                glow ? 0.085F : 0.031F, primary, glow ? 23 : 147);
        if (detailed) arc(stack.last(), out, 3.9, 0, 0, Math.TAU, 64,
                glow ? 0.05F : 0.018F, lattice, glow ? 14 : 102);
        stack.popPose();
        for (int shard = 0, count = detailed ? 8 : 3; shard < count; shard++) {
            double phase = shard * Math.TAU / count + angle * 0.0018;
            Vec3 point = circle(7.6 + Math.sin(shard * 2.3), 0.4 + Math.sin(phase * 2 + shard) * 3.1, phase);
            stack.pushPose();
            stack.translate(point.x, point.y, point.z);
            stack.mulPose(Axis.YP.rotationDegrees(angle * 0.55F + shard * 37));
            stack.mulPose(Axis.ZP.rotationDegrees(18));
            jewel(stack.last(), out, 0.17 + shard % 3 * 0.05, 0.38 + shard % 3 * 0.09,
                    lattice, glow ? field : primary, glow, false);
            stack.popPose();
        }
    }

    private static void jewel(PoseStack.Pose pose, VertexConsumer out, double radius, double height,
            int color, int accent, boolean glow, boolean inner) {
        Vec3 top = new Vec3(0, height, 0), bottom = new Vec3(0, -height, 0);
        int sides = inner ? 4 : 6;
        for (int side = 0; side < sides; side++) {
            Vec3 a = circle(radius, 0, side * Math.TAU / sides), b = circle(radius, 0, (side + 1) * Math.TAU / sides);
            if ((glow && radius < 1) || inner) {
                int alpha = glow ? (inner ? 18 : 24) : 158;
                face(pose, out, top, a, b, b, argb(side % 2 == 0 ? color : accent, alpha));
                face(pose, out, bottom, b, a, a, argb(side % 2 == 0 ? accent : color, alpha - (glow ? 7 : 28)));
            }
            float width = (float) Math.min(glow ? 0.11 : 0.045, radius * 0.14);
            stroke(pose, out, top, a, width, color, glow ? 28 : 186);
            stroke(pose, out, bottom, a, width, accent, glow ? 25 : 169);
            stroke(pose, out, a, b, width * 0.65F, color, glow ? 22 : 150);
        }
    }

    private static Vec3 circle(double radius, double y, double phase) {
        return new Vec3(Math.cos(phase) * radius, y, Math.sin(phase) * radius);
    }

    private static void arc(PoseStack.Pose pose, VertexConsumer out, double radius, double y,
            double start, double sweep, int segments, float width, int color, int alpha) {
        for (int step = 0; step < segments; step++) stroke(pose, out,
                circle(radius, y, start + sweep * step / segments),
                circle(radius, y, start + sweep * (step + 1) / segments), width, color, alpha);
    }

    private static void diamond(PoseStack.Pose pose, VertexConsumer out, Vec3 center,
            Vec3 first, Vec3 second, float width, int color, int alpha) {
        Vec3 a = center.add(first), b = center.add(second), c = center.subtract(first), d = center.subtract(second);
        stroke(pose, out, a, b, width, color, alpha); stroke(pose, out, b, c, width, color, alpha);
        stroke(pose, out, c, d, width, color, alpha); stroke(pose, out, d, a, width, color, alpha);
    }

    private static void stroke(PoseStack.Pose pose, VertexConsumer out, Vec3 a, Vec3 b,
            float width, int color, int alpha) {
        Vec3 direction = b.subtract(a).normalize();
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
        out.addVertex(pose, (float) point.x, (float) point.y, (float) point.z).setColor(color);
    }
}
