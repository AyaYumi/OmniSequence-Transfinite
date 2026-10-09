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
    private static final String TEAM_TAG = "bound_team";
    private boolean whiteHoleReady;
    private UUID boundTeam;

    public CosmicSingularityBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.COSMIC_SINGULARITY_BE.get(), pos, state);
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
        if (whiteHole == null || !server.getBlockState(whiteHole).is(ModContent.WHITE_HOLE_BLOCK.get())) return;
        var center = worldPosition.getCenter();
        var captureBox = new AABB(center, center).inflate(CAPTURE_RADIUS);
        for (var entity : server.getEntities((Entity) null, captureBox,
                entity -> !(entity instanceof Player) && !entity.isRemoved())) {
            var offset = center.subtract(entity.position());
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

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (boundTeam != null) tag.putUUID(TEAM_TAG, boundTeam);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        boundTeam = tag.hasUUID(TEAM_TAG) ? tag.getUUID(TEAM_TAG) : null;
        whiteHoleReady = false;
    }
}
