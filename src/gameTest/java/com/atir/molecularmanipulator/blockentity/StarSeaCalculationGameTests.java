package com.atir.molecularmanipulator.blockentity;

import appeng.api.AECapabilities;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.atir.molecularmanipulator.ae2.StarSignalKey;
import com.atir.molecularmanipulator.block.StarSeaCalculationGeometry;
import com.atir.molecularmanipulator.block.StarSeaCalculationMainframeBlock;
import com.atir.molecularmanipulator.blockentity.StarSeaCalculationMainframeBlockEntity.Status;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.StarSeaCalculationContent;
import com.atir.molecularmanipulator.config.ModConfig;
import com.atir.molecularmanipulator.world.DetachedSignalRealm;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native item/protection/removal hooks and real world/AE2 gates, outside the published mod JAR. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class StarSeaCalculationGameTests {
    private static final List<Direction> FACINGS = List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST);
    private static final int ROOF_COLUMNS = 25;

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void oneItemPlaces225CellsAndOneBlockEntityInEveryFacing(GameTestHelper helper) {
        var fixture = fixture(helper);
        var player = fixture.player("star-sea-place", fixture.origin);
        player.getInventory().setItem(1, new ItemStack(StarSeaCalculationContent.ITEM.get()));
        for (int index = 0; index < FACINGS.size(); index++) {
            var origin = siteOrigin(fixture, index);
            var facing = FACINGS.get(index);
            prepareSite(fixture, origin, facing);
            equipBuilding(player);
            helper.assertTrue(player.getInventory().countItem(StarSeaCalculationContent.ITEM.get()) == 2,
                    "One held building and one untouched reserve item precede placement");
            helper.assertTrue(useBuildingItem(player, origin, facing).consumesAction(), "Native item placement succeeds: " + facing);
            helper.assertTrue(player.getMainHandItem().isEmpty()
                            && player.getInventory().countItem(StarSeaCalculationContent.ITEM.get()) == 1,
                    "All 225 cells cost exactly one building item: " + facing);
            assertBuilding(fixture, origin, facing);
            assertFoundation(fixture, origin, facing, null);
        }
        assertDrops(fixture, 0);
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 600)
    public static void onlyTheBottomCenterFrontSocketConnectsRealMeCablesInAllFourFacings(GameTestHelper helper) {
        var fixture = fixture(helper);
        var player = fixture.player("star-sea-white-port", fixture.origin);
        var cores = new ArrayList<StarSeaCalculationMainframeBlockEntity>();
        var cables = new ArrayList<CableBusBlockEntity>();
        var blocked = new ArrayList<CableBusBlockEntity>();
        var chests = new ArrayList<MEChestBlockEntity>();
        for (int index = 0; index < FACINGS.size(); index++) {
            var facing = FACINGS.get(index);
            var origin = siteOrigin(fixture, index);
            var core = placeBuilding(fixture, player, origin, facing);
            cores.add(core);
            cables.add(placeCable(fixture, origin.relative(facing), player));
            // This is outside the building, underneath the same core. It must
            // not create a second connection through the unmarked bottom face.
            blocked.add(placeCable(fixture, origin.below(), player));
            var chestPos = origin.relative(facing, 2);
            var chest = fixture.place(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
            chest.setCell(new ItemStack(ModContent.STAR_SIGNAL_STORAGE_CELL.get()));
            fixture.onCleanup(() -> chest.setCell(ItemStack.EMPTY));
            chests.add(chest);
            var controller = chestPos.relative(facing.getClockWise());
            fixture.place(controller, AEBlocks.CONTROLLER.block().defaultBlockState());
            fixture.place(controller.relative(facing), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (int index = 0; index < FACINGS.size(); index++) {
                var facing = FACINGS.get(index);
                var core = cores.get(index);
                var node = core.getMainNode().getNode();
                helper.assertTrue(node != null && core.getMainNode().isActive()
                                && core.getMainNode().getGrid() == chests.get(index).getMainNode().getGrid(),
                        "A physical cable at the white front socket reaches the powered storage network: " + facing);
                var connections = node.getInWorldConnections();
                helper.assertTrue(connections.size() == 1 && connections.containsKey(facing)
                                && connections.get(facing).getOtherSide(node) == cables.get(index).getGridNode(facing.getOpposite())
                                && node.getUsedChannels() == 1,
                        "Only one channel enters through the actual bottom-center front cable: " + facing);
                helper.assertTrue(core.getGridNode(Direction.DOWN) == null
                                && !blocked.get(index).getGridNode(Direction.UP).getInWorldConnections().containsKey(Direction.UP),
                        "The unmarked bottom face cannot connect a second cable: " + facing);
                var decorative = core.getBlockPos().relative(facing.getOpposite()).above();
                helper.assertTrue(fixture.level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, decorative) == null,
                        "The two upper decorative panels expose no AE node");
            }
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void farTopObstructionRejectsTheWholePlacementWithoutItemLoss(GameTestHelper helper) {
        var fixture = fixture(helper);
        var player = fixture.player("star-sea-blocked", fixture.origin);
        for (int index = 0; index < FACINGS.size(); index++) {
            var origin = siteOrigin(fixture, index);
            var facing = FACINGS.get(index);
            prepareSite(fixture, origin, facing);
            var obstruction = origin.offset(StarSeaCalculationGeometry.offset(
                    StarSeaCalculationGeometry.partIndex(2, 8, 4), facing));
            fixture.place(obstruction, Blocks.OBSIDIAN.defaultBlockState());
            var before = equipBuilding(player).copy();
            helper.assertTrue(useBuildingItem(player, origin, facing) == InteractionResult.FAIL,
                    "An obstruction in the last far/top cell rejects placement: " + facing);
            helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "Rejected placement preserves the exact held item");
            assertEmptyReservation(fixture, origin, facing, obstruction);
            assertFoundation(fixture, origin, facing, null);
        }
        assertDrops(fixture, 0);
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void oneUnsupportedFarFoundationCellRejectsPlacementWithoutItemLoss(GameTestHelper helper) {
        var fixture = fixture(helper);
        var player = fixture.player("star-sea-support", fixture.origin);
        for (int index = 0; index < FACINGS.size(); index++) {
            var origin = siteOrigin(fixture, index);
            var facing = FACINGS.get(index);
            prepareSite(fixture, origin, facing);
            var missing = origin.offset(StarSeaCalculationGeometry.offset(
                    StarSeaCalculationGeometry.partIndex(2, 0, 4), facing)).below();
            fixture.place(missing, Blocks.AIR.defaultBlockState());
            var before = equipBuilding(player).copy();
            helper.assertTrue(useBuildingItem(player, origin, facing) == InteractionResult.FAIL,
                    "The core's supported anchor cannot hide one missing far foundation cell: " + facing);
            helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "Unsupported placement spends no item");
            assertEmptyReservation(fixture, origin, facing, null);
            assertFoundation(fixture, origin, facing, missing);
        }
        assertDrops(fixture, 0);
        helper.succeed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void canceledNeoForgeMultiPlaceRestoresEveryCellAndTheItemWithoutDrops(GameTestHelper helper) {
        var fixture = fixture(helper);
        var origin = siteOrigin(fixture, 0);
        var facing = Direction.WEST;
        prepareSite(fixture, origin, facing);
        var player = fixture.player("star-sea-claim", origin);
        var before = equipBuilding(player).copy();
        var events = new ArrayList<BlockEvent.EntityMultiPlaceEvent>();
        boolean[] completeAtCancellation = {false};
        Consumer<BlockEvent.EntityMultiPlaceEvent> deny = event -> {
            if (event.getEntity() != player || event.getLevel() != fixture.level) return;
            events.add(event);
            completeAtCancellation[0] = StarSeaCalculationMainframeBlock.structureComplete(fixture.level, origin, facing);
            event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(BlockEvent.EntityMultiPlaceEvent.class, deny);
        fixture.onCleanup(() -> NeoForge.EVENT_BUS.unregister(deny));
        InteractionResult result;
        try {
            result = useBuildingItem(player, origin, facing);
        } finally {
            NeoForge.EVENT_BUS.unregister(deny);
        }
        helper.assertTrue(result == InteractionResult.FAIL && events.size() == 1 && completeAtCancellation[0],
                "The real multi-place event cancels one fully written 225-cell building");
        var snapshots = events.getFirst().getReplacedBlockSnapshots();
        var expected = new HashSet<BlockPos>();
        for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++)
            expected.add(origin.offset(StarSeaCalculationGeometry.offset(part, facing)));
        var captured = new HashSet<BlockPos>();
        for (var snapshot : snapshots) {
            helper.assertTrue(snapshot.getState().isAir(), "Every protected reservation cell originally contained air");
            helper.assertTrue(captured.add(snapshot.getPos()), "The native event captures each cell exactly once");
        }
        helper.assertTrue(snapshots.size() == 225 && captured.equals(expected), "The event protects the whole transformed reservation");
        assertEmptyReservation(fixture, origin, facing, null);
        assertFoundation(fixture, origin, facing, null);
        helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "Cancellation restores the exact unspent held item");
        helper.assertTrue(!fixture.level.captureBlockSnapshots && !fixture.level.restoringBlockSnapshots
                        && fixture.level.capturedBlockSnapshots.isEmpty(), "Native snapshot flags and capture buffers are released");
        assertDrops(fixture, 0);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            assertEmptyReservation(fixture, origin, facing, null);
            assertDrops(fixture, 0);
            helper.assertTrue(ItemStack.matches(before, player.getMainHandItem()), "No deferred initialization consumes the canceled item");
            helper.assertTrue(useBuildingItem(player, origin, facing).consumesAction() && player.getMainHandItem().isEmpty(),
                    "After removing the claim listener, the same item places successfully");
            assertBuilding(fixture, origin, facing);
            assertDrops(fixture, 0);
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void survivalIronPickaxeMemberBreakDropsExactlyOneWholeBuilding(GameTestHelper helper) {
        memberBreak(helper, GameType.SURVIVAL);
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void creativeMemberBreakRemovesTheWholeBuildingWithoutDrops(GameTestHelper helper) {
        memberBreak(helper, GameType.CREATIVE);
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 200)
    public static void commandRemovalOfTheCoreCleansEveryTransformedMember(GameTestHelper helper) {
        var fixture = fixture(helper);
        var player = fixture.player("star-sea-command", fixture.origin);
        for (int index = 0; index < FACINGS.size(); index++) {
            var origin = siteOrigin(fixture, index);
            var facing = FACINGS.get(index);
            placeBuilding(fixture, player, origin, facing);
            fixture.level.getServer().getCommands().performPrefixedCommand(
                    fixture.level.getServer().createCommandSourceStack().withLevel(fixture.level).withSuppressedOutput(),
                    "setblock " + origin.getX() + " " + origin.getY() + " " + origin.getZ() + " minecraft:air replace");
            assertEmptyReservation(fixture, origin, facing, null);
            assertFoundation(fixture, origin, facing, null);
        }
        assertDrops(fixture, 0);
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            for (int index = 0; index < FACINGS.size(); index++)
                assertEmptyReservation(fixture, siteOrigin(fixture, index), FACINGS.get(index), null);
            assertDrops(fixture, 0);
        }).thenSucceed();
    }

    // Separate batches keep these world-time owners from running concurrently with each other or other tests.
    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "star_sea_status", timeoutTicks = 800)
    public static void realServerTimeAndSkyAndBlockLightGateStatusBeforeNetworkReadiness(GameTestHelper helper) {
        var fixture = fixture(helper);
        controlEnvironment(fixture);
        environment(fixture, 15_000, false);
        var origin = siteOrigin(fixture, 0);
        var facing = Direction.EAST;
        var player = fixture.player("star-sea-status", origin);
        var core = placeBuilding(fixture, player, origin, facing);
        var sample = roof(origin, facing, 24);
        helper.startSequence().thenWaitUntil(() -> assertOpenNightSky(fixture, origin, facing)).thenExecute(() -> {
            assertStatus(fixture, core, player, Status.OFFLINE);
            environment(fixture, 6_000, false);
            helper.assertTrue(fixture.level.getBrightness(LightLayer.SKY, sample) == 15
                            && fixture.level.getMaxLocalRawBrightness(sample) == 15, "Noon uses actual unobstructed server skylight");
            assertStatus(fixture, core, player, Status.DAYTIME);
            environment(fixture, 15_000, false);
            assertStatus(fixture, core, player, Status.OFFLINE);
            placeTorch(fixture, sample, 7);
        }).thenWaitUntil(() -> assertLightProfile(fixture, origin, facing, 24, 7)).thenExecute(() -> {
            assertStatus(fixture, core, player, Status.OFFLINE);
            removeTorch(fixture, sample, 7);
            placeTorch(fixture, sample, 6);
        }).thenWaitUntil(() -> assertLightProfile(fixture, origin, facing, 24, 8)).thenExecute(() -> {
            assertStatus(fixture, core, player, Status.TOO_BRIGHT);
            removeTorch(fixture, sample, 6);
            canopy(fixture, origin, facing, true);
            environment(fixture, 6_000, true);
        }).thenWaitUntil(() -> assertDarkRoof(fixture, origin, facing)).thenExecute(() -> {
            helper.assertTrue(fixture.level.isThundering() && !fixture.level.isDay() && !fixture.level.canSeeSky(sample),
                    "A real daytime thunderstorm and an opaque cover leave all roof samples dark");
            assertStatus(fixture, core, player, Status.DAYTIME);
            environment(fixture, 15_000, false);
            assertStatus(fixture, core, player, Status.OFFLINE);
            helper.assertTrue(core.energyCredit() == 0, "Status probes and blocked ticks never buy production energy");
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "star_sea_live_production", timeoutTicks = 3_000)
    public static void liveAeCellProductionStopsAtDayAndAtEachOf25RoofSamples(GameTestHelper helper) {
        var fixture = fixture(helper);
        controlEnvironment(fixture);
        environment(fixture, 15_000, false);
        var origin = siteOrigin(fixture, 0);
        var facing = Direction.WEST;
        var player = fixture.player("star-sea-live", origin);
        var core = placeBuilding(fixture, player, origin, facing);
        placeCable(fixture, origin.relative(facing), player);
        var chestPos = origin.relative(facing, 2);
        var chest = fixture.place(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
        chest.setCell(new ItemStack(ModContent.STAR_SIGNAL_STORAGE_CELL.get()));
        fixture.onCleanup(() -> chest.setCell(ItemStack.EMPTY));
        // An ad-hoc grid reports the total channel count at every node. A physical controller makes
        // the core a leaf with exactly one routed channel, independent of the chest's own channel.
        var controllerPos = chestPos.north();
        fixture.place(controllerPos, AEBlocks.CONTROLLER.block().defaultBlockState());
        var energy = fixture.place(controllerPos.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), AENetworkedBlockEntity.class);
        long[] stoppedAt = {0};
        double[] credit = {0};
        var sequence = helper.startSequence().thenWaitUntil(() -> {
            assertLiveNetwork(fixture, core, chest, facing);
            helper.assertTrue(energy.getMainNode().getGrid() == core.getMainNode().getGrid(), "The physical creative energy cell powers the same grid");
            assertOpenNightSky(fixture, origin, facing);
            helper.assertTrue(core.getStatus() == Status.RUNNING && stored(chest) > 0,
                    "Normal server block ticks produce real star signal in the mounted finite cell");
        }).thenExecute(() -> {
            environment(fixture, 6_000, false);
            stopAndRecord(fixture, core, chest, Status.DAYTIME, stoppedAt, credit);
        }).thenExecuteFor(8, () -> assertStopped(fixture, core, chest, facing, Status.DAYTIME, stoppedAt[0], credit[0]))
                .thenExecute(() -> environment(fixture, 15_000, false))
                .thenWaitUntil(() -> assertResumed(fixture, core, chest, stoppedAt[0]))
                .thenExecute(() -> {
                    canopy(fixture, origin, facing, true);
                    environment(fixture, 6_000, true);
                    stopAndRecord(fixture, core, chest, Status.DAYTIME, stoppedAt, credit);
                }).thenWaitUntil(() -> assertDarkRoof(fixture, origin, facing))
                .thenExecuteFor(5, () -> {
                    helper.assertTrue(fixture.level.isThundering() && !fixture.level.isDay()
                                    && !fixture.level.canSeeSky(roof(origin, facing, 12)),
                            "Weather-darkened server daytime and a dark roof cannot bypass the real night-time gate");
                    assertStopped(fixture, core, chest, facing, Status.DAYTIME, stoppedAt[0], credit[0]);
                }).thenExecute(() -> {
                    canopy(fixture, origin, facing, false);
                    environment(fixture, 15_000, false);
                }).thenWaitUntil(() -> assertOpenNightSky(fixture, origin, facing))
                .thenWaitUntil(() -> assertResumed(fixture, core, chest, stoppedAt[0]))
                .thenExecute(() -> placeTorch(fixture, roof(origin, facing, 24), 7))
                .thenWaitUntil(() -> assertLightProfile(fixture, origin, facing, 24, 7))
                .thenExecute(() -> stoppedAt[0] = stored(chest))
                .thenExecuteAfter(5, () -> {
                    assertResumed(fixture, core, chest, stoppedAt[0]);
                    assertLightProfile(fixture, origin, facing, 24, 7);
                    removeTorch(fixture, roof(origin, facing, 24), 7);
                })
                .thenWaitUntil(() -> assertOpenNightSky(fixture, origin, facing));
        for (int column = 0; column < ROOF_COLUMNS; column++) {
            int sampleColumn = column;
            var sample = roof(origin, facing, column);
            sequence.thenExecute(() -> placeTorch(fixture, sample, 6))
                    .thenWaitUntil(() -> assertLightProfile(fixture, origin, facing, sampleColumn, 8))
                    .thenExecute(() -> stopAndRecord(fixture, core, chest, Status.TOO_BRIGHT, stoppedAt, credit))
                    .thenExecuteFor(3, () -> assertStopped(fixture, core, chest, facing, Status.TOO_BRIGHT, stoppedAt[0], credit[0]))
                    .thenExecute(() -> removeTorch(fixture, sample, 6))
                    .thenWaitUntil(() -> assertDarkRoof(fixture, origin, facing))
                    .thenWaitUntil(() -> assertResumed(fixture, core, chest, stoppedAt[0]));
        }
        sequence.thenExecute(() -> {
            var gridContents = core.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks();
            helper.assertTrue(gridContents.get(StarSignalKey.INSTANCE) == stored(chest) && stored(chest) > 0,
                    "Produced signal belongs to the real network's physical STAR_SIGNAL_STORAGE_CELL");
            assertBuilding(fixture, origin, facing);
            assertDrops(fixture, 0);
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "star_sea_running_sync", timeoutTicks = 800)
    public static void actualProductionSyncsInitialAndChangedRunningStateWithoutStatusProbeSideEffects(GameTestHelper helper) {
        var fixture = fixture(helper);
        controlEnvironment(fixture);
        environment(fixture, 15_000, false);
        var origin = siteOrigin(fixture, 0);
        var facing = Direction.NORTH;
        var player = fixture.player("star-sea-running-sync", origin);
        var core = placeBuilding(fixture, player, origin, facing);
        var receiver = new StarSeaCalculationMainframeBlockEntity(origin, core.getBlockState());
        receiver.loadAdditional(core.getUpdateTag(fixture.level.registryAccess()), fixture.level.registryAccess());
        helper.assertTrue(!core.isRunning() && !receiver.isRunning(), "A newly placed core and its initial chunk update start stopped");
        var cablePos = origin.relative(facing);
        placeCable(fixture, cablePos, player);
        var chestPos = origin.relative(facing, 2);
        var chest = fixture.place(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
        chest.setCell(new ItemStack(ModContent.STAR_SIGNAL_STORAGE_CELL.get()));
        fixture.onCleanup(() -> chest.setCell(ItemStack.EMPTY));
        var controller = chestPos.east();
        fixture.place(controller, AEBlocks.CONTROLLER.block().defaultBlockState());
        fixture.place(controller.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        ItemStack[] savedCell = {ItemStack.EMPTY};
        helper.startSequence().thenWaitUntil(() -> {
            assertLiveNetwork(fixture, core, chest, facing);
            helper.assertTrue(core.isRunning() && stored(chest) > 0, "A real paid insertion starts the effect");
        }).thenExecute(() -> {
            // The initial chunk snapshot also covers a player arriving after production began.
            receiver.loadAdditional(core.getUpdateTag(fixture.level.registryAccess()), fixture.level.registryAccess());
            helper.assertTrue(receiver.isRunning(), "An already-running building reaches a newly tracking client");
            var reloaded = new StarSeaCalculationMainframeBlockEntity(origin, core.getBlockState());
            reloaded.loadAdditional(core.saveWithoutMetadata(fixture.level.registryAccess()), fixture.level.registryAccess());
            helper.assertTrue(!reloaded.isRunning(), "Disk loading revalidates production instead of restoring stale visual activity");
            environment(fixture, 6_000, false);
            core.serverTick();
            assertRunningPacket(fixture, core, receiver, false);
            environment(fixture, 15_000, false);
            long before = stored(chest);
            double credit = core.energyCredit();
            core.showStatus(player);
            helper.assertTrue(core.getStatus() == Status.RUNNING && !core.isRunning()
                            && stored(chest) == before && core.energyCredit() == credit,
                    "A readiness-only click neither produces nor starts the surrounding code rain");
            assertRunningPacket(fixture, core, receiver, false);
            core.serverTick();
            helper.assertTrue(stored(chest) == before + core.outputBitsPerTick(),
                    "Only the actual production tick enables the effect again");
            assertRunningPacket(fixture, core, receiver, true);
            savedCell[0] = chest.getCell().copy();
            chest.setCell(ItemStack.EMPTY);
        }).thenWaitUntil(() -> {
            helper.assertTrue(core.getStatus() == Status.OUTPUT_FULL, "Missing signal storage stops output");
            assertRunningPacket(fixture, core, receiver, false);
        }).thenExecute(() -> chest.setCell(savedCell[0])).thenWaitUntil(() -> {
            helper.assertTrue(core.getStatus() == Status.RUNNING, "Replacing the real signal cell resumes production");
            assertRunningPacket(fixture, core, receiver, true);
        }).thenExecute(() -> fixture.place(cablePos, Blocks.AIR.defaultBlockState())).thenWaitUntil(() -> {
            helper.assertTrue(core.getStatus() == Status.OFFLINE, "Removing the real front cable disconnects the mainframe");
            assertRunningPacket(fixture, core, receiver, false);
        }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, batch = "star_sea_realm_bonus", timeoutTicks = 800)
    public static void detachedSignalRealmProducesEightfoldAndUsesConfiguredOutputAndEnergy(GameTestHelper helper) {
        var realm = helper.getLevel().getServer().getLevel(DetachedSignalRealm.DIMENSION);
        helper.assertTrue(realm != null, "The Detached Signal Realm exists");
        var fixture = new ServerRegressionFixture(helper, realm);
        fixture.onCleanup(() -> drops(fixture).forEach(ItemEntity::discard));
        int oldOutput = ModConfig.STAR_SEA_OUTPUT_BITS_PER_TICK.get();
        double oldEnergy = ModConfig.STAR_SEA_ENERGY_PER_TICK.get();
        int oldMultiplier = ModConfig.STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER.get();
        fixture.onCleanup(() -> {
            ModConfig.STAR_SEA_OUTPUT_BITS_PER_TICK.set(oldOutput);
            ModConfig.STAR_SEA_ENERGY_PER_TICK.set(oldEnergy);
            ModConfig.STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER.set(oldMultiplier);
        });
        ModConfig.STAR_SEA_OUTPUT_BITS_PER_TICK.set(128);
        ModConfig.STAR_SEA_ENERGY_PER_TICK.set(1024D);
        ModConfig.STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER.set(8);
        var origin = siteOrigin(fixture, 0);
        var facing = Direction.NORTH;
        var player = fixture.player("star-sea-realm-bonus", origin);
        var core = placeBuilding(fixture, player, origin, facing);
        placeCable(fixture, origin.relative(facing), player);
        var chestPos = origin.relative(facing, 2);
        var chest = fixture.place(chestPos, AEBlocks.ME_CHEST.block().defaultBlockState(), MEChestBlockEntity.class);
        chest.setCell(new ItemStack(ModContent.STAR_SIGNAL_STORAGE_CELL.get()));
        fixture.onCleanup(() -> chest.setCell(ItemStack.EMPTY));
        var controller = chestPos.east();
        fixture.place(controller, AEBlocks.CONTROLLER.block().defaultBlockState());
        fixture.place(controller.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        long[] startedAt = {0L}, outputAt = {0L};
        helper.startSequence().thenWaitUntil(() -> {
            assertLiveNetwork(fixture, core, chest, facing);
            assertDarkRoof(fixture, origin, facing);
            helper.assertTrue(core.isRunning() && stored(chest) > 0, "The fixed-midnight realm actually permits production");
        }).thenExecute(() -> {
            helper.assertTrue(core.outputBitsPerTick() == 1024L && core.energyPerTick() == 1024D,
                    "The default realm bonus multiplies output by eight without multiplying energy");
            long before = stored(chest);
            core.serverTick();
            helper.assertTrue(stored(chest) - before == 1024L, "A normal paid production tick inserts 1024 real bits");
            ModConfig.STAR_SEA_OUTPUT_BITS_PER_TICK.set(3);
            ModConfig.STAR_SEA_ENERGY_PER_TICK.set(80.5D);
            ModConfig.STAR_SEA_DETACHED_SIGNAL_OUTPUT_MULTIPLIER.set(7);
            helper.assertTrue(core.outputBitsPerTick() == 21L && core.energyPerTick() == 80.5D,
                    "The three config values independently control base output, price and realm multiplier");
            var ordinary = new StarSeaCalculationMainframeBlockEntity(origin, core.getBlockState());
            ordinary.setLevel(helper.getLevel());
            helper.assertTrue(ordinary.outputBitsPerTick() == 3L && ordinary.energyPerTick() == 80.5D,
                    "Ordinary dimensions receive the configured base output, not the realm multiplier");
            before = stored(chest);
            core.showStatus(player);
            helper.assertTrue(stored(chest) == before, "The live status probe still generates no output");
            core.serverTick();
            helper.assertTrue(stored(chest) - before == 21L, "The configured batch reaches real signal storage");
            startedAt[0] = realm.getGameTime();
            outputAt[0] = stored(chest);
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(stored(chest) - outputAt[0] == (realm.getGameTime() - startedAt[0]) * 21L,
                    "Configured realm production follows its half-speed/double-speed local clock");
        }).thenSucceed();
    }

    private static void assertRunningPacket(ServerRegressionFixture fixture, StarSeaCalculationMainframeBlockEntity core,
            StarSeaCalculationMainframeBlockEntity receiver, boolean expected) {
        var packet = (ClientboundBlockEntityDataPacket) core.getUpdatePacket();
        fixture.helper.assertTrue(packet.getPos().equals(core.getBlockPos()) && packet.getType() == core.getType(),
                "The standard AE2 block-entity update targets the existing bottom-front core");
        receiver.loadAdditional(packet.getTag(), fixture.level.registryAccess());
        fixture.helper.assertTrue(core.isRunning() == expected && receiver.isRunning() == expected,
                "Actual production and the decoded client update agree: running=" + expected);
    }

    private static CableBusBlockEntity placeCable(ServerRegressionFixture fixture, BlockPos pos, ServerPlayer player) {
        var cable = fixture.place(pos, AEBlocks.CABLE_BUS.block().defaultBlockState(), CableBusBlockEntity.class);
        fixture.helper.assertTrue(cable.addPart(AEParts.SMART_CABLE.item(AEColor.TRANSPARENT), null, player) != null,
                "A real AE2 smart cable is installed in the cable bus");
        return cable;
    }

    private static ServerRegressionFixture fixture(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        // Includes all four local sites; the next fixture starts 512 blocks away.
        fixture.onCleanup(() -> drops(fixture).forEach(ItemEntity::discard));
        return fixture;
    }

    private static BlockPos siteOrigin(ServerRegressionFixture fixture, int index) {
        // Deliberately approach chunk edges so transformed reservations exercise multiple forced chunks.
        return fixture.origin.offset(7 + index * 32, 0, 7);
    }

    private static void prepareSite(ServerRegressionFixture fixture, BlockPos origin, Direction facing) {
        // place() forces chunks AND records all cells, including future passive members, for fixture cleanup.
        for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++)
            fixture.place(origin.offset(StarSeaCalculationGeometry.offset(part, facing)), Blocks.AIR.defaultBlockState());
        for (int part = 0; part < ROOF_COLUMNS; part++)
            fixture.place(origin.offset(StarSeaCalculationGeometry.offset(part, facing)).below(), Blocks.STONE.defaultBlockState());
    }

    private static ItemStack equipBuilding(ServerPlayer player) {
        var stack = new ItemStack(StarSeaCalculationContent.ITEM.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        return stack;
    }

    private static InteractionResult useBuildingItem(ServerPlayer player, BlockPos origin, Direction facing) {
        player.setPos(origin.relative(facing, 4).getCenter());
        player.setYRot(facing.getOpposite().toYRot());
        var hit = new BlockHitResult(new Vec3(origin.getX() + .5, origin.getY(), origin.getZ() + .5),
                Direction.UP, origin.below(), false);
        var context = new UseOnContext(player, InteractionHand.MAIN_HAND, hit);
        if (context.getHorizontalDirection().getOpposite() != facing)
            throw new IllegalStateException("The player's actual horizontal direction must determine the opposite building facing");
        // Do not call BlockItem.place/useOn directly: ItemStack enters NeoForge's capture/event/rollback path.
        return player.getMainHandItem().useOn(context);
    }

    private static StarSeaCalculationMainframeBlockEntity placeBuilding(ServerRegressionFixture fixture, ServerPlayer player,
            BlockPos origin, Direction facing) {
        prepareSite(fixture, origin, facing);
        equipBuilding(player);
        fixture.helper.assertTrue(useBuildingItem(player, origin, facing).consumesAction() && player.getMainHandItem().isEmpty(),
                "Place the complete building through the real survival item");
        return assertBuilding(fixture, origin, facing);
    }

    private static StarSeaCalculationMainframeBlockEntity assertBuilding(ServerRegressionFixture fixture, BlockPos origin, Direction facing) {
        var helper = fixture.helper;
        int entities = 0;
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++) {
            var pos = origin.offset(StarSeaCalculationGeometry.offset(part, facing));
            var state = fixture.level.getBlockState(pos);
            helper.assertTrue(state.is(StarSeaCalculationContent.BLOCK.get())
                            && state.getValue(StarSeaCalculationMainframeBlock.PART) == part
                            && state.getValue(HorizontalDirectionalBlock.FACING) == facing,
                    "Native placement retains each canonical PART at its rotated position: " + facing + " part=" + part);
            helper.assertTrue(StarSeaCalculationMainframeBlock.corePosition(pos, state).equals(origin), "Every passive member resolves its actual owner");
            var entity = fixture.level.getBlockEntity(pos);
            if (entity != null) entities++;
            helper.assertTrue(part == StarSeaCalculationMainframeBlock.CORE_PART
                            ? entity instanceof StarSeaCalculationMainframeBlockEntity : entity == null,
                    "Only the bottom-front core owns a BE; all other reserved cells are passive");
            minX = Math.min(minX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int occupied = 0;
        for (var pos : BlockPos.betweenClosed(origin.offset(-5, 0, -5), origin.offset(5, 9, 5)))
            if (fixture.level.getBlockState(pos).is(StarSeaCalculationContent.BLOCK.get())) occupied++;
        var actualBounds = new AABB(minX, origin.getY(), minZ, maxX + 1, origin.getY() + 9, maxZ + 1);
        var expectedBounds = expectedBounds(origin, facing);
        helper.assertTrue(occupied == 225 && entities == 1 && actualBounds.equals(expectedBounds)
                        && StarSeaCalculationMainframeBlock.bounds(origin, facing).equals(expectedBounds),
                "Exactly 225 cells and one BE occupy the independently specified 5 x 9 x 5 transformed extent: " + facing);
        helper.assertTrue(StarSeaCalculationMainframeBlock.structureComplete(fixture.level, origin, facing), "The placed structure validates on the real server");
        return (StarSeaCalculationMainframeBlockEntity) fixture.level.getBlockEntity(origin);
    }

    private static AABB expectedBounds(BlockPos origin, Direction facing) {
        return switch (facing) {
            case NORTH -> new AABB(origin.getX() - 2, origin.getY(), origin.getZ(), origin.getX() + 3, origin.getY() + 9, origin.getZ() + 5);
            case EAST -> new AABB(origin.getX() - 4, origin.getY(), origin.getZ() - 2, origin.getX() + 1, origin.getY() + 9, origin.getZ() + 3);
            case SOUTH -> new AABB(origin.getX() - 2, origin.getY(), origin.getZ() - 4, origin.getX() + 3, origin.getY() + 9, origin.getZ() + 1);
            case WEST -> new AABB(origin.getX(), origin.getY(), origin.getZ() - 2, origin.getX() + 5, origin.getY() + 9, origin.getZ() + 3);
            default -> throw new IllegalArgumentException("Horizontal building facing required");
        };
    }

    private static void assertEmptyReservation(ServerRegressionFixture fixture, BlockPos origin, Direction facing, BlockPos obstruction) {
        for (int part = 0; part < StarSeaCalculationGeometry.PART_COUNT; part++) {
            var pos = origin.offset(StarSeaCalculationGeometry.offset(part, facing));
            var state = fixture.level.getBlockState(pos);
            fixture.helper.assertTrue(pos.equals(obstruction) ? state.is(Blocks.OBSIDIAN) : state.isAir(),
                    "No partial placement or orphan member remains: " + facing + " part=" + part);
            fixture.helper.assertTrue(fixture.level.getBlockEntity(pos) == null, "No controller or passive BE survives rollback/removal");
        }
    }

    private static void assertFoundation(ServerRegressionFixture fixture, BlockPos origin, Direction facing, BlockPos missing) {
        for (int part = 0; part < ROOF_COLUMNS; part++) {
            var pos = origin.offset(StarSeaCalculationGeometry.offset(part, facing)).below();
            var state = fixture.level.getBlockState(pos);
            fixture.helper.assertTrue(pos.equals(missing) ? state.isAir() : state.is(Blocks.STONE), "Placement/removal preserves the supplied foundation");
        }
    }

    private static void memberBreak(GameTestHelper helper, GameType mode) {
        var fixture = fixture(helper);
        var player = fixture.player("star-sea-break", fixture.origin);
        for (int index = 0; index < FACINGS.size(); index++) {
            var origin = siteOrigin(fixture, index);
            var facing = FACINGS.get(index);
            player.setGameMode(GameType.SURVIVAL);
            placeBuilding(fixture, player, origin, facing);
            var member = origin.offset(StarSeaCalculationGeometry.offset(StarSeaCalculationGeometry.partIndex(1, 4, 2), facing));
            player.setPos(member.relative(facing, 2).getCenter());
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
            player.setGameMode(mode);
            helper.assertTrue(fixture.level.getBlockState(member).canHarvestBlock(fixture.level, member, player),
                    "A real iron pickaxe satisfies the actual server harvest tags");
            helper.assertTrue(player.gameMode.destroyBlock(member), "Use the native playerWillDestroy/harvest/removal hooks: " + mode);
            assertEmptyReservation(fixture, origin, facing, null);
            assertFoundation(fixture, origin, facing, null);
            // Entity additions can be queued while the server ticks; inspect drops after a few real ticks below.
        }
        helper.startSequence().thenIdle(5).thenExecute(() -> {
            for (int index = 0; index < FACINGS.size(); index++)
                assertEmptyReservation(fixture, siteOrigin(fixture, index), FACINGS.get(index), null);
            assertDrops(fixture, mode == GameType.SURVIVAL ? 4 : 0);
        }).thenSucceed();
    }

    private static List<ItemEntity> drops(ServerRegressionFixture fixture) {
        return fixture.level.getEntitiesOfClass(ItemEntity.class, new AABB(fixture.origin).inflate(192));
    }

    private static void assertDrops(ServerRegressionFixture fixture, int expected) {
        var drops = drops(fixture);
        fixture.helper.assertTrue(drops.size() == expected && drops.stream().allMatch(entity ->
                        entity.getItem().is(StarSeaCalculationContent.ITEM.get()) && entity.getItem().getCount() == 1),
                "Expect exactly " + expected + " whole-building items and no passive-cell or other item drops; actual="
                        + drops.stream().map(ItemEntity::getItem).toList());
    }

    private static BlockPos roof(BlockPos origin, Direction facing, int column) {
        return origin.offset(StarSeaCalculationGeometry.offset(column, facing)).above(StarSeaCalculationEnvironment.ROOF_SAMPLE_HEIGHT);
    }

    private static void assertOpenNightSky(ServerRegressionFixture fixture, BlockPos origin, Direction facing) {
        for (int column = 0; column < ROOF_COLUMNS; column++) {
            var sample = roof(origin, facing, column);
            fixture.helper.assertTrue(fixture.level.getBrightness(LightLayer.SKY, sample) == 15
                            && fixture.level.getBrightness(LightLayer.BLOCK, sample) == 0
                            && fixture.level.getMaxLocalRawBrightness(sample) <= 7,
                    "Wait for real open-sky night lighting at rotated roof column " + column);
        }
    }

    private static void assertDarkRoof(ServerRegressionFixture fixture, BlockPos origin, Direction facing) {
        for (int column = 0; column < ROOF_COLUMNS; column++)
            fixture.helper.assertTrue(fixture.level.getMaxLocalRawBrightness(roof(origin, facing, column)) <= 7,
                    "Wait for every actual roof sample to be within the inclusive darkness threshold: " + column);
    }

    private static void placeTorch(ServerRegressionFixture fixture, BlockPos sample, int distance) {
        // A wall torch leaves the downward light path open: 14 - 6 = 8 at just this column,
        // and <= 7 at its neighbors. At distance 7 the target itself is exactly the allowed 7.
        var torch = sample.above(distance);
        fixture.place(torch.north(), Blocks.STONE.defaultBlockState());
        fixture.place(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
    }

    private static void removeTorch(ServerRegressionFixture fixture, BlockPos sample, int distance) {
        var torch = sample.above(distance);
        fixture.place(torch, Blocks.AIR.defaultBlockState());
        fixture.place(torch.north(), Blocks.AIR.defaultBlockState());
    }

    private static void assertLightProfile(ServerRegressionFixture fixture, BlockPos origin, Direction facing, int target, int brightness) {
        for (int column = 0; column < ROOF_COLUMNS; column++) {
            var sample = roof(origin, facing, column);
            int blockLight = fixture.level.getBrightness(LightLayer.BLOCK, sample);
            int rawLight = fixture.level.getMaxLocalRawBrightness(sample);
            fixture.helper.assertTrue(column == target ? blockLight == brightness && rawLight == brightness : rawLight <= 7,
                    "Only roof column " + target + " reaches " + brightness + "; column=" + column + ", block=" + blockLight + ", raw=" + rawLight);
        }
    }

    private static void canopy(ServerRegressionFixture fixture, BlockPos origin, Direction facing, boolean present) {
        // Eight blocks of overhang prevent lateral skylight from exceeding 7 even at the footprint's edges.
        for (int x = -10; x <= 10; x++) for (int z = -8; z <= 12; z++)
            fixture.place(origin.offset(StarSeaCalculationGeometry.rotateOffset(new BlockPos(x, 10, z), facing)),
                    (present ? Blocks.STONE : Blocks.AIR).defaultBlockState());
    }

    private static void assertStatus(ServerRegressionFixture fixture, StarSeaCalculationMainframeBlockEntity core, ServerPlayer player, Status expected) {
        core.showStatus(player);
        fixture.helper.assertTrue(core.getStatus() == expected, "Real server time/light/network status: expected=" + expected + ", actual=" + core.getStatus());
    }

    private static long stored(MEChestBlockEntity chest) {
        return chest.getOriginalCellInventory(0).getAvailableStacks().get(StarSignalKey.INSTANCE);
    }

    private static void assertLiveNetwork(ServerRegressionFixture fixture, StarSeaCalculationMainframeBlockEntity core,
            MEChestBlockEntity chest, Direction facing) {
        var node = core.getMainNode().getNode();
        fixture.helper.assertTrue(node != null && core.getMainNode().isActive() && chest.getMainNode().isActive()
                        && core.getMainNode().getGrid() == chest.getMainNode().getGrid()
                        && chest.getOriginalCellInventory(0) != null, "The native adjacent AE2 network has power, channels, and its finite signal cell mounted");
        fixture.helper.assertTrue(node.getUsedChannels() == 1 && node.getInWorldConnections().containsKey(facing),
                "Exactly one channel reaches the bottom-front core through a real connection on its front face");
    }

    private static void stopAndRecord(ServerRegressionFixture fixture, StarSeaCalculationMainframeBlockEntity core,
            MEChestBlockEntity chest, Status expected, long[] stoppedAt, double[] credit) {
        long before = stored(chest);
        double previousCredit = core.energyCredit();
        core.serverTick();
        fixture.helper.assertTrue(core.getStatus() == expected && !core.isRunning()
                        && stored(chest) == before && core.energyCredit() == previousCredit,
                "The first tick observing " + expected + " halts production and its effect and preserves paid credit immediately");
        stoppedAt[0] = before;
        credit[0] = previousCredit;
    }

    private static void assertStopped(ServerRegressionFixture fixture, StarSeaCalculationMainframeBlockEntity core,
            MEChestBlockEntity chest, Direction facing, Status expected, long amount, double credit) {
        assertLiveNetwork(fixture, core, chest, facing);
        fixture.helper.assertTrue(core.getStatus() == expected && !core.isRunning()
                        && stored(chest) == amount && core.energyCredit() == credit,
                "Natural server ticks stay blocked by " + expected + " with the effect stopped while the AE2 network remains operational");
    }

    private static void assertResumed(ServerRegressionFixture fixture, StarSeaCalculationMainframeBlockEntity core,
            MEChestBlockEntity chest, long previous) {
        fixture.helper.assertTrue(core.getStatus() == Status.RUNNING && core.isRunning() && stored(chest) > previous,
                "Normal server ticks resume real cell production and its effect when the environment permits it");
    }

    private static void environment(ServerRegressionFixture fixture, long time, boolean thunder) {
        fixture.level.setDayTime(time);
        fixture.level.setWeatherParameters(thunder ? 0 : 12_000, 12_000, thunder, thunder);
        fixture.level.setRainLevel(thunder ? 1 : 0);
        fixture.level.setThunderLevel(thunder ? 1 : 0);
        fixture.level.updateSkyBrightness();
    }

    private static void controlEnvironment(ServerRegressionFixture fixture) {
        var level = fixture.level;
        var data = (ServerLevelData) level.getLevelData();
        long dayTime = level.getDayTime();
        float fraction = data.getDayTimeFraction();
        boolean daylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        boolean weather = level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE);
        boolean raining = data.isRaining(), thundering = data.isThundering();
        int clearTime = data.getClearWeatherTime(), rainTime = data.getRainTime(), thunderTime = data.getThunderTime();
        float rain = level.getRainLevel(1), thunder = level.getThunderLevel(1);
        fixture.onCleanup(() -> {
            level.setDayTime(dayTime);
            data.setDayTimeFraction(fraction);
            data.setClearWeatherTime(clearTime);
            data.setRainTime(rainTime);
            data.setThunderTime(thunderTime);
            data.setRaining(raining);
            data.setThundering(thundering);
            level.setRainLevel(rain);
            level.setThunderLevel(thunder);
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(daylight, level.getServer());
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(weather, level.getServer());
            level.updateSkyBrightness();
        });
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, level.getServer());
    }
}
