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

    /**
     * Draw a smoothly subdivided annular prism. The legacy segment-fill
     * argument is retained for renderer compatibility, but visual gaps are no
     * longer emitted; animated nodes now provide rhythm without a dashed line.
     */
    public static void segmentedRing(PoseStack poseStack, VertexConsumer consumer,
            float centerX, float centerY, float centerZ,
            float radius, float width, float height, int segments,
            float segmentFill, float tiltX, float tiltZ, float spinDegrees,
            int color) {
        int count = Math.max(3, segments);
        float fill = 1.0F;
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
            double visibleArcLength = radius * (a1 - a0);
            int subdivisions = Math.max(1, Math.min(8,
                    (int) Math.ceil(visibleArcLength / 0.58D)));
            for (int subdivision = 0; subdivision < subdivisions; subdivision++) {
                double t0 = subdivision / (double) subdivisions;
                double t1 = (subdivision + 1) / (double) subdivisions;
                double sectionStart = a0 + (a1 - a0) * t0;
                double sectionEnd = a0 + (a1 - a0) * t1;
                emitAnnularSegment(pose, consumer, inner, outer, halfHeight,
                        sectionStart, sectionEnd, color);
            }
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

    /**
     * Draw a tapered beam with independent start/end colors. This is the shared
     * primitive used by flowing molecular bonds and matter-stream ribbons; the
     * color interpolation happens along the beam instead of adding another
     * oversized glow shell.
     */
    public static void taperedBeam(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 start, Vec3 end,
            float startHalfWidth, float endHalfWidth,
            float startHalfDepth, float endHalfDepth,
            int startColor, int endColor) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-5D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        Vec3 reference = Math.abs(direction.y) < 0.92D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : new Vec3(1.0D, 0.0D, 0.0D);
        Vec3 rightUnit = direction.cross(reference).normalize();
        Vec3 depthUnit = direction.cross(rightUnit).normalize();
        int sides = 8;
        Vec3[] startRing = new Vec3[sides];
        Vec3[] endRing = new Vec3[sides];
        for (int side = 0; side < sides; side++) {
            double angle = Math.PI * 2.0D * side / sides;
            startRing[side] = start
                    .add(rightUnit.scale(Math.cos(angle) * Math.max(0.001F, startHalfWidth)))
                    .add(depthUnit.scale(Math.sin(angle) * Math.max(0.001F, startHalfDepth)));
            endRing[side] = end
                    .add(rightUnit.scale(Math.cos(angle) * Math.max(0.001F, endHalfWidth)))
                    .add(depthUnit.scale(Math.sin(angle) * Math.max(0.001F, endHalfDepth)));
        }
        for (int side = 0; side < sides; side++) {
            int next = (side + 1) % sides;
            gradientFace(pose, consumer,
                    startRing[side], startColor,
                    endRing[side], endColor,
                    endRing[next], endColor,
                    startRing[next], startColor);
            triangle(pose, consumer, start, startRing[next], startRing[side], startColor);
            triangle(pose, consumer, end, endRing[side], endRing[next], endColor);
        }
    }

    /**
     * Draw a double-sided gradient ribbon between two curve samples. Unlike a
     * beam this keeps a broad, weightless profile and is suited to plasma
     * spirals, scan trails and fading computation paths.
     */
    public static void ribbonSegment(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 start, Vec3 end, Vec3 referenceNormal,
            float startHalfWidth, float endHalfWidth,
            int startColor, int endColor) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 1.0E-5D) {
            return;
        }
        Vec3 direction = delta.scale(1.0D / length);
        Vec3 normal = referenceNormal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : referenceNormal.normalize();
        Vec3 side = direction.cross(normal);
        if (side.lengthSqr() < 1.0E-8D) {
            normal = Math.abs(direction.y) < 0.92D
                    ? new Vec3(0.0D, 1.0D, 0.0D)
                    : new Vec3(1.0D, 0.0D, 0.0D);
            side = direction.cross(normal);
        }
        side = side.normalize();
        Vec3 startSide = side.scale(Math.max(0.001F, startHalfWidth));
        Vec3 endSide = side.scale(Math.max(0.001F, endHalfWidth));
        gradientFace(pose, consumer,
                start.subtract(startSide), startColor,
                end.subtract(endSide), endColor,
                end.add(endSide), endColor,
                start.add(startSide), startColor);
    }

    /**
     * Draw a joined ribbon strip. Tangents are averaged at every sample so
     * adjacent sections share their edge and no longer produce saw-tooth joins.
     */
    public static void smoothRibbonPath(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3[] points, Vec3 referenceNormal,
            float startHalfWidth, float endHalfWidth,
            int startColor, int endColor) {
        if (points == null || points.length < 2) {
            return;
        }
        Vec3 normal = referenceNormal.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 1.0D, 0.0D)
                : referenceNormal.normalize();
        Vec3[] sides = new Vec3[points.length];
        for (int index = 0; index < points.length; index++) {
            Vec3 tangent = pathTangent(points, index);
            Vec3 side = tangent.cross(normal);
            if (side.lengthSqr() < 1.0E-8D) {
                Vec3 fallback = Math.abs(tangent.y) < 0.92D
                        ? new Vec3(0.0D, 1.0D, 0.0D)
                        : new Vec3(1.0D, 0.0D, 0.0D);
                side = tangent.cross(fallback);
            }
            sides[index] = side.normalize();
            if (index > 0 && sides[index].dot(sides[index - 1]) < 0.0D) {
                sides[index] = sides[index].scale(-1.0D);
            }
        }
        for (int index = 0; index < points.length - 1; index++) {
            float t0 = index / (float) (points.length - 1);
            float t1 = (index + 1) / (float) (points.length - 1);
            float width0 = startHalfWidth + (endHalfWidth - startHalfWidth) * t0;
            float width1 = startHalfWidth + (endHalfWidth - startHalfWidth) * t1;
            int color0 = lerpColor(startColor, endColor, t0);
            int color1 = lerpColor(startColor, endColor, t1);
            Vec3 side0 = sides[index].scale(width0);
            Vec3 side1 = sides[index + 1].scale(width1);
            gradientFace(pose, consumer,
                    points[index].subtract(side0), color0,
                    points[index + 1].subtract(side1), color1,
                    points[index + 1].add(side1), color1,
                    points[index].add(side0), color0);
        }
    }

    /** Draw a ribbon with three additive falloff shells for a soft halo. */
    public static void smoothRibbonPath(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3[] points, Vec3 referenceNormal,
            float startHalfWidth, float endHalfWidth,
            int startColor, int endColor, boolean softGlow) {
        if (!softGlow) {
            smoothRibbonPath(pose, consumer, points, referenceNormal,
                    startHalfWidth, endHalfWidth, startColor, endColor);
            return;
        }
        smoothRibbonPath(pose, consumer, points, referenceNormal,
                startHalfWidth * 2.15F, endHalfWidth * 2.15F,
                scaleAlpha(startColor, 0.13F), scaleAlpha(endColor, 0.13F));
        smoothRibbonPath(pose, consumer, points, referenceNormal,
                startHalfWidth * 1.48F, endHalfWidth * 1.48F,
                scaleAlpha(startColor, 0.30F), scaleAlpha(endColor, 0.30F));
        smoothRibbonPath(pose, consumer, points, referenceNormal,
                startHalfWidth * 0.92F, endHalfWidth * 0.92F,
                scaleAlpha(startColor, 0.76F), scaleAlpha(endColor, 0.76F));
    }

    /**
     * Draw a continuous round tube along a sampled curve. A parallel-transport
     * frame carries the cross-section along the path, avoiding the visible
     * twisting and square joints produced by independent beam segments.
     */
    public static void smoothTubePath(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3[] points,
            float startRadius, float endRadius, int radialSegments,
            int startColor, int endColor) {
        if (points == null || points.length < 2) {
            return;
        }
        int sides = Math.max(5, Math.min(12, radialSegments));
        Vec3[] tangents = new Vec3[points.length];
        Vec3[] rights = new Vec3[points.length];
        Vec3[] depths = new Vec3[points.length];
        for (int index = 0; index < points.length; index++) {
            tangents[index] = pathTangent(points, index);
            if (index == 0) {
                Vec3 reference = Math.abs(tangents[index].y) < 0.92D
                        ? new Vec3(0.0D, 1.0D, 0.0D)
                        : new Vec3(1.0D, 0.0D, 0.0D);
                rights[index] = tangents[index].cross(reference).normalize();
            } else {
                Vec3 transported = rights[index - 1].subtract(tangents[index]
                        .scale(rights[index - 1].dot(tangents[index])));
                if (transported.lengthSqr() < 1.0E-8D) {
                    Vec3 reference = Math.abs(tangents[index].y) < 0.92D
                            ? new Vec3(0.0D, 1.0D, 0.0D)
                            : new Vec3(1.0D, 0.0D, 0.0D);
                    transported = tangents[index].cross(reference);
                }
                rights[index] = transported.normalize();
            }
            depths[index] = tangents[index].cross(rights[index]).normalize();
        }

        for (int index = 0; index < points.length - 1; index++) {
            float t0 = index / (float) (points.length - 1);
            float t1 = (index + 1) / (float) (points.length - 1);
            float radius0 = startRadius + (endRadius - startRadius) * t0;
            float radius1 = startRadius + (endRadius - startRadius) * t1;
            int color0 = lerpColor(startColor, endColor, t0);
            int color1 = lerpColor(startColor, endColor, t1);
            for (int side = 0; side < sides; side++) {
                double a0 = Math.PI * 2.0D * side / sides;
                double a1 = Math.PI * 2.0D * (side + 1) / sides;
                Vec3 start0 = tubePoint(points[index], rights[index], depths[index],
                        radius0, a0);
                Vec3 start1 = tubePoint(points[index], rights[index], depths[index],
                        radius0, a1);
                Vec3 end0 = tubePoint(points[index + 1], rights[index + 1],
                        depths[index + 1], radius1, a0);
                Vec3 end1 = tubePoint(points[index + 1], rights[index + 1],
                        depths[index + 1], radius1, a1);
                gradientFace(pose, consumer,
                        start0, color0, end0, color1,
                        end1, color1, start1, color0);
            }
        }
    }

    /** Draw a tube with three additive falloff shells for a soft halo. */
    public static void smoothTubePath(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3[] points,
            float startRadius, float endRadius, int radialSegments,
            int startColor, int endColor, boolean softGlow) {
        if (!softGlow) {
            smoothTubePath(pose, consumer, points, startRadius, endRadius,
                    radialSegments, startColor, endColor);
            return;
        }
        smoothTubePath(pose, consumer, points,
                startRadius * 2.05F, endRadius * 2.05F, radialSegments,
                scaleAlpha(startColor, 0.12F), scaleAlpha(endColor, 0.12F));
        smoothTubePath(pose, consumer, points,
                startRadius * 1.42F, endRadius * 1.42F, radialSegments,
                scaleAlpha(startColor, 0.28F), scaleAlpha(endColor, 0.28F));
        smoothTubePath(pose, consumer, points,
                startRadius * 0.90F, endRadius * 0.90F, radialSegments,
                scaleAlpha(startColor, 0.78F), scaleAlpha(endColor, 0.78F));
    }

    /** Draw the twelve edges of an arbitrarily oriented box. */
    public static void wireframeBox(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, Vec3 firstAxis, Vec3 secondAxis, Vec3 thirdAxis,
            float width, int color) {
        Vec3 p000 = center.subtract(firstAxis).subtract(secondAxis).subtract(thirdAxis);
        Vec3 p001 = center.subtract(firstAxis).subtract(secondAxis).add(thirdAxis);
        Vec3 p010 = center.subtract(firstAxis).add(secondAxis).subtract(thirdAxis);
        Vec3 p011 = center.subtract(firstAxis).add(secondAxis).add(thirdAxis);
        Vec3 p100 = center.add(firstAxis).subtract(secondAxis).subtract(thirdAxis);
        Vec3 p101 = center.add(firstAxis).subtract(secondAxis).add(thirdAxis);
        Vec3 p110 = center.add(firstAxis).add(secondAxis).subtract(thirdAxis);
        Vec3 p111 = center.add(firstAxis).add(secondAxis).add(thirdAxis);
        Vec3[] corners = {p000, p001, p010, p011, p100, p101, p110, p111};
        int[][] edges = {
                {0, 1}, {0, 2}, {0, 4}, {1, 3}, {1, 5}, {2, 3},
                {2, 6}, {3, 7}, {4, 5}, {4, 6}, {5, 7}, {6, 7}
        };
        for (int[] edge : edges) {
            taperedBeam(pose, consumer, corners[edge[0]], corners[edge[1]],
                    width, width, width, width, color, color);
        }
    }

    /** Draw a wireframe octahedral containment cage. */
    public static void wireframeOctahedron(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3 center,
            float radiusX, float radiusY, float radiusZ,
            float yawDegrees, float width, int color) {
        double yaw = Math.toRadians(yawDegrees);
        Vec3 east = new Vec3(Math.cos(yaw) * radiusX, 0.0D,
                Math.sin(yaw) * radiusZ);
        Vec3 north = new Vec3(-Math.sin(yaw) * radiusX, 0.0D,
                Math.cos(yaw) * radiusZ);
        Vec3[] vertices = {
                center.add(0.0D, radiusY, 0.0D),
                center.add(0.0D, -radiusY, 0.0D),
                center.add(east), center.add(north),
                center.subtract(east), center.subtract(north)
        };
        for (int equator = 2; equator < 6; equator++) {
            int next = 2 + (equator - 1) % 4;
            taperedBeam(pose, consumer, vertices[0], vertices[equator],
                    width, width, width, width, color, color);
            taperedBeam(pose, consumer, vertices[1], vertices[equator],
                    width, width, width, width, color, color);
            taperedBeam(pose, consumer, vertices[equator], vertices[next],
                    width, width, width, width, color, color);
        }
    }

    /** A closed round torus in the local XZ plane, using only POSITION_COLOR vertices. */
    public static void torus(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, float radius, float tubeRadius,
            int segments, int sides, int color) {
        int majorSegments = Math.max(8, segments);
        int minorSegments = Math.max(3, sides);
        float tube = Math.max(0.001F, Math.abs(tubeRadius));
        for (int segment = 0; segment < majorSegments; segment++) {
            double major0 = Math.PI * 2.0D * segment / majorSegments;
            // Wrap by index so the closing cross-section is bit-identical to the first.
            double major1 = Math.PI * 2.0D * ((segment + 1) % majorSegments) / majorSegments;
            for (int side = 0; side < minorSegments; side++) {
                double minor0 = Math.PI * 2.0D * side / minorSegments;
                double minor1 = Math.PI * 2.0D * ((side + 1) % minorSegments) / minorSegments;
                face(pose, consumer,
                        torusPoint(center, radius, tube, major0, minor0),
                        torusPoint(center, radius, tube, major1, minor0),
                        torusPoint(center, radius, tube, major1, minor1),
                        torusPoint(center, radius, tube, major0, minor1), color);
            }
        }
    }

    private static Vec3 torusPoint(Vec3 center, float radius, float tubeRadius,
            double major, double minor) {
        double radial = radius + tubeRadius * Math.cos(minor);
        return center.add(radial * Math.cos(major), tubeRadius * Math.sin(minor),
                radial * Math.sin(major));
    }

    /** Draw a rounded UV sphere for scientific atom and status-node motifs. */
    public static void sphere(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, float radius, int latitudeSegments,
            int longitudeSegments, int color) {
        ellipsoid(pose, consumer, center, radius, radius, radius,
                latitudeSegments, longitudeSegments, color);
    }

    /** Draw a UV ellipsoid for probability clouds and condensation volumes. */
    public static void ellipsoid(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, float radiusX, float radiusY, float radiusZ,
            int latitudeSegments, int longitudeSegments, int color) {
        int latitudes = Math.max(4, latitudeSegments);
        int longitudes = Math.max(8, longitudeSegments);
        for (int latitude = 0; latitude < latitudes; latitude++) {
            double v0 = latitude / (double) latitudes;
            double v1 = (latitude + 1) / (double) latitudes;
            double phi0 = -Math.PI * 0.5D + Math.PI * v0;
            double phi1 = -Math.PI * 0.5D + Math.PI * v1;
            double y0 = Math.sin(phi0) * radiusY;
            double y1 = Math.sin(phi1) * radiusY;
            double ring0X = Math.cos(phi0) * radiusX;
            double ring1X = Math.cos(phi1) * radiusX;
            double ring0Z = Math.cos(phi0) * radiusZ;
            double ring1Z = Math.cos(phi1) * radiusZ;
            for (int longitude = 0; longitude < longitudes; longitude++) {
                double a0 = Math.PI * 2.0D * longitude / longitudes;
                double a1 = Math.PI * 2.0D * (longitude + 1) / longitudes;
                Vec3 p00 = center.add(Math.cos(a0) * ring0X, y0,
                        Math.sin(a0) * ring0Z);
                Vec3 p01 = center.add(Math.cos(a1) * ring0X, y0,
                        Math.sin(a1) * ring0Z);
                Vec3 p11 = center.add(Math.cos(a1) * ring1X, y1,
                        Math.sin(a1) * ring1Z);
                Vec3 p10 = center.add(Math.cos(a0) * ring1X, y1,
                        Math.sin(a0) * ring1Z);
                face(pose, consumer, p00, p01, p11, p10, color);
            }
        }
    }

    /** Draw a four-sided technical frame in an arbitrary plane. */
    public static void rectangleFrame(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3 center,
            Vec3 firstAxis, Vec3 secondAxis, float width, int color) {
        Vec3 first = center.subtract(firstAxis).subtract(secondAxis);
        Vec3 second = center.add(firstAxis).subtract(secondAxis);
        Vec3 third = center.add(firstAxis).add(secondAxis);
        Vec3 fourth = center.subtract(firstAxis).add(secondAxis);
        taperedBeam(pose, consumer, first, second,
                width, width, width, width, color, color);
        taperedBeam(pose, consumer, second, third,
                width, width, width, width, color, color);
        taperedBeam(pose, consumer, third, fourth,
                width, width, width, width, color, color);
        taperedBeam(pose, consumer, fourth, first,
                width, width, width, width, color, color);
    }

    /** Draw a regular diagnostic grid in an arbitrary plane. */
    public static void gridPlane(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 center, Vec3 firstAxis, Vec3 secondAxis,
            int divisions, float width, int color) {
        int safeDivisions = Math.max(2, divisions);
        for (int index = 0; index <= safeDivisions; index++) {
            double offset = -1.0D + 2.0D * index / safeDivisions;
            Vec3 alongFirst = firstAxis.scale(offset);
            Vec3 alongSecond = secondAxis.scale(offset);
            taperedBeam(pose, consumer,
                    center.add(alongFirst).subtract(secondAxis),
                    center.add(alongFirst).add(secondAxis),
                    width, width, width, width, color, color);
            taperedBeam(pose, consumer,
                    center.add(alongSecond).subtract(firstAxis),
                    center.add(alongSecond).add(firstAxis),
                    width, width, width, width, color, color);
        }
    }

    /**
     * Draw a diagnostic spacetime grid bent toward (or away from) a focal
     * point. Positive depth produces a gravity well; negative depth produces
     * the outward distortion used around a white-hole exhaust.
     */
    public static void gravityWellGrid(PoseStack.Pose pose,
            VertexConsumer consumer, Vec3 center, double halfExtent,
            int divisions, int samples, double depth,
            float radius, int color, boolean softGlow) {
        int safeDivisions = Math.max(4, divisions);
        int safeSamples = Math.max(8, samples);
        double sigma = Math.max(0.5D, halfExtent * 0.42D);
        for (int line = 0; line <= safeDivisions; line++) {
            double fixed = -halfExtent
                    + halfExtent * 2.0D * line / safeDivisions;
            Vec3[] alongX = new Vec3[safeSamples + 1];
            Vec3[] alongZ = new Vec3[safeSamples + 1];
            for (int sample = 0; sample <= safeSamples; sample++) {
                double variable = -halfExtent
                        + halfExtent * 2.0D * sample / safeSamples;
                double radialSquared = fixed * fixed + variable * variable;
                double displacement = depth
                        * Math.exp(-radialSquared / (sigma * sigma));
                alongX[sample] = center.add(variable, -displacement, fixed);
                alongZ[sample] = center.add(fixed, -displacement, variable);
            }
            smoothTubePath(pose, consumer, alongX,
                    radius, radius, 6, color, color, softGlow);
            smoothTubePath(pose, consumer, alongZ,
                    radius, radius, 6, color, color, softGlow);
        }
    }

    private static Vec3 pathTangent(Vec3[] points, int index) {
        Vec3 tangent;
        if (index == 0) {
            tangent = points[1].subtract(points[0]);
        } else if (index == points.length - 1) {
            tangent = points[index].subtract(points[index - 1]);
        } else {
            tangent = points[index + 1].subtract(points[index - 1]);
        }
        if (tangent.lengthSqr() < 1.0E-10D) {
            return new Vec3(0.0D, 1.0D, 0.0D);
        }
        return tangent.normalize();
    }

    private static Vec3 tubePoint(Vec3 center, Vec3 right, Vec3 depth,
            float radius, double angle) {
        return center.add(right.scale(Math.cos(angle) * radius))
                .add(depth.scale(Math.sin(angle) * radius));
    }

    /** Interpolate packed ARGB colors, including alpha. */
    public static int lerpColor(int first, int second, float progress) {
        float t = Math.max(0.0F, Math.min(1.0F, progress));
        int a = Math.round(channel(first, 24) + (channel(second, 24) - channel(first, 24)) * t);
        int r = Math.round(channel(first, 16) + (channel(second, 16) - channel(first, 16)) * t);
        int g = Math.round(channel(first, 8) + (channel(second, 8) - channel(first, 8)) * t);
        int b = Math.round(channel(first, 0) + (channel(second, 0) - channel(first, 0)) * t);
        return a << 24 | r << 16 | g << 8 | b;
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

    private static void gradientFace(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 first, int firstColor, Vec3 second, int secondColor,
            Vec3 third, int thirdColor, Vec3 fourth, int fourthColor) {
        vertex(pose, consumer, first, firstColor);
        vertex(pose, consumer, second, secondColor);
        vertex(pose, consumer, third, thirdColor);
        vertex(pose, consumer, fourth, fourthColor);
        vertex(pose, consumer, fourth, fourthColor);
        vertex(pose, consumer, third, thirdColor);
        vertex(pose, consumer, second, secondColor);
        vertex(pose, consumer, first, firstColor);
    }

    private static int channel(int color, int shift) {
        return color >>> shift & 0xFF;
    }

    private static int scaleAlpha(int color, float scale) {
        int alpha = Math.max(0, Math.min(255,
                Math.round(channel(color, 24) * scale)));
        return alpha << 24 | color & 0x00FFFFFF;
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer,
            Vec3 position, int color) {
        consumer.vertex(pose.pose(), (float) position.x, (float) position.y,
                (float) position.z).color(color).endVertex();
    }

    private static void vertex(PoseStack.Pose pose, VertexConsumer consumer,
            float x, float y, float z, float normalX, float normalY,
            float normalZ, int color) {
        consumer.vertex(pose.pose(), x, y, z).color(color).endVertex();
    }
}
