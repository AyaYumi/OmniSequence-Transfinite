package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.blockentity.CosmicSingularityBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

/** Queues the placed hole for composition against the finished world image. */
public final class CosmicSingularityRenderer implements BlockEntityRenderer<CosmicSingularityBlockEntity> {
    public CosmicSingularityRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(CosmicSingularityBlockEntity singularity, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (singularity.getLevel() == null) return;
        double distance = Vec3.atCenterOf(singularity.getBlockPos()).distanceToSqr(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        if (distance > 64.0D * 64.0D) return;
        poses.pushPose();
        poses.translate(0.5D, 0.5D, 0.5D);
        CosmicSingularityPostRenderer.enqueue(poses, singularity.isWhiteHole(),
                singularity.getLevel().getGameTime() + partialTick, distance);
        poses.popPose();
    }

    @Override
    public int getViewDistance() { return 64; }

    @Override
    public boolean shouldRenderOffScreen(CosmicSingularityBlockEntity singularity) { return true; }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox(CosmicSingularityBlockEntity singularity) {
        var p = singularity.getBlockPos();
        return new net.minecraft.world.phys.AABB(p.getX() - 1.0D, p.getY() - 1.0D, p.getZ() - 1.0D,
                p.getX() + 2.0D, p.getY() + 2.0D, p.getZ() + 2.0D);
    }
}
