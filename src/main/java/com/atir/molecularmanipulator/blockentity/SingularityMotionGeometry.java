package com.atir.molecularmanipulator.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Rigid component geometry, in blueprint coordinates. No client classes or mutable world state. */
public final class SingularityMotionGeometry {
    public static final int GROUP_COUNT = 11;
    public static final int ALL_GROUPS = (1 << GROUP_COUNT) - 1;
    public static final int VERSION = 3;
    private static final double[] RING_SPEEDS = {.08, -.11, .06};
    public record Pose(double angle, double lift) { }
    public record Group(int id, List<SingularityStructure.Part> parts, Map<BlockPos, SingularityStructure.Part> index, AABB bounds) { }
    private static final Map<BlockPos, Integer> TOWERS = towerIndex(SingularityStructure.VERSION);
    private static final Map<BlockPos, Integer> CLASSIC_TOWERS = towerIndex(SingularityStructure.EMBEDDED_VERSION);
    private static final List<Group> GROUPS = createGroups(SingularityStructure.VERSION, VERSION);
    private static final List<Group> CLASSIC_GROUPS = createGroups(SingularityStructure.EMBEDDED_VERSION, VERSION);
    private static final List<Group> PREVIOUS_GROUPS = createGroups(SingularityStructure.VERSION, 2);
    private static final List<Group> PREVIOUS_CLASSIC_GROUPS = createGroups(SingularityStructure.EMBEDDED_VERSION, 2);
    public static List<Group> groups() { return GROUPS; }
    public static List<Group> groups(int version) { return version >= SingularityStructure.VERSION ? GROUPS : CLASSIC_GROUPS; }
    static List<Group> groups(int version, int geometryVersion) {
        return geometryVersion == 2 ? version >= SingularityStructure.VERSION ? PREVIOUS_GROUPS : PREVIOUS_CLASSIC_GROUPS : groups(version);
    }
    public static Group group(int id) { return GROUPS.get(com.atir.molecularmanipulator.util.MathCompat.clamp(id, 0, GROUP_COUNT - 1)); }
    public static Group group(int id, int version) { return groups(version).get(com.atir.molecularmanipulator.util.MathCompat.clamp(id, 0, GROUP_COUNT - 1)); }
    public static int groupOf(SingularityStructure.Part part) { return groupOf(part, SingularityStructure.VERSION); }
    public static int groupOf(SingularityStructure.Part part, int version) {
        var tower = (version >= SingularityStructure.VERSION ? TOWERS : CLASSIC_TOWERS).get(part.pos());
        if (tower != null) return tower;
        return ringOf(part);
    }
    static int groupOf(SingularityStructure.Part part, int version, int geometryVersion) {
        return geometryVersion == 2 ? previousGroupOf(part, version) : groupOf(part, version);
    }
    private static int previousGroupOf(SingularityStructure.Part part, int version) {
        int reach = version >= SingularityStructure.VERSION ? 4 : 2;
        for (int i = 0; i < 8; i++) {
            int x = towerX(i), z = towerZ(i), top = i % 2 == 0 ? 105 : 92;
            if (part.y() >= 24 && part.y() <= top && Math.abs(part.x() - x) <= reach && Math.abs(part.z() - z) <= reach
                    && !(part.y() >= 63 && part.y() <= 65 && Math.hypot(part.x(), part.z()) >= 45)
                    || part.pos().equals(SingularityStructure.collectionNodePos(i, version))) return 3 + i;
        }
        return ringOf(part);
    }
    private static int ringOf(SingularityStructure.Part part) {
        int[] radii = {30, 48, 32}, heights = {36, 64, 96};
        double radius = Math.hypot(part.x(), part.z());
        for (int i = 0; i < 3; i++) if (part.y() >= heights[i] - 1 && part.y() <= heights[i] + 1
                && radius <= radii[i] && radius >= radii[i] - 3) return i;
        return -1;
    }
    private static Map<BlockPos, Integer> towerIndex(int version) {
        var index = new HashMap<BlockPos, Integer>();
        for (int tower = 0; tower < 8; tower++)
            for (var part : SingularityStructure.towerBodyParts(tower, version)) index.put(part.pos(), 3 + tower);
        return Map.copyOf(index);
    }
    public static int towerX(int i) { return (int) Math.round(Math.sin(Math.PI * i / 4) * 41); }
    public static int towerZ(int i) { return (int) Math.round(Math.cos(Math.PI * i / 4) * 41); }
    public static Pose runningPose(int group, double ticks) {
        ticks = Math.max(0, ticks);
        if (group < 3) return new Pose(wrap(ticks * RING_SPEEDS[group]), 0);
        double ramp = Math.min(1, ticks / 60); ramp = ramp * ramp * (3 - 2 * ramp);
        return new Pose(0, 1.5 * Math.sin(ticks * Math.PI / 160 + (group - 3) * Math.PI / 4) * ramp);
    }
    public static Pose dockingPose(int group, double phase, double ticks) {
        var pose = runningPose(group, phase);
        return new Pose(approach(pose.angle, .22 * Math.max(0, ticks)), approach(pose.lift, .035 * Math.max(0, ticks)));
    }
    private static double approach(double value, double amount) { return Math.copySign(Math.max(0, Math.abs(value) - amount), value); }
    private static double wrap(double angle) { return angle - Math.floor((angle + 180) / 360) * 360; }
    public static double facingAngle(Direction facing) { return switch (facing) { case EAST -> 90; case SOUTH -> 180; case WEST -> -90; default -> 0; }; }
    public static Vec3 origin(BlockPos controller, Direction facing) {
        return origin(controller, facing, SingularityStructure.VERSION);
    }
    public static Vec3 origin(BlockPos controller, Direction facing, int version) {
        var zero = SingularityStructure.worldPos(controller, facing, BlockPos.ZERO, version);
        return new Vec3(zero.getX() + .5, zero.getY(), zero.getZ() + .5);
    }
    public static Vec3 toWorld(Vec3 local, Vec3 origin, Direction facing, Pose pose) {
        return frame(origin, facing, pose).apply(local);
    }
    public static Vec3 toLocal(Vec3 world, Vec3 origin, Direction facing, Pose pose) {
        return inverse(origin, facing, pose).apply(world);
    }
    /**
     * A rigid Y-rotation plus lift with its trigonometric terms evaluated once.
     * The arithmetic deliberately mirrors {@link #toWorld(Vec3, Vec3, Direction, Pose)}
     * so callers can batch points and boxes without changing a single result.
     */
    public record Frame(double originX, double originY, double originZ, double cos, double sin, double lift) {
        public Vec3 apply(Vec3 local) {
            double x = local.x - .5, z = local.z - .5;
            return new Vec3(originX + x * cos - z * sin, originY + local.y + lift, originZ + x * sin + z * cos);
        }
        public AABB apply(AABB box) {
            double x0 = box.minX - .5, x1 = box.maxX - .5, z0 = box.minZ - .5, z1 = box.maxZ - .5;
            double ax0 = x0 * cos, ax1 = x1 * cos, sz0 = z0 * sin, sz1 = z1 * sin;
            double wx00 = originX + ax0 - sz0, wx01 = originX + ax0 - sz1;
            double wx10 = originX + ax1 - sz0, wx11 = originX + ax1 - sz1;
            double sx0 = x0 * sin, sx1 = x1 * sin, cz0 = z0 * cos, cz1 = z1 * cos;
            double wz00 = originZ + sx0 + cz0, wz01 = originZ + sx0 + cz1;
            double wz10 = originZ + sx1 + cz0, wz11 = originZ + sx1 + cz1;
            double y0 = originY + box.minY + lift, y1 = originY + box.maxY + lift;
            return new AABB(Math.min(Math.min(wx00, wx01), Math.min(wx10, wx11)), y0,
                    Math.min(Math.min(wz00, wz01), Math.min(wz10, wz11)),
                    Math.max(Math.max(wx00, wx01), Math.max(wx10, wx11)), y1,
                    Math.max(Math.max(wz00, wz01), Math.max(wz10, wz11)));
        }
    }
    /** Inverse of {@link Frame}, with the same arithmetic as {@link #toLocal(Vec3, Vec3, Direction, Pose)}. */
    public record InverseFrame(double originX, double originY, double originZ, double cos, double sin, double lift) {
        public Vec3 apply(Vec3 world) {
            double x = world.x - originX, z = world.z - originZ;
            return new Vec3(.5 + x * cos + z * sin, world.y - originY - lift, .5 - x * sin + z * cos);
        }
        public AABB apply(AABB box) {
            double x0 = box.minX - originX, x1 = box.maxX - originX, z0 = box.minZ - originZ, z1 = box.maxZ - originZ;
            double ax0 = x0 * cos, ax1 = x1 * cos, sz0 = z0 * sin, sz1 = z1 * sin;
            double wx00 = .5 + ax0 + sz0, wx01 = .5 + ax0 + sz1;
            double wx10 = .5 + ax1 + sz0, wx11 = .5 + ax1 + sz1;
            double sx0 = x0 * sin, sx1 = x1 * sin, cz0 = z0 * cos, cz1 = z1 * cos;
            double wz00 = .5 - sx0 + cz0, wz01 = .5 - sx0 + cz1;
            double wz10 = .5 - sx1 + cz0, wz11 = .5 - sx1 + cz1;
            double y0 = box.minY - originY - lift, y1 = box.maxY - originY - lift;
            return new AABB(Math.min(Math.min(wx00, wx01), Math.min(wx10, wx11)), y0,
                    Math.min(Math.min(wz00, wz01), Math.min(wz10, wz11)),
                    Math.max(Math.max(wx00, wx01), Math.max(wx10, wx11)), y1,
                    Math.max(Math.max(wz00, wz01), Math.max(wz10, wz11)));
        }
    }
    public static Frame frame(Vec3 origin, Direction facing, Pose pose) {
        double angle = Math.toRadians(facingAngle(facing) + pose.angle), cos = Math.cos(angle), sin = Math.sin(angle);
        return new Frame(origin.x, origin.y, origin.z, cos, sin, pose.lift());
    }
    public static InverseFrame inverse(Vec3 origin, Direction facing, Pose pose) {
        double angle = Math.toRadians(facingAngle(facing) + pose.angle), cos = Math.cos(angle), sin = Math.sin(angle);
        return new InverseFrame(origin.x, origin.y, origin.z, cos, sin, pose.lift());
    }
    /**
     * Union of one local box over a sequence of poses. This is exactly the union
     * produced by transforming the box with each {@link Frame} and taking min/max,
     * without allocating eight corner vectors per sample.
     */
    public static AABB sweep(AABB local, Frame... frames) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX;
        double maxX = Double.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        for (var frame : frames) {
            double x0 = local.minX - .5, x1 = local.maxX - .5, z0 = local.minZ - .5, z1 = local.maxZ - .5;
            double ax0 = x0 * frame.cos(), ax1 = x1 * frame.cos(), sz0 = z0 * frame.sin(), sz1 = z1 * frame.sin();
            double wx00 = frame.originX() + ax0 - sz0, wx01 = frame.originX() + ax0 - sz1;
            double wx10 = frame.originX() + ax1 - sz0, wx11 = frame.originX() + ax1 - sz1;
            double sx0 = x0 * frame.sin(), sx1 = x1 * frame.sin(), cz0 = z0 * frame.cos(), cz1 = z1 * frame.cos();
            double wz00 = frame.originZ() + sx0 + cz0, wz01 = frame.originZ() + sx0 + cz1;
            double wz10 = frame.originZ() + sx1 + cz0, wz11 = frame.originZ() + sx1 + cz1;
            minX = Math.min(minX, Math.min(Math.min(wx00, wx01), Math.min(wx10, wx11)));
            minZ = Math.min(minZ, Math.min(Math.min(wz00, wz01), Math.min(wz10, wz11)));
            maxX = Math.max(maxX, Math.max(Math.max(wx00, wx01), Math.max(wx10, wx11)));
            maxZ = Math.max(maxZ, Math.max(Math.max(wz00, wz01), Math.max(wz10, wz11)));
            double y0 = frame.originY() + local.minY + frame.lift(), y1 = frame.originY() + local.maxY + frame.lift();
            minY = Math.min(minY, y0); maxY = Math.max(maxY, y1);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
    public static AABB sweepBlock(int x, int y, int z, Frame... frames) {
        return sweep(new AABB(x, y, z, x + 1, y + 1, z + 1), frames);
    }
    public static AABB transform(AABB box, java.util.function.Function<Vec3, Vec3> transform) {
        double minX = Double.POSITIVE_INFINITY, minY = minX, minZ = minX, maxX = -minX, maxY = -minX, maxZ = -minX;
        for (int i = 0; i < 8; i++) {
            var p = transform.apply(new Vec3((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ));
            minX = Math.min(minX, p.x); minY = Math.min(minY, p.y); minZ = Math.min(minZ, p.z);
            maxX = Math.max(maxX, p.x); maxY = Math.max(maxY, p.y); maxZ = Math.max(maxZ, p.z);
        }
        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }
    private static List<Group> createGroups(int version, int geometryVersion) {
        var lists = new ArrayList<List<SingularityStructure.Part>>();
        for (int i = 0; i < GROUP_COUNT; i++) lists.add(new ArrayList<>());
        for (var part : SingularityStructure.parts(version)) { int group = groupOf(part, version, geometryVersion); if (group >= 0) lists.get(group).add(part); }
        var result = new ArrayList<Group>();
        for (int i = 0; i < GROUP_COUNT; i++) {
            var index = new HashMap<BlockPos, SingularityStructure.Part>(); AABB bounds = null;
            for (var part : lists.get(i)) { index.put(part.pos(), part); bounds = bounds == null ? new AABB(part.pos()) : bounds.minmax(new AABB(part.pos())); }
            result.add(new Group(i, List.copyOf(lists.get(i)), Map.copyOf(index), bounds));
        }
        return List.copyOf(result);
    }
}
