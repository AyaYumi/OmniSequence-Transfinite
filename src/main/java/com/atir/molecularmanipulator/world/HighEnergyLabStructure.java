package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import com.mojang.serialization.MapCodec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/** A single floating laboratory at fixed coordinates in the outer End. */
public final class HighEnergyLabStructure extends Structure {
    public static final MapCodec<HighEnergyLabStructure> CODEC = simpleCodec(HighEnergyLabStructure::new);
    public static final BlockPos ORIGIN = new BlockPos(8109, 128, 8105);
    public static final Vec3i SIZE = new Vec3i(163, 73, 169);
    public static final ChunkPos START_CHUNK = new ChunkPos(512, 512);
    public static final ResourceLocation LANDING_TEMPLATE = MolecularManipulator.id("end_lab_warp_landing");
    public static final BlockPos LANDING_ORIGIN = ORIGIN.offset(80, 35, 140);
    public static final Vec3i LANDING_SIZE = new Vec3i(7, 5, 12);

    public HighEnergyLabStructure(StructureSettings settings) {
        super(settings);
    }

    @Override
    public Optional<GenerationStub> findGenerationPoint(GenerationContext context) {
        if (!START_CHUNK.equals(context.chunkPos())) return Optional.empty();
        return Optional.of(new GenerationStub(ORIGIN.offset(83, 34, 87), pieces -> {
            pieces.addPiece(new HighEnergyLabPiece(context.structureTemplateManager(), ORIGIN, Rotation.NONE));
            pieces.addPiece(new EndWarpTemplatePiece(context.structureTemplateManager(), LANDING_TEMPLATE, LANDING_ORIGIN));
        }));
    }

    @Override
    public StructureType<?> type() {
        return HighEnergyLabContent.STRUCTURE_TYPE.get();
    }
}
