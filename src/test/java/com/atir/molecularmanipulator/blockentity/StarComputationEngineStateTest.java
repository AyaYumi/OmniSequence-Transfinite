package com.atir.molecularmanipulator.blockentity;

import static org.junit.jupiter.api.Assertions.*;

import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.block.StarOrreryFoundationBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationObservationPortBlock;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
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
    private static final StarOrreryFoundationBlock FOUNDATION;
    private static final Block UNRELATED;
    private static final StarPositionAnchorBlock[] ANCHORS = new StarPositionAnchorBlock[3];

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
            FOUNDATION = register("foundation", new StarOrreryFoundationBlock(BlockBehaviour.Properties.of()));
            for (int tier = 0; tier < ANCHORS.length; tier++) {
                ANCHORS[tier] = register("anchor_" + tier, new StarPositionAnchorBlock(BlockBehaviour.Properties.of()));
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
    void starOrreryGuiTierCountsFollowActualSlotsInTheVerifiedPrefix() {
        int[] previous = new int[3];
        for (int lit=0; lit<=9; lit++) {
            int[] expected = new int[3];
            for (int ring=0; ring<lit; ring++) {
                expected[StarOrreryLayout.tierOf(ring)] += StarOrreryLayout.slotCount(ring);
            }
            for (int tier=0; tier<3; tier++) {
                int actual = StarComputationEngineCoreBlockEntity.starOrreryTierCount(lit,tier);
                assertEquals(expected[tier], actual, "L"+lit+" tier "+tier);
                assertTrue(actual>=previous[tier]);
                previous[tier]=actual;
            }
            assertEquals(0, StarComputationEngineCoreBlockEntity.starOrreryTierCount(lit,-1));
            assertEquals(0, StarComputationEngineCoreBlockEntity.starOrreryTierCount(lit,3));
        }
        assertArrayEquals(new int[] {96,168,240}, previous);
        assertEquals(0, StarComputationEngineCoreBlockEntity.starOrreryTierCount(-1,0));
        assertEquals(240, StarComputationEngineCoreBlockEntity.starOrreryTierCount(10,2));
    }

    @Test
    void formedFoundationHidesOnlyItsMeshAndKeepsPhysicalBlocks() {
        var loose = FOUNDATION.defaultBlockState();
        var hidden = loose.setValue(StarOrreryFoundationBlock.FORMED,true);
        assertFalse(loose.getValue(StarOrreryFoundationBlock.FORMED));
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
    void formationHidesTheCentralMembersUnderTheWholeBodyModelWithoutChangingFacing() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            var loose = CORE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
            var formed = StarComputationEngineCoreBlockEntity.withFormed(loose, true);
            assertNotNull(formed);
            assertTrue(formed.getValue(StarComputationEngineCoreBlock.FORMED));
            assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
            assertEquals(RenderShape.MODEL, formed.getRenderShape());
            assertFalse(formed.hasProperty(StarComputationEngineCasingBlock.INVISIBLE));
            assertNull(StarComputationEngineCoreBlockEntity.withFormed(formed, true));
            assertSame(loose, StarComputationEngineCoreBlockEntity.withFormed(formed, false));
            for (Block part : new Block[] {CASING, WINDOW}) {
                var material = part.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
                var hidden = StarComputationEngineCoreBlockEntity.withFormed(material, true);
                assertNotNull(hidden);
                assertTrue(hidden.getValue(StarComputationEngineCoreBlock.FORMED));
                assertTrue(hidden.getValue(StarComputationEngineCasingBlock.INVISIBLE));
                assertEquals(facing, hidden.getValue(HorizontalDirectionalBlock.FACING));
                assertEquals(RenderShape.INVISIBLE, hidden.getRenderShape());
                assertNull(StarComputationEngineCoreBlockEntity.withFormed(hidden, true));
                assertSame(material, StarComputationEngineCoreBlockEntity.withFormed(hidden, false));
                assertEquals(RenderShape.MODEL, material.getRenderShape());
            }
        }
    }

    @Test
    void orphanRepairRestoresEveryShellStateWithoutDeletingBlocksOrChangingFacing() {
        for (Block part : new Block[] {CASING, WINDOW}) {
            for (var facing : Direction.Plane.HORIZONTAL) {
                var loose = part.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing);
                for (boolean formed : new boolean[] {false, true}) for (boolean invisible : new boolean[] {false, true}) {
                    var stale = loose.setValue(StarComputationEngineCoreBlock.FORMED, formed)
                            .setValue(StarComputationEngineCasingBlock.INVISIBLE, invisible);
                    var hidden = loose.setValue(StarComputationEngineCoreBlock.FORMED, true)
                            .setValue(StarComputationEngineCasingBlock.INVISIBLE, true);
                    if (formed && invisible) assertNull(StarComputationEngineCoreBlockEntity.withFormed(stale, true));
                    else assertSame(hidden, StarComputationEngineCoreBlockEntity.withFormed(stale, true));
                    if (!formed && !invisible) assertNull(StarComputationEngineCoreBlockEntity.withFormed(stale, false));
                    else assertSame(loose, StarComputationEngineCoreBlockEntity.withFormed(stale, false));
                    var restored = StarComputationEngineCoreBlockEntity.restoredLegacyState(stale);
                    if (!formed && !invisible) assertNull(restored);
                    else {
                        assertSame(loose, restored);
                        assertSame(part, restored.getBlock());
                        assertEquals(RenderShape.MODEL, restored.getRenderShape());
                    }
                }
            }
        }
    }

    @Test
    void legacyCoreMigrationKeepsFacingButDropsItsOldFormedFlagBeforeRevalidation() {
        assertEquals(16, CORE.getStateDefinition().getPossibleStates().size(), "Keep both saved star_orrery states readable");
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (boolean formed : new boolean[] {false, true}) {
                var legacy = CORE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                        .setValue(StarComputationEngineCoreBlock.STAR_ORRERY, false)
                        .setValue(StarComputationEngineCoreBlock.FORMED, formed);
                var migrated = StarComputationEngineCoreBlockEntity.migrateCoreState(legacy);
                assertSame(CORE, migrated.getBlock());
                assertTrue(StarComputationEngineCoreBlock.isStarOrrery(migrated));
                assertFalse(migrated.getValue(StarComputationEngineCoreBlock.FORMED));
                assertEquals(facing, migrated.getValue(HorizontalDirectionalBlock.FACING));
                assertSame(migrated, StarComputationEngineCoreBlockEntity.migrateCoreState(migrated));
                var invalid = StarComputationEngineCoreBlockEntity.withFormed(legacy, true);
                assertEquals(formed ? legacy.setValue(StarComputationEngineCoreBlock.FORMED, false) : null, invalid,
                        "An unmigrated core cannot be formed through the compatibility updater");
            }
        }
        var formedOrrery = CORE.defaultBlockState().setValue(StarComputationEngineCoreBlock.FORMED, true);
        assertSame(formedOrrery, StarComputationEngineCoreBlockEntity.migrateCoreState(formedOrrery),
                "Existing orreries retain their reload-recovery state");
    }

    @Test
    void compatibleHiddenStatesRetainCollisionAndSelectionUntilRestored() {
        for (Block part : new Block[] {CASING, WINDOW}) {
            BlockState formed = part.defaultBlockState().setValue(StarComputationEngineCoreBlock.FORMED, true)
                    .setValue(StarComputationEngineCasingBlock.INVISIBLE, true);
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
        var hidden = loose.setValue(StarComputationEngineCoreBlock.FORMED, true)
                .setValue(StarComputationEngineCasingBlock.INVISIBLE, true);
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
            assertFalse(loose.getValue(StarPositionAnchorBlock.FORMED));
            assertEquals(0, loose.getValue(StarPositionAnchorBlock.ANCHOR_POS));
            assertFalse(loose.getValue(StarPositionAnchorBlock.ANCHOR_END));
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (int index = 0; index < 9; index++) {
                    for (boolean end : new boolean[] {false, true}) {
                        var formed = StarPositionAnchorBlock.formedState(loose, facing, index, end);
                        assertSame(anchor, formed.getBlock());
                        assertEquals(RenderShape.MODEL, formed.getRenderShape(), "Anchors render individually, unlike hidden casing");
                        assertFalse(formed.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                        assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
                        assertEquals(index, formed.getValue(StarPositionAnchorBlock.ANCHOR_POS));
                        assertEquals(end, formed.getValue(StarPositionAnchorBlock.ANCHOR_END));
                        assertSame(loose.setValue(HorizontalDirectionalBlock.FACING, facing), StarPositionAnchorBlock.unformedState(formed));
                        assertNull(StarComputationEngineCoreBlockEntity.withFormed(formed, false),
                                "The shell's generic reset must not bypass anchor-state cleanup");
                    }
                }
            }
        }
    }

    @Test
    void visualStateUpdatesDoNotPropagateIntoUnloadedNeighbors() {
        assertEquals(0, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS & Block.UPDATE_NEIGHBORS);
        assertNotEquals(0, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS & Block.UPDATE_CLIENTS);
        assertNotEquals(0, StarPositionAnchorBlock.VISUAL_UPDATE_FLAGS & Block.UPDATE_KNOWN_SHAPE);
    }

    @Test
    void retiredColumnsAreUnsupportedInEveryDirectionEvenWithTheirEntireOldStructureIntact() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            var blocks = formedColumn(facing, 4);
            for (int layer = 0; layer < 4; layer++) for (int index = 0; index < 9; index++) {
                var pos = legacyColumnPosition(facing, layer, index);
                var state = blocks.get(pos);
                assertEquals(StarPositionAnchorBlock.Support.UNSUPPORTED, support(blocks, pos));
                assertEquals(StarPositionAnchorBlock.Support.UNSUPPORTED,
                        StarPositionAnchorBlock.support(pos, state, part -> fail("Legacy cleanup must not read another chunk")));
                var restored = StarComputationEngineCoreBlockEntity.restoredLegacyState(state);
                assertSame(StarPositionAnchorBlock.unformedState(state), restored);
                assertSame(state.getBlock(), restored.getBlock(), "Materials stay in place; there is no refund or deletion");
                assertEquals(facing, restored.getValue(HorizontalDirectionalBlock.FACING));
                assertNull(StarComputationEngineCoreBlockEntity.restoredLegacyState(restored));
            }
        }
    }

    @Test
    void ringOrphanChecksStillDeferUnloadedCoresAndRejectLegacyCores() {
        for (var facing : Direction.Plane.HORIZONTAL) for (int ring = 0; ring < 9; ring++) {
            var pos = StarOrreryLayout.slot(BlockPos.ZERO, facing, ring, 0);
            var member = StarPositionAnchorBlock.starOrreryState(ANCHORS[ring / 3].defaultBlockState(), facing, ring, false);
            var core = CORE.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, facing)
                    .setValue(StarComputationEngineCoreBlock.FORMED, true);
            assertEquals(StarPositionAnchorBlock.Support.SUPPORTED, StarPositionAnchorBlock.support(pos, member,
                    part -> part.equals(BlockPos.ZERO) ? core : Blocks.AIR.defaultBlockState()));
            assertEquals(StarPositionAnchorBlock.Support.UNLOADED, StarPositionAnchorBlock.support(pos, member,
                    part -> part.equals(BlockPos.ZERO) ? null : Blocks.AIR.defaultBlockState()));
            assertEquals(StarPositionAnchorBlock.Support.UNSUPPORTED, StarPositionAnchorBlock.support(pos, member,
                    part -> part.equals(BlockPos.ZERO) ? core.setValue(StarComputationEngineCoreBlock.STAR_ORRERY, false)
                            : Blocks.AIR.defaultBlockState()));
            assertEquals(StarPositionAnchorBlock.Support.UNSUPPORTED, StarPositionAnchorBlock.support(pos, member,
                    part -> part.equals(BlockPos.ZERO) ? core.setValue(HorizontalDirectionalBlock.FACING, facing.getOpposite())
                            : Blocks.AIR.defaultBlockState()));
            assertEquals(StarPositionAnchorBlock.Support.UNSUPPORTED, StarPositionAnchorBlock.support(pos, member,
                    part -> Blocks.AIR.defaultBlockState()));
            assertNull(StarComputationEngineCoreBlockEntity.restoredLegacyState(member), "Migration must not dissolve real ring members");
        }
    }

    @Test
    void rotationsKeepLocalPositionsAndMirrorsReverseColumns() {
        var anchor = ANCHORS[0];
        var state = StarPositionAnchorBlock.formedState(anchor.defaultBlockState(), Direction.NORTH, 0, true);
        var rotated = anchor.rotate(state, Rotation.CLOCKWISE_90);
        assertEquals(Direction.EAST, rotated.getValue(HorizontalDirectionalBlock.FACING));
        assertEquals(0, rotated.getValue(StarPositionAnchorBlock.ANCHOR_POS));
        for (var mirror : new Mirror[] {Mirror.LEFT_RIGHT, Mirror.FRONT_BACK}) {
            var mirrored = anchor.mirror(state, mirror);
            assertEquals(2, mirrored.getValue(StarPositionAnchorBlock.ANCHOR_POS));
            assertSame(state, anchor.mirror(mirrored, mirror));
        }
    }

    @Test
    void newCoresDefaultToStarOrreryAndOnlyFormedRingAnchorsHideTheirModel() {
        var core = CORE.defaultBlockState();
        assertTrue(core.getValue(StarComputationEngineCoreBlock.STAR_ORRERY));
        assertEquals(RenderShape.MODEL, core.getRenderShape());
        for (var anchor : ANCHORS) {
            var loose = anchor.defaultBlockState();
            assertFalse(loose.getValue(StarPositionAnchorBlock.STAR_ORRERY));
            assertEquals(RenderShape.MODEL, loose.getRenderShape());
            for (var facing : Direction.Plane.HORIZONTAL) {
                for (int ring : new int[] {0, 4, 8}) {
                    var formed = StarPositionAnchorBlock.starOrreryState(loose, facing, ring, true);
                    assertTrue(formed.getValue(StarPositionAnchorBlock.STAR_ORRERY));
                    assertEquals(ring, formed.getValue(StarPositionAnchorBlock.ANCHOR_POS));
                    assertEquals(facing, formed.getValue(HorizontalDirectionalBlock.FACING));
                    assertTrue(formed.getValue(StarPositionAnchorBlock.ANCHOR_END));
                    assertEquals(RenderShape.INVISIBLE, formed.getRenderShape());
                    assertTrue(formed.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                    assertEquals(0, formed.getLightBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    assertFalse(formed.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                    assertEquals(StarPositionAnchorBlock.STAR_ORRERY_SHAPE,
                            formed.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    assertFalse(formed.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty());
                    assertTrue(formed.propagatesSkylightDown(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                    assertSame(loose.setValue(HorizontalDirectionalBlock.FACING, facing),
                            StarPositionAnchorBlock.unformedState(formed));
                    var legacy = StarPositionAnchorBlock.formedState(formed, facing, ring, false);
                    assertFalse(legacy.getValue(StarPositionAnchorBlock.STAR_ORRERY));
                    assertEquals(RenderShape.MODEL, legacy.getRenderShape());
                    for (var mirror : new Mirror[] {Mirror.LEFT_RIGHT, Mirror.FRONT_BACK}) {
                        var mirrored = anchor.mirror(formed, mirror);
                        assertTrue(mirrored.getValue(StarPositionAnchorBlock.STAR_ORRERY));
                        assertEquals(ring, mirrored.getValue(StarPositionAnchorBlock.ANCHOR_POS));
                    }
                }
            }
        }
    }

    @Test
    void starOrreryProbeRequiresExactTierFacingRingAndOwnership() {
        var facing = Direction.EAST;
        for (int tier = 0; tier < ANCHORS.length; tier++) {
            var loose = ANCHORS[tier].defaultBlockState();
            assertEquals(StarOrreryRings.Probe.MATCH,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(loose, ANCHORS[tier], facing, 4, false));
            assertEquals(StarOrreryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(loose,
                            ANCHORS[(tier + 1) % ANCHORS.length], facing, 4, false));
            assertEquals(StarOrreryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(loose, ANCHORS[tier], facing, 4, true));
            var formed = StarPositionAnchorBlock.starOrreryState(loose, facing, 4, false);
            assertEquals(StarOrreryRings.Probe.MATCH,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(formed, ANCHORS[tier], facing, 4, false));
            assertEquals(StarOrreryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(formed, ANCHORS[tier], facing, 4, true));
            assertEquals(StarOrreryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(formed, ANCHORS[tier], facing, 5, false));
            assertEquals(StarOrreryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(formed, ANCHORS[tier], facing.getOpposite(), 4, false));
            assertEquals(StarOrreryRings.Probe.OTHER,
                    StarComputationEngineCoreBlockEntity.starOrreryProbe(
                            StarPositionAnchorBlock.formedState(loose, facing, 4, false), ANCHORS[tier], facing, 4, false));
        }
        assertEquals(StarOrreryRings.Probe.UNLOADED,
                StarComputationEngineCoreBlockEntity.starOrreryProbe(null, ANCHORS[0], facing, 4, false));
    }

    @Test
    void starOrreryScanIsAContiguousPrefixInAllFourDirectionsAndRejectsWrongTier() {
        for (var facing : Direction.Plane.HORIZONTAL) {
            for (int lit = 0; lit <= StarOrreryRings.RINGS; lit++) {
                var blocks = new HashMap<BlockPos, BlockState>();
                for (int ring = 0; ring < lit; ring++) {
                    var tier = StarOrreryRings.tierOf(ring);
                    for (int slot = 0; slot < StarOrreryRings.slotCount(ring); slot++) {
                        var pos = StarOrreryLayout.slot(BlockPos.ZERO, facing, ring, slot);
                        blocks.put(pos, StarPositionAnchorBlock.starOrreryState(
                                ANCHORS[tier].defaultBlockState(), facing, ring, false));
                    }
                }
                assertEquals(new StarOrreryRings.Scan(lit, true), scan(facing, blocks));
                if (lit > 0) {
                    int ring = lit - 1;
                    var pos = StarOrreryLayout.slot(BlockPos.ZERO, facing, ring,
                            ring % StarOrreryRings.slotCount(ring));
                    blocks.put(pos, StarPositionAnchorBlock.starOrreryState(
                            ANCHORS[(StarOrreryRings.tierOf(ring) + 1) % ANCHORS.length].defaultBlockState(),
                            facing, ring, false));
                    assertEquals(new StarOrreryRings.Scan(lit - 1, true), scan(facing, blocks));
                }
            }
        }
    }

    private static StarOrreryRings.Scan scan(Direction facing, Map<BlockPos, BlockState> blocks) {
        return StarOrreryRings.scan(BlockPos.ZERO, facing, (pos, ring) ->
                StarComputationEngineCoreBlockEntity.starOrreryProbe(
                        blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState()),
                        ANCHORS[StarOrreryRings.tierOf(ring)], facing, ring, false));
    }

    private static BlockPos legacyColumnPosition(Direction facing, int layer, int index) {
        var right = facing.getCounterClockWise();
        return BlockPos.ZERO.relative(facing, layer + 2).offset(
                right.getStepX() * (index % 3 - 1), 1 - index / 3, right.getStepZ() * (index % 3 - 1));
    }

    private static Map<BlockPos, BlockState> formedColumn(Direction facing, int layers) {
        var blocks = new HashMap<BlockPos, BlockState>();
        blocks.put(BlockPos.ZERO, CORE.defaultBlockState().setValue(StarComputationEngineCoreBlock.STAR_ORRERY, false)
                .setValue(StarComputationEngineCoreBlock.FORMED, true));
        blocks.put(BlockPos.ZERO.relative(facing), WINDOW.defaultBlockState()
                .setValue(StarComputationObservationPortBlock.FORMED, true));
        for (int layer = 0; layer < layers; layer++) {
            for (int index = 0; index < 9; index++) {
                var pos = legacyColumnPosition(facing, layer, index);
                blocks.put(pos, StarPositionAnchorBlock.formedState(ANCHORS[(index + layer) % ANCHORS.length].defaultBlockState(),
                        facing, index, layer == layers - 1));
            }
        }
        return blocks;
    }

    private static StarPositionAnchorBlock.Support support(Map<BlockPos, BlockState> blocks, BlockPos pos) {
        return StarPositionAnchorBlock.support(pos, blocks.get(pos), part -> blocks.getOrDefault(part, Blocks.AIR.defaultBlockState()));
    }

    @Test
    void runtimeRequiresCentralBodyAndFoundationButNeverRestoresIndependentLegacyCredit() throws Exception {
        var root = java.nio.file.Path.of("src/main/java/com/atir/molecularmanipulator");
        var engine = java.nio.file.Files.readString(root.resolve("blockentity/StarComputationEngineCoreBlockEntity.java"));
        assertFalse(engine.contains("StarComputationAnchorStructure"));
        assertFalse(engine.contains("anchorCounts"));
        assertFalse(engine.contains("StarComputationBenefits.entropy("));
        assertFalse(engine.contains("StarComputationBenefits.levelFor("));
        assertTrue(engine.contains("return isStarOrrery() && getBlockState().getValue(StarComputationEngineCoreBlock.FORMED)"));
        assertTrue(engine.contains("return new StarComputationBenefits(getLitRings(), overclocked)"));
        var refresh = engine.substring(engine.indexOf("private void refreshStructure()"), engine.indexOf("private void refreshAnchors("));
        assertTrue(refresh.contains("StarOrreryFoundation.scan("));
        assertTrue(refresh.contains("StarOrreryCoreStructure.scan("));
        assertTrue(refresh.contains("coreScan.complete() && foundationScan.complete()"));
        assertFalse(refresh.contains("hasChunksAt("));
        assertTrue(refresh.indexOf("migrateCoreState(state)") < refresh.indexOf("StarOrreryCoreStructure.scan("));
        assertTrue(refresh.indexOf("migrateCoreState(state)") < refresh.indexOf("StarOrreryFoundation.scan("));
        assertFalse(refresh.contains("restoreLegacyShell("), "Valid central members must not be unconditionally unhidden every refresh");
        assertTrue(engine.contains("&& (level != null && level.isClientSide || structureValidated)"),
                "Saved formed flags do not expose central membership before server validation");
        var find = engine.substring(engine.indexOf("public static StarComputationEngineCoreBlockEntity findCore("),
                engine.indexOf("private static StarComputationEngineCoreBlockEntity findStarOrreryCore("));
        assertTrue(find.contains("getBlockEntity(part)"));
        assertTrue(find.contains("StarPositionAnchorBlock.STAR_ORRERY"));
        assertTrue(find.contains("StarOrreryCoreStructure.members()"));
        assertTrue(find.contains("state.is(coreBlock(member.material()))"));
        assertTrue(find.contains("level.hasChunkAt(corePos)"));
        assertFalse(find.contains("betweenClosed("));
        assertFalse(find.contains("while ("));
        var capabilities = java.nio.file.Files.readString(root.resolve("MolecularManipulator.java"));
        assertTrue(capabilities.contains("ModContent.STAR_COMPUTATION_ENGINE_CASING.get()"));
        assertTrue(capabilities.contains("ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get()"));
        var interaction = java.nio.file.Files.readString(root.resolve("starcomputation/StarComputationInteraction.java"));
        assertTrue(interaction.contains("star_orrery.core_missing"));
        assertTrue(interaction.contains("StarPositionAnchorBlock.isStarOrrery(state)"));
        assertTrue(interaction.contains("StarComputationEngineCoreBlockEntity.findCore(level, part)"));
    }

    @Test
    void reloadRepairsAreDeferredAndVerifiedRingMembershipIsPublishedBeforeNetworkNotification() throws Exception {
        var engine = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/com/atir/molecularmanipulator/blockentity/StarComputationEngineCoreBlockEntity.java"));
        var onLoad = engine.substring(engine.indexOf("public void onLoad()"), engine.indexOf("private void refreshStructure()"));
        assertFalse(onLoad.contains("setBlock("), "Loading a core must not mutate states during chunk promotion");
        var chunkLoad = engine.substring(engine.indexOf("public static void legacyChunkLoaded("),
                engine.indexOf("public static void restoreLegacyChunks("));
        assertFalse(chunkLoad.contains("setBlock("));
        assertTrue(engine.contains("restoreLegacyChunks(LevelTickEvent.Post event)"));
        assertTrue(engine.contains("getChunkNow(chunkPos.x, chunkPos.z)"), "Orphan repairs never load neighboring chunks");
        assertTrue(engine.contains("if (!deferShellRepair(level, pos, state)) restoreLegacyMember(level, pos)"),
                "Chunk repairs must not undo a valid or not-yet-validated central body's hidden states");
        var apply = engine.substring(engine.indexOf("private static void applyFormed("), engine.indexOf("private static void updateNetworkNeighbors("));
        assertTrue(apply.contains("updateNetworkNeighbors(level, pos)"));
        assertFalse(apply.contains("if (updated != null) {"), "Matching saved central states must still refresh cached network capabilities");
        var anchors = engine.substring(engine.indexOf("private void refreshStarOrreryAnchors("), engine.indexOf("static StarOrreryRings.Probe starOrreryProbe("));
        assertTrue(anchors.indexOf("managedAnchors.put(pos, member)") < anchors.indexOf("updateNetworkNeighbors(level, pos)"));
        assertTrue(anchors.contains("!member.equals(previousMember)"), "Reloaded states reconnect cables even when their saved visuals already match");
    }

    @Test
    void unrelatedBlocksWithAFormedPropertyAreNeverChanged() {
        assertNull(StarComputationEngineCoreBlockEntity.withFormed(UNRELATED.defaultBlockState(), true));
        assertNull(StarComputationEngineCoreBlockEntity.withFormed(Blocks.AIR.defaultBlockState(), false));
    }
}
