package com.atir.molecularmanipulator.mixin;

import com.atir.molecularmanipulator.world.gravity.GravityController;
import com.atir.molecularmanipulator.world.gravity.GravityFrame;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ScreenEffectRenderer.class)
public abstract class GravityScreenEffectMixin {
    @Inject(method = "getOverlayBlock", remap = false, at = @At("HEAD"), cancellable = true)
    private static void omnisequence$rotatedObstruction(Player player,
            CallbackInfoReturnable<Pair<BlockState, BlockPos>> cir) {
        if (!GravityController.rotated(player)) return;

        // Vanilla samples X/Z around the feet. Under sideways gravity those points hit the supporting wall.
        var direction = GravityController.direction(player);
        Vec3 eyes = player.getEyePosition();
        var pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 8; i++) {
            Vec3 offset = new Vec3(
                    (((i >> 0) & 1) - 0.5F) * player.getBbWidth() * 0.8F,
                    (((i >> 1) & 1) - 0.5F) * 0.1F,
                    (((i >> 2) & 1) - 0.5F) * player.getBbWidth() * 0.8F);
            Vec3 sample = eyes.add(GravityFrame.toWorld(direction, offset));
            pos.set(sample.x, sample.y, sample.z);
            BlockState state = player.level().getBlockState(pos);
            if (state.getRenderShape() != RenderShape.INVISIBLE && state.isViewBlocking(player.level(), pos)) {
                cir.setReturnValue(Pair.of(state, pos.immutable()));
                return;
            }
        }
        cir.setReturnValue(null);
    }
}
