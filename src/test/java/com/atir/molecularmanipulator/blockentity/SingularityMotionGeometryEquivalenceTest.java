package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The batched rigid transforms must be bit-for-bit equivalent to the reference 8-corner transform. */
class SingularityMotionGeometryEquivalenceTest {
    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final double[] RUNNING_TICKS = {0, 1, 7, 13, 47, 59, 60, 61, 159, 160, 161, 1000, 1780, 12345.5};
    private static final double[] DOCKING_PHASES = {0, 17.25, 180, 1780};
    private static final double[] DOCKING_TICKS = {0, 1, 9, 10, 1000};
    private static final List<Vec3> ORIGINS = List.of(
            Vec3.ZERO,
            new Vec3(15.5, 120, -31.5),
            new Vec3(-472.25, 6.75, 91.125));
    private static final List<AABB> BOXES = List.of(
            new AABB(0, 0, 0, 1, 1, 1),
            new AABB(-48.3, 0.05, -48.7, 48.9, 128.95, 47.2),
            new AABB(28.75, 37.5, -.25, 28.75, 37.5, -.25),
            new AABB(-.001, 1_000_000, .001, .001, 1_000_002, .003));
    private static final List<Vec3> POINTS = List.of(
            Vec3.ZERO,
            new Vec3(48.5, 65, .5),
            new Vec3(-.125, -1024.75, 4096.5));

    @Test void precomputedFramesMatchWorldAndLocalReferenceForEveryFacingAndPoseMode() {
        for (var origin : ORIGINS) for (var facing : FACINGS)
            for (int group = 0; group < SingularityMotionGeometry.GROUP_COUNT; group++) {
                for (double ticks : RUNNING_TICKS)
                    assertFrames(origin, facing, SingularityMotionGeometry.runningPose(group, ticks));
                for (double phase : DOCKING_PHASES) for (double ticks : DOCKING_TICKS)
                    assertFrames(origin, facing, SingularityMotionGeometry.dockingPose(group, phase, ticks));
            }
    }

    @Test void sweptProbeBoundsMatchTheReferenceTransformForEveryGroupAndMode() {
        for (var origin : ORIGINS) for (var facing : FACINGS)
            for (int group = 0; group < SingularityMotionGeometry.GROUP_COUNT; group++) {
                var parts = SingularityMotionGeometry.group(group).parts();
                var samples = List.of(parts.get(0), parts.get(parts.size() / 2), parts.get(parts.size() - 1));
                for (boolean docking : new boolean[]{false, true})
                    for (double base : new double[]{0, 37.5, 1780}) {
                        var poses = new SingularityMotionGeometry.Pose[5];
                        var frames = new SingularityMotionGeometry.Frame[poses.length];
                        for (int sample = 0; sample < poses.length; sample++) {
                            int step = sample * 3;
                            poses[sample] = docking
                                    ? SingularityMotionGeometry.dockingPose(group, base, base + step)
                                    : SingularityMotionGeometry.runningPose(group, base + step);
                            frames[sample] = SingularityMotionGeometry.frame(origin, facing, poses[sample]);
                        }
                        for (var part : samples) {
                            AABB expected = null;
                            for (var pose : poses) {
                                var sample = SingularityMotionGeometry.transform(new AABB(part.pos()),
                                        p -> legacyWorld(p, origin, facing, pose));
                                expected = expected == null ? sample : expected.minmax(sample);
                            }
                            expected = expected.inflate(.02);
                            var actual = SingularityMotionGeometry.sweepBlock(part.x(), part.y(), part.z(), frames).inflate(.02);
                            assertEquals(expected, actual, "Sweep and reference must cover identical probes");
                        }
                    }
            }
    }

    @Test void sweepCoversEveryAuthoredPartInEveryLayoutVersion() {
        var origin = new Vec3(15.5, 120, -31.5);
        for (int version : new int[]{2, 3, 4}) for (var facing : FACINGS) {
            int parts = 0;
            for (var group : SingularityMotionGeometry.groups(version)) {
                var frames = new SingularityMotionGeometry.Frame[5];
                for (int sample = 0; sample < frames.length; sample++)
                    frames[sample] = SingularityMotionGeometry.frame(origin, facing,
                            SingularityMotionGeometry.runningPose(group.id(), 1780 + sample * 3));
                for (var part : group.parts()) {
                    AABB expected = frames[0].apply(new AABB(part.pos()));
                    for (int sample = 1; sample < frames.length; sample++)
                        expected = expected.minmax(frames[sample].apply(new AABB(part.pos())));
                    assertEquals(expected, SingularityMotionGeometry.sweepBlock(part.x(), part.y(), part.z(), frames),
                            "Every authored part must keep the conservative five-sample sweep");
                    parts++;
                }
            }
            assertEquals(SingularityMotionGeometry.groups(version).stream().mapToInt(g -> g.parts().size()).sum(), parts,
                    "All authored moving parts must be covered");
        }
    }

    @Test void dockingEndsAtAnIdentityFrameForEveryFacing() {
        for (var origin : ORIGINS) for (var facing : FACINGS) for (int group = 0; group < SingularityMotionGeometry.GROUP_COUNT; group++) {
            var docked = SingularityMotionGeometry.dockingPose(group, 37.5, 1000);
            var frame = SingularityMotionGeometry.frame(origin, facing, docked);
            var identity = SingularityMotionGeometry.frame(origin, facing, new SingularityMotionGeometry.Pose(0, 0));
            for (var box : BOXES) assertEquals(identity.apply(box), frame.apply(box), "Docked frames must reach home");
            assertEquals(identity.cos(), frame.cos(), 1e-15, "Docking must not retain angle");
            assertEquals(identity.sin(), frame.sin(), 1e-15, "Docking must not retain angle");
            assertEquals(identity.lift(), frame.lift(), 1e-15, "Docking must not retain lift");
        }
    }

    private static void assertFrames(Vec3 origin, Direction facing, SingularityMotionGeometry.Pose pose) {
        var frame = SingularityMotionGeometry.frame(origin, facing, pose);
        var inverse = SingularityMotionGeometry.inverse(origin, facing, pose);
        for (var box : BOXES) {
            assertEquals(reference(box, p -> legacyWorld(p, origin, facing, pose)), frame.apply(box), "World AABB transform");
            assertEquals(reference(box, p -> legacyLocal(p, origin, facing, pose)), inverse.apply(box), "Local AABB transform");
        }
        for (var point : POINTS) {
            assertEquals(legacyWorld(point, origin, facing, pose), SingularityMotionGeometry.toWorld(point, origin, facing, pose), "Public world transform");
            assertEquals(legacyLocal(point, origin, facing, pose), SingularityMotionGeometry.toLocal(point, origin, facing, pose), "Public local transform");
            assertEquals(legacyWorld(point, origin, facing, pose), frame.apply(point), "World point transform");
            assertEquals(legacyLocal(point, origin, facing, pose), inverse.apply(point), "Local point transform");
        }
    }
    // Verbatim pre-optimization arithmetic, kept as an independent reference.
    private static Vec3 legacyWorld(Vec3 local, Vec3 origin, Direction facing, SingularityMotionGeometry.Pose pose) {
        double angle = Math.toRadians(SingularityMotionGeometry.facingAngle(facing) + pose.angle()), cos = Math.cos(angle), sin = Math.sin(angle);
        double x = local.x - .5, z = local.z - .5;
        return new Vec3(origin.x + x * cos - z * sin, origin.y + local.y + pose.lift(), origin.z + x * sin + z * cos);
    }
    private static Vec3 legacyLocal(Vec3 world, Vec3 origin, Direction facing, SingularityMotionGeometry.Pose pose) {
        double angle = Math.toRadians(SingularityMotionGeometry.facingAngle(facing) + pose.angle()), cos = Math.cos(angle), sin = Math.sin(angle);
        double x = world.x - origin.x, z = world.z - origin.z;
        return new Vec3(.5 + x * cos + z * sin, world.y - origin.y - pose.lift(), .5 - x * sin + z * cos);
    }

    private static AABB reference(AABB box, Function<Vec3, Vec3> transform) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (int corner = 0; corner < 8; corner++) {
            var point = transform.apply(new Vec3((corner & 1) == 0 ? box.minX : box.maxX,
                    (corner & 2) == 0 ? box.minY : box.maxY, (corner & 4) == 0 ? box.minZ : box.maxZ));
            minX = Math.min(minX, point.x); minY = Math.min(minY, point.y); minZ = Math.min(minZ, point.z);
            maxX = Math.max(maxX, point.x); maxY = Math.max(maxY, point.y); maxZ = Math.max(maxZ, point.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
}
