package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Living-player timers tick in the connection, separately from ServerLevel's entity loop. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class DetachedSignalPlayerTickMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerPlayer;doTick()V"))
    private void omnisequence$variablePlayerTime(ServerPlayer player, Operation<Void> original) {
        var level = player.serverLevel();
        for (int tick = 0; tick < DetachedSignalRealm.localTicks(level); tick++) {
            original.call(player);
            if (player.level() != level) break;
        }
    }
}
