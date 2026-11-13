package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.client.render.ForgeRenderSupport;
import com.atir.molecularmanipulator.client.render.EarlyFrameGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.client.render.ParallelEffectGeometry;
import com.atir.molecularmanipulator.client.render.SingularityShaderVertices;
import com.atir.molecularmanipulator.blockentity.MachineEffectLevels;
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

import java.util.TimeZone;

/**
 * Layout-aware fields rendered through the built-in molecular spectral pipeline.
 * The outer field never writes depth, so the transparent sphere cannot mask its core.
 */
public final class MolecularCenterRenderer implements BlockEntityRenderer<MolecularCenterBlockEntity>,
        EarlyFrameGeometry.Source<MolecularCenterBlockEntity, MolecularCenterRenderer.Snapshot> {
    private static final float FIELD_RADIUS = 6.35F;
    private static final float CORE_RADIUS = 1.82F;
    private final ParallelEffectGeometry geometry = new ParallelEffectGeometry();

    public MolecularCenterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MolecularCenterBlockEntity center, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var snapshot = EarlyFrameGeometry.take(this, center, partialTick, poseStack);
        if (snapshot == null) snapshot = capture(center, partialTick, poseStack, geometry);
        if (snapshot == null) return;
        var frame = snapshot;
        try (frame) {
            var poses = snapshot.poses();
            var depth = OmniRenderLayers.molecularSpectralDepth(buffers);
            if (snapshot.prepared() != null) snapshot.prepared().submit(0, depth);
            else renderLayoutPass(snapshot.layout(), poses, depth,
                    snapshot.angle(), snapshot.visualMode(), snapshot.effectLevel(), false,
                    snapshot.fieldColor(), snapshot.coreColor(), snapshot.primaryColor(),
                    snapshot.secondaryColor(), snapshot.latticeColor(), snapshot.activity(),
                    snapshot.completion(), snapshot.clockTicks());
            if (snapshot.crystalFeathers()) {
                var surface = OmniRenderLayers.molecularSurface(buffers);
                if (snapshot.prepared() != null) snapshot.prepared().submit(1, surface);
                else try (var batch = SingularityShaderVertices.batch(surface)) {
                    FeatherResonanceEffects.renderCoreSurface(poses, surface, snapshot.angle(),
                            snapshot.coreColor(), snapshot.secondaryColor());
                }
            }
            var glow = OmniRenderLayers.molecularSpectralGlow(buffers);
            if (snapshot.prepared() != null) snapshot.prepared().submit(2, glow);
            else renderLayoutPass(snapshot.layout(), poses, glow,
                    snapshot.angle(), snapshot.visualMode(), snapshot.effectLevel(), true,
                    snapshot.fieldColor(), snapshot.coreColor(), snapshot.primaryColor(),
                    snapshot.secondaryColor(), snapshot.latticeColor(), snapshot.activity(),
                    snapshot.completion(), snapshot.clockTicks());
        }
    }

    @Override
    public Snapshot capture(MolecularCenterBlockEntity center, float partialTick, PoseStack base,
            ParallelEffectGeometry geometry) {
        int effectLevel = MachineEffectLevels.forMachine(center);
        if (effectLevel <= 0 || center.getLevel() == null
                || !center.getBlockState().getValue(BlockStateProperties.POWERED)) {
            return null;
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
        // The dial reads the client's local wall clock rather than world time.
        long now = System.currentTimeMillis();
        double clockTicks = FeatherResonanceEffects.wallClockTicks(now, TimeZone.getDefault().getOffset(now));

        var poses = localPose(base, visualCenter, center.getBlockPos(), facing, crystalFeathers);
        int fieldColor = center.getFieldColor(), coreColor = center.getCoreColor();
        int primaryColor = center.getPrimaryRingColor(), secondaryColor = center.getSecondaryRingColor();
        int latticeColor = center.getLatticeColor();
        var prepared = crystalFeathers ? prepareFeatherPasses(geometry, poses,
                angle, crown.activity(), completion, effectLevel, fieldColor, coreColor,
                primaryColor, secondaryColor, latticeColor, clockTicks) : null;
        return new Snapshot(ForgeRenderSupport.copyPose(poses.last()), layout, visualMode, effectLevel, angle,
                crown.activity(), completion, fieldColor, coreColor, primaryColor, secondaryColor,
                latticeColor, clockTicks, crystalFeathers, prepared);
    }

    private static PoseStack localPose(PoseStack base, Vec3 visualCenter, net.minecraft.core.BlockPos blockPos,
            Direction facing, boolean crystalFeathers) {
        var poses = new PoseStack();
        poses.last().pose().set(base.last().pose());
        poses.last().normal().set(base.last().normal());
        poses.translate(visualCenter.x - blockPos.getX(), visualCenter.y - blockPos.getY(),
                visualCenter.z - blockPos.getZ());
        if (crystalFeathers) {
            // Match worldPoint's right/back axes so fixed trails follow the blocks
            // for every controller facing, including asymmetric tilted orbits.
            poses.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        }
        return poses;
    }

    /** Immutable frame values plus the optional prepared color geometry for one renderer capture. */
    public record Snapshot(PoseStack.Pose pose, MolecularCenterStructure.StructureLayout layout,
            int visualMode, int effectLevel, float angle, float activity, float completion,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor,
            double clockTicks, boolean crystalFeathers, ParallelEffectGeometry.Frame prepared)
            implements EarlyFrameGeometry.Snapshot {
        private PoseStack poses() {
            var poses = new PoseStack();
            poses.last().pose().set(pose.pose());
            poses.last().normal().set(pose.normal());
            return poses;
        }

        @Override
        public void close() {
            if (prepared != null) prepared.close();
        }
    }

    /** Current layout only; the historical molecular renderer stays synchronous. */
    static ParallelEffectGeometry.Frame prepareFeatherPasses(ParallelEffectGeometry geometry,
            PoseStack poses, float angle, float activity, float completion, int effectLevel,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor,
            double clockTicks) {
        if (effectLevel < 2 || !ParallelEffectGeometry.isAvailable()) return null;
        var input = new FeatherFrame(ForgeRenderSupport.copyPose(poses.last()), angle, activity, completion, effectLevel,
                fieldColor, coreColor, primaryColor, secondaryColor, latticeColor, clockTicks);
        return geometry.prepare(out -> input.render(out, false), input::surface, out -> input.render(out, true));
    }

    private record FeatherFrame(PoseStack.Pose pose, float angle, float activity, float completion,
            int effectLevel, int field, int core, int primary, int secondary, int lattice, double clockTicks) {
        private PoseStack poses() {
            var poses = new PoseStack();
            poses.last().pose().set(pose.pose());
            poses.last().normal().set(pose.normal());
            return poses;
        }

        private void render(VertexConsumer out, boolean glow) {
            renderFeatherPass(poses(), out, angle, activity, completion, effectLevel, glow,
                    field, core, primary, secondary, lattice, clockTicks);
        }

        private void surface(VertexConsumer out) {
            FeatherResonanceEffects.renderCoreSurface(poses(), out, angle, core, secondary);
        }
    }

    static void renderLayoutPass(MolecularCenterStructure.StructureLayout layout,
            PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        renderLayoutPass(layout, poseStack, consumer, angle, visualMode, effectLevel, glow,
                fieldColor, coreColor, primaryColor, secondaryColor, latticeColor,
                visualMode == 0 ? 0 : 1, 0, 0);
    }

    static void renderLayoutPass(MolecularCenterStructure.StructureLayout layout,
            PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor,
            double clockTicks) {
        renderLayoutPass(layout, poseStack, consumer, angle, visualMode, effectLevel, glow,
                fieldColor, coreColor, primaryColor, secondaryColor, latticeColor,
                visualMode == 0 ? 0 : 1, 0, clockTicks);
    }

    private static void renderLayoutPass(MolecularCenterStructure.StructureLayout layout,
            PoseStack poseStack, VertexConsumer consumer, float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor,
            float activity, float completion, double clockTicks) {
        try (var batch = SingularityShaderVertices.batch(consumer)) {
            if (layout == MolecularCenterStructure.StructureLayout.CURRENT) {
                renderFeatherPass(poseStack, consumer, angle, activity, completion, effectLevel, glow,
                        fieldColor, coreColor, primaryColor, secondaryColor, latticeColor, clockTicks);
            } else if (layout == MolecularCenterStructure.StructureLayout.LEGACY_1_3_9) {
                renderPass(poseStack, consumer, angle, visualMode, effectLevel, glow,
                        fieldColor, coreColor, primaryColor, secondaryColor, latticeColor);
            }
        }
    }

    // A faceted seed and exposed feather highlights keep the open crystal silhouette.
    static void renderFeatherPass(PoseStack poseStack, VertexConsumer consumer,
            float angle, int visualMode, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        renderFeatherPass(poseStack, consumer, angle, visualMode == 0 ? 0 : 1, 0,
                effectLevel, glow, fieldColor, coreColor, primaryColor, secondaryColor, latticeColor, 0);
    }

    static void renderFeatherPass(PoseStack poseStack, VertexConsumer consumer,
            float angle, float activity, float completion, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor) {
        renderFeatherPass(poseStack, consumer, angle, activity, completion, effectLevel, glow,
                fieldColor, coreColor, primaryColor, secondaryColor, latticeColor, 0);
    }

    static void renderFeatherPass(PoseStack poseStack, VertexConsumer consumer,
            float angle, float activity, float completion, int effectLevel, boolean glow,
            int fieldColor, int coreColor, int primaryColor, int secondaryColor, int latticeColor,
            double clockTicks) {
        if (effectLevel <= 0) return;
        FeatherResonanceEffects.render(poseStack, consumer, angle, activity, completion, effectLevel > 1, glow,
                clockTicks, fieldColor, coreColor, primaryColor, secondaryColor, latticeColor);
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


}
