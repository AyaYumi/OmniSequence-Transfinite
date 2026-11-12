package com.atir.molecularmanipulator.blockentity;

import com.atir.molecularmanipulator.block.WarpPadGeometry;
import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import com.atir.molecularmanipulator.registry.WarpContent;
import com.atir.molecularmanipulator.network.WarpScreenPayload;
import com.atir.molecularmanipulator.world.WarpAnimation;
import com.atir.molecularmanipulator.world.EndWarpTravel;
import java.util.UUID;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Server-owned symbols and the reusable ender-pearl catalyst in one pad core. */
public final class EndWarpPadBlockEntity extends BlockEntity {
    public static final int GLYPH_COUNT = 6;
    private static final int[] LAB_GLYPHS = {0, 1, 2};
    private static final String RETURN_TAG = "Return";
    private static final String PEARL_TAG = "Pearl";
    private static final String[] GLYPH_TAGS = {"Glyph0", "Glyph1", "Glyph2"};
    private boolean returning;
    private boolean pearl;
    private final int[] glyphs = {0, 0, 0};
    private UUID chargingPlayer;
    private int animationPhase;
    private long animationStartedAt;
    private long chargeStartedAt;

    public EndWarpPadBlockEntity(BlockPos pos, BlockState state) {
        super(WarpContent.WARP_PAD_BE.get(), pos, state);
    }

    public static int partForOffset(int dx, int dy, int dz) {
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1 || dz < -1 || dz > 1) {
            throw new IllegalArgumentException("Warp pad part offset must be within -1..1");
        }
        return WarpPadGeometry.partIndex(dx, dy, dz);
    }

    public boolean returning() { return returning; }
    public boolean hasPearl() { return pearl; }
    public int glyph(int index) { return glyphs[index]; }
    public boolean isCharging() { return chargingPlayer != null; }
    public int animationPhase() { return animationPhase; }
    public float animationAge(float partialTick) {
        return level == null ? 0 : level.getGameTime() - animationStartedAt + partialTick;
    }

    public boolean glyphsMatchDestination() {
        for (int i = 0; i < LAB_GLYPHS.length; i++) if (glyphs[i] != LAB_GLYPHS[i]) return false;
        return true;
    }

    public InteractionResult useEmptyHand(Player player, Vec3 worldHit) {
        var canonical = canonicalHit(worldHit);
        if (canonical == null) return InteractionResult.PASS;
        int key = WarpPadGeometry.keyAt(canonical);
        if (key >= 0) {
            if (level == null || level.isClientSide()) return InteractionResult.sidedSuccess(true);
            cancelActiveCharge();
            glyphs[key] = Math.floorMod(glyphs[key] + (player.isShiftKeyDown() ? -1 : 1), GLYPH_COUNT);
            sync();
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.end_warp.glyph", key + 1, glyphs[key] + 1), true);
            level.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, .55F,
                    .8F + glyphs[key] * .09F);
            return InteractionResult.sidedSuccess(false);
        }
        if (WarpPadGeometry.pearlAt(canonical)) {
            if (level == null || level.isClientSide()) return InteractionResult.sidedSuccess(true);
            if (!pearl) return InteractionResult.PASS;
            cancelActiveCharge();
            pearl = false;
            givePearl(player);
            sync();
            level.playSound(null, worldPosition, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, .7F, 1.2F);
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.end_warp.pearl_removed"), true);
            return InteractionResult.sidedSuccess(false);
        }
        if (WarpPadGeometry.activationAt(canonical)) {
            if (level == null || level.isClientSide()) return InteractionResult.sidedSuccess(true);
            confirm(player);
            return InteractionResult.sidedSuccess(false);
        }
        return InteractionResult.PASS;
    }

    public ItemInteractionResult usePearl(Player player, InteractionHand hand, Vec3 worldHit) {
        var canonical = canonicalHit(worldHit);
        if (canonical == null || !WarpPadGeometry.pearlAt(canonical)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level == null || level.isClientSide()) return ItemInteractionResult.sidedSuccess(true);
        if (pearl) {
            player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                    "message.molecularmanipulator.end_warp.pearl_present"), true);
            return ItemInteractionResult.sidedSuccess(false);
        }
        pearl = true;
        if (!player.getAbilities().instabuild) player.getItemInHand(hand).shrink(1);
        sync();
        level.playSound(null, worldPosition, SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.BLOCKS, .8F, 1.0F);
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.molecularmanipulator.end_warp.pearl_inserted"), true);
        return ItemInteractionResult.sidedSuccess(false);
    }

    private Vec3 canonicalHit(Vec3 worldHit) {
        if (level == null) return null;
        var state = getBlockState();
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        return WarpPadGeometry.canonicalPoint(worldHit.subtract(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()), facing);
    }

    private void confirm(Player player) {
        if (!(player instanceof ServerPlayer traveller) || !(level instanceof ServerLevel server)) return;
        if (chargingPlayer != null) return;
        var problem = activationProblem(traveller, server);
        if (problem != null) { message(player, problem); return; }
        try {
            // Prepare the remote structure before starting the visible countdown.
            if (resolveArrival(server, traveller) == null) return;
            chargingPlayer = traveller.getUUID();
            animate(WarpAnimation.CHARGING);
            chargeStartedAt = animationStartedAt;
            PacketDistributor.sendToPlayer(traveller, new WarpScreenPayload(animationPhase, animationStartedAt));
            message(player, "charging");
        } catch (RuntimeException error) {
            failed(traveller, error);
        }
    }

    private String activationProblem(ServerPlayer player, ServerLevel server) {
        if (!player.isAlive() || player.isSpectator()) return "unavailable";
        if (!player.getMainHandItem().isEmpty()) return "empty_hand";
        if (!Level.END.equals(server.dimension())) return "end_only";
        var feet = canonicalHit(player.position());
        if (feet == null || Math.abs(feet.y - WarpPadGeometry.STANDING_OFFSET.y) > .2
                || Math.abs(feet.x - .5) > .85 || Math.abs(feet.z - .5) > .85) return "stand_on_pad";
        if (!pearl) return "need_pearl";
        return glyphsMatchDestination() ? null : "wrong_coordinates";
    }

    private record Arrival(EndWarpPadBlockEntity pad, Vec3 position) {}

    private Arrival resolveArrival(ServerLevel server, ServerPlayer player) {
        var target = EndWarpTravel.destination(server, returning);
        if (!(server.getBlockEntity(target) instanceof EndWarpPadBlockEntity destination)
                || destination == this || destination.returning() == returning) {
            message(player, "destination_missing");
            return null;
        }
        var arrival = Vec3.atLowerCornerOf(target).add(WarpPadGeometry.worldPoint(WarpPadGeometry.STANDING_OFFSET,
                destination.getBlockState().getValue(HorizontalDirectionalBlock.FACING)));
        var box = player.getBoundingBox().move(arrival.subtract(player.position())).deflate(.001);
        if (!server.noCollision(player, box) || server.noCollision(player, box.move(0, -.05, 0))) {
            message(player, "destination_blocked");
            return null;
        }
        return new Arrival(destination, arrival);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, EndWarpPadBlockEntity pad) {
        if (!(level instanceof ServerLevel server)) return;
        if (pad.chargingPlayer == null) {
            if (pad.animationPhase == WarpAnimation.ARRIVING
                    && pad.animationAge(0) >= WarpAnimation.ARRIVAL_TICKS) pad.animate(WarpAnimation.IDLE);
            return;
        }
        var player = server.getServer().getPlayerList().getPlayer(pad.chargingPlayer);
        if (player == null || player.serverLevel() != server || pad.activationProblem(player, server) != null) {
            pad.cancelCharge(player, true);
            return;
        }
        int age = (int)(server.getGameTime() - pad.chargeStartedAt);
        if (age == 3 || age == 9 || age == 15) {
            server.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS,
                    .7F, .8F + (age - 3) / 6 * .2F);
        }
        if (age < WarpAnimation.CHARGE_TICKS) return;
        try {
            // Recheck the destination at the actual transfer, after the charging delay.
            var arrival = pad.resolveArrival(server, player);
            if (arrival == null) { pad.cancelCharge(player, false); return; }
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    player.getX(), player.getY() + .6, player.getZ(), 48, .45, .7, .45, .15);
            server.playSound(null, pos, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, .8F);
            pad.chargingPlayer = null;
            pad.animate(WarpAnimation.IDLE);
            player.stopRiding();
            var point = arrival.position();
            player.teleportTo(server, point.x, point.y, point.z,
                    java.util.Set.of(), player.getYRot(), player.getXRot());
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
            player.hurtMarked = true;
            // Keep another traveller's outgoing lock and its visual timeline intact.
            if (!arrival.pad().isCharging()) arrival.pad().animate(WarpAnimation.ARRIVING);
            PacketDistributor.sendToPlayer(player, new WarpScreenPayload(
                    WarpAnimation.ARRIVING, server.getGameTime()));
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.END_ROD,
                    point.x, point.y + .4, point.z, 48, .5, .65, .5, .04);
            server.playSound(null, arrival.pad().getBlockPos(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.1F);
        } catch (RuntimeException error) {
            pad.failed(player, error);
        }
    }

    private void animate(int phase) {
        animationPhase = phase;
        animationStartedAt = level.getGameTime();
        sync();
    }

    private void cancelActiveCharge() {
        if (chargingPlayer != null && level instanceof ServerLevel server) {
            cancelCharge(server.getServer().getPlayerList().getPlayer(chargingPlayer), true);
        }
    }

    private void cancelCharge(ServerPlayer player, boolean explain) {
        chargingPlayer = null;
        animate(WarpAnimation.IDLE);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new WarpScreenPayload(WarpAnimation.IDLE, 0));
            if (explain) message(player, "cancelled");
        }
    }

    private void failed(ServerPlayer player, RuntimeException error) {
        RateLimitedLog.error("End warp failed at {}", worldPosition, error);
        cancelCharge(player, false);
        message(player, "unavailable");
    }

    @Override
    public void setRemoved() {
        if (chargingPlayer != null && level instanceof ServerLevel server) {
            var player = server.getServer().getPlayerList().getPlayer(chargingPlayer);
            if (player != null) {
                PacketDistributor.sendToPlayer(player, new WarpScreenPayload(WarpAnimation.IDLE, 0));
                message(player, "cancelled");
            }
            chargingPlayer = null;
        }
        super.setRemoved();
    }

    private static void message(Player player, String key) {
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.molecularmanipulator.end_warp." + key), true);
    }

    private void givePearl(Player player) {
        var stack = new ItemStack(Items.ENDER_PEARL);
        if (!player.addItem(stack)) player.drop(stack, false);
    }

    private void sync() {
        setChanged();
        if (level instanceof ServerLevel server) server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean(RETURN_TAG, returning);
        tag.putBoolean(PEARL_TAG, pearl);
        for (int i = 0; i < glyphs.length; i++) tag.putInt(GLYPH_TAGS[i], glyphs[i]);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        // A live /data reload must also release the traveller's screen transition.
        cancelActiveCharge();
        super.loadAdditional(tag, registries);
        returning = tag.getBoolean(RETURN_TAG);
        pearl = tag.getBoolean(PEARL_TAG);
        for (int i = 0; i < glyphs.length; i++) glyphs[i] = Math.floorMod(tag.getInt(GLYPH_TAGS[i]), GLYPH_COUNT);
        chargingPlayer = null;
        animationPhase = tag.getInt("AnimationPhase");
        animationStartedAt = tag.getLong("AnimationStartedAt");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = saveWithoutMetadata(registries);
        tag.putInt("AnimationPhase", animationPhase);
        tag.putLong("AnimationStartedAt", animationStartedAt);
        return tag;
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
