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
    public static Group group(int id) { return GROUPS.get(Math.clamp(id, 0, GROUP_COUNT - 1)); }
    public static Group group(int id, int version) { return groups(version).get(Math.clamp(id, 0, GROUP_COUNT - 1)); }
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
        if (group < 3) return new Pose(wrap(ticks * new double[]{.08, -.11, .06}[group]), 0);
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
        double angle = Math.toRadians(facingAngle(facing) + pose.angle), cos = Math.cos(angle), sin = Math.sin(angle);
        double x = local.x - .5, z = local.z - .5;
        return new Vec3(origin.x + x * cos - z * sin, origin.y + local.y + pose.lift, origin.z + x * sin + z * cos);
    }
    public static Vec3 toLocal(Vec3 world, Vec3 origin, Direction facing, Pose pose) {
        double angle = Math.toRadians(facingAngle(facing) + pose.angle), cos = Math.cos(angle), sin = Math.sin(angle);
        double x = world.x - origin.x, z = world.z - origin.z;
        return new Vec3(.5 + x * cos + z * sin, world.y - origin.y - pose.lift, .5 - x * sin + z * cos);
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
