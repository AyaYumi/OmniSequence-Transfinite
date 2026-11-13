package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.WarpContent;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** One small floating warp ruin south of the main End island. */
public final class EndWarpRuinStructure extends Structure {
    public static final Codec<EndWarpRuinStructure> CODEC = simpleCodec(EndWarpRuinStructure::new);
    public static final ResourceLocation TEMPLATE = MolecularManipulator.id("end_warp_ruin");
    public static final BlockPos ORIGIN = new BlockPos(-20, 68, 204);
    public static final Vec3i SIZE = new Vec3i(41, 32, 41);
    public static final ChunkPos START_CHUNK = new ChunkPos(0, 14);

    public EndWarpRuinStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!START_CHUNK.equals(context.chunkPos())) return Optional.empty();
        return Optional.of(new GenerationStub(ORIGIN.offset(20, 22, 20), pieces -> pieces.addPiece(
                new EndWarpTemplatePiece(context.structureTemplateManager(), TEMPLATE, ORIGIN))));
    }

    @Override
    public StructureType<?> type() {
        return WarpContent.RUIN_STRUCTURE_TYPE.get();
    }
}
