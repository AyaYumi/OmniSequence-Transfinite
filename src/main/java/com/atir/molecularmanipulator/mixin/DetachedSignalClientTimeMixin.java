package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.client.DetachedSignalClientTime;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Timer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Movement, entities, particles and local timers follow the dimension's server-selected rate. */
@Mixin(Timer.class)
public abstract class DetachedSignalClientTimeMixin {
    @Shadow @Final private float msPerTick;

    @Redirect(method = "advanceTime", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/Timer;msPerTick:F", opcode = Opcodes.GETFIELD))
    private float omnisequence$realmClientTickInterval(Timer timer) {
        return DetachedSignalRealm.isRealm(Minecraft.getInstance().level)
                ? msPerTick / DetachedSignalClientTime.scale() : msPerTick;
    }
}
