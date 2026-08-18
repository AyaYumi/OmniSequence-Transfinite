package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
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

/**
 * Vertex-built suspended reactor for the omni computation array.
 *
 * <p>The current layout uses rectangular deck frames, tower-fed conduits and
 * a faceted cyan focus so the effect follows the physical reference build.
 * All visible motifs are real quads; the second pass is only a controlled
 * additive glow, with no particle or billboard dependency.</p>
 */
public final class OmniComputationRenderer
        implements BlockEntityRenderer<OmniComputationCoreBlockEntity> {
    private static final int CYAN = 0x55EEFF;
    private static final int BLUE = 0x409CFF;
    private static final int PURPLE = 0x9D61FF;
    private static final int DEEP_PURPLE = 0x572DCC;
    private static final int GOLD = 0xFFD98A;
    private static final int WHITE = 0xF2FCFF;
    private static final int[][] REFERENCE_TOWERS = {
            {-10, -8}, {10, -8}, {-10, 8}, {10, 8}
    };
    private static final int[][] REFERENCE_INNER_TOWERS = {
            {-6, -4}, {6, -4}, {-6, 4}, {6, 4}
    };

    public OmniComputationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(OmniComputationCoreBlockEntity core, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || core.getLevel() == null
                || !core.getBlockState().getValue(BlockStateProperties.POWERED)) {
            return;
        }

        Direction facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        boolean legacy = core.getInspection().layout()
                == OmniComputationStructure.StructureLayout.LEGACY;
        int visualY = legacy ? OmniComputationStructure.EFFECT_Y
                : OmniComputationStructure.VISUAL_CENTER_Y;
        Vec3 visualCenter = OmniComputationStructure.worldPoint(core.getBlockPos(), facing,
                OmniComputationStructure.VISUAL_CENTER_X, visualY,
                OmniComputationStructure.VISUAL_CENTER_Z);
        int activity = core.getClientVisualActivity();
        float activityStrength = activity <= 0 ? 0.0F
                : Math.min(1.0F, (float) (Math.log1p(activity) / Math.log(9.0)));
        float angle = core.sampleClientVisualAngle(partialTick);
        float pulse = 1.0F + (float) Math.sin(angle * 0.075F)
                * (0.035F + activityStrength * 0.055F);
        float completionPulse = core.getClientCompletionPulse();
        double distanceSquared = visualCenter.distanceToSqr(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        boolean detailed = effectLevel > 1 && distanceSquared < 160.0D * 160.0D;
        int segments = distanceSquared > 144.0D * 144.0D
                ? 20 : distanceSquared > 82.0D * 82.0D ? 36 : detailed ? 64 : 44;

        poseStack.pushPose();
        poseStack.translate(visualCenter.x - core.getBlockPos().getX(),
                visualCenter.y - core.getBlockPos().getY(),
                visualCenter.z - core.getBlockPos().getZ());
        poseStack.mulPose(Axis.YP.rotationDegrees(facingRotation(facing)));

        VertexConsumer solid = buffers.getBuffer(OmniRenderLayers.solidEmissiveColor());
        drawGate(poseStack, solid, angle, pulse, activityStrength, activity,
                completionPulse, segments, detailed, false);
        poseStack.pushPose();
        poseStack.scale(1.04F, 1.04F, 1.04F);
        VertexConsumer glow = buffers.getBuffer(OmniRenderLayers.additiveColor());
        drawGate(poseStack, glow, angle, pulse, activityStrength, activity,
                completionPulse, segments, detailed, true);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void drawGate(PoseStack poseStack, VertexConsumer consumer,
            float angle, float pulse, float activityStrength, int activity,
            float completionPulse, int segments, boolean detailed, boolean glow) {
        int frameAlpha = glow ? 42 : 228;
        int accentAlpha = glow ? 58 : 246;
        int coreAlpha = glow ? 82 : 255;
        int beamAlpha = glow ? 36 : Math.round(118.0F + activityStrength * 110.0F);
        float speed = 1.0F + activityStrength * 1.55F;
        float spin = angle * 0.52F * speed;
        float scale = 1.0F + activityStrength * 0.045F;

        // Layered rectangular frames are the effect counterpart of the three
        // physical decks surrounding the suspended center cube.
        drawRectFrame(poseStack, consumer, 7.2F * scale, -4.0F,
                7.2F * scale, 0.15F, 0.12F, argb(PURPLE, frameAlpha));
        drawRectFrame(poseStack, consumer, 6.3F * scale, 4.0F,
                6.3F * scale, 0.13F, 0.10F, argb(CYAN, frameAlpha));
        drawRectFrame(poseStack, consumer, 8.1F * scale, 8.0F,
                8.1F * scale, 0.13F, 0.10F, argb(PURPLE, frameAlpha));
        drawRectFrame(poseStack, consumer, 10.2F * scale, 14.0F,
                10.2F * scale, 0.11F, 0.09F, argb(BLUE, frameAlpha));

        PoseStack.Pose pose = poseStack.last();
        drawCubeFrame(poseStack, consumer, 3.35F * pulse,
                3.1F * pulse, argb(DEEP_PURPLE, accentAlpha));
        OmniRenderGeometry.octahedron(pose, consumer,
                new Vec3(0.0D, 0.0D, 0.0D),
                2.45F * pulse, 2.65F * pulse, 2.45F * pulse,
                -spin * 1.1F, argb(DEEP_PURPLE, coreAlpha));
        OmniRenderGeometry.octahedron(pose, consumer,
                new Vec3(0.0D, 0.0D, 0.0D),
                1.18F * pulse, 1.45F * pulse, 1.18F * pulse,
                spin * 1.8F, argb(CYAN, glow ? 105 : 255));
        OmniRenderGeometry.octahedron(pose, consumer,
                new Vec3(0.0D, 0.0D, 0.0D),
                0.48F * pulse, 0.62F * pulse, 0.48F * pulse,
                -spin * 2.4F, argb(WHITE, glow ? 132 : 255));

        // Four tower pairs feed the focus through stepped purple/cyan conduits.
        for (int index = 0; index < REFERENCE_TOWERS.length; index++) {
            int[] tower = REFERENCE_TOWERS[index];
            float signX = Math.signum(tower[0]);
            float signZ = Math.signum(tower[1]);
            Vec3 lower = new Vec3(tower[0] * 0.82D, -7.0D, tower[1] * 0.82D);
            Vec3 lowerInner = new Vec3(signX * 4.2D, -3.6D, signZ * 4.2D);
            Vec3 upper = new Vec3(tower[0] * 0.82D, 11.0D, tower[1] * 0.82D);
            Vec3 upperInner = new Vec3(signX * 4.2D, 3.8D, signZ * 4.2D);
            OmniRenderGeometry.beam(pose, consumer, lower, lowerInner,
                    0.14F, 0.11F, argb(index % 2 == 0 ? PURPLE : CYAN, beamAlpha));
            OmniRenderGeometry.beam(pose, consumer, upper, upperInner,
                    0.12F, 0.10F, argb(index % 2 == 0 ? CYAN : PURPLE, beamAlpha));
            if (detailed) {
                OmniRenderGeometry.rune(pose, consumer, lower, 0.62F,
                        0.10F, index * 90.0F + spin,
                        argb(index % 2 == 0 ? GOLD : WHITE, beamAlpha));
            }
        }
        for (int index = 0; index < REFERENCE_INNER_TOWERS.length; index++) {
            int[] tower = REFERENCE_INNER_TOWERS[index];
            Vec3 start = new Vec3(tower[0], -5.0D, tower[1]);
            Vec3 end = new Vec3(Math.signum(tower[0]) * 3.4D, -1.5D,
                    Math.signum(tower[1]) * 3.4D);
            OmniRenderGeometry.beam(pose, consumer, start, end,
                    0.095F, 0.075F, argb(PURPLE, beamAlpha));
        }

        // Transparent-looking corner cables and a central vertical power shaft.
        for (int x : new int[] {-5, 5}) {
            for (int z : new int[] {-5, 5}) {
                OmniRenderGeometry.beam(pose, consumer,
                        new Vec3(x, -3.0D, z), new Vec3(x, 8.0D, z),
                        0.055F, 0.045F, argb(CYAN, Math.round(beamAlpha * 0.72F)));
            }
        }
        OmniRenderGeometry.beam(pose, consumer,
                new Vec3(0.0D, -10.5D, 0.0D), new Vec3(0.0D, 18.0D, 0.0D),
                0.11F + activityStrength * 0.05F,
                0.09F + activityStrength * 0.04F, argb(BLUE, beamAlpha));

        int runeCount = detailed ? 8 : 4;
        for (int index = 0; index < runeCount; index++) {
            double a = Math.PI * 2.0D * index / runeCount - Math.toRadians(spin * 0.24F);
            float x = (float) Math.cos(a) * 9.4F;
            float z = (float) Math.sin(a) * 9.4F;
            OmniRenderGeometry.rune(pose, consumer, new Vec3(x, 8.15D, z),
                    0.48F, 0.085F, (float) Math.toDegrees(a),
                    argb(index % 2 == 0 ? PURPLE : CYAN, frameAlpha));
        }

        if (completionPulse > 0.001F) {
            float progress = 1.0F - completionPulse;
            float extent = 3.4F + progress * 10.0F;
            drawRectFrame(poseStack, consumer, extent,
                    -3.2F + progress * 6.5F, extent, 0.18F,
                    0.13F, argb(GOLD, glow ? Math.round(completionPulse * 84.0F)
                            : Math.round(completionPulse * 225.0F)));
        }
    }

    private static void drawRectFrame(PoseStack poseStack, VertexConsumer consumer,
            float halfX, float y, float halfZ, float halfWidth, float halfDepth,
            int color) {
        PoseStack.Pose pose = poseStack.last();
        OmniRenderGeometry.beam(pose, consumer,
                new Vec3(-halfX, y, -halfZ), new Vec3(halfX, y, -halfZ),
                halfWidth, halfDepth, color);
        OmniRenderGeometry.beam(pose, consumer,
                new Vec3(halfX, y, -halfZ), new Vec3(halfX, y, halfZ),
                halfWidth, halfDepth, color);
        OmniRenderGeometry.beam(pose, consumer,
                new Vec3(halfX, y, halfZ), new Vec3(-halfX, y, halfZ),
                halfWidth, halfDepth, color);
        OmniRenderGeometry.beam(pose, consumer,
                new Vec3(-halfX, y, halfZ), new Vec3(-halfX, y, -halfZ),
                halfWidth, halfDepth, color);
    }

    private static void drawCubeFrame(PoseStack poseStack, VertexConsumer consumer,
            float half, float halfHeight, int color) {
        PoseStack.Pose pose = poseStack.last();
        drawRectFrame(poseStack, consumer, half, -halfHeight, half,
                0.13F, 0.10F, color);
        drawRectFrame(poseStack, consumer, half, halfHeight, half,
                0.13F, 0.10F, color);
        for (int x : new int[] {-1, 1}) {
            for (int z : new int[] {-1, 1}) {
                OmniRenderGeometry.beam(pose, consumer,
                        new Vec3(x * half, -halfHeight, z * half),
                        new Vec3(x * half, halfHeight, z * half),
                        0.13F, 0.10F, color);
            }
        }
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
        return true;
    }

    @Override
    public int getViewDistance() {
        return 512;
    }

    @Override
    public boolean shouldRender(OmniComputationCoreBlockEntity core, Vec3 cameraPos) {
        return true;
    }

    @Override
    public AABB getRenderBoundingBox(OmniComputationCoreBlockEntity core) {
        Direction facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        boolean legacy = core.getInspection().layout()
                == OmniComputationStructure.StructureLayout.LEGACY;
        int visualY = legacy ? OmniComputationStructure.EFFECT_Y
                : OmniComputationStructure.VISUAL_CENTER_Y;
        Vec3 origin = OmniComputationStructure.worldPoint(core.getBlockPos(), facing,
                OmniComputationStructure.VISUAL_CENTER_X, visualY,
                OmniComputationStructure.VISUAL_CENTER_Z);
        return new AABB(origin.x - 28.0D, origin.y - 22.0D, origin.z - 28.0D,
                origin.x + 28.0D, origin.y + 22.0D, origin.z + 28.0D);
    }
}
