package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.block.EntropyAnchorBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyEngineCasingBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyEngineCoreBlock;
import com.atir.molecularmanipulator.block.NegativeEntropyObservationPortBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

class NegativeEntropyEngineStateTest {
    private static final NegativeEntropyEngineCoreBlock CORE;
    private static final NegativeEntropyEngineCasingBlock CASING;
    private static final NegativeEntropyObservationPortBlock WINDOW;
    private static final Block UNRELATED;
    private static final EntropyAnchorBlock[] ANCHORS = new EntropyAnchorBlock[3];

    static {
        if (net.neoforged.fml.loading.LoadingModList.get() == null) {
            net.neoforged.fml.loading.LoadingModList.of(List.of(), List.of(), List.of(), List.of(), Map.of());
        }
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        // Register isolated fixtures without loading ModContent or a mod event bus.
        var registry = (MappedRegistry<Block>) BuiltInRegistries.BLOCK;
        registry.unfreeze();
        try {
            CORE = register("core", new NegativeEntropyEngineCoreBlock(BlockBehaviour.Properties.of()));
            CASING = register("casing", new NegativeEntropyEngineCasingBlock(BlockBehaviour.Properties.of()));
            WINDOW = register("window", new NegativeEntropyObservationPortBlock(BlockBehaviour.Properties.of().noOcclusion()));
            for (int tier = 0; tier < ANCHORS.length; tier++) {
                ANCHORS[tier] = register("anchor_" + tier, new EntropyAnchorBlock(BlockBehaviour.Properties.of()));
            }
            UNRELATED = register("unrelated", new Block(BlockBehaviour.Properties.of()) {
                @Override
                protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                    builder.add(NegativeEntropyEngineCoreBlock.FORMED);
                }
            });
        } finally {
            registry.freeze();
        }
        for (Block part : new Block[] {CORE, CASING, WINDOW, UNRELATED, ANCHORS[0], ANCHORS[1], ANCHORS[2]}) {
            part.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        }
    }

    private static <T extends Block> T register(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("negative_entropy_test", name), block);
    }

    @Test
    void formingHidesOnlyShellMembersAndPreservesFacing() {
        for (Block part : new Block[] {CORE, CASING, WINDOW}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState loose = part.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
                assertEquals(RenderShape.MODEL, loose.getRenderShape());
                BlockState formed = NegativeEntropyEngineCoreBlockEntity.withFormed(loose, true);
                assertNotNull(formed);
                assertTrue(formed.getValue(NegativeEntropyEngineCoreBlock.FORMED));
                assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
                if (part == CORE) {
                    assertEquals(RenderShape.MODEL, formed.getRenderShape(), "The core draws the whole machine");
                    assertFalse(formed.hasProperty(NegativeEntropyEngineCasingBlock.INVISIBLE));
                } else {
                    assertTrue(formed.getValue(NegativeEntropyEngineCasingBlock.INVISIBLE));
                    assertEquals(RenderShape.INVISIBLE, formed.getRenderShape());
                }
                assertNull(NegativeEntropyEngineCoreBlockEntity.withFormed(formed, true));
                assertSame(loose, NegativeEntropyEngineCoreBlockEntity.withFormed(formed, false));
            }
        }
    }

    @Test
    void migrationRepairsInvisibleEvenWhenFormedAlreadyMatches() {
        for (Block part : new Block[] {CASING, WINDOW}) {
            for (boolean formed : new boolean[] {false, true}) {
                BlockState stale = part.defaultBlockState()
                        .setValue(NegativeEntropyEngineCoreBlock.FORMED, formed)
                        .setValue(NegativeEntropyEngineCasingBlock.INVISIBLE, !formed);
                BlockState repaired = NegativeEntropyEngineCoreBlockEntity.withFormed(stale, formed);
                assertNotNull(repaired);
                assertEquals(formed, repaired.getValue(NegativeEntropyEngineCasingBlock.INVISIBLE));
                assertEquals(formed, repaired.getValue(NegativeEntropyEngineCoreBlock.FORMED));
            }
        }
    }

    @Test
    void hiddenMembersDoNotOccludeButRetainCollisionAndSelection() {
        for (Block part : new Block[] {CASING, WINDOW}) {
            BlockState formed = NegativeEntropyEngineCoreBlockEntity.withFormed(part.defaultBlockState(), true);
            var level = EmptyBlockGetter.INSTANCE;
            var pos = BlockPos.ZERO;
            assertTrue(formed.getOcclusionShape(level, pos).isEmpty());
            assertEquals(0, formed.getLightBlock(level, pos));
            assertFalse(formed.isSolidRender(level, pos), "Invisible members must not close section visibility");
            assertFalse(formed.getCollisionShape(level, pos, CollisionContext.empty()).isEmpty());
            assertFalse(formed.getShape(level, pos).isEmpty(), "Members must remain targetable for dismantling");
        }
        var restored = CASING.defaultBlockState();
        assertFalse(restored.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
    }

    @Test
    void hiddenWindowDoesNotCullTheFaceOfAnAdjacentLooseWindow() {
        var loose = WINDOW.defaultBlockState();
        var hidden = NegativeEntropyEngineCoreBlockEntity.withFormed(loose, true);
        for (Direction direction : Direction.values()) {
            assertFalse(WINDOW.skipRendering(loose, hidden, direction));
            assertTrue(WINDOW.skipRendering(loose, loose, direction));
        }
    }

    @Test
    void anchorStatesMatchAllResourceVariantsAndKeepTheirTierAndCollision() {
        for (var anchor : ANCHORS) {
            assertEquals(144, anchor.getStateDefinition().getPossibleStates().size());
            var loose = anchor.defaultBlockState();
            assertFalse(loose.getValue(EntropyAnchorBlock.FORMED));
            assertEquals(0, loose.getValue(EntropyAnchorBlock.ANCHOR_POS));
            assertFalse(loose.getValue(EntropyAnchorBlock.ANCHOR_END));
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (int index = 0; index < 9; index++) {
                    for (boolean end : new boolean[] {false, true}) {
                        var formed = EntropyAnchorBlock.formedState(loose, facing, index, end);
                        assertSame(anchor, formed.getBlock());
                        assertEquals(RenderShape.MODEL, formed.getRenderShape(), "Anchors render individually, unlike hidden casing");
                        assertFalse(formed.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                        assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
                        assertEquals(index, formed.getValue(EntropyAnchorBlock.ANCHOR_POS));
                        assertEquals(end, formed.getValue(EntropyAnchorBlock.ANCHOR_END));
                        assertSame(loose.setValue(HorizontalDirectionalBlock.FACING, facing), EntropyAnchorBlock.unformedState(formed));
                        assertNull(NegativeEntropyEngineCoreBlockEntity.withFormed(formed, false),
                                "The shell's generic reset must not bypass anchor-state cleanup");
                    }
                }
            }
        }
    }

    @Test
    void columnDiscoveryAcceptsMixedTiersButDoesNotStealAnotherFacing() {
        assertEquals(NegativeEntropyAnchorStructure.Probe.UNLOADED,
                NegativeEntropyEngineCoreBlockEntity.anchorProbe(null, Direction.EAST, 4));
        for (var anchor : ANCHORS) {
            var loose = anchor.defaultBlockState();
            assertEquals(NegativeEntropyAnchorStructure.Probe.ANCHOR,
                    NegativeEntropyEngineCoreBlockEntity.anchorProbe(loose, Direction.EAST, 4));
            var formed = EntropyAnchorBlock.formedState(loose, Direction.EAST, 4, true);
            assertEquals(NegativeEntropyAnchorStructure.Probe.ANCHOR,
                    NegativeEntropyEngineCoreBlockEntity.anchorProbe(formed, Direction.EAST, 4));
            assertEquals(NegativeEntropyAnchorStructure.Probe.OTHER,
                    NegativeEntropyEngineCoreBlockEntity.anchorProbe(formed, Direction.WEST, 4));
        }
        assertEquals(NegativeEntropyAnchorStructure.Probe.OTHER,
                NegativeEntropyEngineCoreBlockEntity.anchorProbe(CASING.defaultBlockState(), Direction.EAST, 4));
    }

    @Test
    void sameFacingColumnsWithDifferentCentersCannotClaimEachOthersMembers() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var owner = formedColumn(facing, 1);
            var otherCore = BlockPos.ZERO.above(2);
            var blocks = new HashMap<BlockPos, BlockState>();
            for (int index = 0; index < 9; index++) {
                blocks.put(NegativeEntropyAnchorStructure.position(otherCore, facing, 0, index),
                        ANCHORS[0].defaultBlockState());
            }
            blocks.putAll(owner);
            var result = NegativeEntropyAnchorStructure.scan(otherCore, facing,
                    (pos, index) -> NegativeEntropyEngineCoreBlockEntity.anchorProbe(
                            blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()), facing, index));
            assertEquals(new NegativeEntropyAnchorStructure.Column(0, true), result);
            var overlap = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, 0, 0);
            assertEquals(NegativeEntropyAnchorStructure.Probe.OTHER,
                    NegativeEntropyEngineCoreBlockEntity.anchorProbe(owner.get(overlap), facing, 6));
        }
    }

    @Test
    void visualStateUpdatesDoNotPropagateIntoUnloadedNeighbors() {
        assertEquals(0, EntropyAnchorBlock.VISUAL_UPDATE_FLAGS & Block.UPDATE_NEIGHBORS);
        assertNotEquals(0, EntropyAnchorBlock.VISUAL_UPDATE_FLAGS & Block.UPDATE_CLIENTS);
        assertNotEquals(0, EntropyAnchorBlock.VISUAL_UPDATE_FLAGS & Block.UPDATE_KNOWN_SHAPE);
    }

    @Test
    void everyMemberOfAMixedTierColumnIsSupportedInAllFourDirections() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var blocks = formedColumn(facing, 4);
            for (int layer = 0; layer < 4; layer++) {
                for (int index = 0; index < 9; index++) {
                    var pos = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, layer, index);
                    assertEquals(EntropyAnchorBlock.Support.SUPPORTED, support(blocks, pos));
                }
            }
        }
    }

    @Test
    void brokenOrUnformedRootInvalidatesTheFirstLayerWithoutRequiringTheCoreEntity() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var blocks = formedColumn(facing, 2);
            var first = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, 0, 4);
            blocks.remove(BlockPos.ZERO);
            assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, first));
            blocks.put(BlockPos.ZERO, CORE.defaultBlockState());
            assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, first));
            blocks.put(BlockPos.ZERO, CORE.defaultBlockState().setValue(NegativeEntropyEngineCoreBlock.FORMED, true));
            blocks.put(BlockPos.ZERO.relative(facing), WINDOW.defaultBlockState());
            assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, first));
        }
    }

    @Test
    void aHoleInvalidatesItsLayerAndSupportLossPropagatesOutward() {
        var facing = Direction.SOUTH;
        var blocks = formedColumn(facing, 4);
        blocks.remove(NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, 1, 0));
        var broken = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, 1, 4);
        var next = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, 2, 4);
        var last = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, 3, 4);
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, broken));
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, next));
        blocks.replaceAll((pos, state) -> pos.getZ() == 4 && state.getBlock() instanceof EntropyAnchorBlock
                ? EntropyAnchorBlock.unformedState(state) : state);
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, last));
    }

    @Test
    void orphanChecksDeferUnloadedChunksInsteadOfMistakingThemForAir() {
        var blocks = formedColumn(Direction.WEST, 1);
        var pos = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, Direction.WEST, 0, 4);
        assertEquals(EntropyAnchorBlock.Support.UNLOADED,
                EntropyAnchorBlock.support(pos, blocks.get(pos), part -> part.equals(BlockPos.ZERO)
                        ? null : blocks.getOrDefault(part, Blocks.AIR.defaultBlockState())));
        var missing = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, Direction.WEST, 0, 8);
        assertEquals(EntropyAnchorBlock.Support.UNLOADED,
                EntropyAnchorBlock.support(pos, blocks.get(pos), part -> part.equals(missing)
                        ? null : blocks.getOrDefault(part, Blocks.AIR.defaultBlockState())));
        blocks.remove(BlockPos.ZERO);
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED,
                EntropyAnchorBlock.support(pos, blocks.get(pos), part -> part.equals(missing)
                        ? null : blocks.getOrDefault(part, Blocks.AIR.defaultBlockState())),
                "Known disconnection still wins over an unrelated unloaded member");
    }

    @Test
    void incorrectPositionFacingOrAnInwardEndCapCannotSupportAColumn() {
        var blocks = formedColumn(Direction.NORTH, 2);
        var first = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, Direction.NORTH, 0, 4);
        var last = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, Direction.NORTH, 1, 4);
        var original = blocks.get(first);
        blocks.put(first, original.setValue(EntropyAnchorBlock.ANCHOR_POS, 0));
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, last));
        blocks.put(first, original.setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, last));
        blocks.put(first, original.setValue(EntropyAnchorBlock.ANCHOR_END, true));
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, last));
    }

    @Test
    void rotationsKeepLocalPositionsAndMirrorsReverseColumns() {
        var anchor = ANCHORS[0];
        var state = EntropyAnchorBlock.formedState(anchor.defaultBlockState(), Direction.NORTH, 0, true);
        var rotated = anchor.rotate(state, Rotation.CLOCKWISE_90);
        assertEquals(Direction.EAST, rotated.getValue(HorizontalDirectionalBlock.FACING));
        assertEquals(0, rotated.getValue(EntropyAnchorBlock.ANCHOR_POS));
        for (var mirror : new Mirror[] {Mirror.LEFT_RIGHT, Mirror.FRONT_BACK}) {
            var mirrored = anchor.mirror(state, mirror);
            assertEquals(2, mirrored.getValue(EntropyAnchorBlock.ANCHOR_POS));
            assertSame(state, anchor.mirror(mirrored, mirror));
        }
    }

    private static Map<BlockPos, BlockState> formedColumn(Direction facing, int layers) {
        var blocks = new HashMap<BlockPos, BlockState>();
        blocks.put(BlockPos.ZERO, CORE.defaultBlockState().setValue(NegativeEntropyEngineCoreBlock.FORMED, true));
        blocks.put(BlockPos.ZERO.relative(facing), WINDOW.defaultBlockState()
                .setValue(NegativeEntropyObservationPortBlock.FORMED, true));
        for (int layer = 0; layer < layers; layer++) {
            for (int index = 0; index < 9; index++) {
                var pos = NegativeEntropyAnchorStructure.position(BlockPos.ZERO, facing, layer, index);
                blocks.put(pos, EntropyAnchorBlock.formedState(ANCHORS[(index + layer) % ANCHORS.length].defaultBlockState(),
                        facing, index, layer == layers - 1));
            }
        }
        return blocks;
    }

    private static EntropyAnchorBlock.Support support(Map<BlockPos, BlockState> blocks, BlockPos pos) {
        return EntropyAnchorBlock.support(pos, blocks.get(pos), part -> blocks.getOrDefault(part, Blocks.AIR.defaultBlockState()));
    }

    @Test
    void unrelatedBlocksWithAFormedPropertyAreNeverChanged() {
        assertNull(NegativeEntropyEngineCoreBlockEntity.withFormed(UNRELATED.defaultBlockState(), true));
        assertNull(NegativeEntropyEngineCoreBlockEntity.withFormed(Blocks.AIR.defaultBlockState(), false));
    }
}
