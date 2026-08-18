package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Physical, vertex-built visual field for the molecular centre.
 *
 * <p>The renderer deliberately uses the same geometry twice: a depth-writing
 * pass makes the rings and crystals read as real machine parts, while a small
 * additive pass supplies the NOVALITH-style glow. No particles or billboards
 * are involved.</p>
 */
public final class MolecularCenterRenderer
        implements BlockEntityRenderer<MolecularCenterBlockEntity> {

    public MolecularCenterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MolecularCenterBlockEntity center, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || center.getLevel() == null
                || !center.getBlockState().getValue(BlockStateProperties.POWERED)) {
            return;
        }

        Direction facing = center.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Vec3 visualCenter = MolecularCenterStructure.worldPoint(
                center.getBlockPos(), facing,
                MolecularCenterStructure.VISUAL_CENTER_X,
                MolecularCenterStructure.CORE_Y,
                MolecularCenterStructure.VISUAL_CENTER_Z);
        float angle = center.sampleClientVisualAngle(partialTick);
        int mode = center.getClientVisualMode();
        double distanceSquared = visualCenter.distanceToSqr(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        int segments = distanceSquared > 144.0D * 144.0D
                ? 24 : distanceSquared > 80.0D * 80.0D ? 40 : 64;
        float pulse = 1.0F + (float) Math.sin(angle * 0.085F)
                * (mode == 3 ? 0.075F : 0.045F);

        poseStack.pushPose();
        poseStack.translate(visualCenter.x - center.getBlockPos().getX(),
                visualCenter.y - center.getBlockPos().getY(),
                visualCenter.z - center.getBlockPos().getZ());

        VertexConsumer solid = buffers.getBuffer(OmniRenderLayers.solidEmissiveColor());
        drawField(poseStack, solid, angle, mode, pulse, effectLevel, segments,
                center.getFieldColor(), center.getCoreColor(),
                center.getPrimaryRingColor(), center.getSecondaryRingColor(),
                center.getLatticeColor(), false);

        poseStack.pushPose();
        poseStack.scale(1.035F, 1.035F, 1.035F);
        VertexConsumer glow = buffers.getBuffer(OmniRenderLayers.additiveColor());
        drawField(poseStack, glow, angle, mode, pulse, effectLevel, segments,
                center.getFieldColor(), center.getCoreColor(),
                center.getPrimaryRingColor(), center.getSecondaryRingColor(),
                center.getLatticeColor(), true);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void drawField(PoseStack poseStack, VertexConsumer consumer,
            float angle, int mode, float pulse, int effectLevel, int segments,
            int fieldRgb, int coreRgb, int primaryRgb, int secondaryRgb,
            int latticeRgb, boolean glow) {
        int outerAlpha = glow ? 48 : 232;
        int ringAlpha = glow ? 64 : 246;
        int coreAlpha = glow ? 78 : 255;
        int detailAlpha = glow ? 42 : 220;
        float modeScale = mode == 3 ? 1.08F : mode == 4 ? 1.04F : 1.0F;
        float spin = angle * (mode == 1 ? -0.38F : mode == 2 ? 0.46F : 0.31F);

        OmniRenderGeometry.ring(poseStack, consumer, 0.0F, 0.0F, 0.0F,
                16.0F * modeScale, 1.15F, segments,
                0.0F, 0.0F, spin, argb(primaryRgb, ringAlpha));
        OmniRenderGeometry.segmentedRing(poseStack, consumer, 0.0F, 1.15F,
                0.0F, 13.35F * modeScale, 0.82F, 0.62F,
                12, 0.78F, 0.0F, 0.0F, -spin * 0.72F,
                argb(secondaryRgb, outerAlpha));
        OmniRenderGeometry.ring(poseStack, consumer, 0.0F, -1.20F, 0.0F,
                10.2F * modeScale, 0.72F, segments,
                7.0F, 0.0F, spin * 0.45F, argb(fieldRgb, detailAlpha));

        // Three orthogonal accelerator hoops make the centre read as a solid
        // machine rather than a flat circle viewed from one direction.
        OmniRenderGeometry.ring(poseStack, consumer, 0.0F, 0.0F, 0.0F,
                7.35F * pulse, 0.62F, segments,
                90.0F, 0.0F, spin * 0.64F, argb(latticeRgb, ringAlpha));
        OmniRenderGeometry.ring(poseStack, consumer, 0.0F, 0.0F, 0.0F,
                7.35F * pulse, 0.62F, segments,
                0.0F, 90.0F, -spin * 0.48F, argb(secondaryRgb, detailAlpha));

        PoseStack.Pose pose = poseStack.last();
        OmniRenderGeometry.octahedron(pose, consumer,
                new Vec3(0.0D, (pulse - 1.0F) * 2.4D, 0.0D),
                2.35F * pulse, 3.15F * pulse, 2.35F * pulse,
                spin * 1.6F, argb(coreRgb, coreAlpha));
        OmniRenderGeometry.octahedron(pose, consumer,
                new Vec3(0.0D, 0.0D, 0.0D),
                1.15F * pulse, 1.85F * pulse, 1.15F * pulse,
                -spin * 2.2F, argb(fieldRgb, glow ? 92 : 245));

        // Eight thick radial members visually connect the core to the outer
        // ring. The mode changes the rotation and the accent colour, while
        // the geometry itself remains stable and readable.
        int spokes = effectLevel > 1 ? 8 : 4;
        for (int index = 0; index < spokes; index++) {
            double a = Math.PI * 2.0D * index / spokes + Math.toRadians(spin);
            Vec3 inner = new Vec3(Math.cos(a) * 3.5D, 0.0D,
                    Math.sin(a) * 3.5D);
            Vec3 outer = new Vec3(Math.cos(a) * 14.6D,
                    Math.sin(a * 2.0D + angle * 0.02D) * 0.72D,
                    Math.sin(a) * 14.6D);
            OmniRenderGeometry.beam(pose, consumer, inner, outer,
                    0.18F, 0.13F,
                    argb(index % 2 == 0 ? primaryRgb : latticeRgb, detailAlpha));
            if (effectLevel > 1) {
                OmniRenderGeometry.rune(pose, consumer, outer, 0.55F,
                        0.10F, (float) Math.toDegrees(a) + 90.0F,
                        argb(index % 2 == 0 ? secondaryRgb : coreRgb, detailAlpha));
            }
        }

        OmniRenderGeometry.beam(pose, consumer,
                new Vec3(0.0D, -8.0D, 0.0D),
                new Vec3(0.0D, 8.0D, 0.0D),
                0.16F, 0.16F, argb(latticeRgb, detailAlpha));
    }

    private static int argb(int rgb, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (rgb & 0xFFFFFF);
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

    @Override
    public AABB getRenderBoundingBox(MolecularCenterBlockEntity center) {
        Direction facing = center.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Vec3 origin = MolecularCenterStructure.worldPoint(center.getBlockPos(), facing,
                MolecularCenterStructure.VISUAL_CENTER_X,
                MolecularCenterStructure.CORE_Y,
                MolecularCenterStructure.VISUAL_CENTER_Z);
        return new AABB(origin.x - 38.0D, origin.y - 18.0D, origin.z - 38.0D,
                origin.x + 38.0D, origin.y + 18.0D, origin.z + 38.0D);
    }
}
