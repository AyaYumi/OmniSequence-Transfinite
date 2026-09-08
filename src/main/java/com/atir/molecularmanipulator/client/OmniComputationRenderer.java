package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.atir.molecularmanipulator.blockentity.OmniCrownGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.function.DoubleFunction;
import java.util.function.DoublePredicate;

/**
 * Vertex-built singularity computer for the omni computation array.
 *
 * <p>A Kerr computation singularity consumes encoded data through its accretion
 * plane and emits synchronized bipolar results into the foundation and crown
 * routers. The remaining geometry reads as an engineered compute chassis.</p>
 */
public final class OmniComputationRenderer
        implements BlockEntityRenderer<OmniComputationCoreBlockEntity> {
    private static final int CYAN = 0x55EEFF;
    private static final int BLUE = 0x409CFF;
    private static final int PURPLE = 0x9D61FF;
    private static final int DEEP_PURPLE = 0x572DCC;
    private static final int GOLD = 0xFFD98A;
    private static final int WHITE = 0xF2FCFF;

    public OmniComputationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OmniComputationCoreBlockEntity core, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || core.getLevel() == null
                || !core.getBlockState().getValue(BlockStateProperties.POWERED)) return;
        var layout = core.getVisualLayout();
        if (!layout.isFormed()) return;
        var facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var visualCenter = OmniComputationStructure.worldPoint(core.getBlockPos(), facing,
                OmniComputationStructure.VISUAL_CENTER_X, OmniComputationStructure.visualCenterY(layout),
                OmniComputationStructure.VISUAL_CENTER_Z, layout);
        int activity = core.getClientVisualActivity();
        float strength = activity <= 0 ? 0 : Math.min(1F, (float) (Math.log1p(activity) / Math.log(9.0)));
        float angle = core.sampleClientVisualAngle(partialTick);
        float pulse = 1F + (float) Math.sin(angle * 0.075F) * (0.035F + strength * 0.055F);
        float completion = core.getClientCompletionPulse();
        double distance = visualCenter.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        boolean detailed = effectLevel > 1 && distance < 128.0 * 128.0;
        int segments = distance > 144.0 * 144.0 ? 32 : distance > 82.0 * 82.0 ? 56 : detailed ? 96 : 68;
        poseStack.pushPose();
        poseStack.translate(visualCenter.x - core.getBlockPos().getX(), visualCenter.y - core.getBlockPos().getY(),
                visualCenter.z - core.getBlockPos().getZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation(facing)));
        if (layout == OmniComputationStructure.StructureLayout.CURRENT) {
            renderCrownLayoutPass(layout, poseStack, buffers.getBuffer(OmniRenderLayers.singularityComputeDepth()),
                    core.getClientOrreryAngle(), strength, completion, detailed, false);
            renderCrownLayoutPass(layout, poseStack, buffers.getBuffer(OmniRenderLayers.singularityComputeGlow()),
                    core.getClientOrreryAngle(), strength, completion, detailed, true);
        } else {
            drawComputationAnomalyOccluder(poseStack.last(), buffers.getBuffer(OmniRenderLayers.solidEmissiveColor()), detailed);
            drawGate(poseStack, buffers.getBuffer(OmniRenderLayers.singularityComputeDepth()), angle, pulse, strength,
                    activity, completion, segments, detailed, true, false);
            drawGate(poseStack, buffers.getBuffer(OmniRenderLayers.singularityComputeGlow()), angle, pulse, strength,
                    activity, completion, segments, detailed, true, true);
        }
        poseStack.popPose();
    }

    /** The live renderer and isolated GPU preview share these exact POSITION_COLOR vertices. */
    static void renderCrownPass(PoseStack poseStack, VertexConsumer consumer, float angle,
            float activityStrength, float completionPulse, boolean detailed, boolean glow) {
        renderCrownLayoutPass(OmniComputationStructure.StructureLayout.CURRENT, poseStack, consumer,
                angle, activityStrength, completionPulse, detailed, glow);
    }

    /** Shared dispatch: an unknown client layout never enters a historical wireframe renderer. */
    static void renderCrownLayoutPass(OmniComputationStructure.StructureLayout layout,
            PoseStack poseStack, VertexConsumer consumer, double angle,
            float activityStrength, float completionPulse, boolean detailed, boolean glow) {
        if (layout == OmniComputationStructure.StructureLayout.CURRENT) {
            OmniOrbitalEffects.render(poseStack, consumer, angle, activityStrength, completionPulse, detailed, glow);
        }
    }

    private static void drawGate(PoseStack poseStack, VertexConsumer consumer,
            float angle, float pulse, float activityStrength, int activity,
            float completionPulse, int segments, boolean detailed,
            boolean renderDataHalos, boolean glow) {
        PoseStack.Pose pose = poseStack.last();
        drawRecursionRingField(poseStack, pose, consumer,
                angle, pulse, activityStrength, segments, detailed, glow);
        drawRecursiveSingularity(pose, consumer,
                angle, pulse, activityStrength, detailed, glow);
        drawCardinalDataBeams(pose, consumer,
                angle, activityStrength, detailed, glow);
        drawInstructionMatrix(poseStack, pose, consumer,
                angle, activityStrength, segments, detailed, glow);
        drawDiagnosticPlane(pose, consumer,
                angle, activityStrength, detailed, glow);
        if (completionPulse > 0.001F) {
            drawRecursionCommitSweep(poseStack, consumer,
                    1.0F - completionPulse, completionPulse, glow);
        }
    }

    private static void drawRecursionRingField(PoseStack poseStack,
            PoseStack.Pose pose, VertexConsumer consumer,
            float angle, float pulse, float activityStrength,
            int segments, boolean detailed, boolean glow) {
        float drive = 0.42F + activityStrength * 0.58F;
        int ringSegments = Math.max(40, segments);
        float[] radii = {9.78F, 10.18F, 10.58F};
        for (int layer = 0; layer < radii.length; layer++) {
            int rgb = layer == 0 ? CYAN : layer == 1 ? PURPLE : BLUE;
            float width = glow ? 0.18F - layer * 0.025F
                    : 0.066F - layer * 0.008F;
            OmniRenderGeometry.segmentedRing(poseStack, consumer,
                    0.0F, 0.0F, -0.38F - layer * 0.035F,
                    radii[layer] * (1.0F + (pulse - 1.0F) * 0.22F),
                    width, width * 0.52F,
                    ringSegments, 1.0F,
                    90.0F, 0.0F,
                    angle * (0.30F + layer * 0.13F)
                            * (layer % 2 == 0 ? 1.0F : -1.0F),
                    argb(rgb, glow
                            ? Math.round((52.0F - layer * 7.0F) * drive)
                            : Math.round((236.0F - layer * 26.0F) * drive)));
        }

        int packets = detailed ? 14 : 8;
        for (int packet = 0; packet < packets; packet++) {
            double phase = Math.PI * 2.0D * packet / packets
                    + angle * (packet % 2 == 0 ? 0.006D : -0.0045D);
            double radius = 10.22D + (packet % 3 - 1) * 0.22D;
            Vec3 point = new Vec3(Math.cos(phase) * radius,
                    Math.sin(phase) * radius, -0.52D);
            float half = glow ? 0.18F : 0.10F;
            OmniRenderGeometry.orientedBox(pose, consumer, point,
                    new Vec3(half, 0.0D, 0.0D),
                    new Vec3(0.0D, half, 0.0D),
                    new Vec3(0.0D, 0.0D, half * 0.48D),
                    argb(packet % 3 == 0 ? WHITE
                                    : packet % 3 == 1 ? CYAN : PURPLE,
                            glow ? Math.round(58.0F * drive)
                                    : Math.round(230.0F * drive)));
        }
    }

    private static void drawRecursiveSingularity(PoseStack.Pose pose,
            VertexConsumer consumer, float angle, float pulse,
            float activityStrength, boolean detailed, boolean glow) {
        float drive = 0.48F + activityStrength * 0.52F;
        float outer = glow ? 2.38F : 2.05F;
        OmniRenderGeometry.wireframeOctahedron(pose, consumer, Vec3.ZERO,
                outer * pulse, outer * pulse, outer * pulse,
                angle * 0.52F,
                glow ? 0.105F : 0.038F,
                argb(PURPLE, glow ? Math.round(56.0F * drive)
                        : Math.round(238.0F * drive)));

        double rotation = Math.toRadians(angle * 0.74F);
        Vec3 xAxis = new Vec3(Math.cos(rotation) * 1.48D, 0.0D,
                Math.sin(rotation) * 1.48D);
        Vec3 zAxis = new Vec3(-Math.sin(rotation) * 1.48D, 0.0D,
                Math.cos(rotation) * 1.48D);
        OmniRenderGeometry.wireframeBox(pose, consumer, Vec3.ZERO,
                xAxis, new Vec3(0.0D, 1.48D, 0.0D), zAxis,
                glow ? 0.090F : 0.032F,
                argb(CYAN, glow ? Math.round(48.0F * drive)
                        : Math.round(218.0F * drive)));
        OmniRenderGeometry.octahedron(pose, consumer, Vec3.ZERO,
                glow ? 0.82F : 0.62F,
                glow ? 1.02F : 0.78F,
                glow ? 0.82F : 0.62F,
                -angle * 0.88F,
                argb(DEEP_PURPLE, glow ? Math.round(28.0F * drive)
                        : Math.round(224.0F * drive)));

        int satellites = detailed ? 10 : 6;
        for (int satellite = 0; satellite < satellites; satellite++) {
            double phase = Math.PI * 2.0D * satellite / satellites
                    - angle * 0.009D;
            Vec3 point = new Vec3(Math.cos(phase) * 2.75D,
                    Math.sin(phase * 1.6D) * 0.65D,
                    Math.sin(phase) * 0.72D);
            float half = glow ? 0.15F : 0.085F;
            OmniRenderGeometry.orientedBox(pose, consumer, point,
                    new Vec3(half, 0.0D, 0.0D),
                    new Vec3(0.0D, half, 0.0D),
                    new Vec3(0.0D, 0.0D, half),
                    argb(satellite % 2 == 0 ? CYAN : PURPLE,
                            glow ? 44 : 216));
        }
    }

    private static void drawCardinalDataBeams(PoseStack.Pose pose,
            VertexConsumer consumer, float angle,
            float activityStrength, boolean detailed, boolean glow) {
        Vec3[] endpoints = {
                new Vec3(-10.38D, 0.0D, -0.42D),
                new Vec3(10.38D, 0.0D, -0.42D),
                new Vec3(0.0D, -10.38D, -0.42D),
                new Vec3(0.0D, 10.38D, -0.42D)
        };
        float drive = 0.45F + activityStrength * 0.55F;
        for (int index = 0; index < endpoints.length; index++) {
            Vec3 outer = endpoints[index];
            Vec3 inner = outer.scale(0.22D).add(0.0D, 0.0D, -0.08D);
            float width = glow ? 0.075F : 0.025F;
            OmniRenderGeometry.taperedBeam(pose, consumer, outer, inner,
                    width, width * 0.58F,
                    width * 0.58F, width * 0.34F,
                    argb(index % 2 == 0 ? BLUE : PURPLE,
                            glow ? Math.round(28.0F * drive)
                                    : Math.round(154.0F * drive)),
                    argb(CYAN, glow ? Math.round(56.0F * drive)
                            : Math.round(232.0F * drive)));

            int packets = detailed ? 2 : 1;
            for (int packet = 0; packet < packets; packet++) {
                float progress = fract(angle * (0.006F + activityStrength * 0.004F)
                        + index * 0.25F + packet * 0.5F);
                Vec3 point = outer.lerp(inner, progress);
                float half = glow ? 0.17F : 0.095F;
                OmniRenderGeometry.orientedBox(pose, consumer, point,
                        new Vec3(half, 0.0D, 0.0D),
                        new Vec3(0.0D, half, 0.0D),
                        new Vec3(0.0D, 0.0D, half * 0.52D),
                        argb(index % 2 == 0 ? WHITE : CYAN,
                                glow ? 62 : 242));
            }
        }
    }

    private static void drawInstructionMatrix(PoseStack poseStack,
            PoseStack.Pose pose, VertexConsumer consumer,
            float angle, float activityStrength, int segments,
            boolean detailed, boolean glow) {
        float y = -15.18F;
        float drive = 0.40F + activityStrength * 0.60F;
        float[] radii = {3.2F, 6.2F, 9.4F, 12.5F};
        for (int layer = 0; layer < radii.length; layer++) {
            float width = glow ? 0.095F : 0.033F;
            int rgb = layer % 2 == 0 ? CYAN : PURPLE;
            OmniRenderGeometry.segmentedRing(poseStack, consumer,
                    0.0F, y + layer * 0.018F, 0.0F, radii[layer],
                    width, width * 0.55F,
                    Math.max(32, segments), 1.0F,
                    0.0F, 0.0F,
                    angle * (layer % 2 == 0 ? 0.20F : -0.16F),
                    argb(rgb, glow ? Math.round(26.0F * drive)
                            : Math.round(142.0F * drive)));
        }
        for (int spoke = 0; spoke < 8; spoke++) {
            double phase = Math.PI * 2.0D * spoke / 8.0D;
            Vec3 inner = new Vec3(Math.cos(phase) * 2.2D, y,
                    Math.sin(phase) * 2.2D);
            Vec3 outer = new Vec3(Math.cos(phase) * 12.1D, y,
                    Math.sin(phase) * 12.1D);
            float width = glow ? 0.055F : 0.018F;
            OmniRenderGeometry.taperedBeam(pose, consumer, inner, outer,
                    width, width, width * 0.45F, width * 0.45F,
                    argb(CYAN, glow ? 24 : 132),
                    argb(spoke % 2 == 0 ? BLUE : PURPLE,
                            glow ? 16 : 88));
        }
        float uplinkWidth = glow ? 0.090F : 0.030F;
        OmniRenderGeometry.taperedBeam(pose, consumer,
                new Vec3(0.0D, y + 0.08D, 0.0D),
                new Vec3(0.0D, -10.55D, 0.0D),
                uplinkWidth, uplinkWidth * 0.55F,
                uplinkWidth, uplinkWidth * 0.55F,
                argb(BLUE, glow ? 26 : 144),
                argb(CYAN, glow ? 58 : 238));
    }

    private static void drawDiagnosticPlane(PoseStack.Pose pose,
            VertexConsumer consumer, float angle, float activityStrength,
            boolean detailed, boolean glow) {
        float response = 0.34F + activityStrength * 0.66F;
        OmniRenderGeometry.gridPlane(pose, consumer,
                new Vec3(0.0D, 0.0D, 0.58D),
                new Vec3(6.4D, 0.0D, 0.0D),
                new Vec3(0.0D, 6.4D, 0.0D),
                detailed ? 10 : 6,
                glow ? 0.025F : 0.009F,
                argb(BLUE, glow ? Math.round(9.0F * response)
                        : Math.round(42.0F * response)));
        float scanX = -5.8F + fract(angle * (0.003F + activityStrength * 0.003F)) * 11.6F;
        float width = glow ? 0.060F : 0.020F;
        OmniRenderGeometry.taperedBeam(pose, consumer,
                new Vec3(scanX, -5.8D, 0.54D),
                new Vec3(scanX, 5.8D, 0.54D),
                width, width, width * 0.42F, width * 0.42F,
                argb(PURPLE, glow ? 24 : 128),
                argb(CYAN, glow ? 46 : 214));
    }

    private static void drawRecursionCommitSweep(PoseStack poseStack,
            VertexConsumer consumer, float progress,
            float completionPulse, boolean glow) {
        float radius = 2.0F + progress * 10.8F;
        float width = glow ? 0.22F : 0.074F;
        OmniRenderGeometry.segmentedRing(poseStack, consumer,
                0.0F, 0.0F, -0.72F, radius,
                width, width * 0.58F,
                72, 1.0F, 90.0F, 0.0F,
                progress * 180.0F,
                argb(GOLD, glow ? Math.round(completionPulse * 72.0F)
                        : Math.round(completionPulse * 244.0F)));
    }

    private static void drawComputationAnomalyOccluder(PoseStack.Pose pose,
            VertexConsumer consumer, boolean detailed) {
        OmniRenderGeometry.sphere(pose, consumer,
                Vec3.ZERO, 1.46F,
                detailed ? 11 : 8, detailed ? 20 : 14,
                0xFF000107);
    }

    /**
     * Reconstructs the former cyan/blue stained-glass accents as animated,
     * non-colliding data halos. Coordinates are relative to the singularity at
     * local Y=17, matching the air channels reserved by the structure layout.
     */

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static int argb(int rgb, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (rgb & 0xFFFFFF);
    }

    private static float facingRotation(Direction facing) {
        return switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };
    }

    @Override
    public boolean shouldRenderOffScreen(OmniComputationCoreBlockEntity core) {
        return false;
    }

    @Override
    public int getViewDistance() {
        return 384;
    }

    @Override
    public boolean shouldRender(OmniComputationCoreBlockEntity core, Vec3 cameraPos) {
        Direction facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        var layout = core.getVisualLayout();
        if (layout == OmniComputationStructure.StructureLayout.INCOMPLETE) return false;
        int visualY = OmniComputationStructure.visualCenterY(layout);
        Vec3 origin = OmniComputationStructure.worldPoint(core.getBlockPos(), facing,
                OmniComputationStructure.VISUAL_CENTER_X, visualY,
                OmniComputationStructure.VISUAL_CENTER_Z, layout);
        return origin.distanceToSqr(cameraPos) <= 384.0D * 384.0D;
    }


}
