package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.block.GravityCrystalBlock;
import com.atir.molecularmanipulator.registry.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** A centered five-block cube; attracting crystals rotate player gravity toward their mounting surface. */
public final class GravityCrystalBlockEntity extends BlockEntity {
    public static final double HALF_EXTENT = 2.5;
    public static final double MAX_SPEED = .35;
    private static final double ACCELERATION = .12;

    public GravityCrystalBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.GRAVITY_CRYSTAL_BE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server) || getBlockState().getValue(GravityCrystalBlock.POWERED)) return;
        var facing = getBlockState().getValue(DirectionalBlock.FACING);
        var axis = new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
        var origin = worldPosition.getCenter();
        var bounds = new AABB(origin, origin).inflate(HALF_EXTENT);
        boolean repel = getBlockState().getValue(GravityCrystalBlock.REPEL);
        for (var entity : server.getEntities((Entity) null, bounds, GravityCrystalBlockEntity::canAffect)) {
            if (!affects(server, worldPosition, entity)) continue;
            if (entity instanceof Player && !repel) {
                // The player's tick selects one surface from all overlapping fields, independent of BE tick order.
                continue;
            }
            var velocity = entity.getDeltaMovement();
            double current = velocity.dot(axis);
            double next = Math.clamp(current + (repel ? ACCELERATION : -ACCELERATION), -MAX_SPEED, MAX_SPEED);
            entity.setDeltaMovement(velocity.add(axis.scale(next - current)));
            entity.fallDistance = 0;
            entity.hurtMarked = true;
        }
    }

    public static boolean affects(Level level, BlockPos pos, Entity entity) {
        var origin = pos.getCenter();
        var center = entity.getBoundingBox().getCenter();
        var offset = center.subtract(origin);
        if (Math.abs(offset.x) > HALF_EXTENT || Math.abs(offset.y) > HALF_EXTENT || Math.abs(offset.z) > HALF_EXTENT)
            return false;
        var facing = level.getBlockState(pos).getValue(DirectionalBlock.FACING);
        var start = origin.add(facing.getStepX() * .55, facing.getStepY() * .55, facing.getStepZ() * .55);
        return level.clip(new ClipContext(start, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity))
                .getType() == HitResult.Type.MISS;
    }

    private static boolean canAffect(Entity entity) {
        if (!entity.isAlive() || !(entity instanceof LivingEntity || entity instanceof ItemEntity)) return false;
        return !(entity instanceof Player player) || (!player.isSpectator() && !player.getAbilities().flying);
    }
}
