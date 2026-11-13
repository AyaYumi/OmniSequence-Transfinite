package com.atir.molecularmanipulator.client;

import com.atir.molecularmanipulator.client.render.ctm.MatterConnectedModel;
import com.atir.molecularmanipulator.network.BuildingPayloads;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/** Client-only geometry, never block entities or archived inventories. Updated at tick rate, not frame rate. */
final class StarChartProjection {
    private final List<SectionedGhostPreviewRenderer.GhostBlock> remaining = new ArrayList<>();
    private List<SectionedGhostPreviewRenderer.GhostBlock> visible = List.of();
    private AABB bounds;
    private final Connections connections;

    @FunctionalInterface interface Connections {
        long masks(Function<BlockPos, BlockState> states, BlockPos position, BlockState state);
    }
    StarChartProjection() { this(MatterConnectedModel::connectionMasks); }
    StarChartProjection(Connections connections) { this.connections = connections; }

    void replace(List<BuildingPayloads.Placement> placements) {
        remaining.clear();
        bounds = null;
        var planned = new HashMap<BlockPos, BlockState>();
        for (var placement : placements) {
            planned.put(placement.position(), Block.stateById(placement.state()));
            var box = new AABB(placement.position());
            bounds = bounds == null ? box : bounds.minmax(box);
        }
        for (var placement : placements) {
            var state = planned.get(placement.position());
            if (state.isAir() && !placement.conflict()) continue;
            remaining.add(new SectionedGhostPreviewRenderer.GhostBlock(placement.position(), state, placement.conflict(),
                    connections.masks(pos -> planned.getOrDefault(pos, Blocks.AIR.defaultBlockState()),
                            placement.position(), state)));
        }
        visible = List.copyOf(remaining);
    }

    /** Keep formed/powered property changes from bringing completed ghosts back. Unloaded chunks are not complete. */
    boolean advance(BiPredicate<BlockPos, BlockState> built) {
        if (!remaining.removeIf(block -> built.test(block.pos(), block.expectedState()))) return false;
        visible = List.copyOf(remaining);
        return true;
    }

    List<SectionedGhostPreviewRenderer.GhostBlock> blocks() { return visible; }
    AABB bounds() { return bounds; }
    void clear() { remaining.clear(); visible = List.of(); bounds = null; }
}
