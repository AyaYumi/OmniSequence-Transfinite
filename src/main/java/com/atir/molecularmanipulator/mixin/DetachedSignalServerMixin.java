package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.progress.ChunkProgressListener;
import net.minecraft.world.RandomSequences;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.ServerLevelData;
import net.neoforged.neoforge.event.EventHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Run zero, one or two local ticks, keeping NeoForge level events on that same simulation clock. */
@Mixin(MinecraftServer.class)
public abstract class DetachedSignalServerMixin {
    @WrapOperation(method = "createLevels", at = @At(value = "NEW", target = "net/minecraft/server/level/ServerLevel"))
    private ServerLevel omnisequence$independentRealmClock(MinecraftServer server, Executor dispatcher,
            LevelStorageSource.LevelStorageAccess storage, ServerLevelData data, ResourceKey<Level> dimension,
            LevelStem stem, ChunkProgressListener listener, boolean debug, long seed,
            List<CustomSpawner> spawners, boolean tickTime, RandomSequences sequences, Operation<ServerLevel> original) {
        boolean realm = DetachedSignalRealm.DIMENSION.equals(dimension);
        if (realm) data = new DetachedSignalRealm.LevelData(server.getWorldData(), data);
        var level = original.call(server, dispatcher, storage, data, dimension, stem, listener, debug,
                seed, spawners, realm ? false : tickTime, sequences);
        if (realm) DetachedSignalRealm.initialize(level);
        return level;
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPre(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"))
    private void omnisequence$prepareRealmTime(Level level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        var serverLevel = (ServerLevel) level;
        DetachedSignalRealm.prepareTime(serverLevel);
        if (DetachedSignalRealm.localTicks(serverLevel) > 0) original.call(level, hasTimeLeft);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE", remap = false,
            target = "Lnet/neoforged/neoforge/event/EventHooks;fireLevelTickPost(Lnet/minecraft/world/level/Level;Ljava/util/function/BooleanSupplier;)V"))
    private void omnisequence$finishRealmTime(Level level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        if (DetachedSignalRealm.localTicks((ServerLevel) level) > 0) original.call(level, hasTimeLeft);
    }

    @WrapOperation(method = "tickChildren", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"))
    private void omnisequence$variableRealmTick(ServerLevel level, BooleanSupplier hasTimeLeft, Operation<Void> original) {
        boolean variableTime = DetachedSignalRealm.isRealm(level) && level.tickRateManager().runsNormally();
        int ticks = DetachedSignalRealm.localTicks(level);
        if (ticks == 0) {
            // Even during a half-speed pause, chunk loading, ticket expiry and saving must keep working.
            level.getChunkSource().tick(hasTimeLeft, false);
            return;
        }
        for (int tick = 0; tick < ticks; tick++) {
            // The caller fires the first Pre and last Post. Supply the pair between local ticks.
            if (tick > 0) EventHooks.fireLevelTickPre(level, hasTimeLeft);
            if (variableTime) {
                DetachedSignalRealm.advanceTime(level);
                DetachedSignalRealm.setAdditionalTick(level, tick > 0);
            }
            try {
                original.call(level, hasTimeLeft);
            } finally {
                if (variableTime) DetachedSignalRealm.setAdditionalTick(level, false);
            }
            if (tick < ticks - 1) EventHooks.fireLevelTickPost(level, hasTimeLeft);
        }
    }
}
