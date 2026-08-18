package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MatterFabricationBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
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
public final class MatterFabricationGhostPreview {
    private static final int REFRESH_INTERVAL = 40;
    private static final double MAX_DISTANCE_SQUARED = 192.0 * 192.0;
    private static final SectionedGhostPreviewRenderer CACHE = new SectionedGhostPreviewRenderer(118);
    private static BlockPos controller;
    private static Direction facing;
    private static ResourceKey<Level> dimension;
    private static long lastRefresh = Long.MIN_VALUE;

    private MatterFabricationGhostPreview() {
    }

    public static boolean toggle(MatterFabricationBlockEntity machine) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        var selected = machine.getBlockPos().immutable();
        if (selected.equals(controller) && minecraft.level.dimension().equals(dimension)) {
            clear();
            return false;
        }
        controller = selected;
        facing = machine.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        dimension = minecraft.level.dimension();
        lastRefresh = Long.MIN_VALUE;
        refresh(minecraft.level);
        return true;
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
        if (event.getCamera().getPosition().distanceToSqr(controller.getCenter()) > MAX_DISTANCE_SQUARED) {
            return;
        }
        if (level.getGameTime() - lastRefresh >= REFRESH_INTERVAL) {
            refresh(level);
        }
        if (!CACHE.isEmpty()) {
            CACHE.render(event);
        }
    }

    private static void refresh(Level level) {
        if (controller == null || !level.hasChunkAt(controller)
                || !(level.getBlockEntity(controller) instanceof MatterFabricationBlockEntity)) {
            clear();
            return;
        }
        facing = level.getBlockState(controller).getValue(HorizontalDirectionalBlock.FACING);
        var blocks = new ArrayList<SectionedGhostPreviewRenderer.GhostBlock>();
        for (var part : MatterFabricationStructure.parts()) {
            if (MatterFabricationStructure.isController(part)) {
                continue;
            }
            var pos = MatterFabricationStructure.worldPos(controller, facing, part);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            var current = level.getBlockState(pos);
            var expected = MatterFabricationStructure.partState(part.type());
            if (current.is(expected.getBlock())) {
                continue;
            }
            blocks.add(new SectionedGhostPreviewRenderer.GhostBlock(pos, expected,
                    !current.canBeReplaced()));
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
