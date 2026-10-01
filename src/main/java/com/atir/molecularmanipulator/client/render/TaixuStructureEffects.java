package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.blockentity.TaixuMotionGeometry;
import com.atir.molecularmanipulator.blockentity.TaixuStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

/** Tianyi ceremony in core-local coordinates; every pass obeys scene depth. */
public final class TaixuStructureEffects {
    private static final double TAU = Math.PI * 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final int ICE = 0x78DFFF, GOLD = 0xFFE3A0, WHITE = 0xE8FBFF;
    private static final double[] RADII = {30, 48, 32};
    private static final double[] HEIGHTS = {-27.3, .7, 32.7};
    private static final double[] PERIODS = {600, 800, 1200};
    private static final TaixuMotionGeometry.Pose STILL = new TaixuMotionGeometry.Pose(0, 0);

    public record Frame(float time, float age, float power) {
        public static Frame steady(float time) { return new Frame(time, 160, 1); }
    }

    private final PoseStack poses;
    private final VertexConsumer out;
    private final Frame frame;
    private final float time;
    private final boolean detailed;
    private double gain;
    private boolean suspended;

    private TaixuStructureEffects(PoseStack poses, VertexConsumer out, Frame frame, boolean detailed) {
        this.poses = poses;
        this.out = out;
        this.frame = frame;
        this.time = frame.time() % 24000;
        this.detailed = detailed;
        this.gain = frame.power();
    }

    public static void render(PoseStack poses, VertexConsumer out, float time, boolean detailed,
                              TaixuMotionGeometry.Pose[] motion) {
        render(poses, out, Frame.steady(time), detailed, motion);
    }

    public static void render(PoseStack poses, VertexConsumer out, Frame frame, boolean detailed,
                              TaixuMotionGeometry.Pose[] motion) {
        new TaixuStructureEffects(poses, out, frame, detailed).ceremony(motion);
    }

    public static void renderCrystal(PoseStack poses, VertexConsumer out, Frame frame, boolean detailed) {
        new TaixuStructureEffects(poses, out, frame, detailed).crystals(false);
    }
    public static void render(PoseStack poses, VertexConsumer out, Frame frame, boolean detailed,
                              TaixuMotionGeometry.Pose[] motion, boolean suspended) {
        var effect = new TaixuStructureEffects(poses, out, frame, detailed);
        effect.suspended = suspended; effect.ceremony(motion);
    }
    public static void renderCrystal(PoseStack poses, VertexConsumer out, Frame frame, boolean detailed,
                                     TaixuMotionGeometry.Pose[] motion, boolean suspended) {
        var effect = new TaixuStructureEffects(poses, out, frame, detailed);
        if (suspended) effect.suspendedCrystals(false, motion); else effect.crystals(false);
    }

    private double opening(double start, double duration) {
        double t = Math.clamp((frame.age() - start) / duration, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private void ceremony(TaixuMotionGeometry.Pose[] motion) {
        gain = frame.power() * (.15 + .85 * opening(65, 75));
        axis();
        if (suspended) { poses.pushPose(); poses.translate(0, 12.5, 0); astrolabe(); poses.popPose(); suspendedCrystals(true, motion); }
        else { astrolabe(); crystals(true); }
        for (int ring = 0; ring < 3; ring++) {
            gain = frame.power() * (.18 + .82 * opening(30 + ring * 20, 40));
            var part = pose(motion, ring);
            poses.pushPose();
            poses.translate(0, HEIGHTS[ring] + part.lift(), 0);
            poses.mulPose(Axis.YP.rotationDegrees((float) -part.angle()));
            orbit(RADII[ring], ring);
            poses.popPose();
        }
        for (int tower = 0; tower < 8; tower++) {
            gain = frame.power() * (.12 + .88 * opening(tower * 4, 40));
            double lift = pose(motion, tower + 3).lift();
            double x = TaixuMotionGeometry.towerX(tower), z = TaixuMotionGeometry.towerZ(tower);
            double top = (tower % 2 == 0 ? 105 : 92) - 64.5 + lift;
            double charge = towerCharge(tower);
            poses.pushPose();
            poses.translate(x, top + 1.5, z);
            poses.mulPose(Axis.YP.rotationDegrees(time * (tower % 2 == 0 ? .045F : -.045F)));
            seal(5.0, charge);
            OmniRenderGeometry.taperedBeam(poses.last(), out, new Vec3(0, .1, 0), new Vec3(0, 7, 0),
                    (float) (.22 + charge * .16), .025F, (float) (.22 + charge * .16), .025F,
                    color(WHITE, 125 + charge * 75), color(ICE, 0));
            poses.popPose();
            arc(new Vec3(x, -41.7 + lift, z), 2.8, 0, TAU,
                    detailed ? 32 : 16, .10F, color(ICE, 105 + charge * 70), false, true);
            towerVeins(x, z, -40.7 + lift, top, tower);
            if (detailed && !suspended) conduit(new Vec3(x, top + 1.6, z), tower);
        }
    }

    private static TaixuMotionGeometry.Pose pose(TaixuMotionGeometry.Pose[] motion, int group) {
        return motion != null && group < motion.length && motion[group] != null ? motion[group] : STILL;
    }

    private void axis() {
        var bottom = new Vec3(0, -44, 0);
        var top = new Vec3(0, 67, 0);
        OmniRenderGeometry.beam(poses.last(), out, bottom, top, .095F, .095F, color(WHITE, 190));
        OmniRenderGeometry.beam(poses.last(), out, bottom, top, .34F, .34F, color(ICE, 35));
        if (suspended) {
            for (double y : new double[]{18, 38, 55, 91, 111, 121})
                arc(new Vec3(0, y - 64.5, 0), y == 91 ? 10 : y == 55 ? 9 : 5.5,
                        0, TAU, detailed ? 48 : 24, .10F, color(ICE, 100), false, true);
            return;
        }
        int strands = detailed ? 3 : 2, steps = detailed ? 72 : 28;
        for (int strand = 0; strand < strands; strand++) {
            for (int i = 0; i < steps; i++) {
                double t0 = i / (double) steps, t1 = (i + 1D) / steps;
                var a = helix(t0, strand);
                var b = helix(t1, strand);
                int rgb = strand == 1 ? GOLD : ICE;
                line(a, b, .13F, color(rgb, 130));
                if (detailed) ribbon(a, b,
                        new Vec3(a.x, 0, a.z), .85F, .85F, color(rgb, 16), color(rgb, 16));
            }
        }
        for (double y : new double[]{-43.5, 56.5}) {
            poses.pushPose();
            poses.translate(0, y, 0);
            poses.mulPose(Axis.YP.rotationDegrees(-time * .06F));
            seal(7.5, 0);
            poses.popPose();
        }
        int motes = detailed ? 20 : 6;
        for (int i = 0; i < motes; i++) {
            double progress = (time / 240D + i / (double) motes) % 1;
            double a = i * TAU / motes + time * TAU / 800;
            double radius = 8.8 + 1.2 * Math.sin(progress * TAU);
            var p = new Vec3(Math.cos(a) * radius, -43 + progress * 105, Math.sin(a) * radius);
            OmniRenderGeometry.octahedron(poses.last(), out, p, .18F, .65F, .18F,
                    time * .18F, color(i % 3 == 0 ? GOLD : WHITE, 150));
        }
    }

    private Vec3 helix(double t, int strand) {
        double angle = t * TAU * 2.5 + strand * TAU / 3 - time * TAU / 400;
        double radius = 2.5 + Math.pow(Math.sin(t * Math.PI), 2) * 6.5;
        return new Vec3(Math.cos(angle) * radius, -43 + t * 106, Math.sin(angle) * radius);
    }

    private void astrolabe() {
        double unfold = .4 + .6 * opening(85, 65);
        double resonance = Math.pow((Math.cos(time * TAU / 60) + 1) * .5, 5) * frame.power();
        poses.pushPose();
        poses.scale((float) unfold, (float) unfold, (float) unfold);
        poses.mulPose(Axis.YP.rotationDegrees(time * .09F));
        if (!suspended) cage(7.0, 11.2, 7.0, 0, .075F, color(GOLD, 145));
        poses.popPose();
        int tracks = detailed ? 3 : 2;
        for (int track = 0; track < tracks; track++) {
            poses.pushPose();
            poses.scale((float) unfold, (float) unfold, (float) unfold);
            poses.mulPose(Axis.YP.rotationDegrees(time * .09F + track * 60));
            poses.mulPose(Axis.XP.rotationDegrees(52 + track * 28));
            double radius = 10.2 + track * 2.0;
            int rgb = track == 1 ? GOLD : ICE;
            arc(Vec3.ZERO, radius, 0, TAU, detailed ? 72 : 32, .15F, color(rgb, 155), false, true);
            if (detailed) {
                arc(Vec3.ZERO, radius, 0, TAU, 72, .75F, color(rgb, 13), false, false);
                for (int mark = 0; mark < 24; mark++) {
                    double a = mark * TAU / 24;
                    line(circle(radius - .35, a, 0), circle(radius + (mark % 3 == 0 ? .65 : .25), a, 0),
                            .085F, color(GOLD, 155));
                }
            }
            for (int i = 0; i < (detailed ? 3 : 1); i++) {
                double a = time * TAU / (track == 1 ? -400 : 600) + i * TAU / 3;
                arc(Vec3.ZERO, radius, a - .62, .62, detailed ? 14 : 8,
                        .26F, color(WHITE, 180), true, true);
                OmniRenderGeometry.octahedron(poses.last(), out, circle(radius, a, 0),
                        .28F, .45F, .28F, time * .18F, color(WHITE, 190));
            }
            poses.popPose();
        }
        if (detailed) {
            // Wave envelopes vanish at their cycle boundaries.
            for (int i = 0; i < 3; i++) {
                double t = (time / 200D + i / 3D) % 1;
                double alpha = Math.pow(Math.sin(Math.PI * t), 2) * (18 + resonance * 22);
                arc(Vec3.ZERO, 7 + t * 11, 0, TAU, 64, .20F, color(GOLD, alpha), false, true);
            }
        }
    }

    private void crystals(boolean glow) {
        double emerge = opening(85, 65);
        gain = frame.power() * (.08 + .92 * emerge);
        double pulse = 1 + .035 * Math.sin(time * TAU / 120);
        poses.pushPose();
        poses.mulPose(Axis.YP.rotationDegrees(time * .12F));
        float size = (float) ((.35 + .65 * emerge) * pulse);
        poses.scale(size, size, size);
        for (int i = 0; i < 8; i++) {
            double a = i * TAU / 8;
            double bob = .4 * Math.sin(time * TAU / 240 + a);
            poses.pushPose();
            poses.translate(Math.cos(a) * 4.2, bob, Math.sin(a) * 4.2);
            poses.mulPose(Axis.YP.rotationDegrees((float) (-i * 45 + 90)));
            poses.mulPose(Axis.ZP.rotationDegrees(i % 2 == 0 ? -12 : 12));
            if (glow) {
                cage(.85, i % 2 == 0 ? 5.8 : 4.6, .65, 0, .055F,
                        color(i % 2 == 0 ? ICE : GOLD, 160));
            } else {
                crystal(.85, i % 2 == 0 ? 5.8 : 4.6, .65, i % 2 == 0);
            }
            poses.popPose();
        }
        for (int sign : new int[]{-1, 1}) {
            poses.pushPose();
            poses.translate(0, sign * 8.0, 0);
            if (glow) cage(1.25, 2.4, 1.25, -time * .18F, .07F, color(GOLD, 190));
            else {
                poses.mulPose(Axis.YP.rotationDegrees(-time * .18F));
                crystal(1.25, 2.4, 1.25, true);
            }
            poses.popPose();
        }
        if (detailed) for (int i = 0; i < 12; i++) {
            double a = i * TAU / 12 - time * TAU / 1200;
            poses.pushPose();
            poses.translate(Math.cos(a) * 7.8, 2.3 * Math.sin(a * 2 + time * TAU / 400), Math.sin(a) * 7.8);
            poses.mulPose(Axis.YP.rotationDegrees((float) (-Math.toDegrees(a))));
            poses.mulPose(Axis.ZP.rotationDegrees(32));
            if (glow) cage(.27, .85, .27, 0, .035F, color(WHITE, 125));
            else crystal(.27, .85, .27, i % 3 != 0);
            poses.popPose();
        }
        poses.popPose();
    }

    private void suspendedCrystals(boolean glow, TaixuMotionGeometry.Pose[] motion) {
        double emerge = .45 + .55 * opening(65, 75);
        gain = Math.max(.35, frame.power()) * (.35 + .65 * opening(65, 75));
        poses.pushPose(); poses.translate(0, 12.5, 0);
        poses.scale((float)emerge, (float)emerge, (float)emerge);
        armillaryHeart(glow);
        poses.popPose();
        gem(0, -20.5, 0, 2.5, 7, -time * .05, glow);
        gem(0, 48.5, 0, 2.2, 6, time * .05, glow);
        gem(0, 61.5, 0, 1.7, 5, -time * .06, glow);
        for (int i = 0; i < 4; i++) {
            double a = i * TAU / 4 + time * TAU / 1600;
            poses.pushPose();poses.translate(9.5 * Math.cos(a), 12.5 + Math.sin(a * 2) * .65, 9.5 * Math.sin(a));
            if(glow) arc(Vec3.ZERO, .9, 0, TAU, 16, .06F, color(GOLD,145), false, true);
            else energySphere(.8,12,6);
            poses.popPose();
        }
        for (int tower = 0; tower < 8; tower++) {
            double x = TaixuMotionGeometry.towerX(tower), z = TaixuMotionGeometry.towerZ(tower);
            double lift = pose(motion, tower + 3).lift();
            gain = Math.max(.35, frame.power()) * (.35 + .65 * opening(tower * 4, 40));
            for (int y : TaixuStructure.crystalChambers(tower))
                gem(x, y - 64.5 + lift, z, 1.7, 4.3, time * .07 + tower * 45, glow);
            int top = tower % 2 == 0 ? 105 : 92;
            gem(x, top - 65.5 + lift, z, 1.25, 3.4, -time * .07, glow);
            if (glow) OmniRenderGeometry.beam(poses.last(), out, new Vec3(x, 24-64.5+lift, z),
                    new Vec3(x, top-64.5+lift, z), .10F, .10F, color(ICE, 140));
        }
    }
    private void gem(double x, double y, double z, double rx, double ry, double yaw, boolean glow) {
        poses.pushPose(); poses.translate(x, y, z); poses.mulPose(Axis.YP.rotationDegrees((float)yaw));
        if (glow) cage(rx, ry, rx, 0, .045F, color(ICE, 115));
        else crystal(rx, ry, rx, true);
        poses.popPose();
    }

    /** Rounded energy heart with separated porcelain shell petals; no central diamond silhouette. */
    private void armillaryHeart(boolean glow) {
        poses.pushPose();poses.mulPose(Axis.YP.rotationDegrees(time*.035F));
        poses.mulPose(Axis.ZP.rotationDegrees(18));
        if(!glow) energySphere(4.9,detailed?32:20,detailed?16:10);
        else {
            for(int i=0;i<3;i++){
                poses.pushPose();poses.mulPose(Axis.XP.rotationDegrees(28+i*58));
                arc(Vec3.ZERO,5.06,0,TAU,detailed?64:32,.07F,color(ICE,115),false,true);
                poses.popPose();
            }
        }
        int steps=detailed?10:6;
        for(int petal=0;petal<4;petal++)for(int sign:new int[]{-1,1}){
            double center=petal*TAU/4;
            double a0=center-.38,a1=center+.38;
            double p0=sign*.28,p1=sign*1.10;
            if(glow){
                for(int j=0;j<steps;j++){
                    double p=p0+(p1-p0)*j/steps,q=p0+(p1-p0)*(j+1)/steps;
                    line(spherePoint(7.25,a0,p),spherePoint(7.25,a0,q),.055F,color(GOLD,170));
                    line(spherePoint(7.25,a1,p),spherePoint(7.25,a1,q),.055F,color(GOLD,170));
                    double a=a0+(a1-a0)*j/steps,b=a0+(a1-a0)*(j+1)/steps;
                    line(spherePoint(7.25,a,p0),spherePoint(7.25,b,p0),.08F,color(GOLD,170));
                }
            }else for(int j=0;j<steps;j++)for(int k=0;k<4;k++){
                double a=a0+(a1-a0)*k/4,b=a0+(a1-a0)*(k+1)/4;
                double p=p0+(p1-p0)*j/steps,q=p0+(p1-p0)*(j+1)/steps;
                shellVertex(spherePoint(7.2,a,p));shellVertex(spherePoint(7.2,b,p));
                shellVertex(spherePoint(7.2,b,q));shellVertex(spherePoint(7.2,a,q));
            }
        }
        if(glow)arc(Vec3.ZERO,7.7,0,TAU,detailed?64:32,.13F,color(GOLD,180),false,true);
        poses.popPose();
    }
    private static Vec3 spherePoint(double r,double longitude,double latitude){
        return new Vec3(r*Math.cos(latitude)*Math.cos(longitude),r*Math.sin(latitude),r*Math.cos(latitude)*Math.sin(longitude));
    }
    private void energySphere(double radius,int columns,int rows){
        for(int y=0;y<rows;y++)for(int x=0;x<columns;x++){
            double a=x*TAU/columns,b=(x+1)*TAU/columns,p=-Math.PI/2+y*Math.PI/rows,q=-Math.PI/2+(y+1)*Math.PI/rows;
            energyVertex(spherePoint(radius,a,p));energyVertex(spherePoint(radius,b,p));
            energyVertex(spherePoint(radius,b,q));energyVertex(spherePoint(radius,a,q));
        }
    }
    private void energyVertex(Vec3 v){
        var n=v.normalize();double lit=Math.clamp(.55+.32*n.y-.22*n.x+.18*n.z,0,1);
        int r=(int)(35+115*lit),g=(int)(109+125*lit),b=(int)(139+110*lit);
        out.addVertex(poses.last().pose(),(float)v.x,(float)v.y,(float)v.z).setColor(color(r<<16|g<<8|b,235));
    }
    private void shellVertex(Vec3 v){
        var n=v.normalize();double lit=Math.clamp(.66+.3*n.y-.2*n.x+.12*n.z,.25,1);
        int r=(int)(95+142*lit),g=(int)(119+123*lit),b=(int)(133+111*lit);
        out.addVertex(poses.last().pose(),(float)v.x,(float)v.y,(float)v.z).setColor(color(r<<16|g<<8|b,248));
    }

    private void crystal(double rx, double ry, double rz, boolean cyan) {
        var top = new Vec3(0, ry, 0);
        var bottom = new Vec3(0, -ry, 0);
        Vec3[] belt = {new Vec3(rx, 0, 0), new Vec3(0, .35, rz), new Vec3(-rx, 0, 0), new Vec3(0, -.35, -rz)};
        int[] colors = cyan ? new int[]{0x6DBFCC, 0x2F7888, 0xA1DED9, 0x234C60}
                : new int[]{0xD2B975, 0x7A765B, 0xEEE1B3, 0x555749};
        for (int face = 0; face < 4; face++) {
            triangle(top, belt[face], belt[(face + 1) % 4], color(colors[face], 190));
            triangle(bottom, belt[(face + 1) % 4], belt[face], color(colors[(face + 1) % 4], 175));
        }
    }

    private void cage(double rx, double ry, double rz, double yaw, float width, int color) {
        double a = Math.toRadians(yaw);
        var east = new Vec3(Math.cos(a) * rx, 0, Math.sin(a) * rz);
        var north = new Vec3(-Math.sin(a) * rx, 0, Math.cos(a) * rz);
        Vec3[] belt = {east, north, east.scale(-1), north.scale(-1)};
        for (int i = 0; i < 4; i++) {
            line(new Vec3(0, ry, 0), belt[i], width, color);
            line(new Vec3(0, -ry, 0), belt[i], width, color);
            line(belt[i], belt[(i + 1) % 4], width, color);
        }
    }

    private void triangle(Vec3 a, Vec3 b, Vec3 c, int color) {
        out.addVertex(poses.last().pose(), (float) a.x, (float) a.y, (float) a.z).setColor(color);
        out.addVertex(poses.last().pose(), (float) b.x, (float) b.y, (float) b.z).setColor(color);
        out.addVertex(poses.last().pose(), (float) c.x, (float) c.y, (float) c.z).setColor(color);
        out.addVertex(poses.last().pose(), (float) c.x, (float) c.y, (float) c.z).setColor(color);
    }

    private void orbit(double radius, int ring) {
        int segments = detailed ? 80 : 36;
        arc(Vec3.ZERO, radius + 1.2, 0, TAU, segments, .15F, color(GOLD, 135), false, true);
        arc(Vec3.ZERO, radius - 3.8, 0, TAU, segments, .13F, color(ICE, 150), false, true);
        if (detailed) {
            arc(Vec3.ZERO, radius + 1.2, 0, TAU, segments, .8F, color(GOLD, 12), false, false);
            for (int petal = 0; petal < 8; petal++) {
                double start = petal * TAU / 8 + .12;
                arc(new Vec3(0, -.25, 0), radius + 3.2, start, .5, 8, .14F, color(GOLD, 115), false, true);
                for (int step = 0; step < 6; step++) {
                    double a = start + step * .5 / 6, b = start + (step + 1) * .5 / 6;
                    double fade = Math.sin(Math.PI * (step + .5) / 6);
                    var p = circle(radius + 1.2, a, -1.1);
                    var q = circle(radius + 1.2, b, -1.1);
                    ribbon(p, q, new Vec3(p.x, 0, p.z),
                            1.2F, 1.2F, color(ICE, 22 * fade), color(ICE, 22 * fade));
                }
            }
        }
        int marks = detailed ? 32 : 16;
        for (int i = 0; i < marks; i++) {
            double a = i * TAU / marks;
            double scan = .5 + .5 * Math.cos(a - time * TAU / PERIODS[ring]);
            line(circle(radius - 4.5, a, 0), circle(radius - 3.9, a, 0), .09F, color(GOLD, 150));
            if (detailed || i % 2 == 0) glyph(radius - 1.35, a, i + ring, 1.1, color(WHITE, 85 + scan * 95));
        }
        int comets = detailed ? 4 : 2;
        double direction = ring == 1 ? -1 : 1;
        double turn = direction * time * TAU / PERIODS[ring];
        for (int i = 0; i < comets; i++) {
            double head = turn + i * TAU / comets, length = direction * .65, r = radius + 1.85;
            arc(new Vec3(0, .3, 0), r, head - length, length, detailed ? 16 : 6,
                    .24F, color(ICE, 200), true, true);
            if (detailed) arc(new Vec3(0, .3, 0), r, head - length, length, 16,
                    .9F, color(ICE, 18), true, false);
            OmniRenderGeometry.octahedron(poses.last(), out, circle(r, head, .3),
                    .27F, .55F, .27F, time * .18F, color(WHITE, 195));
        }
        ringRole(radius, ring);
    }

    private void ringRole(double radius, int ring) {
        int count = detailed ? 8 : 4;
        for (int i = 0; i < count; i++) {
            double a = i * TAU / count;
            double t = (time / 120D + i / (double) count) % 1;
            double envelope = Math.pow(Math.sin(t * Math.PI), 2);
            if (ring == 0) {
                double r = radius - 5 - t * 10;
                line(circle(r + 3, a, .8), circle(r, a, .8), .16F, color(ICE, 160 * envelope));
                if (detailed) line(circle(r + 3, a, .8), circle(r, a, .8), .6F, color(ICE, 18 * envelope));
            } else if (ring == 1) {
                poses.pushPose();
                var p = circle(radius - 5.1, a, 1.8);
                poses.translate(p.x, p.y, p.z);
                poses.mulPose(Axis.YP.rotationDegrees((float) -Math.toDegrees(a)));
                poses.mulPose(Axis.ZP.rotationDegrees(90));
                glyph(0, 0, i, 1.9, color(GOLD, 100 + envelope * 70));
                poses.popPose();
            } else {
                var p = circle(radius - 5.2, a + time * TAU / 1200, 1 + t * 7);
                OmniRenderGeometry.octahedron(poses.last(), out, p, .16F, .7F, .16F,
                        time * .18F, color(WHITE, 175 * envelope));
            }
        }
    }

    private void seal(double radius, double charge) {
        int segments = detailed ? 36 : 16;
        arc(Vec3.ZERO, radius, 0, TAU, segments, .11F, color(GOLD, 145 + charge * 55), false, true);
        arc(new Vec3(0, .16, 0), radius * .73, 0, TAU, segments, .09F, color(ICE, 140), false, true);
        for (int i = 0; i < 8; i++) {
            double a = i * TAU / 8;
            line(circle(radius * .73, a, .12), circle(radius, a, .12), .055F, color(WHITE, 105));
            if (detailed) glyph(radius * .85, a, i, radius * .105, color(GOLD, 160));
        }
        if (detailed) {
            for (int i = 0; i < 8; i++) {
                double a = i * TAU / 8 - time * TAU / 600;
                line(circle(radius * .59, a, .18), circle(radius * .59, a + 3 * TAU / 8, .18), .065F, color(ICE, 100));
                if (i % 2 == 0) OmniRenderGeometry.octahedron(poses.last(), out, circle(radius, a, .25),
                        .16F, .4F, .16F, time * .18F, color(WHITE, 165));
            }
            arc(new Vec3(0, 1 + charge, 0), radius * .88, 0, TAU, 36,
                    .16F, color(ICE, charge * 110), false, true);
        }
    }

    private void glyph(double radius, double angle, int variant, double size, int color) {
        var center = circle(radius, angle, .08);
        var radial = new Vec3(Math.cos(angle), 0, Math.sin(angle));
        var tangent = new Vec3(-Math.sin(angle), 0, Math.cos(angle));
        var a = center.add(radial.scale(size));
        var b = center.add(tangent.scale(size * .55));
        var c = center.subtract(radial.scale(size));
        var d = center.subtract(tangent.scale(size * .55));
        line(a, b, .075F, color); line(b, c, .075F, color);
        line(c, d, .075F, color); line(d, a, .075F, color);
        if (variant % 2 == 0) line(a, c, .06F, color);
        else line(b, d, .06F, color);
        if (variant % 3 == 0) {
            line(a.add(tangent.scale(size * .3)), a.add(radial.scale(size * .4)), .06F, color);
            line(c.subtract(tangent.scale(size * .3)), c.subtract(radial.scale(size * .4)), .06F, color);
        }
    }

    private double cycle(int tower) { return (time / 480D - tower / 8D + 1) % 1; }
    private double towerCharge(int tower) {
        double t = cycle(tower);
        return t < .45 ? Math.pow(Math.sin(t / .45 * Math.PI), 2) : 0;
    }

    private void towerVeins(double x, double z, double bottom, double top, int tower) {
        double t = cycle(tower);
        int sides = detailed ? 4 : 2;
        for (int side = 0; side < sides; side++) {
            double a = side * TAU / sides + Math.PI / 4;
            double px = x + Math.cos(a) * 3.0, pz = z + Math.sin(a) * 3.0;
            if (detailed) line(new Vec3(px, bottom, pz), new Vec3(px, top, pz), .055F, color(ICE, 28));
            double rise = Math.clamp(t / .45, 0, 1), y = bottom + (top - bottom) * rise;
            double alpha = t < .45 ? Math.pow(Math.sin(Math.PI * rise), 2) : 0;
            var p = new Vec3(px, y - 3, pz);
            var q = new Vec3(px, y + .5, pz);
            line(p, q, .18F, color(ICE, alpha * 190));
            if (detailed) line(p, q, .7F, color(ICE, alpha * 20));
        }
    }

    private void conduit(Vec3 start, int tower) {
        int steps = detailed ? 32 : 12;
        double cycle = cycle(tower), pulse = Math.clamp((cycle - .4) / .6, 0, 1);
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps, t1 = (i + 1D) / steps;
            var a = conduitPoint(start, t0, tower);
            var b = conduitPoint(start, t1, tower);
            line(a, b, .05F, color(ICE, detailed ? 45 : 25));
            double distance = Math.abs((t0 + t1) / 2 - pulse);
            double envelope = cycle > .4 ? Math.pow(Math.sin(Math.PI * pulse), .5) : 0;
            double alpha = Math.max(0, 1 - distance / .13) * envelope;
            line(a, b, .27F, color(GOLD, alpha * 205));
            if (detailed) ribbon(a, b, UP,
                    1.1F, 1.1F, color(ICE, alpha * 22), color(ICE, alpha * 22));
        }
        if (cycle > .4) OmniRenderGeometry.octahedron(poses.last(), out, conduitPoint(start, pulse, tower),
                .34F, .55F, .34F, time * .18F, color(WHITE, 190 * Math.sin(Math.PI * pulse)));
    }

    private Vec3 conduitPoint(Vec3 start, double t, int tower) {
        double bend = Math.sin(t * Math.PI) * 5;
        double a = tower * Math.PI / 4;
        return new Vec3(start.x * (1 - t) + Math.cos(a) * bend,
                start.y * (1 - t) + 4 * t + Math.sin(t * Math.PI) * 7,
                start.z * (1 - t) - Math.sin(a) * bend);
    }

    private void arc(Vec3 center, double radius, double start, double sweep, int steps,
                     float width, int color, boolean fade, boolean cross) {
        for (int i = 0; i < steps; i++) {
            double t0 = i / (double) steps, t1 = (i + 1D) / steps;
            var a = center.add(circle(radius, start + sweep * t0, 0));
            var b = center.add(circle(radius, start + sweep * t1, 0));
            int c0 = fade ? scaleAlpha(color, t0 * t0) : color;
            int c1 = fade ? scaleAlpha(color, t1 * t1) : color;
            ribbon(a, b, UP, width, width, c0, c1);
            if (cross) ribbon(a, b,
                    a.subtract(center), width, width, c0, c1);
        }
    }

    private void line(Vec3 a, Vec3 b, float width, int color) {
        ribbon(a, b, UP, width, width, color, color);
        ribbon(a, b, new Vec3(1, 0, 0), width, width, color, color);
    }

    private void ribbon(Vec3 a, Vec3 b, Vec3 normal, float w0, float w1, int c0, int c1) {
        var delta = b.subtract(a);
        if (delta.lengthSqr() < 1e-10) return;
        var direction = delta.normalize();
        var side = direction.cross(normal);
        if (side.lengthSqr() < 1e-8) side = direction.cross(Math.abs(direction.y) < .92 ? UP : new Vec3(1, 0, 0));
        side = side.normalize();
        var p = a.subtract(side.scale(w0));
        var q = b.subtract(side.scale(w1));
        var r = b.add(side.scale(w1));
        var s = a.add(side.scale(w0));
        // Both effect layers disable culling, so each ribbon needs only one quad.
        out.addVertex(poses.last().pose(), (float) p.x, (float) p.y, (float) p.z).setColor(c0);
        out.addVertex(poses.last().pose(), (float) q.x, (float) q.y, (float) q.z).setColor(c1);
        out.addVertex(poses.last().pose(), (float) r.x, (float) r.y, (float) r.z).setColor(c1);
        out.addVertex(poses.last().pose(), (float) s.x, (float) s.y, (float) s.z).setColor(c0);
    }

    private static Vec3 circle(double radius, double angle, double y) {
        return new Vec3(radius * Math.cos(angle), y, radius * Math.sin(angle));
    }
    private int color(int rgb, double alpha) { return OmniRenderGeometry.argb(rgb, (int) Math.round(alpha * gain)); }
    private static int scaleAlpha(int color, double scale) { return color & 0xFFFFFF | (int) ((color >>> 24) * scale) << 24; }
}
