package com.atir.molecularmanipulator.client.render;

import com.atir.molecularmanipulator.block.LabPlatformGeometry;
import com.atir.molecularmanipulator.blockentity.HighEnergyLabCradleBlockEntity;
import com.atir.molecularmanipulator.world.LabCrystallization;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.Vec3;

/** Visible stacks on the approved trays and the local negative-time experiment. */
public final class HighEnergyLabCradleRenderer implements BlockEntityRenderer<HighEnergyLabCradleBlockEntity> {
    public HighEnergyLabCradleRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(HighEnergyLabCradleBlockEntity cradle, float partialTick, PoseStack poses,
            MultiBufferSource buffers, int light, int overlay) {
        float age = cradle.renderProgress(partialTick);
        boolean beforeIntake = !cradle.working().isEmpty() && age < LabCrystallization.INTAKE_START;
        ItemStack displayed = !cradle.stored().isEmpty() ? cradle.stored() : beforeIntake ? cradle.working() : ItemStack.EMPTY;
        var facing = cradle.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        float yaw = 90 * LabPlatformGeometry.turns(facing);
        poses.pushPose();
        poses.translate(.5, 0, .5);
        poses.mulPose(Axis.YP.rotationDegrees(-yaw));
        poses.translate(-.5, 0, -.5);
        boolean input = cradle.input();
        double x = input ? .25 : .75;
        if (!displayed.isEmpty()) {
            poses.pushPose();
            poses.translate(x, .95, .75);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            poses.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            poses.scale(1.6F, 1.6F, 1.6F);
            Minecraft.getInstance().getItemRenderer().renderStatic(displayed, ItemDisplayContext.FIXED,
                    light, OverlayTexture.NO_OVERLAY, poses, buffers, cradle.getLevel(), 0);
            poses.popPose();
            String label = Integer.toString(cradle.stored().getCount() + (beforeIntake ? cradle.working().getCount() : 0));
            poses.pushPose();
            poses.translate(x, 1.35, .75);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            poses.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            poses.scale(.014F, -.014F, .014F);
            var font = Minecraft.getInstance().font;
            font.drawInBatch(label, -font.width(label) / 2F, 0, 0xFFF1F3FF, false, poses.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0x500A0E15, LightTexture.FULL_BRIGHT);
            poses.popPose();
        }
        boolean advancing = cradle.advancing();
        if (input && beforeIntake) startup(poses, buffers, age, advancing);
        if (input && cradle.running() && (!beforeIntake || !advancing)) {
            String message = advancing ? "message.molecularmanipulator.lab.running" : "message.molecularmanipulator.lab.paused";
            int color = advancing ? 0xFFAEF3D0 : 0xFFFFD58A;
            var label = Component.translatable(message).getVisualOrderText();
            poses.pushPose();
            poses.translate(x, 1.7, .75);
            poses.mulPose(Axis.YP.rotationDegrees(yaw));
            poses.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            poses.scale(.012F, -.012F, .012F);
            var font = Minecraft.getInstance().font;
            font.drawInBatch(label, -font.width(label) / 2F, 0, color, false, poses.last().pose(), buffers,
                    Font.DisplayMode.NORMAL, 0x700A0E15, LightTexture.FULL_BRIGHT);
            poses.popPose();
        }
        poses.popPose();
        if (!input || !cradle.running()) return;
        var anchor = cradle.anchor();
        if (anchor == null || !anchor.powered()) return;
        poses.pushPose();
        var origin = cradle.getBlockPos();
        poses.translate(anchor.getX() - origin.getX(), anchor.getY() - origin.getY(), anchor.getZ() - origin.getZ());
        poses.mulPose(Axis.YP.rotationDegrees(-anchor.getYRot()));
        poses.translate(0, -4, -50);
        float spriteYaw = anchor.getYRot();
        if (LabCrystallization.intake(age)) {
            float t = LabCrystallization.portion(age, LabCrystallization.INTAKE_START, LabCrystallization.ABSORBED);
            float scale = (1.6F + 3 * (float)Math.sin(Math.PI * t)) * (1 - .8F * t * t * t);
            item(poses, buffers, cradle.working(), LabCrystallization.inputPosition(age), scale, 1, spriteYaw);
            trail(poses, buffers, age, false);
        }
        if (LabCrystallization.delivery(age)) {
            float t = LabCrystallization.portion(age, LabCrystallization.ABSORBED, LabCrystallization.DURATION);
            item(poses, buffers, LabCrystallization.resultFor(cradle.working()), LabCrystallization.outputPosition(age),
                    5.5F - 3.9F * t, 1, spriteYaw);
            trail(poses, buffers, age, true);
        }
        poses.popPose();
    }

    private static void startup(PoseStack poses, MultiBufferSource buffers, float age, boolean advancing) {
        var out = buffers.getBuffer(OmniRenderLayers.additiveDepthColor());
        float charge = LabCrystallization.portion(age, 0, LabCrystallization.INTAKE_START);
        float pulse = .5F + .5F * (float) Math.sin(age * .3);
        int color = OmniRenderGeometry.argb(advancing ? 0xB9A0FF : 0xFFD58A, 130 + (int) (60 * pulse));
        for (int ring = 0; ring < 2; ring++) {
            OmniRenderGeometry.ring(poses, out, .25F, 1.03F + ring * .18F, .75F,
                    .68F - charge * .25F + ring * .12F, .035F, 24, ring == 0 ? 20 : -20, 0,
                    age * (ring == 0 ? 3 : -3), color);
        }
        OmniRenderGeometry.ring(poses, out, .5F - LabPlatformGeometry.INPUT.getX(), 1.32F,
                .5F - LabPlatformGeometry.INPUT.getZ(), .55F + .06F * pulse, .045F, 24, 0, 0, 0,
                OmniRenderGeometry.argb(advancing ? 0x93EEE5 : 0xFFD58A, 110 + (int) (70 * pulse)));
    }

    private static void trail(PoseStack poses, MultiBufferSource buffers, float age, boolean output) {
        var out = buffers.getBuffer(OmniRenderLayers.additiveDepthColor());
        float start = Math.max(output ? LabCrystallization.ABSORBED : LabCrystallization.INTAKE_START, age - 10);
        for (int i = 0; i < 10; i++) {
            float a = start + (age - start) * i / 10;
            float b = start + (age - start) * (i + 1) / 10;
            Vec3 first = output ? LabCrystallization.outputPosition(a) : LabCrystallization.inputPosition(a);
            Vec3 second = output ? LabCrystallization.outputPosition(b) : LabCrystallization.inputPosition(b);
            if (first.distanceToSqr(second) < .000001) continue;
            int color = OmniRenderGeometry.argb(output ? 0x9FF5CF : 0xACB5FF, 20 + i * 12);
            OmniRenderGeometry.beam(poses.last(), out, first, second, .035F, .035F, color);
        }
    }

    private static void item(PoseStack poses, MultiBufferSource buffers, ItemStack stack, Vec3 position,
            float scale, float opacity, float yaw) {
        var mc = Minecraft.getInstance();
        var sprite = mc.getItemRenderer().getModel(stack, mc.level, null, 0).getParticleIcon();
        poses.pushPose();
        poses.translate(position.x, position.y, position.z);
        poses.mulPose(Axis.YP.rotationDegrees(yaw));
        poses.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        poses.scale(scale, scale, scale);
        var out = buffers.getBuffer(RenderType.entityTranslucent(TextureAtlas.LOCATION_BLOCKS));
        float[] xs = {-.5F, .5F, .5F, -.5F};
        float[] ys = {-.5F, -.5F, .5F, .5F};
        for (int i = 0; i < 4; i++) {
            out.vertex(poses.last().pose(), xs[i], ys[i], 0).color(255, 255, 255, (int)(opacity * 255))
                    .uv(i == 0 || i == 3 ? sprite.getU0() : sprite.getU1(), i < 2 ? sprite.getV1() : sprite.getV0())
                    .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                    .normal(poses.last().normal(), 0, 0, 1).endVertex();
        }
        if (stack.getCount() > 1) {
            poses.translate(.12, -.12, .015);
            poses.scale(.024F, -.024F, .024F);
            mc.font.drawInBatch(Integer.toString(stack.getCount()), 0, 0, ((int)(opacity * 255) << 24) | 0xE7EEFF,
                    false, poses.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        poses.popPose();
    }

    @Override public boolean shouldRenderOffScreen(HighEnergyLabCradleBlockEntity cradle) { return cradle.input(); }
    @Override public int getViewDistance() { return 160; }
}
