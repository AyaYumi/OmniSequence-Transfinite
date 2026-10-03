package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.SingularityBlockEntity;
import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedModel;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.client.event.RenderLevelStageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.function.Function;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID, value = Dist.CLIENT)
public final class SingularityGhostPreview {
    private static final int REFRESH_INTERVAL = 40;
    private static final double MAX_DISTANCE_SQUARED = 192.0 * 192.0;
    private static final SectionedGhostPreviewRenderer CACHE = new SectionedGhostPreviewRenderer(118);
    private static BlockPos controller;
    private static Direction facing;
    private static ResourceKey<Level> dimension;
    private static long lastRefresh = Long.MIN_VALUE;

    private SingularityGhostPreview() {
    }

    public static boolean toggle(SingularityBlockEntity machine) {
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
        if (lastRefresh == Long.MIN_VALUE || level.getGameTime() - lastRefresh >= REFRESH_INTERVAL) {
            refresh(level);
        }
        if (!CACHE.isEmpty()) {
            CACHE.render(event);
        }
    }

    private static void refresh(Level level) {
        if (controller == null || !level.hasChunkAt(controller)
                || !(level.getBlockEntity(controller) instanceof SingularityBlockEntity machine)) {
            clear();
            return;
        }
        facing = level.getBlockState(controller).getValue(HorizontalDirectionalBlock.FACING);
        // Connections use the completed blueprint, including parts that are already built.
        var planned = new HashMap<BlockPos, BlockState>();
        for (var part : machine.structureParts()) {
            var pos = machine.worldPos(part);
            var current = level.hasChunkAt(pos) ? level.getBlockState(pos) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            planned.put(pos, SingularityStructure.matches(current, part, facing)
                    ? current : SingularityStructure.state(part, facing));
        }
        Function<BlockPos, BlockState> previewStates = pos -> {
            var expected = planned.get(pos);
            return expected != null ? expected : level.hasChunkAt(pos) ? level.getBlockState(pos) : net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        };
        var blocks = new ArrayList<SectionedGhostPreviewRenderer.GhostBlock>();
        for (var part : machine.structureParts()) {
            if (SingularityStructure.isController(part) || ((SingularityBlockEntity) level.getBlockEntity(controller)).motion().owns(part)) {
                continue;
            }
            var pos = machine.worldPos(part);
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            var current = level.getBlockState(pos);
            var expected = SingularityStructure.state(part, facing);
            if (SingularityStructure.matches(current, part, facing)) {
                continue;
            }
            blocks.add(new SectionedGhostPreviewRenderer.GhostBlock(pos, expected,
                    !current.canBeReplaced(), MatterConnectedModel.connectionMasks(previewStates, pos, expected)));
        }
        for (var local : SingularityStructure.requiredAir()) {
            var pos = machine.worldPos(local);
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).isAir())
                blocks.add(new SectionedGhostPreviewRenderer.GhostBlock(pos,
                        com.atir.molecularmanipulator.registry.SingularityContent.GLASS.get().defaultBlockState(), true, 0));
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
