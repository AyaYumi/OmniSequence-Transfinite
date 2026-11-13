package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Vertex-built luminous ribbons for the star orrery belts.
 *
 * <p>All geometry is authored in a ring's local XZ plane (local +Y is the ring
 * normal), exactly like the imported OBJ belts. The renderer applies the same
 * {@link StarOrreryMotion#planeRotation moving ring-plane} transform as
 * the physical belt, then submits one depth pass and one additive pass for every
 * active ring. Nothing here cancels the ring plane or re-applies world gravity,
 * so rails and inset ornaments co-move with their belt instead of hanging.</p>
 *
 * <p>The imported belt occupies layout radius +/- .14 and Y +/- .57. The light
 * rail rides at layout radius + .20, safely outside the outer belt face, while
 * all additive thickness stays within the belt's vertical silhouette. Colors
 * follow the three star orrery tiers: green/cyan, violet, and gold.</p>
 */
public final class StarOrreryEffects {
    public static final int RINGS = StarOrreryLayout.RINGS;
    /** Radial distance from the layout radius to the centre of the light rail. */
    public static final double RAIL_CLEARANCE = .20D;
    /** Vertical half-height of the imported belt body. */
    public static final float BELT_HALF_HEIGHT = .45F;
    /** Vertical half-height including the imported belt rim. */
    public static final float BELT_RIM_HALF_HEIGHT = .57F;
    /** Full rail detail is reserved for the same close range as other effects. */
    public static final double DETAIL_DISTANCE = 112.0D;

    /** Embedded crystal ornaments stay centred on the belt's symmetry plane. */
    public static final float EMBEDDED_CRYSTAL_Y = 0F;
    /** Model-space scales: X is vertical, Y is tangent, Z is radial after the belt pose. */
    public static final float EMBEDDED_CRYSTAL_VERTICAL_SCALE = .28F;
    public static final float EMBEDDED_CRYSTAL_TANGENT_SCALE = .40F;
    public static final float EMBEDDED_CRYSTAL_RADIAL_SCALE = .18F;

    private static final double TAU = Math.PI * 2;
    private static final float CORE_RIBBON_HALF_HEIGHT = BELT_HALF_HEIGHT * .27F;
    private static final float HALO_RIBBON_HALF_HEIGHT = BELT_RIM_HALF_HEIGHT * .35F;
    private static final float PULSE_RATE = .30F;
    private static final int[] TIER_RGB = {0x63F2C4, 0xB88CFF, 0xFFD27D};
    private record Scratch(Quaternionf normal, Vector3f position) {}
    private static final ThreadLocal<Scratch> SCRATCH = ThreadLocal.withInitial(
            () -> new Scratch(new Quaternionf(), new Vector3f()));
    // Ring tessellation only uses fixed segment counts; share their unit circles
    // across rings and both passes instead of recomputing sin/cos every frame.
    private record Circle(float[] cos, float[] sin) {}
    private static final Map<Integer, Circle> CIRCLES = new ConcurrentHashMap<>();

    private static Circle circle(int segments) {
        return CIRCLES.computeIfAbsent(segments, count -> {
            var cos = new float[count + 1];
            var sin = new float[count + 1];
            for (int index = 0; index <= count; index++) {
                double angle = TAU * index / count;
                cos[index] = (float) Math.cos(angle);
                sin[index] = (float) Math.sin(angle);
            }
            return new Circle(cos, sin);
        });
    }

    private StarOrreryEffects() {
    }

    /** World fog controls visibility; distance only selects a cheaper geometry LOD. */
    public static boolean effectsVisible(int effectLevel, double distanceSqr) {
        return effectLevel > 0;
    }

    /** Full ribbon detail: high quality setting and close enough to resolve it. */
    public static boolean isDetailed(int effectLevel, double distanceSqr) {
        return effectLevel > 1 && distanceSqr < DETAIL_DISTANCE * DETAIL_DISTANCE;
    }

    /** Number of active rings that may receive rails; never more than verified lit rings. */
    public static int visibleRings(int litRings, int effectLevel, double distanceSqr) {
        if (!effectsVisible(effectLevel, distanceSqr)) return 0;
        return com.atir.molecularmanipulator.util.MathCompat.clamp(litRings, 0, RINGS);
    }

    /** Tier hue: green/cyan, violet, or gold. */
    public static int tierColor(int ring) {
        return TIER_RGB[StarOrreryLayout.tierOf(ring)];
    }

    /** Belt angular direction and speed in radians per second (same value the pose uses). */
    public static float beltSpeed(int ring) {
        if (ring < 0 || ring >= RINGS) throw new IndexOutOfBoundsException("Ring " + ring);
        return (float) (.5D / Math.sqrt(ring + 1D)) * (ring % 2 == 0 ? 1F : -1F);
    }

    /** Current belt rotation in the ring plane, for a verified active belt. */
    public static float beltSpin(int ring, float time) {
        return beltSpeed(ring) * time;
    }

    /** Unlit rings retain a zero spin for callers querying their layout state. */
    public static float beltSpin(int ring, int litRings, float time) {
        validate(ring);
        return ring < com.atir.molecularmanipulator.util.MathCompat.clamp(litRings, 0, RINGS) ? beltSpin(ring, time) : 0F;
    }

    /** Centre radius of the luminous rail, outside the imported belt's outer face. */
    public static double railRadius(int ring) {
        return StarOrreryLayout.radius(ring) + RAIL_CLEARANCE;
    }

    /** Number of inset float-crystal ornaments on one belt. */
    public static int embeddedCrystalCount(int ring) {
        validate(ring);
        return 3 + ring / 3;
    }

    /** Angle in the un-spun belt plane; the renderer applies {@link #beltSpin} separately. */
    public static double embeddedCrystalAngle(int ring, int index) {
        int count = embeddedCrystalCount(ring);
        if (index < 0 || index >= count) {
            throw new IndexOutOfBoundsException("Ornament " + index + " on ring " + ring);
        }
        return TAU * (index + .5D) / count + ring * .37D;
    }

    /** Slightly proud of the belt centre so the inset crystal remains visible. */
    public static double embeddedCrystalRadius(int ring) {
        return StarOrreryLayout.radius(ring) + .10D;
    }

    /** Y rotation that turns the model's long axis onto the belt tangent. */
    public static float embeddedCrystalYaw(int ring, int index) {
        return (float) (-embeddedCrystalAngle(ring, index) - Math.PI * .5D);
    }

    public static float embeddedCrystalX(int ring, int index) {
        return (float) (Math.cos(embeddedCrystalAngle(ring, index)) * embeddedCrystalRadius(ring));
    }

    public static float embeddedCrystalY(int ring, int index) {
        embeddedCrystalAngle(ring, index);
        return EMBEDDED_CRYSTAL_Y;
    }

    public static float embeddedCrystalZ(int ring, int index) {
        return (float) (Math.sin(embeddedCrystalAngle(ring, index)) * embeddedCrystalRadius(ring));
    }

    /**
     * Draw active rails after applying the same effect-level and distance gate
     * used by the renderer. This overload lets callers remain unaware of the
     * visible-ring count while still submitting nothing when effects are off.
     */
    public static void render(PoseStack poses, VertexConsumer out, int litRings, int effectLevel,
            double distanceSqr, float time, boolean detailed, boolean glow) {
        int rings = visibleRings(litRings, effectLevel, distanceSqr);
        if (rings == 0) return;
        render(poses, out, rings, time, detailed, glow);
    }

    /**
     * Draw all active rails in one batch. The caller supplies the already
     * shader-compatible consumer; no RenderType is switched between rings.
     */
    public static void render(PoseStack poses, VertexConsumer out, int litRings,
            float time, boolean detailed, boolean glow) {
        int rings = com.atir.molecularmanipulator.util.MathCompat.clamp(litRings, 0, RINGS);
        if (rings == 0 || out == null) return;
        var normal = SCRATCH.get().normal;
        for (int ring = 0; ring < rings; ring++) {
            poses.pushPose();
            poses.mulPose(StarOrreryMotion.planeRotation(normal, ring, rings, time));
            renderRing(poses, out, ring, time, detailed, glow);
            poses.popPose();
        }
    }

    /**
     * Draw one belt rail. The pose must already contain the ring-plane
     * transform; this method never mutates the pose stack.
     */
    public static void renderRing(PoseStack poses, VertexConsumer out, int ring,
            float time, boolean detailed, boolean glow) {
        validate(ring);
        double radius = railRadius(ring);
        int segments = railSegments(ring, detailed);
        int color = tierColor(ring);
        PoseStack.Pose pose = poses.last();
        if (glow) {
            // Restrained instrument trace; the solid belt remains the silhouette.
            tube(pose, out, radius + .005D, .035F, segments, 4, argb(color, 4));
            band(pose, out, radius - .005D, -HALO_RIBBON_HALF_HEIGHT,
                    HALO_RIBBON_HALF_HEIGHT, segments, argb(color, 2));
            pulses(pose, out, ring, -beltSpin(ring, time), time, detailed, true, color);
        } else {
            // A flat luminous ribbon against the outer belt face plus a crisp
            // round core rail. Both are outside the belt surface to avoid z-fighting.
            band(pose, out, radius - .015D, -CORE_RIBBON_HALF_HEIGHT * .5F,
                    CORE_RIBBON_HALF_HEIGHT * .5F, segments, argb(color, 140));
            tube(pose, out, radius, .014F, segments, 4, argb(color, 164));
            pulses(pose, out, ring, -beltSpin(ring, time), time, detailed, false, color);
        }
        scanArcs(pose, out, ring, time, detailed, glow, color);
    }

    /**
     * Short travelling panels and calibration strokes, like the computation
     * orrery's orbital traces. Keep them on the outer belt face rather than
     * introducing floating seals or world-aligned particle clouds.
     */
    private static void scanArcs(PoseStack.Pose pose, VertexConsumer out, int ring,
            float time, boolean detailed, boolean glow, int color) {
        double radius = railRadius(ring) + .025D;
        // Positive Y rotation sends (cos(a), 0, sin(a)) to angle a - spin.
        double direction = -Math.signum(beltSpeed(ring));
        double spin = -beltSpin(ring, time);
        int panels = detailed ? 3 : 1;
        int steps = detailed ? 6 : 3;
        float height = glow ? .30F : .21F;
        for (int panel = 0; panel < panels; panel++) {
            double head = spin + direction * time * .48D + TAU * panel / panels;
            double sweep = .25D + ring * .008D;
            for (int step = 0; step < steps; step++) {
                double a = head - direction * sweep * (step + 1D) / steps;
                double b = head - direction * sweep * step / steps;
                int alpha = glow ? 14 - step : 188 - step * 10;
                arcFace(pose, out, radius, a, b, -height, height, argb(color, alpha));
            }
        }
        if (!detailed) return;
        int marks = 8 + ring / 3 * 4;
        for (int mark = 0; mark < marks; mark++) {
            double angle = spin + TAU * mark / marks;
            double half = (glow ? .09D : .045D) / radius;
            float halfHeight = mark % 2 == 0 ? .38F : .27F;
            arcFace(pose, out, radius + .005D, angle - half, angle + half,
                    -halfHeight, halfHeight, argb(color, glow ? 9 : 164));
        }
    }

    private static void arcFace(PoseStack.Pose pose, VertexConsumer out, double radius,
            double a, double b, float y0, float y1, int color) {
        float ax = (float) (Math.cos(a) * radius), az = (float) (Math.sin(a) * radius);
        float bx = (float) (Math.cos(b) * radius), bz = (float) (Math.sin(b) * radius);
        quad(pose, out, ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az, color);
    }

    private static int railSegments(int ring, boolean detailed) {
        double radius = StarOrreryLayout.radius(ring);
        int wanted = (int) Math.ceil(radius * (detailed ? 3D : 1.6D));
        return Math.max(detailed ? 56 : 32, Math.min(detailed ? 96 : 48, wanted));
    }

    private static void pulses(PoseStack.Pose pose, VertexConsumer out, int ring,
            float spin, float time, boolean detailed, boolean glow, int color) {
        int words = detailed ? 3 : 2;
        int bytes = detailed ? 4 : 3;
        float direction = beltSpeed(ring) >= 0 ? -1F : 1F;
        double travel = direction * (PULSE_RATE + ring * .015D);
        double radius = railRadius(ring);
        for (int word = 0; word < words; word++) {
            double head = spin + TAU * word / words + time * travel;
            for (int index = 0; index < bytes; index++) {
                double angle = head - direction * index * .34D / radius;
                int alpha = glow ? (index % 2 == 0 ? 12 : 7) : (index % 2 == 0 ? 224 : 152);
                packet(pose, out, angle, radius, .10F, .055F, .023F, argb(color, alpha));
            }
        }
    }

    /** A thin vertical ribbon around the belt; emitted as whole two-sided quads. */
    private static void band(PoseStack.Pose pose, VertexConsumer out, double radius,
            float y0, float y1, int segments, int color) {
        int count = Math.max(8, segments);
        float r = (float) radius;
        var circle = circle(count);
        for (int index = 0; index < count; index++) {
            float c0 = circle.cos[index], s0 = circle.sin[index];
            float c1 = circle.cos[index + 1], s1 = circle.sin[index + 1];
            quad(pose, out,
                    c0 * r, y0, s0 * r,
                    c1 * r, y0, s1 * r,
                    c1 * r, y1, s1 * r,
                    c0 * r, y1, s0 * r,
                    color);
        }
    }

    /** A diamond-section rail riding just outside the belt's outer face. */
    private static void tube(PoseStack.Pose pose, VertexConsumer out, double radius,
            float tubeRadius, int segments, int sides, int color) {
        int count = Math.max(8, segments);
        int crossSections = Math.max(3, Math.min(8, sides));
        float base = (float) radius;
        var circle = circle(count);
        var section = circle(crossSections);
        for (int index = 0; index < count; index++) {
            float c0 = circle.cos[index], s0 = circle.sin[index];
            float c1 = circle.cos[index + 1], s1 = circle.sin[index + 1];
            for (int side = 0; side < crossSections; side++) {
                float radial0 = section.cos[side] * tubeRadius;
                float vertical0 = section.sin[side] * tubeRadius;
                float radial1 = section.cos[side + 1] * tubeRadius;
                float vertical1 = section.sin[side + 1] * tubeRadius;
                float r0 = base + radial0;
                float r1 = base + radial1;
                quad(pose, out,
                        c0 * r0, vertical0, s0 * r0,
                        c1 * r0, vertical0, s1 * r0,
                        c1 * r1, vertical1, s1 * r1,
                        c0 * r1, vertical1, s0 * r1,
                        color);
            }
        }
    }

    /** A rectangular data cell, with equal spacing rather than a tapered comet tail. */
    private static void packet(PoseStack.Pose pose, VertexConsumer out, double angle,
            double radius, float tangentHalf, float verticalHalf, float radialHalf, int color) {
        float c = (float) Math.cos(angle), s = (float) Math.sin(angle);
        float cx = c * (float) radius, cz = s * (float) radius;
        float tx = -s * tangentHalf, tz = c * tangentHalf;
        float rx = c * radialHalf, rz = s * radialHalf;
        float ax = cx - tx - rx, az = cz - tz - rz;
        float bx = cx + tx - rx, bz = cz + tz - rz;
        float dx = cx - tx + rx, dz = cz - tz + rz;
        float ex = cx + tx + rx, ez = cz + tz + rz;
        float h = verticalHalf;
        quad(pose,out,ax,-h,az,bx,-h,bz,bx,h,bz,ax,h,az,color);
        quad(pose,out,ex,-h,ez,dx,-h,dz,dx,h,dz,ex,h,ez,color);
        quad(pose,out,dx,-h,dz,ax,-h,az,ax,h,az,dx,h,dz,color);
        quad(pose,out,bx,-h,bz,ex,-h,ez,ex,h,ez,bx,h,bz,color);
        quad(pose,out,ax,h,az,bx,h,bz,ex,h,ez,dx,h,dz,color);
        quad(pose,out,dx,-h,dz,ex,-h,ez,bx,-h,bz,ax,-h,az,color);
    }

    private static void quad(PoseStack.Pose pose, VertexConsumer out,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz, int color) {
        // Both windings contribute to the authored blending. Transform each corner
        // once and reuse it for the reverse winding, keeping the same eight vertices.
        var matrix = pose.pose();
        var position = SCRATCH.get().position;
        matrix.transformPosition(ax, ay, az, position);
        float aX = position.x, aY = position.y, aZ = position.z;
        matrix.transformPosition(bx, by, bz, position);
        float bX = position.x, bY = position.y, bZ = position.z;
        matrix.transformPosition(cx, cy, cz, position);
        float cX = position.x, cY = position.y, cZ = position.z;
        matrix.transformPosition(dx, dy, dz, position);
        float dX = position.x, dY = position.y, dZ = position.z;
        out.vertex(aX, aY, aZ).color(color).endVertex();
        out.vertex(bX, bY, bZ).color(color).endVertex();
        out.vertex(cX, cY, cZ).color(color).endVertex();
        out.vertex(dX, dY, dZ).color(color).endVertex();
        out.vertex(dX, dY, dZ).color(color).endVertex();
        out.vertex(cX, cY, cZ).color(color).endVertex();
        out.vertex(bX, bY, bZ).color(color).endVertex();
        out.vertex(aX, aY, aZ).color(color).endVertex();
    }

    private static int argb(int rgb, int alpha) {
        return OmniRenderGeometry.argb(rgb, alpha);
    }

    private static void validate(int ring) {
        if (ring < 0 || ring >= RINGS) throw new IndexOutOfBoundsException("Ring " + ring);
    }
}
