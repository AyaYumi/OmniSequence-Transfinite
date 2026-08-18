package com.atir.molecularmanipulator.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.world.phys.Vec3;

/**
 * Reusable solid geometry helpers for multiblock renderers.
 *
 * <p>Every method submits real quads to a {@link VertexConsumer}; these are
 * not particles or billboards.  Coordinates are local to the caller's
 * current pose.  Submit the same geometry once to
 * {@link OmniRenderLayers#solidEmissiveColor()} and once (at a slightly
 * larger scale or with reduced alpha) to
 * {@link OmniRenderLayers#additiveColor()} for the NOVALITH-style physical
 * silhouette plus glow.</p>
 */
public final class OmniRenderGeometry {

    private OmniRenderGeometry() {
    }

    /** Pack an RGB color and alpha for {@link VertexConsumer#setColor(int)}. */
    public static int argb(int rgb, int alpha) {
        int clampedAlpha = Math.max(0, Math.min(255, alpha));
        return (clampedAlpha << 24) | (rgb & 0x00FFFFFF);
    }

    /**
     * Draw an annular prism in the local XZ plane.  It includes upper/lower
     * faces and both radial walls, so it remains visibly three-dimensional at
     * shallow camera angles.
     */
    public static void ring(PoseStack poseStack, VertexConsumer consumer,
            float centerX, float centerY, float centerZ,
            float radius, float width, int segments,
            float tiltX, float tiltZ, float spinDegrees, int color) {
        int safeSegments = Math.max(8, segments);
        float outerRadius = Math.max(0.05F, radius + width * 0.5F);
        float innerRadius = Math.max(0.02F, radius - width * 0.5F);
        float halfHeight = Math.max(0.045F, Math.abs(width) * 0.14F);

        poseStack.pushPose();
        poseStack.translate(centerX, centerY, centerZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
        PoseStack.Pose pose = poseStack.last();

        for (int index = 0; index < safeSegments; index++) {
            double a0 = Math.PI * 2.0D * index / safeSegments;
            double a1 = Math.PI * 2.0D * (index + 1) / safeSegments;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float u0 = (float) index / safeSegments;
            float u1 = (float) (index + 1) / safeSegments;

            // Upper and lower annular faces.
            vertex(pose, consumer, c0 * outerRadius, halfHeight, s0 * outerRadius,
                    0.0F, 1.0F, 0.0F, color);
            vertex(pose, consumer, c0 * innerRadius, halfHeight, s0 * innerRadius,
                    0.0F, 1.0F, 0.0F, color);
            vertex(pose, consumer, c1 * innerRadius, halfHeight, s1 * innerRadius,
                    0.0F, 1.0F, 0.0F, color);
            vertex(pose, consumer, c1 * outerRadius, halfHeight, s1 * outerRadius,
                    0.0F, 1.0F, 0.0F, color);

            vertex(pose, consumer, c1 * outerRadius, -halfHeight, s1 * outerRadius,
                    0.0F, -1.0F, 0.0F, color);
            vertex(pose, consumer, c1 * innerRadius, -halfHeight, s1 * innerRadius,
                    0.0F, -1.0F, 0.0F, color);
            vertex(pose, consumer, c0 * innerRadius, -halfHeight, s0 * innerRadius,
                    0.0F, -1.0F, 0.0F, color);
            vertex(pose, consumer, c0 * outerRadius, -halfHeight, s0 * outerRadius,
                    0.0F, -1.0F, 0.0F, color);

            // Outer radial wall.
            float nx = (c0 + c1) * 0.5F;
            float nz = (s0 + s1) * 0.5F;
            vertex(pose, consumer, c0 * outerRadius, -halfHeight, s0 * outerRadius,
                    nx, 0.0F, nz, color);
            vertex(pose, consumer, c1 * outerRadius, -halfHeight, s1 * outerRadius,
                    nx, 0.0F, nz, color);
            vertex(pose, consumer, c1 * outerRadius, halfHeight, s1 * outerRadius,
                    nx, 0.0F, nz, color);
            vertex(pose, consumer, c0 * outerRadius, halfHeight, s0 * outerRadius,
                    nx, 0.0F, nz, color);

            // Inner radial wall.
            vertex(pose, consumer, c1 * innerRadius, -halfHeight, s1 * innerRadius,
                    -nx, 0.0F, -nz, color);
            vertex(pose, consumer, c0 * innerRadius, -halfHeight, s0 * innerRadius,
                    -nx, 0.0F, -nz, color);
            vertex(pose, consumer, c0 * innerRadius, halfHeight, s0 * innerRadius,
                    -nx, 0.0F, -nz, color);
            vertex(pose, consumer, c1 * innerRadius, halfHeight, s1 * innerRadius,
                    -nx, 0.0F, -nz, color);
        }
        poseStack.popPose();
    }

    /** Draw a segmented annular prism with a gap between each arc. */
    public static void segmentedRing(PoseStack poseStack, VertexConsumer consumer,
            float centerX, float centerY, float centerZ,
            float radius, float width, float height, int segments,
            float segmentFill, float tiltX, float tiltZ, float spinDegrees,
            int color) {
        int count = Math.max(3, segments);
        float fill = Math.max(0.05F, Math.min(0.95F, segmentFill));
        float arc = (float) (Math.PI * 2.0D / count);
        float halfWidth = Math.abs(width) * 0.5F;
        float halfHeight = Math.max(0.02F, Math.abs(height) * 0.5F);
        float inner = Math.max(0.02F, radius - halfWidth);
        float outer = Math.max(inner + 0.02F, radius + halfWidth);

        poseStack.pushPose();
        poseStack.translate(centerX, centerY, centerZ);
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltX));
        poseStack.mulPose(Axis.ZP.rotationDegrees(tiltZ));
        poseStack.mulPose(Axis.YP.rotationDegrees(spinDegrees));
        PoseStack.Pose pose = poseStack.last();

        for (int i = 0; i < count; i++) {
            double center = i * arc;
            double a0 = center - arc * fill * 0.5D;
            double a1 = center + arc * fill * 0.5D;
            emitAnnularSegment(pose, consumer, inner, outer, halfHeight, a0, a1, color);
        }
        poseStack.popPose();
    }

    /** Draw a box oriented by three half-axis vectors. */
    public static void orientedBox(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, Vec3 firstAxis, Vec3 secondAxis, Vec3 thirdAxis,
            int color) {
        Vec3 p000 = center.subtract(firstAxis).subtract(secondAxis).subtract(thirdAxis);
        Vec3 p001 = center.subtract(firstAxis).subtract(secondAxis).add(thirdAxis);
        Vec3 p010 = center.subtract(firstAxis).add(secondAxis).subtract(thirdAxis);
        Vec3 p011 = center.subtract(firstAxis).add(secondAxis).add(thirdAxis);
        Vec3 p100 = center.add(firstAxis).subtract(secondAxis).subtract(thirdAxis);
        Vec3 p101 = center.add(firstAxis).subtract(secondAxis).add(thirdAxis);
        Vec3 p110 = center.add(firstAxis).add(secondAxis).subtract(thirdAxis);
        Vec3 p111 = center.add(firstAxis).add(secondAxis).add(thirdAxis);

        face(pose, consumer, p000, p100, p110, p010, color);
        face(pose, consumer, p101, p001, p011, p111, color);
        face(pose, consumer, p001, p000, p010, p011, color);
        face(pose, consumer, p100, p101, p111, p110, color);
        face(pose, consumer, p010, p110, p111, p011, color);
        face(pose, consumer, p001, p101, p100, p000, color);
    }

    /** Draw a solid rectangular beam joining two points. */
    public static void beam(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 start, Vec3 end, float halfWidth, float halfDepth, int color) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-5D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        Vec3 reference = Math.abs(direction.y) < 0.92D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 right = direction.cross(reference).normalize().scale(halfWidth);
        Vec3 depth = direction.cross(right).normalize().scale(halfDepth);
        orientedBox(pose, consumer, start.add(end).scale(0.5D), right,
                direction.scale(length * 0.5D), depth, color);
    }

    /** Draw a yaw-oriented octahedron with independent radii. */
    public static void octahedron(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, float radiusX, float radiusY, float radiusZ,
            float yawDegrees, int color) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3 east = new Vec3(Math.cos(yaw) * radiusX, 0.0D,
                Math.sin(yaw) * radiusZ);
        Vec3 north = new Vec3(-Math.sin(yaw) * radiusX, 0.0D,
                Math.cos(yaw) * radiusZ);
        Vec3 top = center.add(0.0D, radiusY, 0.0D);
        Vec3 bottom = center.add(0.0D, -radiusY, 0.0D);
        Vec3 e = center.add(east);
        Vec3 n = center.add(north);
        Vec3 w = center.subtract(east);
        Vec3 s = center.subtract(north);
        triangle(pose, consumer, top, e, n, color);
        triangle(pose, consumer, top, n, w, color);
        triangle(pose, consumer, top, w, s, color);
        triangle(pose, consumer, top, s, e, color);
        triangle(pose, consumer, bottom, n, e, color);
        triangle(pose, consumer, bottom, w, n, color);
        triangle(pose, consumer, bottom, s, w, color);
        triangle(pose, consumer, bottom, e, s, color);
    }

    /** Draw a triangular prism (useful for crystal petals and rune shards). */
    public static void triangularPrism(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 first, Vec3 second, Vec3 third, Vec3 halfThickness, int color) {
        Vec3 a = first.add(halfThickness);
        Vec3 b = second.add(halfThickness);
        Vec3 c = third.add(halfThickness);
        Vec3 d = first.subtract(halfThickness);
        Vec3 e = second.subtract(halfThickness);
        Vec3 f = third.subtract(halfThickness);
        triangle(pose, consumer, a, b, c, color);
        triangle(pose, consumer, f, e, d, color);
        face(pose, consumer, d, e, b, a, color);
        face(pose, consumer, e, f, c, b, color);
        face(pose, consumer, f, d, a, c, color);
    }

    /** Draw a quadrilateral prism (useful for petals, panels, and runes). */
    public static void prism(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 first, Vec3 second, Vec3 third, Vec3 fourth,
            Vec3 halfThickness, int color) {
        Vec3 a = first.add(halfThickness);
        Vec3 b = second.add(halfThickness);
        Vec3 c = third.add(halfThickness);
        Vec3 d = fourth.add(halfThickness);
        Vec3 e = first.subtract(halfThickness);
        Vec3 f = second.subtract(halfThickness);
        Vec3 g = third.subtract(halfThickness);
        Vec3 h = fourth.subtract(halfThickness);
        face(pose, consumer, a, b, c, d, color);
        face(pose, consumer, h, g, f, e, color);
        face(pose, consumer, e, f, b, a, color);
        face(pose, consumer, f, g, c, b, color);
        face(pose, consumer, g, h, d, c, color);
        face(pose, consumer, h, e, a, d, color);
    }

    /**
     * Draw a radial rune made from a thick rectangular beam and four diamond
     * corners.  It is a small building-block for machine sigils, not a
     * billboard or particle.
     */
    public static void rune(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, float radius, float thickness, float yawDegrees,
            int color) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3 radial = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 tangent = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 left = center.add(radial.scale(radius)).add(tangent.scale(-radius * 0.55D));
        Vec3 right = center.add(radial.scale(radius)).add(tangent.scale(radius * 0.55D));
        Vec3 tip = center.add(radial.scale(radius * 1.65D));
        beam(pose, consumer, left, right, thickness, thickness * 0.55F, color);
        triangularPrism(pose, consumer,
                left, right, tip,
                new Vec3(0.0D, thickness * 0.65D, 0.0D), color);
    }

    private static void emitAnnularSegment(PoseStack.Pose pose, VertexConsumer consumer,
            float inner, float outer, float halfHeight,
            double a0, double a1, int color) {
        float c0 = (float) Math.cos(a0);
        float s0 = (float) Math.sin(a0);
        float c1 = (float) Math.cos(a1);
        float s1 = (float) Math.sin(a1);
        vertex(pose, consumer, c0 * outer, halfHeight, s0 * outer, 0, 1, 0, color);
        vertex(pose, consumer, c0 * inner, halfHeight, s0 * inner, 0, 1, 0, color);
        vertex(pose, consumer, c1 * inner, halfHeight, s1 * inner, 0, 1, 0, color);
        vertex(pose, consumer, c1 * outer, halfHeight, s1 * outer, 0, 1, 0, color);
        vertex(pose, consumer, c1 * outer, -halfHeight, s1 * outer, 0, -1, 0, color);
        vertex(pose, consumer, c1 * inner, -halfHeight, s1 * inner, 0, -1, 0, color);
        vertex(pose, consumer, c0 * inner, -halfHeight, s0 * inner, 0, -1, 0, color);
        vertex(pose, consumer, c0 * outer, -halfHeight, s0 * outer, 0, -1, 0, color);
        float nx = (c0 + c1) * 0.5F;
        float nz = (s0 + s1) * 0.5F;
        vertex(pose, consumer, c0 * outer, -halfHeight, s0 * outer, nx, 0, nz, color);
        vertex(pose, consumer, c1 * outer, -halfHeight, s1 * outer, nx, 0, nz, color);
        vertex(pose, consumer, c1 * outer, halfHeight, s1 * outer, nx, 0, nz, color);
        vertex(pose, consumer, c0 * outer, halfHeight, s0 * outer, nx, 0, nz, color);
        vertex(pose, consumer, c1 * inner, -halfHeight, s1 * inner, -nx, 0, -nz, color);
        vertex(pose, consumer, c0 * inner, -halfHeight, s0 * inner, -nx, 0, -nz, color);
        vertex(pose, consumer, c0 * inner, halfHeight, s0 * inner, -nx, 0, -nz, color);
        vertex(pose, consumer, c1 * inner, halfHeight, s1 * inner, -nx, 0, -nz, color);
    }

    private static void triangle(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 first, Vec3 second, Vec3 third, int color) {
        // Emit both windings because the additive pass may be viewed from the
        // back of a crystal or rune.  QUADS needs four vertices per primitive,
        // so each three-sided face repeats its final vertex as a degenerate
        // fourth corner (the same convention used by Minecraft's dynamic
        // geometry helpers).
        vertex(pose, consumer, first, color);
        vertex(pose, consumer, second, color);
        vertex(pose, consumer, third, color);
        vertex(pose, consumer, third, color);
        vertex(pose, consumer, third, color);
        vertex(pose, consumer, second, color);
        vertex(pose, consumer, first, color);
        vertex(pose, consumer, first, color);
    }

    private static void face(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 first, Vec3 second, Vec3 third, Vec3 fourth, int color) {
        vertex(pose, consumer, first, color);
        vertex(pose, consumer, second, color);
        vertex(pose, consumer, third, color);
        vertex(pose, consumer, fourth, color);
        vertex(pose, consumer, fourth, color);
        vertex(pose, consumer, third, color);
        vertex(pose, consumer, second, color);
        vertex(pose, consumer, first, color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 position, int color) {
        consumer.addVertex(pose, (float) position.x, (float) position.y,
                (float) position.z).setColor(color);
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer,
            float x, float y, float z, float normalX, float normalY,
            float normalZ, int color) {
        consumer.addVertex(pose, x, y, z).setColor(color);
    }
}
