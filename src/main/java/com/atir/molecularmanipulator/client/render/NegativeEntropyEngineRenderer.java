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
 * <p>The four side faces use a copy of the 64x64 panel with the centre
 * one-block cell cut out, so the observation windows stay visible. The top
 * and bottom faces use the full panel. Both shells are slightly outside the
 * 3x3x3 block boundary so they cover the loose casing models.</p>
 */
public final class NegativeEntropyEngineRenderer
        implements BlockEntityRenderer<NegativeEntropyEngineCoreBlockEntity> {
    private static final ResourceLocation FULL_TEXTURE =
            MolecularManipulator.id("textures/block/negative_entropy_engine_formed.png");
    private static final ResourceLocation SIDE_TEXTURE =
            MolecularManipulator.id("textures/block/negative_entropy_engine_formed_side.png");

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
        var side = buffers.getBuffer(RenderType.entityCutoutNoCull(SIDE_TEXTURE));
        var full = buffers.getBuffer(RenderType.entityCutoutNoCull(FULL_TEXTURE));

        // Side faces use the windowed copy; the centre cutout reveals the
        // observation window block behind it.
        quad(side, vertexPose, 0, 0, 1, 1, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1);
        quad(side, vertexPose, 1, 0, 0, 0, 0, 0, 1, 0, 0, 1, 1, 0, 0, 0, -1);
        quad(side, vertexPose, 0, 0, 0, 0, 0, 1, 0, 1, 1, 0, 1, 0, -1, 0, 0);
        quad(side, vertexPose, 1, 0, 1, 1, 0, 0, 1, 0, 0, 1, 1, 1, 1, 0, 0);

        // Top and bottom have no windows, so they use the full reactor face.
        quad(full, vertexPose, 0, 1, 1, 1, 1, 1, 1, 0, 1, 0, 0, 1, 0, 1, 0);
        quad(full, vertexPose, 0, 0, 0, 1, 0, 0, 1, 0, 1, 0, 0, 1, 0, -1, 0);

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
