package com.atir.molecularmanipulator.world.gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Right-handed player coordinates: local -Y is gravity, local +Y is the head. */
public final class GravityFrame {
    private GravityFrame() {}

    public static Vec3 toWorld(Direction down, Vec3 v) {
        return switch (down) {
            case DOWN -> v;
            case UP -> new Vec3(v.x, -v.y, -v.z);
            case NORTH -> new Vec3(v.x, -v.z, v.y);
            case SOUTH -> new Vec3(v.x, v.z, -v.y);
            case WEST -> new Vec3(v.y, -v.x, v.z);
            case EAST -> new Vec3(-v.y, v.x, v.z);
        };
    }

    public static Vec3 toLocal(Direction down, Vec3 v) {
        return switch (down) {
            case DOWN -> v;
            case UP -> new Vec3(v.x, -v.y, -v.z);
            case NORTH -> new Vec3(v.x, v.z, -v.y);
            case SOUTH -> new Vec3(v.x, -v.z, v.y);
            case WEST -> new Vec3(-v.y, v.x, v.z);
            case EAST -> new Vec3(v.y, -v.x, v.z);
        };
    }

    public static Quaternionf rotation(Direction down) {
        return switch (down) {
            case DOWN -> new Quaternionf();
            case UP -> new Quaternionf().rotationX((float) Math.PI);
            case NORTH -> new Quaternionf().rotationX((float) (Math.PI / 2));
            case SOUTH -> new Quaternionf().rotationX((float) (-Math.PI / 2));
            case WEST -> new Quaternionf().rotationZ((float) (-Math.PI / 2));
            case EAST -> new Quaternionf().rotationZ((float) (Math.PI / 2));
        };
    }

    public static Vec3 up(Direction down) {
        return new Vec3(-down.getStepX(), -down.getStepY(), -down.getStepZ());
    }

    public static AABB bounds(Direction down, Vec3 feet, EntityDimensions dimensions) {
        double half = dimensions.width / 2.0;
        Vec3 a = feet.add(toWorld(down, new Vec3(-half, 0, -half)));
        Vec3 b = feet.add(toWorld(down, new Vec3(half, dimensions.height, half)));
        return new AABB(a, b);
    }
}
