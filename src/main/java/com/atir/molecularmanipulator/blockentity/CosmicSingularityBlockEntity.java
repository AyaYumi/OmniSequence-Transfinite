package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.block.CosmicSingularityBlock;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.world.MultiblockChunkLoading;
import com.atir.molecularmanipulator.world.WhiteHoleRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Stores the placed singularity so its renderer is independent of the large structure. */
public final class CosmicSingularityBlockEntity extends BlockEntity {
    private static final double CAPTURE_RADIUS = 12.0;
    private static final double CAPTURE_DISTANCE = 1.35;
    private static final int ENTITY_SCAN_INTERVAL = 5;
    private boolean whiteHoleReady;
    private final net.minecraft.world.phys.Vec3 captureCenter;
    private final AABB captureBox;
    private java.util.List<Entity> nearbyEntities = java.util.List.of();
    private long nextEntityScan = Long.MIN_VALUE;
    private static final String TEAM_TAG = "bound_team";
    private UUID boundTeam;

    public CosmicSingularityBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.COSMIC_SINGULARITY_BE.get(), pos, state);
        captureCenter = pos.getCenter();
        captureBox = new AABB(captureCenter, captureCenter).inflate(CAPTURE_RADIUS);
    }

    public boolean isWhiteHole() {
        return getBlockState().getBlock() instanceof CosmicSingularityBlock block
                && block.kind() == CosmicSingularityBlock.Kind.WHITE_HOLE;
    }

    @Nullable
    public UUID getBoundTeam() {
        return boundTeam;
    }

    /** Binding is a placement snapshot and stays with the block across reloads. */
    public void bindToPlayerTeam(ServerPlayer player) {
        boundTeam = FtbTeamOwnership.forPlayer(player);
        setChanged();
        if (level instanceof ServerLevel server && isWhiteHole()) {
            WhiteHoleRegistry.register(server, worldPosition, boundTeam);
        }
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        if (isWhiteHole()) {
            if (!whiteHoleReady || server.getGameTime() % 20 == 0) {
                WhiteHoleRegistry.register(server, worldPosition, boundTeam);
                MultiblockChunkLoading.maintain(this);
                whiteHoleReady = true;
            }
            return;
        }

        var whiteHole = WhiteHoleRegistry.nearest(server, worldPosition, boundTeam);
        if (whiteHole == null || !server.getBlockState(whiteHole).is(ModContent.WHITE_HOLE_BLOCK.get())) {
            nearbyEntities = java.util.List.of();
            nextEntityScan = Long.MIN_VALUE;
            return;
        }
        // Reuse the local query for five ticks while keeping attraction smooth.
        // New arrivals wait at most four ticks; removed/teleported entities are
        // checked each tick and never retained as active capture targets.
        if (server.getGameTime() >= nextEntityScan) {
            nearbyEntities = server.getEntities((Entity) null, captureBox,
                    entity -> !(entity instanceof Player) && !entity.isRemoved());
            nextEntityScan = server.getGameTime() + ENTITY_SCAN_INTERVAL;
        }
        for (var entity : nearbyEntities) {
            if (entity.isRemoved() || entity.level() != server || !entity.getBoundingBox().intersects(captureBox)) continue;
            var offset = captureCenter.subtract(entity.position());
            var distance = offset.length();
            if (distance <= CAPTURE_DISTANCE) {
                sendToWhiteHole(entity, whiteHole);
                continue;
            }
            var pull = offset.scale(Math.min(0.12, 0.018 + 0.08 / Math.max(1.0, distance)) / distance);
            entity.setDeltaMovement(entity.getDeltaMovement().scale(0.86).add(pull));
            entity.hurtMarked = true;
        }
    }

    private static void sendToWhiteHole(Entity entity, BlockPos whiteHole) {
        var exit = whiteHole.getCenter().add(0.0, 0.85, 0.0);
        entity.teleportTo(exit.x, exit.y, exit.z);
        entity.setDeltaMovement(0.0, 0.18, 0.0);
        entity.hurtMarked = true;
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        var p = getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX() - 1.0D, p.getY() - 1.0D, p.getZ() - 1.0D,
                p.getX() + 2.0D, p.getY() + 2.0D, p.getZ() + 2.0D);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (boundTeam != null) tag.putUUID(TEAM_TAG, boundTeam);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        boundTeam = tag.hasUUID(TEAM_TAG) ? tag.getUUID(TEAM_TAG) : null;
        whiteHoleReady = false;
    }
}
