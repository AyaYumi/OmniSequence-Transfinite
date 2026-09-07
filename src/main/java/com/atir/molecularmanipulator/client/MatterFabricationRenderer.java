package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.Part;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.PartType;
import com.atir.molecularmanipulator.blockentity.MatterPearlGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.atir.molecularmanipulator.client.render.MatterRasterEffects;
import com.atir.molecularmanipulator.client.render.MatterStellarEffects;
import com.atir.molecularmanipulator.config.ModConfig;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.Map;
import java.util.WeakHashMap;

/** Renders a vacuum-condensation and additive-manufacturing cell. */
public final class MatterFabricationRenderer
        implements BlockEntityRenderer<MatterFabricationBlockEntity> {

    private final Map<MatterFabricationBlockEntity, MatterRasterEffects.Animation> fabricationAnimations = new WeakHashMap<>();
    private final Map<MatterFabricationBlockEntity, MatterStellarEffects.Animation> stellarAnimations = new WeakHashMap<>();

    public MatterFabricationRenderer(Context context) {
    }

    @Override
    public void render(MatterFabricationBlockEntity machine, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        MatterFabricationPlacementPreview.render(machine, poseStack, buffers);
        int effectLevel = ModConfig.DYNAMIC_EFFECT_LEVEL.get();
        if (effectLevel <= 0 || machine.getLevel() == null
                || !machine.isClientStructureFormed()) {
            return;
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

        poseStack.pushPose();
        poseStack.translate(center.getX() - machine.getBlockPos().getX() + 0.5D,
                center.getY() - machine.getBlockPos().getY() + 0.5D,
                center.getZ() - machine.getBlockPos().getZ() + 0.5D);
        for (var pass : FoundryPass.values()) {
            // The clipped workpiece and light raster share the same per-frame progress snapshot.
            var layer = pass.glow() ? OmniRenderLayers.additiveColor() : OmniRenderLayers.translucentEmissiveColor();
            renderFabricationPass(poseStack, buffers.getBuffer(layer), time, detailed,
                    machine.isClientStructureFormed(), facing, pass, animation, stellar);
        }
        poseStack.popPose();
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

    /** Live rendering and GPU verification share formed/running gating, orientation and pass selection. */
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
