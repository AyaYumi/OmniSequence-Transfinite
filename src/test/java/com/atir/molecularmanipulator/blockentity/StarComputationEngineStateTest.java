package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.block.EntropyAnchorBlock;
import com.atir.molecularmanipulator.block.EntropyArmillaryFoundationBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.starcomputation.EntropyArmillaryLayout;
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

class StarComputationEngineStateTest {
    private static final StarComputationEngineCoreBlock CORE;
    private static final StarComputationEngineCasingBlock CASING;
    private static final StarComputationObservationPortBlock WINDOW;
    private static final EntropyArmillaryFoundationBlock FOUNDATION;
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
            CORE = register("core", new StarComputationEngineCoreBlock(BlockBehaviour.Properties.of()));
            CASING = register("casing", new StarComputationEngineCasingBlock(BlockBehaviour.Properties.of()));
            WINDOW = register("window", new StarComputationObservationPortBlock(BlockBehaviour.Properties.of().noOcclusion()));
            FOUNDATION = register("foundation", new EntropyArmillaryFoundationBlock(BlockBehaviour.Properties.of()));
            for (int tier = 0; tier < ANCHORS.length; tier++) {
                ANCHORS[tier] = register("anchor_" + tier, new EntropyAnchorBlock(BlockBehaviour.Properties.of()));
            }
            UNRELATED = register("unrelated", new Block(BlockBehaviour.Properties.of()) {
                @Override
                protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
                    builder.add(StarComputationEngineCoreBlock.FORMED);
                }
            });
        } finally {
            registry.freeze();
        }
        for (Block part : new Block[] {CORE, CASING, WINDOW, FOUNDATION, UNRELATED, ANCHORS[0], ANCHORS[1], ANCHORS[2]}) {
            part.getStateDefinition().getPossibleStates().forEach(BlockState::initCache);
        }
    }

    private static <T extends Block> T register(String name, T block) {
        return Registry.register(BuiltInRegistries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("star_computation_test", name), block);
    }

    @Test
    void armillaryGuiTierCountsFollowActualSlotsInTheVerifiedPrefix() {
        int[] previous = new int[3];
        for (int lit=0; lit<=9; lit++) {
            int[] expected = new int[3];
            for (int ring=0; ring<lit; ring++) {
                expected[EntropyArmillaryLayout.tierOf(ring)] += EntropyArmillaryLayout.slotCount(ring);
            }
            for (int tier=0; tier<3; tier++) {
                int actual = StarComputationEngineCoreBlockEntity.armillaryTierCount(lit,tier);
                assertEquals(expected[tier], actual, "L"+lit+" tier "+tier);
                assertTrue(actual>=previous[tier]);
                previous[tier]=actual;
            }
            assertEquals(0, StarComputationEngineCoreBlockEntity.armillaryTierCount(lit,-1));
            assertEquals(0, StarComputationEngineCoreBlockEntity.armillaryTierCount(lit,3));
        }
        assertArrayEquals(new int[] {96,168,240}, previous);
        assertEquals(0, StarComputationEngineCoreBlockEntity.armillaryTierCount(-1,0));
        assertEquals(240, StarComputationEngineCoreBlockEntity.armillaryTierCount(10,2));
    }

    @Test
    void formedFoundationHidesOnlyItsMeshAndKeepsPhysicalBlocks() {
        var loose = FOUNDATION.defaultBlockState();
        var hidden = loose.setValue(EntropyArmillaryFoundationBlock.FORMED,true);
        assertFalse(loose.getValue(EntropyArmillaryFoundationBlock.FORMED));
        assertEquals(RenderShape.MODEL,loose.getRenderShape());
        assertEquals(RenderShape.INVISIBLE,hidden.getRenderShape());
        assertEquals(net.minecraft.world.level.material.PushReaction.BLOCK,hidden.getPistonPushReaction(),
                "Pistons must not move hidden blocks beyond their owner's restoration footprint");
        assertEquals(net.minecraft.world.level.material.PushReaction.NORMAL,loose.getPistonPushReaction());
        for (var state : List.of(loose,hidden)) {
            assertFalse(state.hasBlockEntity());
            assertEquals(1.0,state.getCollisionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).bounds().getSize());
            assertEquals(loose.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO),state.getShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));
        }
        assertTrue(hidden.getOcclusionShape(EmptyBlockGetter.INSTANCE,BlockPos.ZERO).isEmpty());
        assertEquals(0,hidden.getLightBlock(EmptyBlockGetter.INSTANCE,BlockPos.ZERO));
        assertNull(StarComputationEngineCoreBlockEntity.withFormed(loose,true),"Shell updates must not claim unrelated foundations");
    }

    @Test
    void formingHidesOnlyShellMembersAndPreservesFacing() {
        for (Block part : new Block[] {CORE, CASING, WINDOW}) {
            for (Direction facing : Direction.Plane.HORIZONTAL) {
                BlockState loose = part.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
                assertEquals(RenderShape.MODEL, loose.getRenderShape());
                BlockState formed = StarComputationEngineCoreBlockEntity.withFormed(loose, true);
                assertNotNull(formed);
                assertTrue(formed.getValue(StarComputationEngineCoreBlock.FORMED));
                assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
                if (part == CORE) {
                    assertEquals(RenderShape.MODEL, formed.getRenderShape(), "The core draws the whole machine");
                    assertFalse(formed.hasProperty(StarComputationEngineCasingBlock.INVISIBLE));
                } else {
                    assertTrue(formed.getValue(StarComputationEngineCasingBlock.INVISIBLE));
                    assertEquals(RenderShape.INVISIBLE, formed.getRenderShape());
                }
                assertNull(StarComputationEngineCoreBlockEntity.withFormed(formed, true));
                assertSame(loose, StarComputationEngineCoreBlockEntity.withFormed(formed, false));
            }
        }
    }

    @Test
    void migrationRepairsInvisibleEvenWhenFormedAlreadyMatches() {
        for (Block part : new Block[] {CASING, WINDOW}) {
            for (boolean formed : new boolean[] {false, true}) {
                BlockState stale = part.defaultBlockState()
                        .setValue(StarComputationEngineCoreBlock.FORMED, formed)
                        .setValue(StarComputationEngineCasingBlock.INVISIBLE, !formed);
                BlockState repaired = StarComputationEngineCoreBlockEntity.withFormed(stale, formed);
                assertNotNull(repaired);
                assertEquals(formed, repaired.getValue(StarComputationEngineCasingBlock.INVISIBLE));
                assertEquals(formed, repaired.getValue(StarComputationEngineCoreBlock.FORMED));
            }
        }
    }

    @Test
    void hiddenMembersDoNotOccludeButRetainCollisionAndSelection() {
        for (Block part : new Block[] {CASING, WINDOW}) {
            BlockState formed = StarComputationEngineCoreBlockEntity.withFormed(part.defaultBlockState(), true);
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
        var hidden = StarComputationEngineCoreBlockEntity.withFormed(loose, true);
        for (Direction direction : Direction.values()) {
            assertFalse(WINDOW.skipRendering(loose, hidden, direction));
            assertTrue(WINDOW.skipRendering(loose, loose, direction));
        }
    }

    @Test
    void anchorStatesMatchAllResourceVariantsAndKeepTheirTierAndCollision() {
        for (var anchor : ANCHORS) {
            assertEquals(288, anchor.getStateDefinition().getPossibleStates().size());
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
                        assertNull(StarComputationEngineCoreBlockEntity.withFormed(formed, false),
                                "The shell's generic reset must not bypass anchor-state cleanup");
                    }
                }
            }
        }
    }

    @Test
    void columnDiscoveryAcceptsMixedTiersButDoesNotStealAnotherFacing() {
        assertEquals(StarComputationAnchorStructure.Probe.UNLOADED,
                StarComputationEngineCoreBlockEntity.anchorProbe(null, Direction.EAST, 4));
        for (var anchor : ANCHORS) {
            var loose = anchor.defaultBlockState();
            assertEquals(StarComputationAnchorStructure.Probe.ANCHOR,
                    StarComputationEngineCoreBlockEntity.anchorProbe(loose, Direction.EAST, 4));
            var formed = EntropyAnchorBlock.formedState(loose, Direction.EAST, 4, true);
            assertEquals(StarComputationAnchorStructure.Probe.ANCHOR,
                    StarComputationEngineCoreBlockEntity.anchorProbe(formed, Direction.EAST, 4));
            assertEquals(StarComputationAnchorStructure.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.anchorProbe(formed, Direction.WEST, 4));
        }
        assertEquals(StarComputationAnchorStructure.Probe.OTHER,
                StarComputationEngineCoreBlockEntity.anchorProbe(CASING.defaultBlockState(), Direction.EAST, 4));
    }

    @Test
    void sameFacingColumnsWithDifferentCentersCannotClaimEachOthersMembers() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var owner = formedColumn(facing, 1);
            var otherCore = BlockPos.ZERO.above(2);
            var blocks = new HashMap<BlockPos, BlockState>();
            for (int index = 0; index < 9; index++) {
                blocks.put(StarComputationAnchorStructure.position(otherCore, facing, 0, index),
                        ANCHORS[0].defaultBlockState());
            }
            blocks.putAll(owner);
            var result = StarComputationAnchorStructure.scan(otherCore, facing,
                    (pos, index) -> StarComputationEngineCoreBlockEntity.anchorProbe(
                            blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()), facing, index));
            assertEquals(new StarComputationAnchorStructure.Column(0, true), result);
            var overlap = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, 0, 0);
            assertEquals(StarComputationAnchorStructure.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.anchorProbe(owner.get(overlap), facing, 6));
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
                    var pos = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, layer, index);
                    assertEquals(EntropyAnchorBlock.Support.SUPPORTED, support(blocks, pos));
                }
            }
        }
    }

    @Test
    void brokenOrUnformedRootInvalidatesTheFirstLayerWithoutRequiringTheCoreEntity() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var blocks = formedColumn(facing, 2);
            var first = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, 0, 4);
            blocks.remove(BlockPos.ZERO);
            assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, first));
            blocks.put(BlockPos.ZERO, CORE.defaultBlockState());
            assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, first));
            blocks.put(BlockPos.ZERO, CORE.defaultBlockState().setValue(StarComputationEngineCoreBlock.FORMED, true));
            blocks.put(BlockPos.ZERO.relative(facing), WINDOW.defaultBlockState());
            assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, first));
        }
    }

    @Test
    void aHoleInvalidatesItsLayerAndSupportLossPropagatesOutward() {
        var facing = Direction.SOUTH;
        var blocks = formedColumn(facing, 4);
        blocks.remove(StarComputationAnchorStructure.position(BlockPos.ZERO, facing, 1, 0));
        var broken = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, 1, 4);
        var next = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, 2, 4);
        var last = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, 3, 4);
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, broken));
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, next));
        blocks.replaceAll((pos, state) -> pos.getZ() == 4 && state.getBlock() instanceof EntropyAnchorBlock
                ? EntropyAnchorBlock.unformedState(state) : state);
        assertEquals(EntropyAnchorBlock.Support.UNSUPPORTED, support(blocks, last));
    }

    @Test
    void orphanChecksDeferUnloadedChunksInsteadOfMistakingThemForAir() {
        var blocks = formedColumn(Direction.WEST, 1);
        var pos = StarComputationAnchorStructure.position(BlockPos.ZERO, Direction.WEST, 0, 4);
        assertEquals(EntropyAnchorBlock.Support.UNLOADED,
                EntropyAnchorBlock.support(pos, blocks.get(pos), part -> part.equals(BlockPos.ZERO)
                        ? null : blocks.getOrDefault(part, Blocks.AIR.defaultBlockState())));
        var missing = StarComputationAnchorStructure.position(BlockPos.ZERO, Direction.WEST, 0, 8);
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
        var first = StarComputationAnchorStructure.position(BlockPos.ZERO, Direction.NORTH, 0, 4);
        var last = StarComputationAnchorStructure.position(BlockPos.ZERO, Direction.NORTH, 1, 4);
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

    @Test
    void armillaryModeIsOptInAndHidesOnlyTheArmillaryModel() {
        var core = CORE.defaultBlockState();
        assertFalse(core.getValue(StarComputationEngineCoreBlock.ARMILLARY));
        assertEquals(RenderShape.MODEL, core.getRenderShape());
        for (var anchor : ANCHORS) {
            var loose = anchor.defaultBlockState();
            assertFalse(loose.getValue(EntropyAnchorBlock.ARMILLARY));
            assertEquals(RenderShape.MODEL, loose.getRenderShape());
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (int ring : new int[] {0, 4, 8}) {
                    var formed = EntropyAnchorBlock.armillaryState(loose, facing, ring, true);
                    assertTrue(formed.getValue(EntropyAnchorBlock.ARMILLARY));
                    assertEquals(ring, formed.getValue(EntropyAnchorBlock.ANCHOR_POS));
                    assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
                    assertTrue(formed.getValue(EntropyAnchorBlock.ANCHOR_END));
                    assertEquals(RenderShape.INVISIBLE, formed.getRenderShape());
                    assertTrue(formed.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                    assertEquals(0, formed.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    assertFalse(formed.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                    assertEquals(EntropyAnchorBlock.ARMILLARY_SHAPE,
                            formed.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    assertFalse(formed.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                    assertTrue(formed.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    assertSame(loose.setValue(HorizontalDirectionalBlock.FACING, facing),
                            EntropyAnchorBlock.unformedState(formed));
                    var legacy = EntropyAnchorBlock.formedState(formed, facing, ring, false);
                    assertFalse(legacy.getValue(EntropyAnchorBlock.ARMILLARY));
                    assertEquals(RenderShape.MODEL, legacy.getRenderShape());
                    for (var mirror : new Mirror[] {Mirror.LEFT_RIGHT, Mirror.FRONT_BACK}) {
                        var mirrored = anchor.mirror(formed, mirror);
                        assertTrue(mirrored.getValue(EntropyAnchorBlock.ARMILLARY));
                        assertEquals(ring, mirrored.getValue(EntropyAnchorBlock.ANCHOR_POS));
                    }
                }
            }
        }
    }

    @Test
    void armillaryProbeRequiresExactTierFacingRingAndOwnership() {
        var facing = Direction.EAST;
        for (int tier = 0; tier < ANCHORS.length; tier++) {
            var loose = ANCHORS[tier].defaultBlockState();
            assertEquals(EntropyArmillaryRings.Probe.MATCH,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(loose, ANCHORS[tier], facing, 4, false));
            assertEquals(EntropyArmillaryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(loose,
                            ANCHORS[(tier + 1) % ANCHORS.length], facing, 4, false));
            assertEquals(EntropyArmillaryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(loose, ANCHORS[tier], facing, 4, true));
            var formed = EntropyAnchorBlock.armillaryState(loose, facing, 4, false);
            assertEquals(EntropyArmillaryRings.Probe.MATCH,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(formed, ANCHORS[tier], facing, 4, false));
            assertEquals(EntropyArmillaryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(formed, ANCHORS[tier], facing, 4, true));
            assertEquals(EntropyArmillaryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(formed, ANCHORS[tier], facing, 5, false));
            assertEquals(EntropyArmillaryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(formed, ANCHORS[tier], facing.getOpposite(), 4, false));
            assertEquals(EntropyArmillaryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.armillaryProbe(
                            EntropyAnchorBlock.formedState(loose, facing, 4, false), ANCHORS[tier], facing, 4, false));
        }
        assertEquals(EntropyArmillaryRings.Probe.UNLOADED,
                StarComputationEngineCoreBlockEntity.armillaryProbe(null, ANCHORS[0], facing, 4, false));
    }

    @Test
    void armillaryScanIsAContiguousPrefixInAllFourDirectionsAndRejectsWrongTier() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int lit = 0; lit <= EntropyArmillaryRings.RINGS; lit++) {
                var blocks = new HashMap<BlockPos, BlockState>();
                for (int ring = 0; ring < lit; ring++) {
                    var tier = EntropyArmillaryRings.tierOf(ring);
                    for (int slot = 0; slot < EntropyArmillaryRings.slotCount(ring); slot++) {
                        var pos = EntropyArmillaryLayout.slot(BlockPos.ZERO, facing, ring, slot);
                        blocks.put(pos, EntropyAnchorBlock.armillaryState(
                                ANCHORS[tier].defaultBlockState(), facing, ring, false));
                    }
                }
                assertEquals(new EntropyArmillaryRings.Scan(lit, true), scan(facing, blocks));
                if (lit > 0) {
                    int ring = lit - 1;
                    var pos = EntropyArmillaryLayout.slot(BlockPos.ZERO, facing, ring,
                            ring % EntropyArmillaryRings.slotCount(ring));
                    blocks.put(pos, EntropyAnchorBlock.armillaryState(
                            ANCHORS[(EntropyArmillaryRings.tierOf(ring) + 1) % ANCHORS.length].defaultBlockState(),
                            facing, ring, false));
                    assertEquals(new EntropyArmillaryRings.Scan(lit - 1, true), scan(facing, blocks));
                }
            }
        }
    }

    private static EntropyArmillaryRings.Scan scan(Direction facing, Map<BlockPos, BlockState> blocks) {
        return EntropyArmillaryRings.scan(BlockPos.ZERO, facing, (pos, ring) ->
                StarComputationEngineCoreBlockEntity.armillaryProbe(
                        blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()),
                        ANCHORS[EntropyArmillaryRings.tierOf(ring)], facing, ring, false));
    }

    private static Map<BlockPos, BlockState> formedColumn(Direction facing, int layers) {
        var blocks = new HashMap<BlockPos, BlockState>();
        blocks.put(BlockPos.ZERO, CORE.defaultBlockState().setValue(StarComputationEngineCoreBlock.FORMED, true));
        blocks.put(BlockPos.ZERO.relative(facing), WINDOW.defaultBlockState()
                .setValue(StarComputationObservationPortBlock.FORMED, true));
        for (int layer = 0; layer < layers; layer++) {
            for (int index = 0; index < 9; index++) {
                var pos = StarComputationAnchorStructure.position(BlockPos.ZERO, facing, layer, index);
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
        assertNull(StarComputationEngineCoreBlockEntity.withFormed(UNRELATED.defaultBlockState(), true));
        assertNull(StarComputationEngineCoreBlockEntity.withFormed(Blocks.AIR.defaultBlockState(), false));
    }
}
