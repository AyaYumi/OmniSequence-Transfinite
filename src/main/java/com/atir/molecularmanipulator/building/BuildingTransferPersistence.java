package com.atir.molecularmanipulator.building;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CompletionException;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.storage.LevelResource;
import com.atir.molecularmanipulator.mixin.BuildingChunkStorageAccessor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Checked durability boundaries for transfers; ordinary vanilla save methods log some failures and return normally. */
final class BuildingTransferPersistence {
    private BuildingTransferPersistence() {}

    static void savePlayer(ServerPlayer player) throws IOException {
        player.getInventory().setChanged();
        player.server.getPlayerList().saveAll();
        var path = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
        var saved = BuildingArchiveStore.localNbt(path);
        if (!player.getInventory().save(new ListTag()).equals(saved.getList("Inventory", Tag.TAG_COMPOUND)))
            throw new IOException("Building transfer receipt was not saved in the player's inventory");
        BuildingArchiveStore.force(path);
    }

    static void checkpoint(ServerLevel level, List<BuildingArchiveStore.Entry> layout, BlockPos origin, Rotation rotation) throws IOException {
        // Save dimension metadata, released machine tickets and normal server bookkeeping first.
        level.getServer().saveEverything(true, true, true);
        var chunks = new HashSet<ChunkPos>();
        for (var entry : layout) chunks.add(new ChunkPos(origin.offset(entry.offset().rotate(rotation))));
        var storage = level.getChunkSource().chunkMap;
        try {
            for (var pos : chunks) {
                var chunk = level.getChunk(pos.x, pos.z);
                var data = ChunkSerializer.write(level, chunk);
                MinecraftForge.EVENT_BUS.post(new ChunkDataEvent.Save(chunk, level, data));
                // Native serialization can log and skip a failing block entity. Do not commit an incomplete machine.
                var savedEntities = new HashSet<BlockPos>();
                for (var value : data.getList("block_entities", Tag.TAG_COMPOUND)) {
                    var tag = (net.minecraft.nbt.CompoundTag) value;
                    savedEntities.add(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")));
                }
                for (var entry : layout) {
                    var at = origin.offset(entry.offset().rotate(rotation));
                    // EntityBlock/hasBlockEntity also covers blocks whose native factory deliberately returns null
                    // (for example non-network MolecularCenterPartBlock casings). Only real entities must serialize.
                    if (new ChunkPos(at).equals(pos) && level.getBlockEntity(at) != null && !savedEntities.contains(at))
                        throw new IOException("Native chunk save omitted a restored component at " + at);
                }
                // Unlike the vanilla background save callback, joining this future propagates IO failures.
                ((BuildingChunkStorageAccessor) storage).folding$worker().store(pos, data).join();
            }
            storage.flushWorker();
        } catch (CompletionException error) {
            throw new IOException("Could not flush building transfer chunks", error.getCause());
        }
    }
}
