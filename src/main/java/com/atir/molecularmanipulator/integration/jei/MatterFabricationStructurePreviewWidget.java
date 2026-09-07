package com.atir.molecularmanipulator.integration.jei;

import com.atir.molecularmanipulator.blockentity.MatterFabricationStructure;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.model.data.ModelData;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class MatterFabricationStructurePreviewWidget extends InteractiveStructurePreviewWidget {
    private static final List<RenderPart> PARTS = createRenderParts(false);
    private static final List<RenderPart> OVERVIEW_PARTS = createRenderParts(true);
    private int connectedGeneration = -1;
    private final Map<Integer, Map<BlockPos, ModelData>> connectedLayers = new HashMap<>();

    MatterFabricationStructurePreviewWidget() {
        super(PARTS, OVERVIEW_PARTS, 0, MatterFabricationStructure.STRUCTURE_HEIGHT, StructureJeiLayout.previewHeight());
    }

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
        for (var part : MatterFabricationStructure.parts()) {
            positions.add(new LocalPos(part.x(), part.y(), part.z()));
        }
        var result = new ArrayList<RenderPart>();
        for (var part : MatterFabricationStructure.parts()) {
            if (overview && shouldCullFromOverview(part, positions)) {
                continue;
            }
            BlockState state = MatterFabricationStructure.isController(part)
                    ? ModContent.MATTER_FABRICATION_CONTROLLER.get().defaultBlockState()
                    : MatterFabricationStructure.partState(part.type());
            result.add(new RenderPart(part.x(), part.y(), part.z(), state));
        }
        return List.copyOf(result);
    }

    private static boolean shouldCullFromOverview(MatterFabricationStructure.Part part,
            Set<LocalPos> positions) {
        if (MatterFabricationStructure.isController(part)
                || part.type() == MatterFabricationStructure.PartType.CORE
                || part.type() == MatterFabricationStructure.PartType.STABILIZER
                || part.type() == MatterFabricationStructure.PartType.AE_FLUIX
                || part.type() == MatterFabricationStructure.PartType.AE_VIBRANT_GLASS) {
            return false;
        }
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
        // The floating iris has exposed pendants at its lowest level, not an old floor slab.
        return false;
    }

    private record LocalPos(int x, int y, int z) {
        LocalPos offset(int dx, int dy, int dz) { return new LocalPos(x + dx, y + dy, z + dz); }
    }
}
