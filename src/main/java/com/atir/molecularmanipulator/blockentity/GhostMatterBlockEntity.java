package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.block.GhostMatterBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class GhostMatterBlockEntity extends BlockEntity {
    public static final ResourceKey<DamageType> DAMAGE_TYPE = ResourceKey.create(Registries.DAMAGE_TYPE,
            MolecularManipulator.id("ghost_matter"));
    public static final double RADIUS = 1.5;
    public static final int DISPERSION_TICKS = 1200;
    private static final String LAST_EXPOSURE = "omnisequenceGhostExposure";
    private long expiresAt;

    public GhostMatterBlockEntity(BlockPos pos, BlockState state) {
        super(ModContent.GHOST_MATTER_BE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel server)) return;
        if (getBlockState().getValue(GhostMatterBlock.DISPERSED)) {
            if (expiresAt == 0) {
                expiresAt = server.getGameTime() + DISPERSION_TICKS;
                setChanged();
                server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                        getBlockState().setValue(GhostMatterBlock.DISPERSED, false)),
                        worldPosition.getX() + .5, worldPosition.getY() + .2, worldPosition.getZ() + .5,
                        32, .35, .15, .35, .08);
                server.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_BREAK, SoundSource.BLOCKS, .7F, 1.5F);
            }
            if (server.getGameTime() >= expiresAt) {
                server.setBlock(worldPosition, getBlockState().getFluidState().createLegacyBlock(), 3);
                return;
            }
        }
        if (server.getGameTime() % 5 != 0 || getBlockState().getValue(BlockStateProperties.WATERLOGGED)) return;
        var origin = origin(worldPosition);
        var damage = new DamageSource(server.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DAMAGE_TYPE));
        for (var entity : server.getEntitiesOfClass(LivingEntity.class, new AABB(origin, origin).inflate(RADIUS))) {
            if (!entity.isAlive() || entity.isInWaterOrBubble()
                    || entity instanceof Player player && (player.isCreative() || player.isSpectator())) continue;
            if (!affects(entity)) continue;
            // Overlapping deposits form one hazard, rather than multiplying damage by the number of blocks.
            var data = entity.getPersistentData();
            if (data.contains(LAST_EXPOSURE) && data.getLong(LAST_EXPOSURE) == server.getGameTime()) continue;
            data.putLong(LAST_EXPOSURE, server.getGameTime());
            entity.hurt(damage, 1F);
        }
    }

    public static Vec3 origin(BlockPos pos) { return pos.getCenter().add(0, -.25, 0); }

    public boolean affects(LivingEntity entity) {
        return level != null && !getBlockState().getValue(BlockStateProperties.WATERLOGGED)
                && mistStrength(0) > 0 && exposes(level, worldPosition, entity);
    }

    public static boolean exposes(Level level, BlockPos pos, LivingEntity entity) {
        var origin = origin(pos);
        var center = entity.getBoundingBox().getCenter();
        var delta = center.subtract(origin);
        if (Math.abs(delta.x) > RADIUS || Math.abs(delta.y) > RADIUS || Math.abs(delta.z) > RADIUS
                || entity.isInWaterOrBubble()) return false;
        return level.clip(new ClipContext(origin, center, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, entity)).getType() == HitResult.Type.MISS;
    }

    public long expiresAt() { return expiresAt; }

    public float mistStrength(float partialTick) {
        if (!getBlockState().getValue(GhostMatterBlock.DISPERSED) || expiresAt == 0 || level == null) return 1F;
        return (float) com.atir.molecularmanipulator.util.MathCompat.clamp((expiresAt - level.getGameTime() - partialTick) / 60.0, 0, 1);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putLong("MistExpiresAt", expiresAt);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        expiresAt = tag.getLong("MistExpiresAt");
    }

    @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
