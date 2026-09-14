package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** All-height natural-spawn protection follows the three multiblocks' owned chunk footprints. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class MultiblockSpawnProtection {
    private MultiblockSpawnProtection() {
    }

    @SubscribeEvent
    public static void checkPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (isNaturalSpawn(event.getSpawnType())
                && MultiblockChunkLoading.isOccupiedChunk(event.getLevel().getLevel(), event.getPos())) {
            event.setResult(MobSpawnEvent.SpawnPlacementCheck.Result.FAIL);
        }
    }

    @SubscribeEvent
    public static void finalizeSpawn(FinalizeSpawnEvent event) {
        // Some natural/special spawns skip placement checks. Cancel the spawn itself,
        // not just initialization; loaded entities never go through this event.
        if (isNaturalSpawn(event.getSpawnType()) && MultiblockChunkLoading.isOccupiedChunk(
                event.getLevel().getLevel(), BlockPos.containing(event.getX(), event.getY(), event.getZ()))) {
            event.setSpawnCancelled(true);
        }
    }

    private static boolean isNaturalSpawn(MobSpawnType type) {
        return type == MobSpawnType.NATURAL || type == MobSpawnType.CHUNK_GENERATION
                || type == MobSpawnType.PATROL || type == MobSpawnType.REINFORCEMENT;
    }
}
