package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.GravityController;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class GravityLocalPlayerMixin {
    @Inject(method = "moveTowardsClosestSpace", at = @At("HEAD"), cancellable = true)
    private void omnisequence$avoidUprightPush(double x, double z, CallbackInfo ci) {
        if (GravityController.rotated((LocalPlayer) (Object) this)) ci.cancel();
    }
}
