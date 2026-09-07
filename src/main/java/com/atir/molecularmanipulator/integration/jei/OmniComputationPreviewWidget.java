package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.blockentity.OmniComputationStructure;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;

final class OmniComputationPreviewWidget extends InteractiveStructurePreviewWidget {
    private static final List<RenderPart> PARTS = createRenderParts(false);
    private static final List<RenderPart> OVERVIEW_PARTS = createRenderParts(true);

    OmniComputationPreviewWidget() {
        super(PARTS, OVERVIEW_PARTS, OmniComputationStructure.MIN_Y, OmniComputationStructure.HEIGHT, StructureJeiLayout.previewHeight());
    }

    private static List<RenderPart> createRenderParts(boolean overview) {
        var result = new ArrayList<RenderPart>();
        for (var part : OmniComputationStructure.parts()) {
            if (part.type() == OmniComputationStructure.PartType.AIR) {
                continue;
            }
            if (overview && shouldCullFromOverview(part)) {
                continue;
            }
            BlockState state = OmniComputationStructure.block(part.type()).defaultBlockState();
            result.add(new RenderPart(part.x(), part.y(), part.z(), state));
        }
        return List.copyOf(result);
    }

    private static boolean shouldCullFromOverview(OmniComputationStructure.Part part) {
        boolean horizontalInterior = occupied(part.x() - 1, part.y(), part.z())
                && occupied(part.x() + 1, part.y(), part.z())
                && occupied(part.x(), part.y(), part.z() - 1)
                && occupied(part.x(), part.y(), part.z() + 1);
        boolean enclosed = horizontalInterior
                && occupied(part.x(), part.y() - 1, part.z())
                && occupied(part.x(), part.y() + 1, part.z());
        if (enclosed) {
            return true;
        }
        return false;
    }

    private static boolean occupied(int x, int y, int z) {
        var part = OmniComputationStructure.partAt(x, y, z);
        return part != null && part.type() != OmniComputationStructure.PartType.AIR;
    }

}
