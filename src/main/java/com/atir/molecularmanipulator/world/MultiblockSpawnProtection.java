package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

/** All-height natural-spawn protection follows the three multiblocks' owned chunk footprints. */
@EventBusSubscriber(modid = MolecularManipulator.MOD_ID)
public final class MultiblockSpawnProtection {
    private MultiblockSpawnProtection() {
    }

    @SubscribeEvent
    public static void checkPlacement(MobSpawnEvent.SpawnPlacementCheck event) {
        if (isNaturalSpawn(event.getSpawnType())
                && MultiblockChunkLoading.isOccupiedChunk(event.getLevel().getLevel(), event.getPos())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void finalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
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
