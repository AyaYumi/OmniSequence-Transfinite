package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.TaixuCoreBlockEntity;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.client.render.TaixuCoreEffects;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

public final class TaixuCoreRenderer implements BlockEntityRenderer<TaixuCoreBlockEntity> {
    public TaixuCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(TaixuCoreBlockEntity core, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        int detail = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (detail <= 0 || core.getLevel() == null) return;
        double distance = Vec3.atCenterOf(core.getBlockPos()).distanceToSqr(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (distance > 48 * 48) return;
        boolean full = detail > 1 && distance < 24 * 24;
        // A bounded clock avoids float precision jitter after long-running worlds.
        float time = (core.getLevel().getGameTime() % 24000L) + partialTick;
        float phase = (core.getBlockPos().asLong() & 255) / 255.0F * 360.0F;
        poses.pushPose();
        poses.translate(0.5, 0.5, 0.5);
        TaixuCoreEffects.render(poses, buffers.getBuffer(OmniRenderLayers.translucentEmissiveColor()),
                time, phase, full, false);
        TaixuCoreEffects.render(poses, buffers.getBuffer(OmniRenderLayers.additiveColor()),
                time, phase, full, true);
        poses.popPose();
    }

    @Override
    public int getViewDistance() { return 48; }
}
