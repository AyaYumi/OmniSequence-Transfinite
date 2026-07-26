package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.List;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class MolecularCenterGhostPreview {
    private static final int REFRESH_INTERVAL = 10;
    private static final int PROJECTION_ALPHA = 118;
    private static final List<GhostBlock> BLOCKS = new ArrayList<>();
    private static BlockPos controller;
    private static Direction facing;
    private static ResourceKey<Level> dimension;
    private static long lastRefresh = Long.MIN_VALUE;

    private MolecularCenterGhostPreview() {
    }

    public static boolean toggle(MolecularCenterBlockEntity center) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        var selectedController = center.getBlockPos().immutable();
        var selectedDimension = minecraft.level.dimension();
        if (selectedController.equals(controller) && selectedDimension.equals(dimension)) {
            clear();
            return false;
        }
        controller = selectedController;
        facing = center.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        dimension = selectedDimension;
        lastRefresh = Long.MIN_VALUE;
        refresh(minecraft.level);
        return true;
    }

    public static boolean isShowing(MolecularCenterBlockEntity center) {
        var minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && controller != null
                && controller.equals(center.getBlockPos())
                && minecraft.level.dimension().equals(dimension);
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || controller == null) {
            return;
        }
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !level.dimension().equals(dimension)) {
            clear();
            return;
        }
        if (level.getGameTime() - lastRefresh >= REFRESH_INTERVAL) {
            refresh(level);
        }
        if (controller == null || BLOCKS.isEmpty()) {
            return;
        }

        var camera = event.getCamera().getPosition();
        var poseStack = event.getPoseStack();
        var buffers = minecraft.renderBuffers().bufferSource();
        var translucentType = RenderType.translucent();
        var translucent = new ProjectionVertexConsumer(
                buffers.getBuffer(translucentType), PROJECTION_ALPHA);
        var dispatcher = minecraft.getBlockRenderer();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (var ghost : BLOCKS) {
            if (!event.getFrustum().isVisible(ghost.bounds())) {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(ghost.pos().getX() + 0.5, ghost.pos().getY() + 0.5, ghost.pos().getZ() + 0.5);
            poseStack.scale(1.003F, 1.003F, 1.003F);
            poseStack.translate(-0.5, -0.5, -0.5);
            var model = dispatcher.getBlockModel(ghost.expectedState());
            dispatcher.getModelRenderer().renderModel(
                    poseStack.last(), translucent, ghost.expectedState(), model,
                    1.0F, 1.0F, 1.0F, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
        poseStack.popPose();
        buffers.endBatch(translucentType);

        var linesType = RenderType.lines();
        var lines = buffers.getBuffer(linesType);
        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);
        for (var ghost : BLOCKS) {
            if (ghost.conflict() && event.getFrustum().isVisible(ghost.bounds())) {
                LevelRenderer.renderLineBox(poseStack, lines, ghost.bounds(),
                        1.0F, 0.12F, 0.12F, 0.9F);
            }
        }
        poseStack.popPose();
        buffers.endBatch(linesType);
    }

    private static void refresh(Level level) {
        if (controller == null || !level.hasChunkAt(controller)
                || !(level.getBlockEntity(controller) instanceof MolecularCenterBlockEntity)) {
            clear();
            return;
        }
        facing = level.getBlockState(controller).getValue(HorizontalDirectionalBlock.FACING);
        BLOCKS.clear();
        for (var part : MolecularCenterStructure.parts()) {
            if (MolecularCenterStructure.isController(part)) {
                continue;
            }
            var pos = MolecularCenterStructure.worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            var currentState = level.getBlockState(pos);
            var expectedState = MolecularCenterStructure.partState(part.partType());
            if (currentState.is(expectedState.getBlock())) {
                continue;
            }
            boolean conflict = !currentState.isAir() && !currentState.canBeReplaced();
            BLOCKS.add(new GhostBlock(pos, expectedState,
                    new AABB(pos).inflate(0.003), conflict));
        }
        lastRefresh = level.getGameTime();
    }

    private static void clear() {
        controller = null;
        facing = null;
        dimension = null;
        BLOCKS.clear();
        lastRefresh = Long.MIN_VALUE;
    }

    private record GhostBlock(BlockPos pos, BlockState expectedState, AABB bounds, boolean conflict) {
    }

    private static final class ProjectionVertexConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final int alpha;

        private ProjectionVertexConsumer(VertexConsumer delegate, int alpha) {
            this.delegate = delegate;
            this.alpha = alpha;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int sourceAlpha) {
            delegate.setColor(red, green, blue, Math.min(sourceAlpha, alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            delegate.setNormal(x, y, z);
            return this;
        }
    }
}
