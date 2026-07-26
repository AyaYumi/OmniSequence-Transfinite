package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.LevelEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class MolecularCenterSpawnProtection {
    public static final int PROTECTED_CHUNK_RADIUS = 2;
    private static final Map<ServerLevel, Map<BlockPos, ProtectedArea>> ACTIVE_AREAS = new WeakHashMap<>();

    private MolecularCenterSpawnProtection() {
    }

    public static void update(ServerLevel level, BlockPos controllerPos, boolean active) {
        if (!active) {
            unregister(level, controllerPos);
            return;
        }
        var state = level.getBlockState(controllerPos);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            unregister(level, controllerPos);
            return;
        }
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var visualCenter = MolecularCenterStructure.worldPoint(controllerPos, facing,
                MolecularCenterStructure.VISUAL_CENTER_X,
                MolecularCenterStructure.CONTROLLER_Y,
                MolecularCenterStructure.VISUAL_CENTER_Z);
        var centerChunk = new ChunkPos(BlockPos.containing(visualCenter));
        ACTIVE_AREAS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(controllerPos.immutable(), new ProtectedArea(centerChunk.x, centerChunk.z));
    }

    public static void updateOmni(ServerLevel level, BlockPos controllerPos, boolean active) {
        if (!active) {
            unregister(level, controllerPos);
            return;
        }
        var state = level.getBlockState(controllerPos);
        if (!state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            unregister(level, controllerPos);
            return;
        }
        var facing = state.getValue(HorizontalDirectionalBlock.FACING);
        var structureCenter = OmniComputationStructure.worldPoint(controllerPos, facing,
                0, OmniComputationStructure.CONTROLLER_Y, 0);
        var centerChunk = new ChunkPos(BlockPos.containing(structureCenter));
        ACTIVE_AREAS.computeIfAbsent(level, ignored -> new HashMap<>())
                .put(controllerPos.immutable(), new ProtectedArea(centerChunk.x, centerChunk.z));
    }

    public static void unregister(ServerLevel level, BlockPos controllerPos) {
        var levelAreas = ACTIVE_AREAS.get(level);
        if (levelAreas == null) {
            return;
        }
        levelAreas.remove(controllerPos);
        if (levelAreas.isEmpty()) {
            ACTIVE_AREAS.remove(level);
        }
    }

    @SubscribeEvent
    public static void preventNaturalHostileSpawns(MobSpawnEvent.SpawnPlacementCheck event) {
        if (event.getEntityType().getCategory() != MobCategory.MONSTER
                || !isProtectedSpawnType(event.getSpawnType())) {
            return;
        }
        var level = event.getLevel().getLevel();
        var levelAreas = ACTIVE_AREAS.get(level);
        if (levelAreas == null || levelAreas.isEmpty()) {
            return;
        }
        int chunkX = event.getPos().getX() >> 4;
        int chunkZ = event.getPos().getZ() >> 4;
        for (var area : levelAreas.values()) {
            if (area.contains(chunkX, chunkZ)) {
                event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
                return;
            }
        }
    }

    private static boolean isProtectedSpawnType(MobSpawnType spawnType) {
        return spawnType == MobSpawnType.NATURAL
                || spawnType == MobSpawnType.CHUNK_GENERATION
                || spawnType == MobSpawnType.PATROL
                || spawnType == MobSpawnType.REINFORCEMENT;
    }

    @SubscribeEvent
    public static void clearLevel(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ACTIVE_AREAS.remove(serverLevel);
        }
    }

    private record ProtectedArea(int centerChunkX, int centerChunkZ) {
        boolean contains(int chunkX, int chunkZ) {
            return Math.abs(chunkX - centerChunkX) <= PROTECTED_CHUNK_RADIUS
                    && Math.abs(chunkZ - centerChunkZ) <= PROTECTED_CHUNK_RADIUS;
        }
    }
}
