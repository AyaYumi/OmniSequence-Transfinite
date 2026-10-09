package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;

/** Vanilla health synchronization independently starts a hurt animation after the damage event. */
@Mixin(LocalPlayer.class)
public abstract class GhostMatterLocalHealthMixin {
    @WrapMethod(method = "hurtTo")
    private void omnisequence$quietHealthSync(float health, Operation<Void> original) {
        var player = (LocalPlayer) (Object) this;
        var source = player.getLastDamageSource();
        boolean exposure = health < player.getHealth() && source != null && source.is(GhostMatterBlockEntity.DAMAGE_TYPE);
        int previousHurt = player.hurtTime;
        int previousDuration = player.hurtDuration;
        original.call(health);
        if (exposure) {
            // Preserve a physical hit that was already animating before this exposure update.
            player.hurtTime = previousHurt;
            player.hurtDuration = previousDuration;
        }
    }
}
