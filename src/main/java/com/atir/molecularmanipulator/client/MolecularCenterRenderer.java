package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Layout-aware fields rendered through the built-in molecular spectral pipeline.
 * The outer field never writes depth, so the transparent sphere cannot mask its core.
 */
public final class MolecularCenterRenderer implements BlockEntityRenderer<MolecularCenterBlockEntity> {
    private static final float FIELD_RADIUS = 6.35F;
    private static final float CORE_RADIUS = 1.82F;

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
        var layout = center.getStructureLayout();
        var visualCenter = MolecularCenterStructure.worldPoint(center.getBlockPos(), facing,
                MolecularCenterStructure.VISUAL_CENTER_X, MolecularCenterStructure.visualCoreY(layout),
                MolecularCenterStructure.VISUAL_CENTER_Z, center.getControllerAnchorLayout());
        int visualMode = center.getClientVisualMode();
        float angle = center.sampleClientVisualAngle(partialTick);
        var crown = center.sampleCrownAnimation(partialTick);
        boolean crystalFeathers = layout == MolecularCenterStructure.StructureLayout.CURRENT;
        if (crystalFeathers) angle = crown.angle();
        float completion = crown.completion(center.getLevel().getGameTime() + (double) partialTick);

        poseStack.pushPose();
        poseStack.translate(visualCenter.x - center.getBlockPos().getX(),
                visualCenter.y - center.getBlockPos().getY(),
                visualCenter.z - center.getBlockPos().getZ());
        boolean fixedOrbits = crystalFeathers;
        if (fixedOrbits) {
            // Match worldPoint's right/back axes so fixed trails follow the blocks
            // for every controller facing, including asymmetric tilted orbits.
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        }

        // Finish emitting each pass before obtaining the next buffer: switching an
        // unfixed RenderType can flush the previous consumer in MultiBufferSource.
        renderLayoutPass(layout, poseStack, buffers.getBuffer(OmniRenderLayers.molecularSpectralDepth()),
                angle, visualMode, effectLevel, false, center.getFieldColor(), center.getCoreColor(),
                center.getPrimaryRingColor(), center.getSecondaryRingColor(), center.getLatticeColor(),
                crown.activity(), completion);
        if (crystalFeathers) {
            FeatherResonanceEffects.renderCoreSurface(poseStack,
                    buffers.getBuffer(OmniRenderLayers.translucentEmissiveColor()), angle,
                    center.getCoreColor(), center.getSecondaryRingColor());
        }
        renderLayoutPass(layout, poseStack, buffers.getBuffer(OmniRenderLayers.molecularSpectralGlow()),
                angle, visualMode, effectLevel, true, center.getFieldColor(), center.getCoreColor(),
                center.getPrimaryRingColor(), center.getSecondaryRingColor(), center.getLatticeColor(),
                crown.activity(), completion);
        poseStack.popPose();
    }

    static void renderLayoutPass(MolecularCenterStructure.StructureLayout layout,
            PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        renderLayoutPass(layout, poseStack, consumer, angle, visualMode, effectLevel, glow,
                fieldColor, coreColor, primaryColor, secondaryColor, latticeColor, visualMode == 0 ? 0 : 1, 0);
    }

    private static void renderLayoutPass(MolecularCenterStructure.StructureLayout layout,
            PoseStack poseStack, VertexConsumer consumer, float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor,
            float activity, float completion) {
        if (layout == MolecularCenterStructure.StructureLayout.CURRENT) {
            renderFeatherPass(poseStack, consumer, angle, activity, completion, effectLevel, glow,
                    fieldColor, coreColor, primaryColor, secondaryColor, latticeColor);
        } else if (layout == MolecularCenterStructure.StructureLayout.LEGACY_1_3_9) {
            renderPass(poseStack, consumer, angle, visualMode, effectLevel, glow,
                    fieldColor, coreColor, primaryColor, secondaryColor, latticeColor);
        }
    }

    // A faceted seed and exposed feather highlights keep the open crystal silhouette.
    static void renderFeatherPass(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        renderFeatherPass(poseStack, consumer, angle, visualMode == 0 ? 0 : 1, 0,
                effectLevel, glow, fieldColor, coreColor, primaryColor, secondaryColor, latticeColor);
    }

    static void renderFeatherPass(PoseStack poseStack, VertexConsumer consumer,
            float angle, float activity, float completion, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        if (effectLevel <= 0) return;
        FeatherResonanceEffects.render(poseStack, consumer, angle, activity, completion, effectLevel > 1, glow,
                fieldColor, coreColor, primaryColor, secondaryColor, latticeColor);
    }

    // The solid amethyst tracks supply the silhouette; light only traces their
    // exposed inner edges. The planes stay fixed while sparse nodes circulate.

    // Package-private for vertex-format, occlusion-role and shape regression tests.
    static void renderPass(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        if (effectLevel <= 0) {
            return;
        }
        boolean detailed = effectLevel > 1;
        poseStack.pushPose();
        float coreSpin = switch (visualMode) {
            case 1 -> 1.1F;
            case 3 -> -2.2F;
            default -> -1.6F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * coreSpin));
        OmniRenderGeometry.sphere(poseStack.last(), consumer, Vec3.ZERO,
                glow ? CORE_RADIUS * 1.12F : CORE_RADIUS,
                detailed ? 12 : 8, detailed ? 24 : 16,
                OmniRenderGeometry.argb(coreColor, glow ? 32 : 205));
        poseStack.popPose();

        renderPrimaryRing(poseStack, consumer, angle, visualMode, detailed, glow, primaryColor);
        if (detailed) {
            renderSecondaryRings(poseStack, consumer, angle, visualMode, glow, secondaryColor);
            renderCoreLattice(poseStack, consumer, angle, visualMode, glow, latticeColor);
        }

        if (glow) {
            float pulseStrength = switch (visualMode) {
                case 1 -> 0.055F;
                case 2 -> 0.045F;
                case 3 -> 0.08F;
                default -> 0.035F;
            };
            float pulse = 1.0F + (float) Math.sin(angle * 0.085F) * pulseStrength;
            poseStack.pushPose();
            poseStack.mulPose(Axis.YP.rotationDegrees(angle * 0.7F));
            OmniRenderGeometry.sphere(poseStack.last(), consumer, Vec3.ZERO,
                    FIELD_RADIUS * pulse, detailed ? 18 : 12, detailed ? 36 : 24,
                    OmniRenderGeometry.argb(fieldColor, 42));
            poseStack.popPose();
        }
    }

    private static void renderPrimaryRing(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, boolean detailed, boolean glow, int color) {
        poseStack.pushPose();
        float spin = switch (visualMode) {
            case 1 -> -0.42F;
            case 2 -> 0.38F;
            case 3 -> 0.58F;
            default -> 0.32F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * spin));
        poseStack.mulPose(Axis.ZP.rotationDegrees(5.0F));
        ring(poseStack, consumer, 14.25F, 0.2F, detailed, glow, color, glow ? 42 : 220);
        poseStack.popPose();
    }

    private static void renderSecondaryRings(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, boolean glow, int color) {
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
        ring(poseStack, consumer, 12.25F, 0.17F, true, glow, color, glow ? 36 : 200);
        poseStack.popPose();

        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(118.0F + angle * secondSpin));
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(-17.0F));
        ring(poseStack, consumer, 11.65F, 0.15F, true, glow, color, glow ? 32 : 180);
        poseStack.popPose();
    }

    private static void renderCoreLattice(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, boolean glow, int color) {
        poseStack.pushPose();
        float spin = switch (visualMode) {
            case 1 -> 0.55F;
            case 3 -> -1.5F;
            default -> -0.9F;
        };
        poseStack.mulPose(Axis.YP.rotationDegrees(angle * spin));
        ring(poseStack, consumer, 4.4F, 0.055F, true, glow, color, glow ? 26 : 170);
        poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
        ring(poseStack, consumer, 4.4F, 0.055F, true, glow, color, glow ? 26 : 170);
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        ring(poseStack, consumer, 4.4F, 0.055F, true, glow, color, glow ? 26 : 170);
        poseStack.popPose();
    }

    private static void ring(PoseStack poseStack, VertexConsumer consumer,
            float radius, float tubeRadius, boolean detailed, boolean glow, int color, int alpha) {
        OmniRenderGeometry.torus(poseStack.last(), consumer, Vec3.ZERO,
                radius, glow ? tubeRadius * 1.8F : tubeRadius,
                detailed ? glow ? 64 : 96 : 48, glow ? 6 : 8,
                OmniRenderGeometry.argb(color, alpha));
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
        var facing = center.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var anchor = center.getControllerAnchorLayout();
        var minimum = MolecularCenterStructure.worldPos(center.getBlockPos(), facing,
                new MolecularCenterStructure.Part(MolecularCenterStructure.MIN_X,
                        0, MolecularCenterStructure.MIN_Z,
                        MolecularCenterStructure.PartType.AIR), anchor);
        var maximum = MolecularCenterStructure.worldPos(center.getBlockPos(), facing,
                new MolecularCenterStructure.Part(MolecularCenterStructure.MAX_X,
                        Math.max(MolecularCenterStructure.CURRENT_MAX_Y, 45),
                        MolecularCenterStructure.MAX_Z,
                        MolecularCenterStructure.PartType.AIR), anchor);
        return new AABB(minimum).minmax(new AABB(maximum)).inflate(2.0);
    }
}
