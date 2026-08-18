package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.Part;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure.PartType;
import com.atir.molecularmanipulator.client.render.OmniRenderGeometry;
import com.atir.molecularmanipulator.client.render.OmniRenderLayers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider.Context;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Renders the original octagonal fabrication well with real vertex geometry. */
public final class MatterFabricationRenderer
        implements BlockEntityRenderer<MatterFabricationBlockEntity> {

    public MatterFabricationRenderer(Context context) {
    }

    @Override
    public void render(MatterFabricationBlockEntity machine, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (machine.getLevel() == null || !machine.isClientStructureFormed()) {
            return;
        }
        Direction facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Part centerPart = new Part(0, 7, 0, PartType.CORE);
        BlockPos center = MatterFabricationStructure.worldPos(machine.getBlockPos(), facing,
                centerPart);
        float time = machine.getLevel().getGameTime() + partialTick;
        float speed = machine.isClientRunning() ? 2.4F : 0.65F;
        float pulse = 1.0F + (float) Math.sin(time * 0.12F * speed) * 0.075F;

        poseStack.pushPose();
        poseStack.translate(center.getX() - machine.getBlockPos().getX() + 0.5D,
                center.getY() - machine.getBlockPos().getY() + 0.5D,
                center.getZ() - machine.getBlockPos().getZ() + 0.5D);
        int segments = machine.isClientRunning() ? 64 : 40;
        VertexConsumer solid = buffers.getBuffer(OmniRenderLayers.solidEmissiveColor());
        drawWell(poseStack, solid, time, speed, pulse, segments, false);
        poseStack.pushPose();
        poseStack.scale(1.045F, 1.045F, 1.045F);
        VertexConsumer glow = buffers.getBuffer(OmniRenderLayers.additiveColor());
        drawWell(poseStack, glow, time, speed, pulse, segments, true);
        poseStack.popPose();
        poseStack.popPose();
    }

    private static void drawWell(PoseStack poseStack, VertexConsumer consumer, float time,
            float speed, float pulse, int segments, boolean glow) {
        int shell = 0xEAD9FF;
        int coil = 0xA95BFF;
        int glass = 0x71E6FF;
        int core = 0xFFF0FF;
        int shellAlpha = glow ? 42 : 228;
        int coilAlpha = glow ? 76 : 250;
        int glassAlpha = glow ? 58 : 238;
        int coreAlpha = glow ? 98 : 255;
        float spin = time * speed * 0.62F;

        OmniRenderGeometry.ring(poseStack, consumer, 0.0F, -0.1F, 0.0F,
                16.4F * pulse, 1.25F, segments, 0.0F, 0.0F, spin,
                argb(coil, coilAlpha));
        OmniRenderGeometry.segmentedRing(poseStack, consumer, 0.0F, 1.1F, 0.0F,
                14.3F * pulse, 0.9F, 0.66F, 16, 0.78F, 0.0F, 0.0F,
                -spin * 0.72F, argb(shell, shellAlpha));
        OmniRenderGeometry.ring(poseStack, consumer, 0.0F, 2.1F, 0.0F,
                11.2F * pulse, 0.78F, segments, 0.0F, 0.0F, spin * 0.43F,
                argb(glass, glassAlpha));
        OmniRenderGeometry.segmentedRing(poseStack, consumer, 0.0F, 3.05F, 0.0F,
                7.75F * pulse, 0.62F, 0.52F, 12, 0.75F, 0.0F, 0.0F,
                spin * 1.1F, argb(coil, coilAlpha));

        PoseStack.Pose pose = poseStack.last();
        OmniRenderGeometry.octahedron(pose, consumer,
                new Vec3(0.0, 3.3 + (pulse - 1.0F) * 2.0, 0.0),
                2.15F * pulse, 2.85F * pulse, 2.15F * pulse, spin * 1.7F,
                argb(core, coreAlpha));
        OmniRenderGeometry.octahedron(pose, consumer, new Vec3(0.0, 1.1, 0.0),
                0.95F * pulse, 1.65F * pulse, 0.95F * pulse, -spin * 2.1F,
                argb(glass, glow ? 108 : 250));

        int[][] stations = {{0, -11}, {0, 11}, {-11, 0}, {11, 0}};
        for (int index = 0; index < stations.length; index++) {
            int x = stations[index][0];
            int z = stations[index][1];
            Vec3 station = new Vec3(x, -3.7, z);
            Vec3 focus = new Vec3(x * 0.25, 1.0, z * 0.25);
            OmniRenderGeometry.beam(pose, consumer, station, focus, 0.24F, 0.18F,
                    argb(index % 2 == 0 ? shell : coil, glow ? 48 : 230));
            OmniRenderGeometry.rune(pose, consumer, station, 0.72F, 0.12F,
                    index * 90.0F + spin,
                    argb(index % 2 == 0 ? glass : core, glow ? 58 : 238));
        }

        for (int index = 0; index < 4; index++) {
            double a = Math.PI / 4.0 + index * Math.PI * 0.5;
            Vec3 start = new Vec3(Math.cos(a) * 8.1, -2.8, Math.sin(a) * 8.1);
            Vec3 end = new Vec3(Math.cos(a) * 3.1, 1.4, Math.sin(a) * 3.1);
            OmniRenderGeometry.beam(pose, consumer, start, end, 0.16F, 0.12F,
                    argb(glass, glow ? 42 : 218));
        }

        OmniRenderGeometry.beam(pose, consumer, new Vec3(0.0, -6.0, 0.0),
                new Vec3(0.0, 6.0, 0.0), 0.18F, 0.18F,
                argb(coil, glow ? 72 : 242));
        int runeCount = 8;
        for (int index = 0; index < runeCount; index++) {
            double a = Math.PI * 2 * index / runeCount + Math.toRadians(spin * 0.35F);
            Vec3 point = new Vec3(Math.cos(a) * 14.9,
                    0.9 + Math.sin(a * 3.0) * 0.28, Math.sin(a) * 14.9);
            OmniRenderGeometry.rune(pose, consumer, point, 0.58F, 0.1F,
                    (float) Math.toDegrees(a) + 90.0F,
                    argb(index % 2 == 0 ? glass : shell, glow ? 46 : 222));
        }
    }

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
        return 512;
    }

    @Override
    public boolean shouldRender(MatterFabricationBlockEntity machine, Vec3 cameraPos) {
        return cameraPos.distanceToSqr(Vec3.atCenterOf(machine.getBlockPos())) < 512.0D * 512.0D;
    }

    @Override
    public AABB getRenderBoundingBox(MatterFabricationBlockEntity machine) {
        Direction facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockPos center = MatterFabricationStructure.worldPos(machine.getBlockPos(), facing,
                new Part(0, 7, 0, PartType.CORE));
        double x = center.getX() + 0.5D;
        double y = center.getY() + 0.5D;
        double z = center.getZ() + 0.5D;
        return new AABB(x - 25.0D, y - 14.0D, z - 25.0D,
                x + 25.0D, y + 14.0D, z + 25.0D);
    }
}
