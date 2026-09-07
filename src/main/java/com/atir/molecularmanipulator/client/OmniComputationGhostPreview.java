package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class OmniComputationGhostPreview {
    private static final int REFRESH_INTERVAL = 40;
    private static final int PROJECTION_ALPHA = 112;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 192.0 * 192.0;
    private static final SectionedGhostPreviewRenderer CACHE =
            new SectionedGhostPreviewRenderer(PROJECTION_ALPHA);
    private static BlockPos controller;
    private static Direction facing;
    private static ResourceKey<Level> dimension;
    private static long lastRefresh = Long.MIN_VALUE;

    private OmniComputationGhostPreview() {
    }

    public static boolean toggle(OmniComputationCoreBlockEntity core) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        if (core.getBlockState().hasProperty(BlockStateProperties.POWERED)
                && core.getBlockState().getValue(BlockStateProperties.POWERED)) {
            clear();
            return false;
        }
        var selectedController = core.getBlockPos().immutable();
        var selectedDimension = minecraft.level.dimension();
        if (selectedController.equals(controller) && selectedDimension.equals(dimension)) {
            clear();
            return false;
        }
        controller = selectedController;
        facing = core.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        dimension = selectedDimension;
        lastRefresh = Long.MIN_VALUE;
        refresh(minecraft.level);
        return true;
    }

    public static boolean isShowing(OmniComputationCoreBlockEntity core) {
        var minecraft = Minecraft.getInstance();
        return minecraft.level != null
                && controller != null
                && controller.equals(core.getBlockPos())
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
        var camera = event.getCamera().getPosition();
        if (camera.distanceToSqr(controller.getCenter()) > MAX_RENDER_DISTANCE_SQUARED) {
            return;
        }
        if (level.getGameTime() - lastRefresh >= REFRESH_INTERVAL) {
            refresh(level);
        }
        if (controller == null || CACHE.isEmpty()) {
            return;
        }

        CACHE.render(event);
    }

    private static void refresh(Level level) {
        if (controller == null || !level.hasChunkAt(controller)
                || !(level.getBlockEntity(controller)
                        instanceof OmniComputationCoreBlockEntity core)) {
            clear();
            return;
        }
        if (core.getInspection().formed()
                || core.getBlockState().hasProperty(BlockStateProperties.POWERED)
                && core.getBlockState().getValue(BlockStateProperties.POWERED)) {
            clear();
            return;
        }
        facing = level.getBlockState(controller).getValue(HorizontalDirectionalBlock.FACING);
        var blocks = new ArrayList<SectionedGhostPreviewRenderer.GhostBlock>();
        for (var part : OmniComputationStructure.parts()) {
            if (part.type() == OmniComputationStructure.PartType.CONTROLLER
                    || isFullyEnclosed(part)) {
                continue;
            }
            var pos = OmniComputationStructure.worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            var expectedState = OmniComputationStructure.block(part.type()).defaultBlockState();
            var currentState = level.getBlockState(pos);
            boolean clearance = part.type() == OmniComputationStructure.PartType.AIR;
            if (clearance ? currentState.isAir() : currentState.is(expectedState.getBlock())) {
                continue;
            }
            boolean conflict = clearance || !currentState.canBeReplaced();
            blocks.add(new SectionedGhostPreviewRenderer.GhostBlock(
                    pos, expectedState, conflict));
        }
        CACHE.update(blocks);
        lastRefresh = level.getGameTime();
    }

    private static boolean isFullyEnclosed(OmniComputationStructure.Part part) {
        return occupied(part.x() - 1, part.y(), part.z())
                && occupied(part.x() + 1, part.y(), part.z())
                && occupied(part.x(), part.y() - 1, part.z())
                && occupied(part.x(), part.y() + 1, part.z())
                && occupied(part.x(), part.y(), part.z() - 1)
                && occupied(part.x(), part.y(), part.z() + 1);
    }

    private static boolean occupied(int x, int y, int z) {
        var part = OmniComputationStructure.partAt(x, y, z);
        return part != null && part.type() != OmniComputationStructure.PartType.AIR;
    }

    static void onResourceReload() {
        Runnable reload = () -> {
            CACHE.close();
            lastRefresh = Long.MIN_VALUE;
        };
        if (RenderSystem.isOnRenderThread()) {
            reload.run();
        } else {
            RenderSystem.recordRenderCall(reload::run);
        }
    }

    private static void clear() {
        controller = null;
        facing = null;
        dimension = null;
        CACHE.close();
        lastRefresh = Long.MIN_VALUE;
    }
}
