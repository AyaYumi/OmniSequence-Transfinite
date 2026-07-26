package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class MolecularCenterRenderer implements BlockEntityRenderer<MolecularCenterBlockEntity> {
    private static final net.minecraft.resources.ResourceLocation FIELD_TEXTURE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    private static final net.minecraft.resources.ResourceLocation RING_TEXTURE =
            net.minecraft.resources.ResourceLocation.withDefaultNamespace("textures/entity/beacon_beam.png");
    private static final int TORUS_SEGMENTS = 96;
    private static final int TORUS_SIDES = 8;

    public MolecularCenterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MolecularCenterBlockEntity center, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || center.getLevel() == null
                || !center.getBlockState().getValue(BlockStateProperties.POWERED)) {
            return;
        }

        Direction facing = center.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var visualCenter = MolecularCenterStructure.worldPoint(center.getBlockPos(), facing,
                MolecularCenterStructure.VISUAL_CENTER_X, MolecularCenterStructure.CORE_Y,
                MolecularCenterStructure.VISUAL_CENTER_Z);
        int visualMode = center.getClientVisualMode();
        float angle = center.sampleClientVisualAngle(partialTick);
        float pulseStrength = switch (visualMode) {
            case 1 -> 0.055F;
            case 2 -> 0.045F;
            case 3 -> 0.08F;
            default -> 0.035F;
        };
        float pulse = 1.0F + (float) Math.sin(angle * 0.085F) * pulseStrength;

        poseStack.pushPose();
        poseStack.translate(visualCenter.x - center.getBlockPos().getX(),
                visualCenter.y - center.getBlockPos().getY(),
                visualCenter.z - center.getBlockPos().getZ());

        var field = buffers.getBuffer(RenderType.entityTranslucentEmissive(FIELD_TEXTURE));
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * 0.7F));
        renderSphere(poseStack, field, 6.35F * pulse, 36, 18, center.getFieldColor(), 68);
        poseStack.popPose();

        poseStack.pushPose();
        float coreSpin = switch (visualMode) {
            case 1 -> 1.1F;
            case 3 -> -2.2F;
            default -> -1.6F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * coreSpin));
        renderSphere(poseStack, field, 1.82F, 24, 12, center.getCoreColor(), 205);
        poseStack.popPose();

        float textureDirection = visualMode == 1 ? -1.0F : 1.0F;
        float textureOffset = angle * 0.0035F * textureDirection;
        var rings = buffers.getBuffer(RenderType.energySwirl(RING_TEXTURE, textureOffset, -textureOffset * 0.65F));
        renderPrimaryRing(poseStack, rings, angle, visualMode, center.getPrimaryRingColor());
        if (effectLevel > 1) {
            renderSecondaryRings(poseStack, rings, angle, visualMode, center.getSecondaryRingColor());
            renderCoreLattice(poseStack, rings, angle, visualMode, center.getLatticeColor());
        }
        poseStack.popPose();
    }

    private static void renderPrimaryRing(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int color) {
        poseStack.pushPose();
        float spin = switch (visualMode) {
            case 1 -> -0.42F;
            case 2 -> 0.38F;
            case 3 -> 0.58F;
            default -> 0.32F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * spin));
        poseStack.mulPose(Axis.ZP.rotationDegrees(5.0F));
        renderTorus(poseStack, consumer, 14.25F, 0.2F, color, 220);
        poseStack.popPose();
    }

    private static void renderSecondaryRings(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int color) {
        float firstSpin = switch (visualMode) {
            case 1 -> -0.34F;
            case 3 -> 0.74F;
            default -> 0.54F;
        };
        float secondSpin = switch (visualMode) {
            case 1 -> 0.27F;
            case 3 -> -0.68F;
            default -> -0.41F;
        };
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(24.0F + angle * firstSpin));
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(13.0F));
        renderTorus(poseStack, consumer, 12.25F, 0.17F, color, 200);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(118.0F + angle * secondSpin));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-17.0F));
        renderTorus(poseStack, consumer, 11.65F, 0.15F, color, 180);
        poseStack.popPose();
    }

    private static void renderCoreLattice(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int color) {
        poseStack.pushPose();
        float spin = switch (visualMode) {
            case 1 -> 0.55F;
            case 3 -> -1.5F;
            default -> -0.9F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * spin));
        renderTorus(poseStack, consumer, 4.4F, 0.055F, color, 170);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        renderTorus(poseStack, consumer, 4.4F, 0.055F, color, 170);
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        renderTorus(poseStack, consumer, 4.4F, 0.055F, color, 170);
        poseStack.popPose();
    }

    private static void renderSphere(PoseStack poseStack, VertexConsumer consumer, float radius,
            int segments, int stacks, int color, int alpha) {
        var pose = poseStack.last();
        for (int stack = 0; stack < stacks; stack++) {
            double latitude0 = -Math.PI / 2.0 + Math.PI * stack / stacks;
            double latitude1 = -Math.PI / 2.0 + Math.PI * (stack + 1) / stacks;
            float v0 = (float) stack / stacks;
            float v1 = (float) (stack + 1) / stacks;
            for (int segment = 0; segment < segments; segment++) {
                double longitude0 = Math.PI * 2.0 * segment / segments;
                double longitude1 = Math.PI * 2.0 * (segment + 1) / segments;
                float u0 = (float) segment / segments;
                float u1 = (float) (segment + 1) / segments;
                emitSphereVertex(pose, consumer, radius, latitude0, longitude0,
                        u0, v0, color, alpha);
                emitSphereVertex(pose, consumer, radius, latitude1, longitude0,
                        u0, v1, color, alpha);
                emitSphereVertex(pose, consumer, radius, latitude1, longitude1,
                        u1, v1, color, alpha);
                emitSphereVertex(pose, consumer, radius, latitude0, longitude1,
                        u1, v0, color, alpha);
            }
        }
    }

    private static void emitSphereVertex(PoseStack.Pose pose, VertexConsumer consumer, float radius,
            double latitude, double longitude, float u, float v,
            int color, int alpha) {
        float horizontal = (float) Math.cos(latitude);
        float normalX = horizontal * (float) Math.cos(longitude);
        float normalY = (float) Math.sin(latitude);
        float normalZ = horizontal * (float) Math.sin(longitude);
        emitVertex(pose, consumer, normalX * radius, normalY * radius, normalZ * radius,
                normalX, normalY, normalZ, u, v, color, alpha);
    }

    private static void renderTorus(PoseStack poseStack, VertexConsumer consumer, float radius,
            float tubeRadius, int color, int alpha) {
        var pose = poseStack.last();
        for (int segment = 0; segment < TORUS_SEGMENTS; segment++) {
            double major0 = Math.PI * 2.0 * segment / TORUS_SEGMENTS;
            double major1 = Math.PI * 2.0 * (segment + 1) / TORUS_SEGMENTS;
            float u0 = (float) segment / TORUS_SEGMENTS * 4.0F;
            float u1 = (float) (segment + 1) / TORUS_SEGMENTS * 4.0F;
            for (int side = 0; side < TORUS_SIDES; side++) {
                double minor0 = Math.PI * 2.0 * side / TORUS_SIDES;
                double minor1 = Math.PI * 2.0 * (side + 1) / TORUS_SIDES;
                float v0 = (float) side / TORUS_SIDES;
                float v1 = (float) (side + 1) / TORUS_SIDES;
                emitTorusVertex(pose, consumer, radius, tubeRadius, major0, minor0,
                        u0, v0, color, alpha);
                emitTorusVertex(pose, consumer, radius, tubeRadius, major1, minor0,
                        u1, v0, color, alpha);
                emitTorusVertex(pose, consumer, radius, tubeRadius, major1, minor1,
                        u1, v1, color, alpha);
                emitTorusVertex(pose, consumer, radius, tubeRadius, major0, minor1,
                        u0, v1, color, alpha);
            }
        }
    }

    private static void emitTorusVertex(PoseStack.Pose pose, VertexConsumer consumer,
            float radius, float tubeRadius, double major, double minor,
            float u, float v, int color, int alpha) {
        float majorCos = (float) Math.cos(major);
        float majorSin = (float) Math.sin(major);
        float minorCos = (float) Math.cos(minor);
        float minorSin = (float) Math.sin(minor);
        float ringRadius = radius + tubeRadius * minorCos;
        float x = ringRadius * majorCos;
        float y = tubeRadius * minorSin;
        float z = ringRadius * majorSin;
        float normalX = minorCos * majorCos;
        float normalY = minorSin;
        float normalZ = minorCos * majorSin;
        emitVertex(pose, consumer, x, y, z, normalX, normalY, normalZ,
                u, v, color, alpha);
    }

    private static void emitVertex(PoseStack.Pose pose, VertexConsumer consumer,
            float x, float y, float z, float normalX, float normalY, float normalZ,
            float u, float v, int color, int alpha) {
        consumer.vertex(pose.pose(), x, y, z)
                .color(color >> 16 & 0xFF, color >> 8 & 0xFF, color & 0xFF, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(pose.normal(), normalX, normalY, normalZ)
                .endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(MolecularCenterBlockEntity center) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 4096;
    }

    @Override
    public boolean shouldRender(MolecularCenterBlockEntity center, Vec3 cameraPos) {
        return true;
    }

}
