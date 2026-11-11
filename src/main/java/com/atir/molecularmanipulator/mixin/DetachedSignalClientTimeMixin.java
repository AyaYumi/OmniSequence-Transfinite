package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.DetachedSignalClientTime;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Movement, entities, particles and local timers follow the dimension's server-selected rate. */
@Mixin(Minecraft.class)
public abstract class DetachedSignalClientTimeMixin {
    @Shadow public ClientLevel level;

    @ModifyReturnValue(method = "getTickTargetMillis", at = @At("RETURN"))
    private float omnisequence$realmClientTickInterval(float millis) {
        return DetachedSignalRealm.isRealm(level) && level.tickRateManager().runsNormally()
                ? millis / DetachedSignalClientTime.scale() : millis;
    }
}
