package com.atir.molecularmanipulator.mixin;

import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Forge 1.20's public write method discards the future needed by the transfer journal. */
@Mixin(ChunkStorage.class)
public interface BuildingChunkStorageAccessor {
    @Accessor("worker")
    IOWorker folding$worker();
}
