package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.network.DetachedSignalTimePayload;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DerivedLevelData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.TickEvent;

/** A real, buildable void dimension. Its persistent clock never changes any other dimension's time. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class DetachedSignalRealm {
    public static final ResourceLocation EFFECTS = MolecularManipulator.id("detached_signal_realm");
    public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, EFFECTS);
    public static final BlockPos ANCHOR = new BlockPos(0, 64, 0);
    public static final long MIDNIGHT = 18000L;
    private static final String DATA_NAME = "molecularmanipulator_detached_signal_realm";

    private DetachedSignalRealm() {}

    public static boolean isRealm(Level level) {
        return level != null && DIMENSION.equals(level.dimension());
    }

    public static void initialize(ServerLevel level) {
        ((LevelData) level.getLevelData()).clock = level.getDataStorage().computeIfAbsent(Clock::load, Clock::new, DATA_NAME);
    }

    /** Make one shared plan per global server tick, used by both the world and player connections. */
    public static void prepareTime(ServerLevel level) {
        if (!isRealm(level)) return;
        var data = (LevelData) level.getLevelData();
        data.localTicks = 1;
        boolean previous = data.timeFlow.accelerated();
        data.localTicks = data.timeFlow.advance();
        if (previous != data.timeFlow.accelerated()) {
            DetachedSignalTimePayload.sendToPlayersInDimension(level, new DetachedSignalTimePayload(data.timeFlow.accelerated()));
        }
    }

    public static int localTicks(ServerLevel level) {
        return isRealm(level) ? ((LevelData) level.getLevelData()).localTicks : 1;
    }

    public static float timeScale(ServerLevel level) {
        return isRealm(level) ? ((LevelData) level.getLevelData()).timeFlow.scale() : 1F;
    }

    private static void syncTime(ServerPlayer player) {
        if (isRealm(player.level())) {
            var data = (LevelData) player.level().getLevelData();
            DetachedSignalTimePayload.sendToPlayer(player, new DetachedSignalTimePayload(data.timeFlow.accelerated()));
        }
    }

    /** Called once for each local world tick, not once for each global server tick. */
    public static void advanceTime(ServerLevel level) {
        var data = (LevelData) level.getLevelData();
        data.setGameTime(data.getGameTime() + 1L);
    }

    public static boolean isAdditionalTick(ServerLevel level) {
        return isRealm(level) && ((LevelData) level.getLevelData()).additionalTick;
    }

    public static void setAdditionalTick(ServerLevel level, boolean additional) {
        ((LevelData) level.getLevelData()).additionalTick = additional;
    }

    public static void ensureAnchor(ServerLevel level) {
        var clock = ((LevelData) level.getLevelData()).clock;
        if (clock.anchorCreated) return;
        level.getChunkAt(ANCHOR);
        // Only the initial empty world gets a block; revisiting never rebuilds or erases a base.
        if (level.getBlockState(ANCHOR).isAir()) level.setBlockAndUpdate(ANCHOR, Blocks.BEDROCK.defaultBlockState());
        clock.anchorCreated = true;
        clock.setDirty();
    }

    /** Any source dimension qualifies; its own minimum build height defines where its void begins. */
    public static boolean canEnter(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !isRealm(player.level())
                && LanternSimulation.isActive(player)
                // Intercept before vanilla's minBuildHeight - 64 void damage, including low-health players.
                && player.getY() < player.level().getMinBuildHeight() - 32D
                && !SimulationLanternLights.illuminates(player.level(), player.getEyePosition());
    }

    public static boolean enter(ServerPlayer player) {
        var destination = player.server.getLevel(DIMENSION);
        if (destination == null) return false;
        ensureAnchor(destination);
        // Preserve later player construction, including anything above the original one-block anchor.
        int y = Math.max(ANCHOR.getY() + 1, destination.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ANCHOR.getX(), ANCHOR.getZ()));
        player.stopRiding();
        player.teleportTo(destination, .5D, y, .5D, player.getYRot(), player.getXRot());
        if (!isRealm(player.level())) return false;
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        player.connection.send(new ClientboundSetTitlesAnimationPacket(15, 70, 25));
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable(
                "dimension.molecularmanipulator.detached_signal_realm.subtitle")));
        player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable(
                "dimension.molecularmanipulator.detached_signal_realm")));
        player.playNotifySound(SoundEvents.AMBIENT_CAVE.value(), SoundSource.AMBIENT, .55F, .7F);
        return true;
    }

    // Forgiving Void handles this same depth at NORMAL priority; enter before it returns the player to the sky.
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void beforePlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player && canEnter(player)) enter(player);
    }

    @SubscribeEvent
    public static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isRealm(player.level())) {
            ensureAnchor(player.serverLevel());
            syncTime(player);
        }
    }

    @SubscribeEvent
    public static void loggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTime(player);
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncTime(player);
    }

    /** Vanilla's other dimensions share overworld time; this dimension intentionally does not. */
    public static final class LevelData extends DerivedLevelData {
        private Clock clock;
        private final DetachedSignalTime timeFlow = new DetachedSignalTime(new Random());
        private int localTicks = 1;
        private boolean additionalTick;

        public LevelData(WorldData worldData, ServerLevelData wrapped) {
            super(worldData, wrapped);
        }

        @Override public long getGameTime() { return clock == null ? 0L : clock.time; }
        @Override public void setGameTime(long time) {
            clock.time = time;
            clock.setDirty();
        }
        @Override public long getDayTime() { return MIDNIGHT; }
        @Override public void setDayTime(long time) { }
        @Override public boolean isRaining() { return false; }
        @Override public boolean isThundering() { return false; }
        @Override public int getXSpawn() { return ANCHOR.getX(); }
        @Override public int getYSpawn() { return ANCHOR.getY() + 1; }
        @Override public int getZSpawn() { return ANCHOR.getZ(); }
        @Override public float getSpawnAngle() { return 0F; }
    }

    private static final class Clock extends SavedData {
        private long time;
        private boolean anchorCreated;

        private static Clock load(CompoundTag tag) {
            var clock = new Clock();
            clock.time = tag.getLong("Time");
            clock.anchorCreated = tag.getBoolean("AnchorCreated");
            return clock;
        }

        @Override public CompoundTag save(CompoundTag tag) {
            tag.putLong("Time", time);
            tag.putBoolean("AnchorCreated", anchorCreated);
            return tag;
        }
    }
}
