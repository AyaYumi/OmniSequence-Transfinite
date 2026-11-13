package com.atir.molecularmanipulator.world;

import com.atir.molecularmanipulator.registry.HighEnergyLabContent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/** Vanilla's template placement clips blocks and the visual entity to each generating chunk. */
public final class HighEnergyLabPiece extends TemplateStructurePiece {
    public static final ResourceLocation TEMPLATE = HighEnergyLabContent.ID;
    public static final BlockPos PIVOT = new BlockPos(83, 0, 87);

    public HighEnergyLabPiece(StructureTemplateManager templates, BlockPos origin, Rotation rotation) {
        super(HighEnergyLabContent.STRUCTURE_PIECE.get(), 0, templates, TEMPLATE, TEMPLATE.toString(),
                settings(rotation), origin);
    }

    public HighEnergyLabPiece(StructureTemplateManager templates, CompoundTag tag) {
        super(HighEnergyLabContent.STRUCTURE_PIECE.get(), migrateTemplate(tag), templates,
                id -> settings(Rotation.valueOf(tag.getString("Rot"))));
    }

    private static CompoundTag migrateTemplate(CompoundTag tag) {
        if (!tag.getString("Template").equals("molecularmanipulator:end_high_energy_lab_ring")) return tag;
        var migrated = tag.copy();
        migrated.putString("Template", TEMPLATE.toString());
        return migrated;
    }

    public static StructurePlaceSettings settings(Rotation rotation) {
        return new StructurePlaceSettings().setRotation(rotation).setRotationPivot(PIVOT).setIgnoreEntities(false);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", getRotation().name());
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level,
            RandomSource random, BoundingBox box) {}
}
