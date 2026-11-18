package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.registry.WarpContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Unrotated template piece shared by the source ruin and the lab's attached warp landing. */
public final class EndWarpTemplatePiece extends TemplateStructurePiece {
    public EndWarpTemplatePiece(StructureTemplateManager templates, ResourceLocation template, BlockPos origin) {
        super(WarpContent.RUIN_PIECE_TYPE.get(), 0, templates, template, template.toString(), settings(), origin);
    }

    public EndWarpTemplatePiece(StructureTemplateManager templates, CompoundTag tag) {
        super(WarpContent.RUIN_PIECE_TYPE.get(), tag, templates, id -> settings());
    }

    public static StructurePlaceSettings settings() {
        return new StructurePlaceSettings().setIgnoreEntities(false);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunk, BlockPos pos) {
        super.postProcess(level, structures, generator, random, box, chunk, pos);
        WarpCoordinates.applyInscriptions(level, level.getSeed(), box);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
            RandomSource random, BoundingBox box) {}
}
