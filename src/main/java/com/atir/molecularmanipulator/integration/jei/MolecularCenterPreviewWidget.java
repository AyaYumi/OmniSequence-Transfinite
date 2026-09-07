package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.blockentity.MolecularCenterStructure;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import com.atir.molecularmanipulator.registry.ModContent;

final class MolecularCenterPreviewWidget extends InteractiveStructurePreviewWidget {
    private static final List<RenderPart> PARTS = createRenderParts(false);
    private static final List<RenderPart> OVERVIEW_PARTS = createRenderParts(true);

    MolecularCenterPreviewWidget() {
        super(PARTS, OVERVIEW_PARTS, MolecularCenterStructure.CURRENT_MIN_Y, MolecularCenterStructure.CURRENT_SIZE, StructureJeiLayout.previewHeight());
    }

    @Override protected String dimensions() {
        return (MolecularCenterStructure.MAX_X - MolecularCenterStructure.MIN_X + 1) + " × "
                + (MolecularCenterStructure.MAX_Z - MolecularCenterStructure.MIN_Z + 1) + " × "
                + MolecularCenterStructure.CURRENT_SIZE;
    }

    private static List<RenderPart> createRenderParts(boolean overview) {
        var result = new ArrayList<RenderPart>();
        for (var part : MolecularCenterStructure.parts()) {
            if (part.partType() == MolecularCenterStructure.PartType.AIR) {
                continue;
            }
            if (overview && shouldCullFromOverview(part)) {
                continue;
            }
            BlockState state;
            if (MolecularCenterStructure.isController(part)) {
                state = ModContent.MOLECULAR_CENTER_CONTROLLER.get().defaultBlockState();
            } else {
                state = MolecularCenterStructure.partState(part.partType());
            }
            result.add(new RenderPart(part.x(), part.y(), part.z(), state));
        }
        return List.copyOf(result);
    }

    private static boolean shouldCullFromOverview(MolecularCenterStructure.Part part) {
        if (part.partType() == MolecularCenterStructure.PartType.CORE) {
            return false;
        }
        return isOpaquePart(MolecularCenterStructure.partAt(part.x() - 1, part.y(), part.z()))
                && isOpaquePart(MolecularCenterStructure.partAt(part.x() + 1, part.y(), part.z()))
                && isOpaquePart(MolecularCenterStructure.partAt(part.x(), part.y() - 1, part.z()))
                && isOpaquePart(MolecularCenterStructure.partAt(part.x(), part.y() + 1, part.z()))
                && isOpaquePart(MolecularCenterStructure.partAt(part.x(), part.y(), part.z() - 1))
                && isOpaquePart(MolecularCenterStructure.partAt(part.x(), part.y(), part.z() + 1));
    }

    private static boolean isOpaquePart(MolecularCenterStructure.Part part) {
        return part != null && part.partType() != MolecularCenterStructure.PartType.AIR
                && part.partType() != MolecularCenterStructure.PartType.GLASS
                && part.partType() != MolecularCenterStructure.PartType.AE_VIBRANT_GLASS;
    }

}
