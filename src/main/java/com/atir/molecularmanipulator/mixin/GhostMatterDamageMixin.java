package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Exposure still uses normal damage/death hooks, without the sound or flinch of a physical hit. */
@Mixin(LivingEntity.class)
public abstract class GhostMatterDamageMixin {
    @Shadow private DamageSource lastDamageSource;
    @Shadow private long lastDamageStamp;

    @Inject(method = "playHurtSound", at = @At("HEAD"), cancellable = true)
    private void omnisequence$quietExposure(DamageSource source, CallbackInfo ci) {
        if (source.is(GhostMatterBlockEntity.DAMAGE_TYPE)) ci.cancel();
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), cancellable = true)
    private void omnisequence$quietClientExposure(DamageSource source, CallbackInfo ci) {
        if (!source.is(GhostMatterBlockEntity.DAMAGE_TYPE)) return;
        lastDamageSource = source;
        lastDamageStamp = ((LivingEntity) (Object) this).level().getGameTime();
        ci.cancel();
    }
}
