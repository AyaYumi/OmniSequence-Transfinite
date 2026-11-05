package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.registry.WarpContent;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/** A single candidate chunk, also visible to vanilla's random-spread locate path. */
public final class FixedStructurePlacement extends RandomSpreadStructurePlacement {
    public static final MapCodec<FixedStructurePlacement> CODEC = RecordCodecBuilder.mapCodec(instance ->
            placementCodec(instance).and(instance.group(
                    Codec.INT.fieldOf("chunk_x").forGetter(placement -> placement.chunk.x),
                    Codec.INT.fieldOf("chunk_z").forGetter(placement -> placement.chunk.z)))
                    .apply(instance, FixedStructurePlacement::new));

    private final ChunkPos chunk;

    public FixedStructurePlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod,
            float frequency, int salt, Optional<ExclusionZone> exclusionZone, int chunkX, int chunkZ) {
        super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone, 1, 0, RandomSpreadType.LINEAR);
        this.chunk = new ChunkPos(chunkX, chunkZ);
    }

    public FixedStructurePlacement(int chunkX, int chunkZ) {
        this(Vec3i.ZERO, FrequencyReductionMethod.DEFAULT, 1.0F, 0, Optional.empty(), chunkX, chunkZ);
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int regionX, int regionZ) {
        return chunk;
    }

    @Override
    public StructurePlacementType<?> type() {
        return WarpContent.FIXED_PLACEMENT.get();
    }
}
