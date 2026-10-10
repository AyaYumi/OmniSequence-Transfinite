package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.GhostMatterBlockEntity;
import com.atir.molecularmanipulator.block.GhostMatterBlock;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

/** Sparse, diffuse detection mist with a slow shared breathing glow above the solid crystal deposit. */
public final class GhostMatterRenderer implements BlockEntityRenderer<GhostMatterBlockEntity> {
    private static final ResourceLocation MIST = MolecularManipulator.id("textures/effect/ghost_matter_mist.png");

    public GhostMatterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(GhostMatterBlockEntity matter, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        var level = matter.getLevel();
        if (level == null || matter.getBlockState().getValue(BlockStateProperties.WATERLOGGED)) return;
        var mc = Minecraft.getInstance();
        var camera = mc.gameRenderer.getMainCamera();
        var origin = matter.getBlockPos().getCenter().add(0, -.25, 0);
        boolean dispersed = matter.getBlockState().getValue(GhostMatterBlock.DISPERSED);
        float strength = matter.mistStrength(partialTick);
        if (strength <= 0) return;
        double time = (level.getGameTime() + partialTick) * .035;
        double seed = Math.floorMod(matter.getBlockPos().asLong(), 997) * .013;
        var buffer = buffers.getBuffer(OmniRenderLayers.additiveEmissive(MIST));
        float breath = (float) (.5 + .5 * Math.sin(time * 1.2 + seed));
        for (int i = 0; i < (dispersed ? 9 : 5); i++) {
            double phase = seed + i * 2.399963;
            double radius = dispersed ? .5 + (i % 3) * .29 : .35 + (i % 3) * .23;
            double x = Math.cos(phase + time * .18) * radius;
            double z = Math.sin(phase + time * .18) * radius;
            double y = .3 + (i % 3) * (dispersed ? .32 : .16) + Math.sin(time * .55 + phase) * .07;
            var point = origin.add(x, y, z);
            // Keep the visible cloud on the same exposed side as the real hazard.
            if (level.clip(new ClipContext(origin, point, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, mc.player)).getType() != HitResult.Type.MISS) continue;
            float pulse = (float) (.5 + .5 * Math.sin(time * 1.7 + phase));
            float width = .36F + pulse * .08F;
            float height = .53F + pulse * .09F;
            float alpha = (.1F + breath * .09F) * (.85F + pulse * .15F) * strength;
            poses.pushPose();
            poses.translate(.5 + x, .25 + y, .5 + z);
            poses.mulPose(camera.rotation());
            poses.mulPose(Axis.ZP.rotation((float) (Math.sin(phase + time * .12) * .12)));
            var pose = poses.last();
            vertex(buffer, pose, -width, -height, 0, 1, alpha);
            vertex(buffer, pose, width, -height, 1, 1, alpha);
            vertex(buffer, pose, width, height, 1, 0, alpha);
            vertex(buffer, pose, -width, height, 0, 0, alpha);
            poses.popPose();
        }
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y,
            float u, float v, float alpha) {
        buffer.vertex(pose.pose(), x, y, 0).color(.18F, 1F, .72F, alpha).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(), 0, 0, 1).endVertex();
    }

    @Override public int getViewDistance() { return 48; }


}
