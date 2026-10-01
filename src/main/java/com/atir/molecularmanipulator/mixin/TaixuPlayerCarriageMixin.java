package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.TaixuMotionWorld;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class TaixuPlayerCarriageMixin {
    @Shadow public ServerPlayer player;
    @Shadow private int aboveGroundTickCount;
    @Shadow private boolean clientIsFloating;
    @Inject(method = "tick", at = @At("HEAD"))
    private void taixu$supportedPlatform(CallbackInfo ci) {
        if (TaixuMotionWorld.supported(player)) { aboveGroundTickCount = 0; clientIsFloating = false; }
    }
}
