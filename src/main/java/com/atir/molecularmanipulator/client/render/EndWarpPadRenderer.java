package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.WarpPadGeometry;
import com.atir.molecularmanipulator.world.WarpAnimation;
import com.atir.molecularmanipulator.blockentity.EndWarpPadBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;

/** The approved geometry stays static; symbols, pearl and timed energy effects render over it. */
public final class EndWarpPadRenderer implements BlockEntityRenderer<EndWarpPadBlockEntity> {
    private static final ResourceLocation ATLAS = MolecularManipulator.id("textures/block/warp_pad.png");
    private final ItemStack pearl = new ItemStack(Items.ENDER_PEARL);

    public EndWarpPadRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(EndWarpPadBlockEntity pad, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        var facing = pad.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        poses.pushPose();
        poses.translate(.5, 0, .5);
        poses.mulPose(Axis.YP.rotationDegrees(-90 * WarpPadGeometry.quarterTurns(facing)));
        poses.translate(-.5, 0, -.5);
        var out = buffers.getBuffer(RenderType.entityCutoutNoCull(ATLAS));
        int phase = pad.animationPhase();
        float age = pad.animationAge(partialTick);
        for (int i = 0; i < WarpPadGeometry.KEYS.length; i++) {
            float brightness = phase == WarpAnimation.CHARGING && WarpAnimation.active(phase, age)
                    ? .3F + .7F * WarpAnimation.keyLock(age, i) : 1;
            drawKey(out, poses.last(), WarpPadGeometry.KEYS[i], WarpPadGeometry.GLYPH_UV[pad.glyph(i)], brightness);
        }
        if (pad.hasPearl()) {
            var center = WarpPadGeometry.PEARL.center().add(WarpPadGeometry.PEARL.normal().scale(.13));
            poses.pushPose();
            poses.translate(center.x, center.y, center.z);
            // The vanilla flat pearl faces the same inward/up plane as the socket.
            poses.mulPose(Axis.XP.rotationDegrees(-45));
            poses.scale(.44F, .44F, .44F);
            Minecraft.getInstance().getItemRenderer().renderStatic(pearl, ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, poses, buffers, pad.getLevel(), 0);
            poses.popPose();
        }
        if (WarpAnimation.active(phase, age)) {
            WarpPadEffects.render(poses, buffers.getBuffer(OmniRenderLayers.additiveColor()), phase, age);
        }
        poses.popPose();
    }

    private static void drawKey(VertexConsumer out, PoseStack.Pose pose, WarpPadGeometry.Surface surface, double[] uv,
            float brightness) {
        var normal = surface.normal();
        for (int i = 0; i < surface.vertices().length; i++) {
            var vertex = surface.vertices()[i].add(normal.scale(.002));
            var corner = surface.uvCorners()[i];
            float u = (float)(uv[0] + (uv[2] - uv[0]) * corner[0]) / 16;
            float v = (float)(uv[1] + (uv[3] - uv[1]) * corner[1]) / 16;
            out.addVertex(pose, (float)vertex.x, (float)vertex.y, (float)vertex.z)
                    .setColor(Math.round(255 * brightness), Math.round(255 * brightness), Math.round(255 * brightness), 255)
                    .setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LightTexture.FULL_BRIGHT)
                    .setNormal(pose, (float)normal.x, (float)normal.y, (float)normal.z);
        }
    }

    @Override public int getViewDistance() { return 96; }
    @Override public AABB getRenderBoundingBox(EndWarpPadBlockEntity pad) {
        var pos = pad.getBlockPos();
        return new AABB(pos.getX() - 1, pos.getY() - 1, pos.getZ() - 1,
                pos.getX() + 2, pos.getY() + 2, pos.getZ() + 2);
    }
}
