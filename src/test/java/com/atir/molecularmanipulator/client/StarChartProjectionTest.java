package com.atir.molecularmanipulator.client;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.network.BuildingPayloads;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class StarChartProjectionTest {
    @BeforeAll static void bootstrap() {
        if (net.neoforged.fml.loading.LoadingModList.get() == null)
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        net.minecraft.SharedConstants.tryDetectVersion(); net.minecraft.server.Bootstrap.bootStrap();
        // NeoForge populates network state IDs during registry freeze, which plain JUnit does not run.
        for (var block : net.minecraft.core.registries.BuiltInRegistries.BLOCK)
            for (var state : block.getStateDefinition().getPossibleStates())
                if (Block.BLOCK_STATE_REGISTRY.getId(state) < 0) Block.BLOCK_STATE_REGISTRY.add(state);
    }

    @Test void completedBlocksDisappearAndUnchangedGeometryKeepsItsCachedList() {
        var projection = new StarChartProjection((states, pos, state) -> 0);
        var first = new BlockPos(10, 64, 12); var second = first.above();
        projection.replace(List.of(placement(first, Blocks.STONE, false), placement(second, Blocks.GLASS, false),
                placement(first.east(), Blocks.AIR, false)));
        assertEquals(2, projection.blocks().size(), "Reserved air does not render a ghost");
        var cached = projection.blocks();
        assertFalse(projection.advance((pos, state) -> false), "Unloaded/unbuilt positions must stay visible");
        assertSame(cached, projection.blocks(), "No per-frame geometry churn");
        assertTrue(projection.advance((pos, state) -> pos.equals(first)));
        assertEquals(List.of(second), projection.blocks().stream().map(SectionedGhostPreviewRenderer.GhostBlock::pos).toList());
        assertTrue(projection.advance((pos, state) -> true));
        assertTrue(projection.blocks().isEmpty());
        projection.clear(); assertNull(projection.bounds());
    }

    @Test void fixedWorldBoundsAndConflictingAirSurviveUntilCancelledOrBuilt() {
        var projection = new StarChartProjection((states, pos, state) -> 0);
        var site = new BlockPos(-18, 80, 200);
        projection.replace(List.of(placement(site, Blocks.STONE, false), placement(site.offset(2, 3, 4), Blocks.AIR, true)));
        assertEquals(-18, projection.bounds().minX); assertEquals(84, projection.bounds().maxY);
        assertEquals(2, projection.blocks().size());
        assertTrue(projection.blocks().getLast().conflict());
        projection.replace(List.of(placement(BlockPos.ZERO, Blocks.GLASS, false)));
        assertEquals(0, projection.bounds().minX); assertEquals(1, projection.blocks().size());
    }

    private static BuildingPayloads.Placement placement(BlockPos pos, Block block, boolean conflict) {
        return new BuildingPayloads.Placement(pos, Block.getId(block.defaultBlockState()), conflict);
    }
}
