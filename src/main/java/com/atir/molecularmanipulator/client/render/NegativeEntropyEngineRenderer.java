package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.NegativeEntropyEngineCoreBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;

/**
 * Draws the formed 3x3x3 Negative Entropy Engine as one continuous shell.
 *
 * <p>The six faces are slightly outside the 3x3x3 block boundary so they cover
 * the loose casing/window models while the core block entity owns the formed
 * state. The texture is deliberately a standalone 64x64 panel, like ECO's
 * formed controller artwork.</p>
 */
public final class NegativeEntropyEngineRenderer
        implements BlockEntityRenderer<NegativeEntropyEngineCoreBlockEntity> {
    private static final ResourceLocation TEXTURE =
            MolecularManipulator.id("textures/block/negative_entropy_engine_formed.png");

    public NegativeEntropyEngineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(NegativeEntropyEngineCoreBlockEntity core, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        if (!core.isFormed()) {
            return;
        }

        pose.pushPose();
        pose.translate(-1.002F, -1.002F, -1.002F);
        pose.scale(3.004F, 3.004F, 3.004F);
        var vertexPose = pose.last();
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        quad(buffer, vertexPose, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1);
        quad(buffer, vertexPose, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, -1);
        quad(buffer, vertexPose, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
        quad(buffer, vertexPose, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0);
        quad(buffer, vertexPose, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0);
        quad(buffer, vertexPose, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);

        pose.popPose();
    }

    private static void quad(VertexConsumer buffer, PoseStack.Pose pose,
            float x0, float y0, float z0,
            float x1, float y1, float z1,
            float x2, float y2, float z2,
            float x3, float y3, float z3,
            float nx, float ny, float nz) {
        vertex(buffer, pose, x0, y0, z0, 0, 1, nx, ny, nz);
        vertex(buffer, pose, x1, y1, z1, 1, 1, nx, ny, nz);
        vertex(buffer, pose, x2, y2, z2, 1, 0, nx, ny, nz);
        vertex(buffer, pose, x3, y3, z3, 0, 0, nx, ny, nz);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose,
            float x, float y, float z, float u, float v, float nx, float ny, float nz) {
        buffer.addVertex(pose, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(pose, nx, ny, nz);
    }

    @Override
    public AABB getRenderBoundingBox(NegativeEntropyEngineCoreBlockEntity core) {
        var pos = core.getBlockPos();
        return new AABB(
                pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
    }

    @Override
    public int getViewDistance() {
        return 64;
    }
}
