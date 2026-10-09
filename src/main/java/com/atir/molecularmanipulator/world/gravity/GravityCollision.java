package com.atir.molecularmanipulator.world.gravity;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/** Resolve the gravity axis first, then the two walking axes, including stairs and sneak edges. */
public final class GravityCollision {
    private GravityCollision() {}

    public static Vec3 collide(Player player, Vec3 local) {
        var down = GravityController.direction(player);
        var box = player.getBoundingBox();
        var world = GravityFrame.toWorld(down, local);
        var upStep = GravityFrame.up(down).scale(player.maxUpStep());
        var search = box.expandTowards(world).expandTowards(upStep).expandTowards(upStep.scale(-1)).inflate(1.0E-7);
        var shapes = new ArrayList<>(player.level().getEntityCollisions(player, search));
        player.level().getBlockCollisions(player, search).forEach(shapes::add);
        var border = player.level().getWorldBorder();
        if (border.isInsideCloseToBorder(player, search)) shapes.add(border.getCollisionShape());
        var result = resolve(down, box, local, shapes);
        boolean walkingBlocked = Math.abs(local.x - result.x) > 1.0E-7 || Math.abs(local.z - result.z) > 1.0E-7;
        if (walkingBlocked && player.maxUpStep() > 0 && (player.onGround() || local.y < 0 && local.y != result.y)) {
            var raised = resolve(down, box, new Vec3(0, player.maxUpStep(), 0), shapes);
            var across = resolve(down, box.move(GravityFrame.toWorld(down, raised)), new Vec3(local.x, 0, local.z), shapes);
            var lower = resolve(down, box.move(GravityFrame.toWorld(down, raised.add(across))),
                    new Vec3(0, local.y - raised.y, 0), shapes);
            var stepped = raised.add(across).add(lower);
            if (stepped.horizontalDistanceSqr() > result.horizontalDistanceSqr()) result = stepped;
        }
        return result;
    }

    private static Vec3 resolve(Direction down, AABB box, Vec3 local, List<VoxelShape> shapes) {
        double[] values = {local.x, local.y, local.z};
        int[] order = Math.abs(local.x) < Math.abs(local.z) ? new int[]{1, 2, 0} : new int[]{1, 0, 2};
        for (int coordinate : order) {
            Vec3 unit = GravityFrame.toWorld(down, coordinate == 0 ? new Vec3(1, 0, 0)
                    : coordinate == 1 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1));
            var axis = Math.abs(unit.x) > .5 ? Direction.Axis.X : Math.abs(unit.y) > .5 ? Direction.Axis.Y : Direction.Axis.Z;
            double sign = unit.x + unit.y + unit.z;
            values[coordinate] = Shapes.collide(axis, box, shapes, values[coordinate] * sign) * sign;
            box = box.move(unit.scale(values[coordinate]));
        }
        return new Vec3(values[0], values[1], values[2]);
    }

    public static Vec3 sneak(Player player, Vec3 local) {
        if (!player.isShiftKeyDown() || !player.onGround() || local.y > 0) return local;
        double x = local.x, z = local.z;
        while (x != 0 && unsupported(player, x, 0)) x = reduce(x);
        while (z != 0 && unsupported(player, 0, z)) z = reduce(z);
        while (x != 0 && z != 0 && unsupported(player, x, z)) { x = reduce(x); z = reduce(z); }
        return new Vec3(x, local.y, z);
    }

    private static boolean unsupported(Player player, double x, double z) {
        return player.level().noCollision(player, player.getBoundingBox().move(GravityFrame.toWorld(
                GravityController.direction(player), new Vec3(x, -player.maxUpStep(), z))));
    }

    private static double reduce(double amount) {
        return Math.abs(amount) <= .05 ? 0 : amount - Math.signum(amount) * .05;
    }
}
