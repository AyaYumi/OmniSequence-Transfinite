package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.Part;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.PartType;
import com.atir.molecularmanipulator.blockentity.MatterPearlGeometry;
import com.atir.molecularmanipulator.client.render.EarlyFrameGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.client.render.ParallelEffectGeometry;
import com.atir.molecularmanipulator.client.render.MatterRasterEffects;
import com.atir.molecularmanipulator.client.render.MatterStellarEffects;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Renders a vacuum-condensation and additive-manufacturing cell. */
public final class MatterFabricationRenderer
        implements BlockEntityRenderer<MatterFabricationBlockEntity>,
        EarlyFrameGeometry.Source<MatterFabricationBlockEntity, MatterFabricationRenderer.Snapshot> {

    private static final int[] SPLIT_PASS_ENDS = {1, 2, 4, 6};
    // Clipping dominates execution; launch both built meshes before their cheaper previews.
    private static final int[] SPLIT_LAUNCH_ORDER = {3, 5, 0, 1, 2, 4};

    private final ParallelEffectGeometry geometry = new ParallelEffectGeometry();
    private final Map<MatterFabricationBlockEntity, MatterRasterEffects.Animation> fabricationAnimations = new WeakHashMap<>();
    private final Map<MatterFabricationBlockEntity, MatterStellarEffects.Animation> stellarAnimations = new WeakHashMap<>();

    public MatterFabricationRenderer(Context context) {
    }

    @Override
    public void render(MatterFabricationBlockEntity machine, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MatterFabricationPlacementPreview.render(machine, poseStack, buffers);
        var snapshot = EarlyFrameGeometry.take(this, machine, partialTick, poseStack);
        if (snapshot == null) snapshot = capture(machine, partialTick, poseStack, geometry);
        if (snapshot == null) return;
        var frame = snapshot;
        try (frame) {
            var poses = snapshot.poses();
            for (var pass : FoundryPass.values()) {
                // The clipped workpiece and light raster share the same per-frame progress snapshot.
                var layer = pass.glow() ? OmniRenderLayers.additiveColor() : OmniRenderLayers.translucentEmissiveColor();
                var target = buffers.getBuffer(layer);
                if (snapshot.prepared() != null) snapshot.prepared().submit(pass.ordinal(), target);
                else renderFabricationPass(poses, target, snapshot.time(), snapshot.detailed(),
                        true, snapshot.facing(), pass, snapshot.animation(), snapshot.stellar());
            }
        }
    }

    @Override
    public Snapshot capture(MatterFabricationBlockEntity machine, float partialTick,
            PoseStack base, ParallelEffectGeometry geometry) {
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || machine.getLevel() == null
                || !machine.isClientStructureFormed()) {
            return null;
        }
        Direction facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Part centerPart = new Part(0, MatterFabricationStructure.EFFECT_CENTER_Y,
                0, PartType.CORE);
        BlockPos center = MatterFabricationStructure.worldPos(machine.getBlockPos(), facing,
                centerPart);
        float time = machine.getLevel().getGameTime() + partialTick;
        boolean running = machine.isClientRunning();
        var animation = fabricationAnimations.computeIfAbsent(machine, ignored -> new MatterRasterEffects.Animation())
                .sample(time, true, running, machine.sampleClientRecipeProgress(partialTick),
                        machine.sampleClientCompletionPulse(partialTick));
        double distanceSquared = Vec3.atCenterOf(center).distanceToSqr(
                Minecraft.getInstance().gameRenderer.getMainCamera().getPosition());
        boolean detailed = effectLevel > 1 && distanceSquared < 112.0D * 112.0D;
        var stellar = stellarAnimations.computeIfAbsent(machine, ignored -> new MatterStellarEffects.Animation())
                .sample(time, running, animation.completion(), machine.getClientResearchVisualState(),
                        machine.sampleClientResearchElapsed(partialTick), machine.sampleClientResearchCompletionPulse(partialTick));
        var poses = localPose(base, center, machine.getBlockPos());
        var prepared = preparePasses(geometry, poses, time, detailed, facing, animation, stellar);
        return new Snapshot(poses.last().copy(), time, detailed, facing, animation, stellar, prepared);
    }

    private static PoseStack localPose(PoseStack base, BlockPos center, BlockPos blockPos) {
        var poses = new PoseStack();
        poses.last().pose().set(base.last().pose());
        poses.last().normal().set(base.last().normal());
        poses.translate(center.getX() - blockPos.getX() + 0.5D,
                center.getY() - blockPos.getY() + 0.5D,
                center.getZ() - blockPos.getZ() + 0.5D);
        return poses;
    }

    /** Immutable sampled animation values plus the optional prepared color geometry for one capture. */
    public record Snapshot(PoseStack.Pose pose, float time, boolean detailed, Direction facing,
            MatterRasterEffects.Frame animation, MatterStellarEffects.Frame stellar,
            ParallelEffectGeometry.Frame prepared) implements EarlyFrameGeometry.Snapshot {
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

    static ParallelEffectGeometry.Frame preparePasses(ParallelEffectGeometry geometry, PoseStack poses,
            float time, boolean detailed, Direction facing, MatterRasterEffects.Frame animation,
            MatterStellarEffects.Frame stellar) {
        if (!detailed || !ParallelEffectGeometry.isAvailable()) return null;
        var input = new GeometryFrame(poses.last().copy(), time, detailed, facing, animation, stellar);
        if (!animation.constructing() || animation.completion() != 0 || animation.progress() <= 0
                || animation.opacity() < 0.001F) {
            return geometry.prepare(out -> input.renderPass(out, FoundryPass.FRAME_DEPTH),
                    out -> input.renderPass(out, FoundryPass.FRAME_GLOW),
                    out -> input.renderPass(out, FoundryPass.CRYSTAL_DEPTH),
                    out -> input.renderPass(out, FoundryPass.CRYSTAL_GLOW));
        }
        // Each original preview/built draw runs once; submission concatenates them inside its pass.
        return geometry.prepareGroups(SPLIT_PASS_ENDS, SPLIT_LAUNCH_ORDER,
                out -> input.renderPass(out, FoundryPass.FRAME_DEPTH),
                out -> input.renderPass(out, FoundryPass.FRAME_GLOW),
                out -> input.renderObject(out, false, 0, 1), out -> input.renderObject(out, false, 1, 3),
                out -> input.renderObject(out, true, 0, 1), out -> input.renderObject(out, true, 1, 3));
    }

    private record GeometryFrame(PoseStack.Pose pose, float time, boolean detailed, Direction facing,
            MatterRasterEffects.Frame animation, MatterStellarEffects.Frame stellar) {
        private PoseStack poses() {
            var stack = new PoseStack();
            stack.last().pose().set(pose.pose()); stack.last().normal().set(pose.normal());
            return stack;
        }

        private void renderPass(VertexConsumer out, FoundryPass pass) {
            renderFabricationPass(poses(), out, time, detailed, true, facing, pass, animation, stellar);
        }

        private void renderObject(VertexConsumer out, boolean glow, int from, int to) {
            var stack = poses();
            stack.pushPose();
            stack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
            MatterRasterEffects.renderObjectRange(stack, out, time, animation, glow, from, to);
            stack.popPose();
        }
    }

    enum FoundryPass {
        FRAME_DEPTH(false, false), FRAME_GLOW(false, true), CRYSTAL_DEPTH(true, false), CRYSTAL_GLOW(true, true);

        private final boolean crystal;
        private final boolean glow;

        FoundryPass(boolean crystal, boolean glow) {
            this.crystal = crystal;
            this.glow = glow;
        }

        boolean crystal() { return crystal; }
        boolean glow() { return glow; }
    }

    /** Applies formed/running gating, orientation and pass selection to fabrication effects. */
    static void renderFoundryPass(PoseStack poseStack, VertexConsumer consumer, float time,
            float completionPulse, boolean detailed, boolean formed, boolean running, Direction facing, FoundryPass pass) {
        renderFoundryPass(poseStack, consumer, time, completionPulse, detailed, formed, running, facing, pass, running ? 0.5F : 0);
    }

    static void renderFoundryPass(PoseStack poseStack, VertexConsumer consumer, float time,
            float completionPulse, boolean detailed, boolean formed, boolean running, Direction facing,
            FoundryPass pass, float progress) {
        var frame = new MatterRasterEffects.Animation().sample(time, formed, running, progress, completionPulse);
        renderFabricationPass(poseStack, consumer, time, detailed, formed, facing, pass, frame);
    }

    static void renderFabricationPass(PoseStack poseStack, VertexConsumer consumer, float time,
            boolean detailed, boolean formed, Direction facing, FoundryPass pass, MatterRasterEffects.Frame frame) {
        renderFabricationPass(poseStack, consumer, time, detailed, formed, facing, pass, frame,
                new MatterStellarEffects.Frame(frame.running() ? 1 : 0, time,
                        com.atir.molecularmanipulator.research.ResearchVisualState.EMPTY, 0, 0));
    }

    static void renderFabricationPass(PoseStack poseStack, VertexConsumer consumer, float time,
            boolean detailed, boolean formed, Direction facing, FoundryPass pass, MatterRasterEffects.Frame frame,
            MatterStellarEffects.Frame stellar) {
        if (!formed) return;
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - facing.toYRot()));
        if (pass.crystal()) {
            MatterRasterEffects.renderObject(poseStack, consumer, time, frame, pass.glow());
        } else {
            MatterRasterEffects.renderField(poseStack, consumer, time, frame, detailed, pass.glow());
            MatterStellarEffects.render(poseStack, consumer, stellar, detailed, pass.glow());
            drawPearlAccents(poseStack.last(), consumer, detailed, pass.glow());
        }
        poseStack.popPose();
    }

    private static void drawPearlAccents(PoseStack.Pose pose, VertexConsumer consumer,
            boolean detailed, boolean glow) {
        if (!detailed) return;
        var perimeter = MatterPearlGeometry.platformOutline();
        for (int index = 0; index < perimeter.size(); index++) {
            OmniRenderGeometry.beam(pose, consumer, pearlSurface(perimeter.get(index)),
                    pearlSurface(perimeter.get((index + 1) % perimeter.size())),
                    glow ? 0.026F : 0.011F, glow ? 0.018F : 0.007F,
                    argb(0xE3D2A1, glow ? 5 : 70));
        }
        for (int side : new int[] {-1, 1}) {
            var conduits = MatterPearlGeometry.crownConduits(side);
            for (int index = 1; index < conduits.size(); index++) {
                OmniRenderGeometry.beam(pose, consumer, pearlSurface(conduits.get(index - 1)),
                        pearlSurface(conduits.get(index)), glow ? 0.024F : 0.010F,
                        glow ? 0.016F : 0.006F, argb(0xEAD9AB, glow ? 5 : 80));
            }
        }
    }

    private static Vec3 pearlSurface(BlockPos pos) {
        return new Vec3(pos.getX(), pos.getY() - MatterPearlGeometry.CENTER_Y + 0.56, pos.getZ());
    }

    /** Emit one outward winding so a closed transparent crystal does not double its back-face glow. */

    private static int argb(int rgb, int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        return (clamped << 24) | (rgb & 0xFFFFFF);
    }

    @Override
    public boolean shouldRenderOffScreen(MatterFabricationBlockEntity machine) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 384;
    }

    @Override
    public boolean shouldRender(MatterFabricationBlockEntity machine, Vec3 cameraPos) {
        return cameraPos.distanceToSqr(Vec3.atCenterOf(machine.getBlockPos())) < 384.0D * 384.0D;
    }

    @Override
    public AABB getRenderBoundingBox(MatterFabricationBlockEntity machine) {
        Direction facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockPos center = MatterFabricationStructure.worldPos(machine.getBlockPos(), facing,
                new Part(0, MatterFabricationStructure.EFFECT_CENTER_Y, 0, PartType.CORE));
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.5D;
        double z = center.getZ() + 0.5D;
        double radius = MatterPearlGeometry.RADIUS + 2.0D;
        return new AABB(x - radius, y + MatterPearlGeometry.MIN_Y - MatterPearlGeometry.CENTER_Y - 1.0D, z - radius,
                x + radius, y + MatterPearlGeometry.MAX_Y - MatterPearlGeometry.CENTER_Y + 1.0D, z + radius);
    }
}
