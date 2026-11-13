package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import com.atir.molecularmanipulator.blockentity.StarSeaCalculationMainframeBlockEntity;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;

/** The placed building and held preview use the exact same M1 geometry and transform. */
public final class StarSeaCalculationRenderer implements BlockEntityRenderer<StarSeaCalculationMainframeBlockEntity> {
    public StarSeaCalculationRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(StarSeaCalculationMainframeBlockEntity core, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        poses.pushPose();
        StarSeaCalculationModel.transform(poses, core.getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        StarSeaCalculationModel.draw(poses, buffers, light, overlay, 1, 1, 1, 1, false);
        poses.popPose();

        int quality = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (!core.isRunning() || core.getLevel() == null || quality <= 0) return;
        var bounds = StarSeaCalculationMainframeBlock.bounds(core.getBlockPos(),
                core.getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(bounds.getCenter());
        // 12000 seconds is a common multiple of the falling and glyph-sequence periods.
        double time = (core.getLevel().getGameTime() % 240_000L + partialTick) / 20D;
        poses.pushPose();
        StarSeaCalculationModel.transformBuilding(poses, core.getBlockState().getValue(HorizontalDirectionalBlock.FACING));
        StarSeaCalculationEffects.render(poses, SingularityEffectLayers.crystal(buffers), true, quality, distance, time, false);
        StarSeaCalculationEffects.render(poses, SingularityEffectLayers.glow(buffers), true, quality, distance, time, true);
        poses.popPose();
    }

    @Override
    public int getViewDistance() { return 128; }

    public AABB getRenderBoundingBox(StarSeaCalculationMainframeBlockEntity core) {
        return StarSeaCalculationMainframeBlock.bounds(core.getBlockPos(),
                core.getBlockState().getValue(HorizontalDirectionalBlock.FACING))
                .inflate(StarSeaCalculationEffects.BOUNDS_MARGIN, 0, StarSeaCalculationEffects.BOUNDS_MARGIN);
    }
}
