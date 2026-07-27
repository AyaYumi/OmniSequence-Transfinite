package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.config.ConfigFileMigration;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ServerLifecycleHooks.class, remap = false)
public abstract class ServerLifecycleHooksMixin {
    @Inject(method = "handleServerAboutToStart", at = @At("HEAD"))
    private static void molecularmanipulator$migrateServerConfig(
            MinecraftServer server, CallbackInfoReturnable<Boolean> callback) {
        ConfigFileMigration.migrateServerConfig(server);
    }
}
