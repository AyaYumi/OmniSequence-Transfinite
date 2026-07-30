package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class MolecularCenterGhostPreview {
    private static final int REFRESH_INTERVAL = 40;
    private static final int PROJECTION_ALPHA = 118;
    private static final double MAX_RENDER_DISTANCE_SQUARED = 192.0 * 192.0;
    private static final SectionedGhostPreviewRenderer CACHE =
            new SectionedGhostPreviewRenderer(PROJECTION_ALPHA);
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
                || !(level.getBlockEntity(controller) instanceof MolecularCenterBlockEntity)) {
            clear();
            return;
        }
        facing = level.getBlockState(controller).getValue(HorizontalDirectionalBlock.FACING);
        var blocks = new ArrayList<SectionedGhostPreviewRenderer.GhostBlock>();
        for (var part : MolecularCenterStructure.parts()) {
            if (MolecularCenterStructure.isController(part)) {
                continue;
            }
            var pos = MolecularCenterStructure.worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            var currentState = level.getBlockState(pos);
            boolean clearance = part.partType() == MolecularCenterStructure.PartType.AIR;
            var expectedState = MolecularCenterStructure.partState(part.partType());
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
