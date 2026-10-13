package com.atir.molecularmanipulator.world.gravity;

import com.atir.molecularmanipulator.block.GravityCrystalBlock;
import com.atir.molecularmanipulator.blockentity.GravityCrystalBlockEntity;
import com.atir.molecularmanipulator.integration.AntarchyGravityCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Choose an exposed mounting surface once per player tick; metadata synchronizes its physical frame. */
public final class GravityController {
    public static final double SWITCH_MARGIN = .35;
    public static final int SWITCH_CONFIRM_TICKS = 3;
    private GravityController() {}

    public static Direction direction(Entity entity) {
        if (!(entity instanceof GravityPlayerAccess access)) return Direction.DOWN;
        var down = access.omnisequence$gravity();
        return down != Direction.DOWN && AntarchyGravityCompat.controlsGravity(entity) ? Direction.DOWN : down;
    }

    public static boolean rotated(Entity entity) {
        return direction(entity) != Direction.DOWN;
    }

    public static boolean eligible(Player player) {
        return player.isAlive() && !player.isSpectator() && !player.getAbilities().flying
                && !player.isPassenger() && !player.isSleeping() && !player.isFallFlying()
                && !player.isInWaterOrBubble() && !player.isInLava()
                && !AntarchyGravityCompat.controlsGravity(player);
    }

    public static void tick(Player player) {
        if (player.level().isClientSide) return;
        var access = (GravityPlayerAccess) player;
        if (AntarchyGravityCompat.controlsGravity(player)) {
            releaseToExternalGravity(player);
            return;
        }
        var source = access.omnisequence$gravitySource();
        if (!eligible(player)) {
            access.omnisequence$gravitySource(null);
            access.omnisequence$gravityCandidate(null, 0);
            change(player, Direction.DOWN);
            return;
        }
        boolean currentValid = source != null && valid(player, source);
        var center = player.getBoundingBox().getCenter();
        BlockPos best = currentValid ? source : null;
        double bestScore = best == null ? Double.POSITIVE_INFINITY : surfaceScore(player, best);
        double extent = GravityCrystalBlockEntity.HALF_EXTENT;
        for (var pos : BlockPos.betweenClosed(BlockPos.containing(center.subtract(extent, extent, extent)),
                BlockPos.containing(center.add(extent, extent, extent)))) {
            if (!valid(player, pos)) continue;
            double score = surfaceScore(player, pos);
            if (score < bestScore - 1.0E-6 || Math.abs(score - bestScore) <= 1.0E-6
                    && (best == null || pos.compareTo(best) < 0)) {
                best = pos.immutable();
                bestScore = score;
            }
        }
        if (best == null) {
            access.omnisequence$gravitySource(null);
            access.omnisequence$gravityCandidate(null, 0);
            change(player, Direction.DOWN);
            return;
        }
        if (currentValid && !best.equals(source) && down(player, best) != down(player, source)) {
            if (bestScore + SWITCH_MARGIN >= surfaceScore(player, source)) {
                best = source;
                access.omnisequence$gravityCandidate(null, 0);
            } else {
                int ticks = best.equals(access.omnisequence$gravityCandidate())
                        ? access.omnisequence$gravityCandidateTicks() + 1 : 1;
                access.omnisequence$gravityCandidate(best, ticks);
                if (ticks < SWITCH_CONFIRM_TICKS) best = source;
            }
        } else access.omnisequence$gravityCandidate(null, 0);
        if (change(player, down(player, best))) {
            access.omnisequence$gravitySource(best);
            if (best.equals(access.omnisequence$gravityCandidate())) access.omnisequence$gravityCandidate(null, 0);
        }
        player.fallDistance = 0;
    }

    private static boolean valid(Player player, BlockPos source) {
        if (!eligible(player) || !player.level().hasChunkAt(source)) return false;
        var state = player.level().getBlockState(source);
        return state.getBlock() instanceof GravityCrystalBlock && !state.getValue(GravityCrystalBlock.POWERED)
                && !state.getValue(GravityCrystalBlock.REPEL)
                && player.getBoundingBox().getCenter().subtract(source.getCenter()).dot(
                        Vec3.atLowerCornerOf(state.getValue(DirectionalBlock.FACING).getNormal())) >= -.5
                && GravityCrystalBlockEntity.affects(player.level(), source, player);
    }

    private static Direction down(Player player, BlockPos source) {
        return player.level().getBlockState(source).getValue(DirectionalBlock.FACING).getOpposite();
    }

    private static double surfaceScore(Player player, BlockPos source) {
        var outward = GravityFrame.up(down(player, source));
        var offset = player.getBoundingBox().getCenter().subtract(source.getCenter());
        double normal = offset.dot(outward);
        double tangent = Math.sqrt(Math.max(0, offset.lengthSqr() - normal * normal));
        // Prefer the nearby installation plane; a small tangential penalty keeps local crystal patches distinct.
        return normal + .5 + tangent * .2;
    }

    public static boolean change(Player player, Direction down) {
        var access = (GravityPlayerAccess) player;
        if (AntarchyGravityCompat.controlsGravity(player)) {
            if (down != Direction.DOWN) return false;
            releaseToExternalGravity(player);
            return true;
        }
        var previous = direction(player);
        if (previous == down) return true;
        var dimensions = player.getDimensions(player.getPose());
        var center = player.getBoundingBox().getCenter();
        var feet = center.subtract(GravityFrame.up(down).scale(dimensions.height() / 2.0));
        Vec3 safe = null;
        // Keep the body's center when possible. Push outward only when rotating would clip a wall.
        for (int step = 0; step <= 20 && safe == null; step++) {
            for (var offset : step == 0 ? new Direction[]{down.getOpposite()} : Direction.values()) {
                var candidate = feet.add(offset.getStepX() * step * .1, offset.getStepY() * step * .1,
                        offset.getStepZ() * step * .1);
                if (player.level().noCollision(player, GravityFrame.bounds(down, candidate, dimensions).deflate(1.0E-7))) {
                    safe = candidate;
                    break;
                }
            }
        }
        if (safe == null) return false;
        var look = GravityFrame.toLocal(down, player.getLookAngle());
        float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
        float pitch = (float) Math.toDegrees(Math.asin(Math.clamp(-look.y, -1, 1)));
        access.omnisequence$gravity(down);
        player.setPos(safe);
        player.setYRot(yaw);
        player.setXRot(pitch);
        player.yRotO = yaw;
        player.xRotO = pitch;
        player.yBodyRot = player.yBodyRotO = yaw;
        player.yHeadRot = player.yHeadRotO = yaw;
        player.fallDistance = 0;
        player.setOnGround(false);
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            // Send self metadata before teleport; the local collision box must use the new frame immediately.
            serverPlayer.connection.send(new ClientboundSetEntityDataPacket(player.getId(),
                    List.of(access.omnisequence$gravityData())));
            serverPlayer.connection.teleport(safe.x, safe.y, safe.z, yaw, pitch);
        }
        return true;
    }

    private static void releaseToExternalGravity(Player player) {
        var access = (GravityPlayerAccess) player;
        access.omnisequence$gravitySource(null);
        access.omnisequence$gravityCandidate(null, 0);
        var previous = access.omnisequence$gravity();
        if (previous == Direction.DOWN) return;
        access.omnisequence$gravity(Direction.DOWN);
        // Antarchy has already chosen its frame and feet. Only resolve overlap left by the old wall body.
        var feet = player.position();
        var bounds = player.getBoundingBox();
        Vec3 safe = feet;
        if (!player.level().noCollision(player, bounds.deflate(1.0E-7))) {
            search: for (int step = 1; step <= 20; step++) {
                for (var offset : new Direction[]{previous.getOpposite(), Direction.DOWN, Direction.UP,
                        Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    var displacement = Vec3.atLowerCornerOf(offset.getNormal()).scale(step * .1);
                    if (player.level().noCollision(player, bounds.move(displacement).deflate(1.0E-7))) {
                        safe = feet.add(displacement);
                        break search;
                    }
                }
            }
        }
        player.setPos(safe);
        player.fallDistance = 0;
        if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
            serverPlayer.connection.send(new ClientboundSetEntityDataPacket(player.getId(),
                    List.of(access.omnisequence$gravityData())));
            if (!safe.equals(feet)) serverPlayer.connection.teleport(safe.x, safe.y, safe.z,
                    player.getYRot(), player.getXRot());
        }
    }
}
