package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.blockentity.SingularityStructure;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.client.model.data.ModelData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class SingularityStructurePreviewWidget extends InteractiveStructurePreviewWidget {
    private static final List<RenderPart> PARTS = createRenderParts(false);
    private static final List<RenderPart> OVERVIEW_PARTS = createRenderParts(true);
    private int connectedGeneration = -1;
    private final Map<Integer, Map<BlockPos, ModelData>> connectedLayers = new HashMap<>();

    SingularityStructurePreviewWidget() {
        super(PARTS, OVERVIEW_PARTS, 0, SingularityStructure.HEIGHT, StructureJeiLayout.previewHeight());
    }

    @Override
    protected float previewScale() { return layer < 0 ? 1.45F : 1.0F; }

    @Override
    protected Map<BlockPos, ModelData> connectedData() {
        int generation = MatterConnectedModel.generation();
        if (connectedGeneration != generation) {
            connectedLayers.clear();
            connectedGeneration = generation;
        }
        return connectedLayers.computeIfAbsent(layer, selectedLayer -> {
            var states = new HashMap<BlockPos, BlockState>();
            for (var part : PARTS) {
                if (selectedLayer < 0 || part.y() == selectedLayer) {
                    states.put(new BlockPos(part.x(), part.y(), part.z()), part.state());
                }
            }
            var result = new HashMap<BlockPos, ModelData>();
            states.forEach((pos, state) -> result.put(pos, MatterConnectedModel.modelData(
                    MatterConnectedModel.connectionMasks(
                            adjacent -> states.getOrDefault(adjacent, Blocks.AIR.defaultBlockState()), pos, state))));
            return result;
        });
    }

    private static List<RenderPart> createRenderParts(boolean overview) {
        Set<LocalPos> positions = new HashSet<>();
        for (var part : SingularityStructure.parts()) {
            positions.add(new LocalPos(part.x(), part.y(), part.z()));
        }
        var result = new ArrayList<RenderPart>();
        for (var part : SingularityStructure.parts()) {
            if (overview && shouldCullFromOverview(part, positions)) {
                continue;
            }
            BlockState state = SingularityStructure.state(part, net.minecraft.core.Direction.NORTH);
            result.add(new RenderPart(part.x(), part.y(), part.z(), state));
        }
        return List.copyOf(result);
    }

    private static boolean shouldCullFromOverview(SingularityStructure.Part part,
            Set<LocalPos> positions) {
        if (part.type() == SingularityStructure.Type.CORE || part.type() == SingularityStructure.Type.GLASS
                || part.type() == SingularityStructure.Type.STAIRS || part.type() == SingularityStructure.Type.SLAB) return false;
        var pos = new LocalPos(part.x(), part.y(), part.z());
        boolean enclosed = positions.contains(pos.offset(-1, 0, 0))
                && positions.contains(pos.offset(1, 0, 0))
                && positions.contains(pos.offset(0, -1, 0))
                && positions.contains(pos.offset(0, 1, 0))
                && positions.contains(pos.offset(0, 0, -1))
                && positions.contains(pos.offset(0, 0, 1));
        if (enclosed) {
            return true;
        }
        return false;
    }

    private record LocalPos(int x, int y, int z) {
        LocalPos offset(int dx, int dy, int dz) { return new LocalPos(x + dx, y + dy, z + dz); }
    }
}
