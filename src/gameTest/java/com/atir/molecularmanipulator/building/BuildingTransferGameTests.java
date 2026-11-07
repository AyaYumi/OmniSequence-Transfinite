package com.atir.molecularmanipulator.building;

import appeng.api.AECapabilities;
import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.IPlayerRegistry;
import appeng.api.ids.AEComponents;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.atir.molecularmanipulator.MolecularManipulator;
import com.atir.molecularmanipulator.block.StarComputationEngineCoreBlock;
import com.atir.molecularmanipulator.block.StarComputationEngineCasingBlock;
import com.atir.molecularmanipulator.block.StarPositionAnchorBlock;
import com.atir.molecularmanipulator.blockentity.*;
import com.atir.molecularmanipulator.registry.ModContent;
import com.atir.molecularmanipulator.registry.BuildingContent;
import com.atir.molecularmanipulator.registry.SingularityContent;
import com.atir.molecularmanipulator.network.BuildingPayloads;
import com.atir.molecularmanipulator.menu.SingularityMenu;
import com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
import com.atir.molecularmanipulator.starcomputation.StarOrreryLayout;
import com.atir.molecularmanipulator.world.SingularityMotionWorld;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Runs with real registries, mixins, AE initialization, world chunks, archives and player .dat files. */
@GameTestHolder(MolecularManipulator.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BuildingTransferGameTests {
    // Only the transport is silent; use a real ServerPlayer (not FakePlayer, whose openMenu is a no-op).
    // PlayerList registration retains native menu creation and the production receipt save/readback path.
    private static ServerPlayer testPlayer(GameTestHelper helper) { return testPlayer(helper, packet -> {}); }
    @SuppressWarnings("unchecked")
    private static ServerPlayer testPlayer(GameTestHelper helper, java.util.function.Consumer<net.minecraft.network.protocol.Packet<?>> packets) {
        var server = helper.getLevel().getServer();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "transfer-test");
        var player = new ServerPlayer(server, helper.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        new io.netty.channel.embedded.EmbeddedChannel(connection);
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, player,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet) { packets.accept(packet); }
            @Override public void send(net.minecraft.network.protocol.Packet<?> packet, net.minecraft.network.PacketSendListener listener) { packets.accept(packet); }
            @Override public void tick() {}
        };
        var players = server.getPlayerList();
        try {
            var list = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
            list.setAccessible(true);
            ((List<ServerPlayer>) list.get(players)).add(player);
            var byId = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
            byId.setAccessible(true);
            ((Map<UUID, ServerPlayer>) byId.get(players)).put(player.getUUID(), player);
            return player;
        } catch (ReflectiveOperationException error) { throw new RuntimeException(error); }
    }

    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedWellDeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.FABRICATION_WELL);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedArrayDeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.SEQUENCE_ARRAY);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOmniDeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.OMNI_COMPUTATION);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL0DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L0);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL1DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L1);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL2DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L2);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL3DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L3);
    }

    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL4DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L4);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL5DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L5);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL6DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L6);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL7DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L7);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL8DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L8);
    }
    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void sealedOrreryL9DeploysFormsAndCanBeReclaimedAgain(GameTestHelper helper) {
        roundTrip(helper, BuildingType.STAR_ORRERY_L9);
    }

    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void upgradedFreshOrreryReclaimsTheServerGradeAfterLateRingDamage(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        var sourceBase = helper.absolutePos(new BlockPos(48, 2, 64));
        var targetBase = helper.absolutePos(new BlockPos(144, 2, 64));
        var source = sourceBase.above(37); var target = targetBase.above(37);
        player.getInventory().setItem(0, BuildingPackage.fresh(BuildingType.STAR_ORRERY_L3));
        helper.assertTrue(BuildingTransfer.deploy(player, 0, sourceBase, Direction.EAST), "An original L3 construction package must deploy");
        helper.startSequence().thenWaitUntil(() -> assertRestored(helper, player, BuildingType.STAR_ORRERY_L3, source, Direction.EAST))
                .thenExecute(() -> {
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Upgrade a committed fresh L3, with no remaining receipt");
                    for (var entry : orreryTestLayout(9)) {
                        if (!(entry.state().getBlock() instanceof StarPositionAnchorBlock)) continue;
                        var pos = source.offset(entry.offset().rotate(BuildingType.rotation(Direction.EAST)));
                        if (level.getBlockState(pos).isAir()) level.setBlock(pos, BuildingType.rotateState(entry.state(), BuildingType.rotation(Direction.EAST)), 2 | 16);
                    }
                    seedOrreryContents(level, player, source);
                }).thenWaitUntil(() -> assertRestored(helper, player, BuildingType.STAR_ORRERY_L9, source, Direction.EAST))
                .thenExecute(() -> {
                    clickBlock(helper, player, source);
                    helper.assertTrue(player.containerMenu instanceof StarComputationEngineMenu, "The upgraded machine must open its real reclaim menu");
                    ((StarComputationEngineMenu) player.containerMenu).receiveClientAction("entropy_reclaim", "");
                    helper.assertTrue(level.getBlockEntity(source) != null && player.getInventory().getItem(0).isEmpty(), "The first menu action must only arm reclaim");
                }).thenIdle(5).thenExecute(() -> {
                    var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(source);
                    helper.assertTrue(core.getLitRings() == 9 && BuildingType.forController(core) == BuildingType.STAR_ORRERY_L9,
                            "Classification must use all nine validated runtime rings after upgrading from L3");
                    var broken = StarOrreryLayout.slot(source, Direction.EAST, 8, 0);
                    level.setBlock(broken, Blocks.AIR.defaultBlockState(), 2 | 16);
                    helper.assertTrue(core.getLitRings() == 9, "Damage and reclaim must share a callback, before the periodic scan can fix the stale grade");
                    ((StarComputationEngineMenu) player.containerMenu).receiveClientAction("entropy_reclaim", "");
                    assertSealedPackage(helper, player, BuildingType.STAR_ORRERY_L8);
                    assertOrreryCentralCleared(helper, source);
                    helper.assertTrue(level.getBlockState(broken).isAir(), "Reclaim must not repair the broken ninth ring");
                    helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, Direction.WEST), "The downgraded L8 archive must deploy after rotation");
                }).thenWaitUntil(() -> assertRestored(helper, player, BuildingType.STAR_ORRERY_L8, target, Direction.WEST))
                .thenExecute(() -> {
                    assertOrreryContents(helper, player, target);
                    assertOpensMenu(helper, player, BuildingType.STAR_ORRERY_L8, target);
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "The actual-grade archive must commit exactly once");
                }).thenSucceed();
    }

    @GameTest(template = "transfer_site_wide", timeoutTicks = 2000)
    public static void legacyGenericOrreryArchiveDeploysAndReclaimsAtItsActualGrade(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.STAR_ORRERY_L6;
        var sourceBase = helper.absolutePos(new BlockPos(48, 2, 64));
        var targetBase = helper.absolutePos(new BlockPos(144, 2, 64));
        var source = sourceBase.above(37); var target = targetBase.above(37);
        createSource(level, player, type, source); seedOrreryContents(level, player, source);
        var archive = new UUID[1];
        helper.startSequence().thenWaitUntil(() -> assertRestored(helper, player, type, source, Direction.NORTH))
                .thenExecute(() -> {
                    helper.assertTrue(BuildingTransfer.seal(player, level.getBlockEntity(source)), "Capture real L6 blocks and native data for an old-format archive");
                    archive[0] = assertSealedPackage(helper, player, type);
                    // Old saves used the generic identity for this same native snapshot, regardless of grade.
                    var saved = manifest(helper, archive[0]); saved.putString("type", BuildingType.STAR_COMPUTATION_ENGINE.id);
                    try { BuildingArchiveStore.forServer(level.getServer()).writeManifest(archive[0], saved); }
                    catch (IOException error) { throw new RuntimeException(error); }
                    player.getInventory().setItem(0, BuildingPackage.sealed(BuildingType.STAR_COMPUTATION_ENGINE, archive[0], saved.getInt("blocks")));
                    helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, Direction.WEST), "An existing generic archive must still deploy its real high-grade footprint");
                }).thenWaitUntil(() -> assertRestored(helper, player, type, target, Direction.WEST))
                .thenExecute(() -> {
                    assertState(helper, archive[0], "DEPLOYED");
                    assertOrreryContents(helper, player, target);
                    assertOpensMenu(helper, player, type, target);
                    helper.assertTrue(BuildingTransfer.seal(player, level.getBlockEntity(target)), "The deployed old archive must remain reclaimable");
                    helper.assertTrue(!archive[0].equals(assertSealedPackage(helper, player, type)), "Reclaim must issue a new exact L6 identity for an old generic archive");
                    assertOrreryCentralCleared(helper, source); assertOrreryCentralCleared(helper, target);
                }).thenSucceed();
    }

    @GameTest(template = "transfer_site", timeoutTicks = 1000)
    public static void freshOrreryL1PackageBuildsCompleteCentralBodyAfterRotation(GameTestHelper helper) {
        freshOrrery(helper, BuildingType.STAR_ORRERY_L1);
    }
    @GameTest(template = "transfer_site", timeoutTicks = 1000)
    public static void freshOrreryL2PackageBuildsCompleteCentralBodyAfterRotation(GameTestHelper helper) {
        freshOrrery(helper, BuildingType.STAR_ORRERY_L2);
    }
    @GameTest(template = "transfer_site", timeoutTicks = 1000)
    public static void freshOrreryL3PackageBuildsCompleteCentralBodyAfterRotation(GameTestHelper helper) {
        freshOrrery(helper, BuildingType.STAR_ORRERY_L3);
    }
    private static void freshOrrery(GameTestHelper helper, BuildingType type) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        assertOrreryTemplate(helper, type);
        var base = helper.absolutePos(new BlockPos(48, 4, 48));
        var origin = BuildingTransfer.controllerAtBase(type.template(), base);
        helper.assertTrue(origin.getY() == base.getY() + 37, "Calibration must place the complete foundation above its base plane");
        player.getInventory().setItem(0, BuildingPackage.fresh(type));
        helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.WEST), "Complete north-authored " + type + " package must deploy facing west");
        var id = BuildingPackage.reservation(player.getInventory().getItem(0));
        helper.startSequence().thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.WEST))
                .thenExecute(() -> {
                    assertState(helper, id, "DEPLOYED");
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "A complete package must be consumed exactly once");
                    var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
                    helper.assertTrue(player.getUUID().equals(core.getBoundTeam()), "Fresh orrery packages must bind the deployment owner");
                    assertOpensMenu(helper, player, type, origin);
                }).thenSucceed();
    }

    @GameTest(template = "transfer_site_tall", timeoutTicks = 2000)
    public static void freshSingularityPackagePreservesTheFullBlueprintAndFormsAfterRotation(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        var type = BuildingType.EVENT_HORIZON_SINGULARITY;
        var base = helper.absolutePos(new BlockPos(64, 4, 64));
        var origin = BuildingTransfer.controllerAtBase(type.template(), base);
        var facing = Direction.WEST;
        helper.assertTrue(origin.getY() == base.getY() + 65, "Singularity controller must be 65 blocks above the base plane");
        helper.assertTrue(type.template().size() == SingularityStructure.parts().size() + SingularityStructure.requiredAir().size(),
                "The package must contain all authored parts and every required empty cell");
        helper.assertTrue(type.template().stream().map(BuildingArchiveStore.Entry::offset).distinct().count() == type.template().size(),
                "Required air must not replace a real structural member");
        var expectedMaterials = new java.util.LinkedHashMap<AEItemKey, Long>();
        SingularityStructure.counts().forEach((material, count) -> expectedMaterials.put(AEItemKey.of(SingularityStructure.block(material).asItem()), count.longValue()));
        helper.assertTrue(type.materials().equals(expectedMaterials), "A complete singularity bill must include the controller and all real construction blocks");
        var indexed = type.template().stream().collect(java.util.stream.Collectors.toMap(BuildingArchiveStore.Entry::offset, BuildingArchiveStore.Entry::state));
        for (var part : SingularityStructure.parts()) {
            var offset = SingularityStructure.worldPos(BlockPos.ZERO, Direction.NORTH, part);
            for (var direction : Direction.Plane.HORIZONTAL)
                helper.assertTrue(BuildingType.rotateState(indexed.get(offset), BuildingType.rotation(direction)).equals(SingularityStructure.state(part, direction)),
                        "Every authored axis, facing and slab half must survive rotation");
        }
        player.getInventory().setItem(0, BuildingPackage.fresh(type));
        var obstruction = SingularityStructure.worldPos(origin, facing, SingularityStructure.requiredAir().getFirst());
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 2 | 16);
        helper.assertTrue(BuildingTransfer.survey(player, type.template(), origin, facing).conflicts().contains(obstruction),
                "The central light well must reject solid obstructions before reserving the package");
        helper.assertTrue(!BuildingTransfer.deploy(player, 0, base, facing) && BuildingPackage.reservation(player.getInventory().getItem(0)) == null,
                "An obstructed light well must not strand a package receipt");
        level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 2 | 16);
        helper.assertTrue(BuildingTransfer.deploy(player, 0, base, facing), "A clear site must accept the complete singularity package");
        var id = BuildingPackage.reservation(player.getInventory().getItem(0));
        helper.startSequence().thenWaitUntil(() -> assertRestored(helper, player, type, origin, facing)).thenExecute(() -> {
            assertState(helper, id, "DEPLOYED");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "A committed singularity consumes exactly its own package");
            assertSingularityBlueprint(helper, origin, facing);
            assertOpensMenu(helper, player, type, origin);
        }).thenSucceed();
    }

    @GameTest(template = "transfer_site_tall", timeoutTicks = 2500)
    public static void unfinishedSingularityPackageRollsBackAndDeploysAgainWithoutExtraMaterials(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.EVENT_HORIZON_SINGULARITY;
        var base = helper.absolutePos(new BlockPos(64, 4, 64));
        var origin = BuildingTransfer.controllerAtBase(type.template(), base);
        player.getInventory().setItem(0, BuildingPackage.fresh(type));
        player.getInventory().setItem(1, new ItemStack(BuildingContent.CALIBRATOR.get())); player.getInventory().selected = 1;
        helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "The complete singularity package must start unfolding");
        var id = BuildingPackage.reservation(player.getInventory().getItem(0));
        helper.startSequence().thenIdle(90).thenExecute(() -> {
            var first = type.template().stream().filter(entry -> !entry.state().isAir()).findFirst().orElseThrow();
            helper.assertTrue(level.getBlockState(origin.offset(first.offset())).is(first.state().getBlock()), "Rollback must exercise an actually placed structure");
            helper.assertTrue(level.getBlockEntity(origin) == null, "The controller must remain last during construction");
            BuildingPayloads.transferControl(player, new BuildingPayloads.TransferControl(UUID.randomUUID(), id, 0, origin, true));
            assertState(helper, id, "ROLLING_BACK");
        }).thenWaitUntil(() -> helper.assertTrue(!BuildingTransfer.locked(level, origin)
                && BuildingPackage.reservation(player.getInventory().getItem(0)) == null, "Rollback must save, release the receipt and unlock the site"))
                .thenExecute(() -> {
                    assertState(helper, id, "CANCELLED");
                    helper.assertTrue(BuildingPackage.type(player.getInventory().getItem(0)) == type
                            && BuildingPackage.archive(player.getInventory().getItem(0)) == null, "Rollback must return the original complete fresh package");
                    helper.assertTrue(player.getInventory().items.stream().filter(stack -> BuildingPackage.type(stack) != null).count() == 1,
                            "Rollback must never mint a second package");
                    for (var entry : type.template()) helper.assertTrue(level.getBlockState(origin.offset(entry.offset())).isAir(), "Rollback must clear every placed part");
                    helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.EAST), "The same package must deploy again after rollback");
                }).thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.EAST)).thenExecute(() -> {
                    assertSingularityBlueprint(helper, origin, Direction.EAST);
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Only the final complete deployment consumes the package");
                }).thenSucceed();
    }
    @GameTest(template = "transfer_site_tall", timeoutTicks = 4000)
    public static void sealedSingularityPreservesNativeContentsThroughTwoRotatedReclaims(GameTestHelper helper) {
        singularityRoundTrip(helper, SingularityStructure.VERSION);
    }
    @GameTest(template = "transfer_site_tall", timeoutTicks = 4000)
    public static void sealedLegacySingularityPreservesItsNativeLayoutAndContents(GameTestHelper helper) {
        singularityRoundTrip(helper, SingularityStructure.LEGACY_VERSION);
    }
    @GameTest(template = "transfer_site_tall", timeoutTicks = 4000)
    public static void sealedEmbeddedSingularityPreservesItsNativeLayoutAndContents(GameTestHelper helper) {
        singularityRoundTrip(helper, SingularityStructure.EMBEDDED_VERSION);
    }
    private static void singularityRoundTrip(GameTestHelper helper, int version) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.EVENT_HORIZON_SINGULARITY;
        var source = singularityOrigin(helper, version, Direction.NORTH);
        var target = singularityOrigin(helper, version, Direction.EAST);
        var rotated = singularityOrigin(helper, version, Direction.WEST);
        createSingularitySource(level, player, source, version);
        var contents = new CompoundTag[1]; var archives = new UUID[2]; var energy = new double[2];
        var restored = new SingularityBlockEntity[1];
        helper.startSequence().thenWaitUntil(() -> assertNativeSingularity(helper, player, source, Direction.NORTH, version))
                .thenExecute(() -> {
                    var machine = (SingularityBlockEntity) level.getBlockEntity(source);
                    seedSingularityContents(level, machine);
                    contents[0] = machine.saveWithFullMetadata(level.registryAccess());
                    var power = machine.getMainNode().getGrid().getEnergyService();
                    power.injectPower(power.getMaxStoredPower(), Actionable.MODULATE);
                    energy[0] = machine.saveWithFullMetadata(level.registryAccess()).getCompound("proxy").getDouble("e");
                    helper.assertTrue(energy[0] > 0 && machine.canSealPackage(), "Idle native inventories and interrupted duplication must remain sealable with real buffered AE power");
                    machine.startDismantle(player);
                    archives[0] = assertSingularityArchive(helper, player, source, Direction.NORTH, version, contents[0], energy[0]);
                    helper.assertTrue(BuildingTransfer.deploy(player, 0, target.below(SingularityStructure.anchor(version).getY()), Direction.EAST),
                            "The native singularity archive must deploy into the cleared footprint after rotation");
                }).thenWaitUntil(() -> assertNativeSingularity(helper, player, target, Direction.EAST, version))
                .thenExecute(() -> {
                    assertState(helper, archives[0], "DEPLOYED");
                    var machine = (SingularityBlockEntity) level.getBlockEntity(target);
                    assertSingularityContents(helper, machine, contents[0]);
                    assertSingularityEnergy(helper, machine, energy[0]);
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "The first complete deployment consumes its sole receipt");
                    clickBlock(helper, player, target);
                    helper.assertTrue(player.containerMenu instanceof SingularityMenu, "Native relocation must retain the real singularity menu");
                    ((SingularityMenu) player.containerMenu).receiveClientAction("dismantle", "");
                    helper.assertTrue(level.getBlockEntity(target) == machine && player.getInventory().getItem(0).isEmpty(), "One menu request must only arm package reclaim");
                }).thenIdle(5).thenExecute(() -> {
                    var machine = (SingularityBlockEntity) level.getBlockEntity(target);
                    // Refill the small native grid buffer so each archive carries a nonzero energy sentinel after menu idle draw.
                    var power = machine.getMainNode().getGrid().getEnergyService();
                    power.injectPower(power.getMaxStoredPower(), Actionable.MODULATE);
                    energy[1] = machine.saveWithFullMetadata(level.registryAccess()).getCompound("proxy").getDouble("e");
                    helper.assertTrue(energy[1] > 0, "Native buffered power must remain available for the second reclaim");
                    ((SingularityMenu) player.containerMenu).receiveClientAction("dismantle", "");
                    archives[1] = assertSingularityArchive(helper, player, target, Direction.EAST, version, contents[0], energy[1]);
                    helper.assertTrue(!archives[0].equals(archives[1]), "A reclaimed deployment must have a new archive, with the first permanently deployed");
                    assertState(helper, archives[0], "DEPLOYED");
                    helper.assertTrue(BuildingTransfer.deploy(player, 0, rotated.below(SingularityStructure.anchor(version).getY()), Direction.WEST),
                            "The reclaimed native layout must support a second rotated deployment in the same tall site");
                }).thenWaitUntil(() -> assertNativeSingularity(helper, player, rotated, Direction.WEST, version))
                .thenExecute(() -> {
                    restored[0] = (SingularityBlockEntity) level.getBlockEntity(rotated);
                    assertSingularityContents(helper, restored[0], contents[0]);
                    assertSingularityEnergy(helper, restored[0], energy[1]);
                    assertState(helper, archives[1], "DEPLOYED");
                    assertOpensMenu(helper, player, type, rotated);
                }).thenIdle(40).thenExecute(() -> {
                    helper.assertTrue(level.getBlockEntity(rotated) == restored[0] && !restored[0].packageReclaimRequested(),
                            "A deployed native controller must remain in the world and never reclaim itself again");
                    assertSingularityContents(helper, restored[0], contents[0]);
                    helper.assertTrue(player.getInventory().items.stream().allMatch(ItemStack::isEmpty), "Neither reclaim may spill native contents or mint another package");
                }).thenSucceed();
    }
    private static BlockPos singularityOrigin(GameTestHelper helper, int version, Direction facing) {
        // Rotate around the blueprint center so even the old front-mounted terminal fits this one tall site.
        return helper.absolutePos(new BlockPos(64, 4, 64)).offset(SingularityStructure.anchor(version).rotate(BuildingType.rotation(facing)));
    }
    private static List<BlockPos> singularityCompatibilityNodes(BlockPos origin, Direction facing, int version) {
        var nodes = new ArrayList<BlockPos>();
        for (int tower = 0; tower < 8; tower++) {
            var local = new BlockPos(SingularityMotionGeometry.towerX(tower), 65, SingularityMotionGeometry.towerZ(tower) - 3);
            var pos = SingularityStructure.worldPos(origin, facing, local, version);
            if (SingularityStructure.isCompatibilityCollectionNode(origin, facing, pos, version)) nodes.add(pos);
        }
        return nodes;
    }
    private static void createSingularitySource(ServerLevel level, ServerPlayer player, BlockPos origin, int version) {
        player.setPos(origin.getCenter());
        for (var part : SingularityStructure.parts(version)) if (!SingularityStructure.isController(part))
            level.setBlock(SingularityStructure.worldPos(origin, Direction.NORTH, part, version), SingularityStructure.state(part, Direction.NORTH), 2 | 16);
        for (var air : SingularityStructure.requiredAir())
            level.setBlock(SingularityStructure.worldPos(origin, Direction.NORTH, air, version), Blocks.AIR.defaultBlockState(), 2 | 16);
        for (var pos : singularityCompatibilityNodes(origin, Direction.NORTH, version))
            level.setBlock(pos, SingularityContent.COLLECTION_NODE.get().defaultBlockState(), 2 | 16);
        level.setBlock(origin, SingularityContent.CONTROLLER.get().defaultBlockState(), 2 | 16);
        var machine = (SingularityBlockEntity) level.getBlockEntity(origin);
        var data = machine.saveWithFullMetadata(level.registryAccess());
        data.putInt("singularityVersion", version); data.putInt("singularityLayout", version);
        var proxy = new CompoundTag(); proxy.putInt("p", IPlayerRegistry.getPlayerId(player)); data.put("proxy", proxy);
        machine.loadWithComponents(data, level.registryAccess());
        machine.getMainNode().setOwningPlayer(player);
    }
    private static void seedSingularityContents(ServerLevel level, SingularityBlockEntity machine) {
        var quantum = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        quantum.set(AEComponents.ENTANGLED_SINGULARITY_ID, (UUID.randomUUID().getLeastSignificantBits() & Long.MAX_VALUE) | 1L);
        quantum.set(DataComponents.CUSTOM_DATA, catalyst().get(DataComponents.CUSTOM_DATA));
        machine.getQuantumInventory().setItemDirect(0, quantum);
        machine.getDuplicationInventory().setItemDirect(0, catalyst());
        machine.getDuplicationInventory().setItemDirect(1, new ItemStack(ModContent.BLACK_HOLE.get(), 11));
        machine.getInternalInventory().setItemDirect(0, new ItemStack(Items.EMERALD, 37));
        machine.getMachineEffects().setOverride(MachineEffectSettings.REDUCED);
        var data = machine.saveWithFullMetadata(level.registryAccess());
        // Exercise both legal interrupted-copy states across the three native layouts.
        var duplication = new CompoundTag();
        boolean refund = machine.structureVersion() == SingularityStructure.LEGACY_VERSION;
        duplication.putInt("refund", refund ? 371 : 0);
        duplication.put("output", (refund ? ItemStack.EMPTY : encodedPattern()).saveOptional(level.registryAccess()));
        data.put("singularityDuplication", duplication);
        machine.loadWithComponents(data, level.registryAccess()); machine.saveChanges();
    }
    private static void assertNativeSingularity(GameTestHelper helper, ServerPlayer player, BlockPos origin, Direction facing, int version) {
        assertRestored(helper, player, BuildingType.EVENT_HORIZON_SINGULARITY, origin, facing);
        var level = helper.getLevel(); var machine = (SingularityBlockEntity) level.getBlockEntity(origin);
        helper.assertTrue(machine.structureVersion() == version && machine.inspection().formed(), "Reclaim must retain the native layout version without upgrading it");
        for (var part : SingularityStructure.parts(version))
            helper.assertTrue(SingularityStructure.matches(level.getBlockState(SingularityStructure.worldPos(origin, facing, part, version)), part, facing),
                    "Every version-specific native structural state must survive rotation");
        for (var air : SingularityStructure.requiredAir())
            helper.assertTrue(level.getBlockState(SingularityStructure.worldPos(origin, facing, air, version)).isAir(), "Every required native air cell must remain empty");
        for (var pos : singularityCompatibilityNodes(origin, facing, version))
            helper.assertTrue(level.getBlockState(pos).is(SingularityContent.COLLECTION_NODE.get())
                    && BuildingType.facing(level.getBlockState(pos)) == facing, "Fixed compatibility nodes must survive both rotations");
    }
    private static void assertSingularityContents(GameTestHelper helper, SingularityBlockEntity machine, CompoundTag expected) {
        var actual = machine.saveWithFullMetadata(helper.getLevel().registryAccess());
        for (var key : List.of("inv", "singularity_quantum_inventory", "singularity_duplication_inventory", "singularityDuplication", MachineEffectSettings.NBT_KEY))
            helper.assertTrue(expected.contains(key) && expected.get(key).equals(actual.get(key)), "Native singularity contents/settings must be exact for " + key);
        helper.assertTrue(ItemStack.matches(catalyst(), machine.getDuplicationInventory().getStackInSlot(0)) && machine.blackHoleCount() == 11
                && machine.getInternalInventory().getStackInSlot(0).is(Items.EMERALD) && machine.getInternalInventory().getStackInSlot(0).getCount() == 37,
                "Actual inventory slots must retain their samples, black holes and buffered recovery items");
        helper.assertTrue(machine.operation() == SingularityBlockEntity.Operation.IDLE && !machine.collectionActive() && !machine.embedRequested()
                && !machine.motion().hasBodies() && !machine.packageReclaimRequested() && !actual.contains("singularityPackageReclaimRequester"),
                "The deployed controller must be idle with no source reclaim request");
    }
    private static void assertSingularityEnergy(GameTestHelper helper, SingularityBlockEntity machine, double sealedEnergy) {
        double current = machine.saveWithFullMetadata(helper.getLevel().registryAccess()).getCompound("proxy").getDouble("e");
        helper.assertTrue(current > 0 && current <= sealedEnergy, "Real AE buffered power must survive relocation, allowing normal idle consumption");
    }
    private static UUID assertSingularityArchive(GameTestHelper helper, ServerPlayer player, BlockPos source, Direction facing,
            int version, CompoundTag contents, double energy) {
        var id = assertSealedPackage(helper, player, BuildingType.EVENT_HORIZON_SINGULARITY);
        try {
            var store = BuildingArchiveStore.forServer(helper.getLevel().getServer());
            var layout = store.readLayout(id, helper.getLevel().registryAccess());
            var indexed = layout.stream().collect(java.util.stream.Collectors.toMap(BuildingArchiveStore.Entry::offset, BuildingArchiveStore.Entry::state));
            var compatibility = singularityCompatibilityNodes(BlockPos.ZERO, Direction.NORTH, version);
            helper.assertTrue(layout.size() == SingularityStructure.parts(version).size() + SingularityStructure.requiredAir().size() + compatibility.size()
                    && manifest(helper, id).getInt("blocks") == SingularityStructure.parts(version).size() + compatibility.size(),
                    "The actual native archive must include its versioned parts, required air and compatibility nodes exactly once");
            for (var part : SingularityStructure.parts(version)) {
                var offset = SingularityStructure.worldPos(BlockPos.ZERO, Direction.NORTH, part, version);
                helper.assertTrue(indexed.containsKey(offset) && SingularityStructure.matches(indexed.get(offset), part, Direction.NORTH), "Archive must contain every normalized native part");
            }
            for (var air : SingularityStructure.requiredAir()) {
                var offset = SingularityStructure.worldPos(BlockPos.ZERO, Direction.NORTH, air, version);
                helper.assertTrue(indexed.containsKey(offset) && indexed.get(offset).isAir(), "Native reserved air must enter the archive as actual AIR entries");
            }
            for (var offset : compatibility)
                helper.assertTrue(indexed.containsKey(offset) && indexed.get(offset).is(SingularityContent.COLLECTION_NODE.get()), "Compatibility nodes must be archived with native geometry");
            for (var entry : layout)
                helper.assertTrue(helper.getLevel().getBlockState(source.offset(entry.offset().rotate(BuildingType.rotation(facing)))).isAir(), "Sealing must clear every archived source cell");
            int controller = java.util.stream.IntStream.range(0, layout.size()).filter(i -> layout.get(i).offset().equals(BlockPos.ZERO)).findFirst().orElseThrow();
            var data = store.readEntry(id, controller, helper.getLevel().registryAccess()).data();
            helper.assertTrue(data != null && !data.contains("singularityPackageReclaimRequester"), "The source pending marker must never enter the sealed controller snapshot");
            if (contents != null) {
                for (var key : List.of("inv", "singularity_quantum_inventory", "singularity_duplication_inventory", "singularityDuplication", MachineEffectSettings.NBT_KEY))
                    helper.assertTrue(contents.get(key).equals(data.get(key)), "Disk archive must preserve the native serializer output for " + key);
                helper.assertTrue(data.getCompound("proxy").getDouble("e") == energy, "Disk archive must retain the exact native AE energy share at sealing");
            }
        } catch (IOException error) { throw new RuntimeException(error); }
        return id;
    }

    @GameTest(template = "transfer_site_tall", timeoutTicks = 3000)
    public static void stoppedSingularitySealResumesPendingDockingAfterNativeReload(GameTestHelper helper) {
        singularityDockingReclaim(helper, false);
    }
    @GameTest(template = "transfer_site_tall", timeoutTicks = 3000)
    public static void singularityMenuCancelAfterNativeReloadKeepsDockingWithoutArchiving(GameTestHelper helper) {
        singularityDockingReclaim(helper, true);
    }
    private static void singularityDockingReclaim(GameTestHelper helper, boolean cancel) {
        var level = helper.getLevel(); var player = testPlayer(helper); int version = SingularityStructure.VERSION;
        var origin = singularityOrigin(helper, version, Direction.NORTH);
        createSingularitySource(level, player, origin, version);
        var machine = new SingularityBlockEntity[]{(SingularityBlockEntity) level.getBlockEntity(origin)};
        var chunks = com.atir.molecularmanipulator.world.MultiblockChunkLoading.rectangle(
                machine[0].worldPos(new BlockPos(-50, 0, -50)), machine[0].worldPos(new BlockPos(50, 128, 50)));
        var forced = new java.util.LinkedHashMap<net.minecraft.world.level.ChunkPos, Boolean>();
        for (var chunk : chunks) {
            forced.put(chunk, level.getForcedChunks().contains(chunk.toLong()));
            level.setChunkForced(chunk.x, chunk.z, true);
        }
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            private void release() {
                if (machine[0] != null) machine[0].motion().discardBodies();
                forced.forEach((chunk, wasForced) -> level.setChunkForced(chunk.x, chunk.z, wasForced));
            }
            @Override public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo test) {}
            @Override public void testPassed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { release(); }
            @Override public void testFailed(net.minecraft.gametest.framework.GameTestInfo test, net.minecraft.gametest.framework.GameTestRunner runner) { release(); }
            @Override public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo oldTest, net.minecraft.gametest.framework.GameTestInfo newTest, net.minecraft.gametest.framework.GameTestRunner runner) {}
        });
        var power = origin.above();
        helper.assertTrue(level.getBlockState(power).isAir(), "The fixture's real AE power source must occupy unused space");
        level.setBlock(power, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), 2 | 16);
        var obstruction = machine[0].worldPos(SingularityMotionGeometry.group(3).parts().getFirst());
        var motion = new CompoundTag[1]; var archive = new UUID[1];
        var sequence = helper.startSequence().thenWaitUntil(() -> {
            assertNativeSingularity(helper, player, origin, Direction.NORTH, version);
            helper.assertTrue(machine[0].networkOnline() && chunks.stream().allMatch(chunk -> level.areEntitiesLoaded(chunk.toLong())),
                    "Normal AE initialization, powered channels and all swept entity chunks must be ready");
        }).thenExecute(() -> {
            machine[0].toggleCollection(player);
            helper.assertTrue(machine[0].collectionActive() && machine[0].motion().mode() == SingularityMotionState.RUNNING
                    && singularityBodyCount(level, origin) == SingularityMotionGeometry.GROUP_COUNT, "Real collection must convert the actual blueprint into eleven world bodies");
            helper.assertTrue(!machine[0].canSealPackage() && !BuildingTransfer.seal(player, machine[0]), "Active collection must reject immediate sealing");
            machine[0].startDismantle(player);
            helper.assertTrue(!machine[0].packageReclaimRequested() && machine[0].collectionActive(), "The new reclaim entry must also reject active collection");
        }).thenIdle(10).thenExecute(() -> {
            helper.assertTrue(machine[0].motion().hasBodies() && machine[0].collectionActive(), "Stop an actual running collection session");
            level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 2 | 16);
            machine[0].toggleCollection(player);
            helper.assertTrue(!machine[0].collectionActive() && machine[0].motion().mode() == SingularityMotionState.DOCKING,
                    "Stopping collection must retain bodies and begin docking");
            var stopped = machine[0].motion().save();
            helper.assertTrue(stopped.getBoolean("dockingFrozen"), "A real obstruction in a tower's home cell must keep docking pending");
            helper.assertTrue(BuildingTransfer.seal(player, machine[0]), "Static seal must adopt stopped bodies as a persistent package request");
            helper.assertTrue(machine[0].packageReclaimRequested() && !machine[0].canSealPackage()
                    && machine[0].motion().save().getLong("dockingElapsed") == stopped.getLong("dockingElapsed"),
                    "Adopting the existing dock must neither restart its pose nor snapshot moving bodies");
            var saved = machine[0].saveWithFullMetadata(level.registryAccess());
            helper.assertTrue(player.getUUID().equals(saved.getUUID("singularityPackageReclaimRequester")), "Pending reclaim must persist its authorized requester");
            motion[0] = saved.getCompound("singularityMotion").copy();
            var state = machine[0].getBlockState();
            level.removeBlockEntity(origin);
            machine[0] = (SingularityBlockEntity) net.minecraft.world.level.block.entity.BlockEntity.loadStatic(origin, state, saved, level.registryAccess());
            helper.assertTrue(machine[0] != null && machine[0].packageReclaimRequested(), "Native BE reload must recover the pending package request");
            level.setBlockEntity(machine[0]);
        }).thenIdle(20).thenExecute(() -> {
            var savedMotion = machine[0].motion().save();
            helper.assertTrue(machine[0].packageReclaimRequested() && machine[0].operation() == SingularityBlockEntity.Operation.IDLE
                    && !machine[0].collectionActive() && machine[0].motion().mode() == SingularityMotionState.DOCKING
                    && savedMotion.getUUID("token").equals(motion[0].getUUID("token"))
                    && java.util.Arrays.equals(savedMotion.getIntArray("ownedMaterials"), motion[0].getIntArray("ownedMaterials"))
                    && singularityBodyCount(level, origin) == SingularityMotionGeometry.GROUP_COUNT,
                    "Normal server ticks must preserve pending docking, native ownership and exactly eleven bodies after reload");
            helper.assertTrue(player.getInventory().items.stream().allMatch(ItemStack::isEmpty), "Blocked docking must not deliver an early package or loose materials");
            clickBlock(helper, player, origin);
            helper.assertTrue(player.containerMenu instanceof SingularityMenu, "Pending recovery must still open the real server menu");
            var menu = (SingularityMenu) player.containerMenu; menu.broadcastChanges();
            helper.assertTrue(menu.packageReclaimRequested && menu.movingBodies && menu.motionMode == SingularityMotionState.DOCKING,
                    "The real menu must synchronize the recovered pending request and docking bodies");
            menu.receiveClientAction("build", ""); menu.receiveClientAction("collection", ""); menu.receiveClientAction("dismantle", "");
            helper.assertTrue(machine[0].packageReclaimRequested() && machine[0].operation() == SingularityBlockEntity.Operation.IDLE && !machine[0].collectionActive(),
                    "Pending reclaim must reject competing menu actions");
            if (cancel) {
                var stranger = testPlayer(helper); stranger.setPos(origin.getCenter());
                machine[0].cancel(stranger);
                helper.assertTrue(machine[0].packageReclaimRequested(), "Another nearby player cannot cancel the persisted requester's archive");
                menu.receiveClientAction("cancel", "");
                helper.assertTrue(!machine[0].packageReclaimRequested() && machine[0].motion().mode() == SingularityMotionState.DOCKING
                        && !machine[0].saveWithFullMetadata(level.registryAccess()).contains("singularityPackageReclaimRequester"),
                        "The real cancel action must clear eventual archival while retaining physical docking");
            }
            level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 2 | 16);
        });
        if (cancel) {
            sequence.thenWaitUntil(() -> {
                helper.assertTrue(!machine[0].motion().hasBodies() && singularityBodyCount(level, origin) == 0, "Cancelled reclaim must finish docking and remove every derived body");
                assertNativeSingularity(helper, player, origin, Direction.NORTH, version);
            }).thenIdle(40).thenExecute(() -> {
                helper.assertTrue(level.getBlockEntity(origin) == machine[0] && machine[0].canSealPackage()
                        && player.getInventory().items.stream().allMatch(ItemStack::isEmpty), "Cancelled eventual archival must stay cancelled after the real structure reforms");
            }).thenSucceed();
        } else {
            var target = singularityOrigin(helper, version, Direction.WEST);
            sequence.thenWaitUntil(() -> helper.assertTrue(level.getBlockState(origin).isAir() && BuildingPackage.archive(player.getInventory().getItem(0)) != null,
                    "The recovered request must archive automatically only after normal docking completes"))
                    .thenExecute(() -> {
                        helper.assertTrue(!machine[0].motion().hasBodies() && singularityBodyCount(level, origin) == 0, "Reclaim must leave no derived bodies or material ownership");
                        archive[0] = assertSingularityArchive(helper, player, origin, Direction.NORTH, version, null, 0);
                        level.setBlock(power, Blocks.AIR.defaultBlockState(), 2 | 16);
                        helper.assertTrue(BuildingTransfer.deploy(player, 0, target.below(SingularityStructure.CONTROLLER.getY()), Direction.WEST), "A recovered docking archive must deploy after rotation");
                    }).thenWaitUntil(() -> assertNativeSingularity(helper, player, target, Direction.WEST, version))
                    .thenIdle(40).thenExecute(() -> {
                        var deployed = (SingularityBlockEntity) level.getBlockEntity(target);
                        helper.assertTrue(deployed != null && !deployed.packageReclaimRequested() && deployed.canSealPackage()
                                && player.getInventory().items.stream().allMatch(ItemStack::isEmpty), "The recovered source marker must never cause a deployed controller to reclaim again");
                        assertState(helper, archive[0], "DEPLOYED");
                        assertSingularityBlueprint(helper, target, Direction.WEST);
                    }).thenSucceed();
        }
    }
    private static long singularityBodyCount(ServerLevel level, BlockPos controller) {
        return SingularityMotionWorld.bodies(level).stream().filter(body -> body.configured() && !body.isRemoved() && body.controller().equals(controller)).count();
    }

    private static void assertSingularityBlueprint(GameTestHelper helper, BlockPos origin, Direction facing) {
        var level = helper.getLevel(); var machine = (SingularityBlockEntity) level.getBlockEntity(origin);
        helper.assertTrue(machine.structureVersion() == SingularityStructure.VERSION && machine.inspection().formed(), "The package must form the current suspended layout");
        for (var part : SingularityStructure.parts())
            helper.assertTrue(SingularityStructure.matches(level.getBlockState(SingularityStructure.worldPos(origin, facing, part)), part, facing),
                    "Every real singularity member must match the native blueprint");
        for (var air : SingularityStructure.requiredAir())
            helper.assertTrue(level.getBlockState(SingularityStructure.worldPos(origin, facing, air)).isAir(), "The light well must remain empty");
        helper.assertTrue(!machine.isSingleBlock() && !machine.collectionActive() && !machine.motion().hasBodies(), "Fresh construction must await the player's collection command");
        helper.assertTrue(machine.getQuantumInventory().isEmpty() && machine.getDuplicationInventory().isEmpty(), "Complete construction must not mint production supplies");
    }

    @GameTest(template = "transfer_site")
    public static void foldingAssemblerPublishesSevenCompleteBillsWithUnclampedCounts(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(48, 4, 48));
        level.setBlock(pos, BuildingContent.ASSEMBLER.get().defaultBlockState(), 2 | 16);
        var assembler = (FoldingAssemblerBlockEntity) level.getBlockEntity(pos);
        var patterns = assembler.getAvailablePatterns();
        helper.assertTrue(patterns.size() == 7 && patterns.size() == BuildingType.orderable().size(), "ME must publish all seven orderable packages, never the old cube");
        helper.assertTrue(BuildingType.orderable().equals(List.of(BuildingType.FABRICATION_WELL, BuildingType.SEQUENCE_ARRAY,
                BuildingType.OMNI_COMPUTATION, BuildingType.EVENT_HORIZON_SINGULARITY,
                BuildingType.STAR_ORRERY_L1, BuildingType.STAR_ORRERY_L2, BuildingType.STAR_ORRERY_L3)),
                "Only the original seven complete packages may be ordered");
        for (int rings = 0; rings <= 9; rings++) {
            var grade = BuildingType.starOrrery(rings);
            helper.assertTrue(grade.isStarOrrery() && grade.orreryLevel() == rings && grade.id.equals("star_orrery_l" + rings)
                    && BuildingType.byId(grade.id) == grade, "Every actual grade L0-L9 must have a stable, exact archive identity");
            if (rings >= 1 && rings <= 3) continue;
            boolean rejected = false;
            try { grade.template(); } catch (IllegalStateException expected) { rejected = true; }
            helper.assertTrue(rejected, "Archive-only grades must not expose free construction templates");
        }
        var actualTypes = new java.util.HashSet<BuildingType>();
        for (var pattern : patterns) {
            var output = pattern.getPrimaryOutput();
            var stack = ((AEItemKey) output.what()).toStack(); var type = BuildingPackage.type(stack);
            helper.assertTrue(type != null && BuildingType.orderable().contains(type) && actualTypes.add(type), "Every ME output must have a distinct orderable identity");
            helper.assertTrue(output.amount() == 1 && BuildingPackage.archive(stack) == null && BuildingPackage.reservation(stack) == null,
                    "ME must output one fresh package, not an archive or a controller");
            var costs = new java.util.LinkedHashMap<AEItemKey, Long>();
            for (var input : pattern.getInputs()) {
                var alternatives = input.getPossibleInputs();
                helper.assertTrue(alternatives.length == 1 && alternatives[0].what() instanceof AEItemKey, "Complete bills cannot substitute cheaper materials");
                costs.merge((AEItemKey) alternatives[0].what(), Math.multiplyExact(alternatives[0].amount(), input.getMultiplier()), Math::addExact);
            }
            helper.assertTrue(costs.equals(type.materials()), "Native AE processing pattern must retain the complete bill for " + type);
            if (type == BuildingType.EVENT_HORIZON_SINGULARITY) {
                var recipe = com.atir.molecularmanipulator.integration.jei.SingularityStructureJeiRecipe.recipe();
                var jeiCosts = new java.util.LinkedHashMap<AEItemKey, Long>();
                for (var material : recipe.materials()) jeiCosts.merge(AEItemKey.of(material), (long) material.getCount(), Math::addExact);
                helper.assertTrue(jeiCosts.equals(costs), "JEI must display the same complete singularity bill as ME, without stack-size clamping");
                helper.assertTrue(costs.get(AEItemKey.of(SingularityContent.CONTROLLER.get().asItem())) == 1L,
                        "The singularity package must include exactly one multiblock controller");
                helper.assertTrue(BuildingPackage.type(recipe.output()) == type && BuildingPackage.archive(recipe.output()) == null,
                        "The singularity JEI output must be its fresh construction package");
            }
            if (type.isStarOrrery()) {
                assertOrreryTemplate(helper, type);
                helper.assertTrue(Long.valueOf(22).equals(costs.get(AEItemKey.of(ModContent.STAR_COMPUTATION_ENGINE_CASING_ITEM.get()))),
                        "Every orrery ME bill must include exactly 22 central casings");
                helper.assertTrue(Long.valueOf(4).equals(costs.get(AEItemKey.of(ModContent.STAR_COMPUTATION_OBSERVATION_PORT_ITEM.get()))),
                        "Every orrery ME bill must include exactly four central observation ports");
                helper.assertTrue(Long.valueOf(1).equals(costs.get(AEItemKey.of(ModContent.STAR_COMPUTATION_ENGINE_CORE_ITEM.get()))),
                        "Every orrery ME bill must include exactly one core");
                helper.assertTrue(costs.get(AEItemKey.of(ModContent.STAR_ORRERY_ENGRAVED_PLINTH_ITEM.get())) == 520L,
                        "The 520 engraved blocks must not be clamped to one inventory stack");
                helper.assertTrue(costs.get(AEItemKey.of(ModContent.STAR_ORRERY_GILDED_PLINTH_ITEM.get())) == 408L,
                        "The 408 gilded blocks must not be clamped to one inventory stack");
            }
        }
        helper.assertTrue(actualTypes.equals(new java.util.HashSet<>(BuildingType.orderable())), "ME package identities must match creative and JEI orderable types");
        helper.succeed();
    }

    @GameTest(template = "transfer_site", timeoutTicks = 400)
    public static void legacyCubeCannotGrantBenefitsAndMigratesWithoutDeletingContents(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        var base = helper.absolutePos(new BlockPos(48, 4, 48));
        var origin = base.above(37);
        var legacyCore = ModContent.STAR_COMPUTATION_ENGINE_CORE.get().defaultBlockState()
                .setValue(StarComputationEngineCoreBlock.STAR_ORRERY, false).setValue(StarComputationEngineCoreBlock.FORMED, true);
        level.setBlock(origin, legacyCore, 2 | 16);
        var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
        var data = core.saveWithFullMetadata(level.registryAccess());
        data.putBoolean("formed", true); data.putInt("entropy_unlocks", 5);
        data.putLong("entropy_refund", 17); data.putUUID("entropy_team", player.getUUID());
        core.loadWithComponents(data, level.registryAccess());
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (pos.equals(origin)) continue;
            level.setBlock(pos, orreryCentralBlock(pos.subtract(origin)).defaultBlockState()
                    .setValue(StarComputationEngineCoreBlock.FORMED, true)
                    .setValue(StarComputationEngineCasingBlock.INVISIBLE, true), 2 | 16);
            assertDoesNotProxy(helper, player, pos);
        }
        var column = origin.north(2);
        level.setBlock(column, ModContent.REFERENCE_STAR_POSITION_ANCHOR.get().defaultBlockState()
                .setValue(StarPositionAnchorBlock.FORMED, true).setValue(StarPositionAnchorBlock.STAR_ORRERY, false), 2 | 16);
        helper.assertTrue(!core.isFormed() && core.getBenefits().level() == 0 && core.getEffectiveEntropy() == 0,
                "Even a complete 22-casing/four-port legacy cube cannot grant benefits without the real foundation");
        assertDoesNotProxy(helper, player, column);
        player.getInventory().setItem(0, BuildingPackage.fresh(BuildingType.STAR_COMPUTATION_ENGINE));
        helper.assertTrue(!BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "A cheap retired cube must not expand into a free orrery");
        helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) == null && !BuildingTransfer.locked(level, origin),
                "Retired fresh packages must be rejected before reservation or region locking");
        // Legacy anchor integrity checks run after 40..49 ticks, independently of the core's 10-tick refresh.
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            helper.assertTrue(core.isStarOrrery() && !core.isFormed() && core.getBenefits().level() == 0
                    && core.getEffectiveEntropy() == 0 && core.getAnchorCount() == 0, "Only a real foundation can form the migrated core");
            helper.assertTrue(core.getUnlockMask() == 5 && player.getUUID().equals(core.getBoundTeam()), "Migration must preserve research and ownership");
            helper.assertTrue(core.saveWithFullMetadata(level.registryAccess()).getLong("entropy_refund") == 17, "Migration must preserve native refund debt");
            assertOrreryCentralBody(helper, origin, Direction.NORTH, false);
            for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1)))
                if (!pos.equals(origin)) assertDoesNotProxy(helper, player, pos);
            helper.assertTrue(!level.getBlockState(column).getValue(StarPositionAnchorBlock.FORMED), "Old columns must lose stale formation flags");
            assertDoesNotProxy(helper, player, column);
            // Keep all 22 original casings, all four original ports and the original BE/data in place.
            for (var entry : BuildingType.STAR_ORRERY_L1.template())
                if (orreryCentralBlock(entry.offset()) == null) level.setBlock(origin.offset(entry.offset()), entry.state(), 2 | 16);
        }).thenWaitUntil(() -> {
            helper.assertTrue(core.isFormed() && core.getLitRings() == 1 && core.getBenefits().level() == 1,
                    "Migrated cores must accept the real foundation, legal central body and first ring automatically");
            assertOrreryCentralBody(helper, origin, Direction.NORTH, true);
            helper.assertTrue(core.foldingMembers().size() == BuildingType.STAR_ORRERY_L1.template().size()
                    && !core.foldingMembers().contains(column), "Legal central members must be captured, but retired columns must remain excluded");
        }).thenExecute(() -> {
            helper.assertTrue(core.getUnlockMask() == 5 && player.getUUID().equals(core.getBoundTeam())
                    && core.saveWithFullMetadata(level.registryAccess()).getLong("entropy_refund") == 17,
                    "Completing the real template must retain migrated research, team and refund debt");
            helper.assertTrue(!level.getBlockState(column).getValue(StarPositionAnchorBlock.FORMED)
                    && core.getAnchorCount() == 24, "Old columns must stay unformed and contribute no anchors or benefits");
            assertDoesNotProxy(helper, player, column);
        }).thenSucceed();
    }

    @GameTest(template = "transfer_site", timeoutTicks = 600)
    public static void orreryCentralMembersAreRequiredAndRepairsReformAutomatically(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.STAR_ORRERY_L1;
        var origin = BuildingTransfer.controllerAtBase(type.template(), helper.absolutePos(new BlockPos(48, 4, 48)));
        var casing = origin.offset(1, 1, 1); var port = origin.north();
        createSource(level, player, type, origin);
        helper.startSequence().thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> {
                    // Replacing between scans can leave the correct materials with loose visual states.
                    level.setBlock(casing, ModContent.STAR_COMPUTATION_ENGINE_CASING.get().defaultBlockState(), 2 | 16);
                    level.setBlock(port, ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get().defaultBlockState(), 2 | 16);
                }).thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> level.setBlock(casing, Blocks.AIR.defaultBlockState(), 3))
                .thenWaitUntil(() -> assertUnformedOrrery(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> level.setBlock(casing, ModContent.STAR_COMPUTATION_ENGINE_CASING.get().defaultBlockState(), 3))
                .thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> level.setBlock(port, Blocks.AIR.defaultBlockState(), 3))
                .thenWaitUntil(() -> assertUnformedOrrery(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> level.setBlock(port, ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get().defaultBlockState(), 3))
                .thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> {
                    // Preserve the 22+4 bill: correct counts with the two materials swapped are still invalid.
                    level.setBlock(casing, ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get().defaultBlockState(), 3);
                    level.setBlock(port, ModContent.STAR_COMPUTATION_ENGINE_CASING.get().defaultBlockState(), 3);
                }).thenWaitUntil(() -> assertUnformedOrrery(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> {
                    level.setBlock(casing, ModContent.STAR_COMPUTATION_ENGINE_CASING.get().defaultBlockState(), 3);
                    level.setBlock(port, ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get().defaultBlockState(), 3);
                }).thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH)).thenSucceed();
    }

    @GameTest(template = "transfer_site", timeoutTicks = 500)
    public static void orreryNeedsItsCentralBodyAndOnlyLegalMembersProxyTheCore(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.STAR_ORRERY_L1;
        var origin = BuildingTransfer.controllerAtBase(type.template(), helper.absolutePos(new BlockPos(48, 4, 48)));
        // A real foundation and complete first ring, but no central members other than the core itself.
        for (var entry : type.template())
            if (entry.offset().equals(BlockPos.ZERO) || orreryCentralBlock(entry.offset()) == null)
                level.setBlock(origin.offset(entry.offset()), entry.state(), 2 | 16);
        var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
        core.getMainNode().setOwningPlayer(player);
        var looseCasing = origin.offset(2, 0, 1); var loosePort = origin.offset(2, 0, -1); var column = origin.north(2);
        level.setBlock(looseCasing, ModContent.STAR_COMPUTATION_ENGINE_CASING.get().defaultBlockState()
                .setValue(StarComputationEngineCoreBlock.FORMED, true).setValue(StarComputationEngineCasingBlock.INVISIBLE, true), 2 | 16);
        level.setBlock(loosePort, ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get().defaultBlockState()
                .setValue(StarComputationEngineCoreBlock.FORMED, true).setValue(StarComputationEngineCasingBlock.INVISIBLE, true), 2 | 16);
        level.setBlock(column, ModContent.REFERENCE_STAR_POSITION_ANCHOR.get().defaultBlockState()
                .setValue(StarPositionAnchorBlock.FORMED, true).setValue(StarPositionAnchorBlock.STAR_ORRERY, false), 2 | 16);
        helper.startSequence().thenIdle(60).thenExecute(() -> {
            for (var entry : type.template()) if (orreryCentralBlock(entry.offset()) == null)
                helper.assertTrue(level.getBlockState(origin.offset(entry.offset())).is(entry.state().getBlock()),
                        "The negative case must retain every physical foundation and ring block");
            assertUnformedOrrery(helper, player, type, origin, Direction.NORTH);
            for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1)))
                if (!offset.equals(BlockPos.ZERO)) helper.assertTrue(level.getBlockState(origin.offset(offset)).isAir(), "The negative case must contain only the core, foundation and ring");
            helper.assertTrue(!level.getBlockState(column).getValue(StarPositionAnchorBlock.FORMED), "Retired columns must shed saved formed flags without granting benefits");
            for (var pos : List.of(looseCasing, loosePort, column)) assertDoesNotProxy(helper, player, pos);
            for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1)))
                if (!offset.equals(BlockPos.ZERO)) level.setBlock(origin.offset(offset), orreryCentralBlock(offset).defaultBlockState(), 3);
        }).thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH)).thenExecute(() -> {
            var node = core.getMainNode().getNode();
            helper.assertTrue(node != null, "The formed core must initialize its real AE node");
            for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))) {
                if (offset.equals(BlockPos.ZERO)) continue;
                var pos = origin.offset(offset);
                helper.assertTrue(StarComputationEngineCoreBlockEntity.findCore(level, pos) == core, "Every legal central member must resolve the same core at " + pos);
                var host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
                helper.assertTrue(host != null, "Every one of the 26 central members must expose the real grid-host capability at " + pos);
                for (var side : Direction.values())
                    helper.assertTrue(host.getGridNode(side) == node, "Every member and side must expose the main core's identical AE node at " + pos);
                assertOpensMenu(helper, player, type, pos, origin);
            }
            for (var pos : List.of(looseCasing, loosePort, column)) {
                assertDoesNotProxy(helper, player, pos);
                helper.assertTrue(!core.foldingMembers().contains(pos), "Loose parts and retired columns cannot enter the folded footprint");
            }
            helper.assertTrue(!level.getBlockState(column).getValue(StarPositionAnchorBlock.FORMED)
                    && core.getBenefits().level() == 1 && core.getAnchorCount() == 24, "Only the real ring may grant benefits; the old column must remain unformed");
        }).thenSucceed();
    }

    // Archive-only grades have no construction template. Build real fixtures from the L3 center/base
    // and the native ring positions, including a complete foundation with no anchors for L0.
    private static List<BuildingArchiveStore.Entry> orreryTestLayout(int rings) {
        var entries = new ArrayList<BuildingArchiveStore.Entry>();
        for (var entry : BuildingType.STAR_ORRERY_L3.template())
            if (!(entry.state().getBlock() instanceof StarPositionAnchorBlock)) entries.add(entry);
        for (int ring = 0; ring < rings; ring++) for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++)
            entries.add(new BuildingArchiveStore.Entry(StarOrreryLayout.offset(Direction.NORTH, ring, slot), orreryAnchor(ring).defaultBlockState(), null));
        return entries;
    }
    private static Block orreryAnchor(int ring) {
        return switch (StarOrreryLayout.tierOf(ring)) {
            case 0 -> ModContent.REFERENCE_STAR_POSITION_ANCHOR.get();
            case 1 -> ModContent.QUANTUM_STAR_POSITION_ANCHOR.get();
            default -> ModContent.TERMINAL_STAR_POSITION_ANCHOR.get();
        };
    }
    private static void seedOrreryContents(ServerLevel level, ServerPlayer player, BlockPos origin) {
        var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
        var data = core.saveWithFullMetadata(level.registryAccess());
        var proxy = data.getCompound("proxy"); proxy.putInt("p", IPlayerRegistry.getPlayerId(player)); data.put("proxy", proxy);
        data.putInt("entropy_unlocks", 5); data.putLong("entropy_refund", 17); data.putUUID("entropy_team", player.getUUID());
        core.loadWithComponents(data, level.registryAccess());
    }
    private static void assertOrreryContents(GameTestHelper helper, ServerPlayer player, BlockPos origin) {
        var core = (StarComputationEngineCoreBlockEntity) helper.getLevel().getBlockEntity(origin);
        helper.assertTrue(core.getUnlockMask() == 5 && player.getUUID().equals(core.getBoundTeam())
                && core.saveWithFullMetadata(helper.getLevel().registryAccess()).getLong("entropy_refund") == 17,
                "Native research, team and unpaid information refund must survive both relocations");
    }
    private static UUID assertSealedPackage(GameTestHelper helper, ServerPlayer player, BuildingType expected) {
        var stack = player.getInventory().getItem(0); var id = BuildingPackage.archive(stack);
        helper.assertTrue(BuildingPackage.type(stack) == expected && id != null && BuildingPackage.reservation(stack) == null && stack.getCount() == 1,
                "Reclaim must deliver one usable archive of the actual server grade " + expected);
        helper.assertTrue(player.getInventory().items.stream().filter(item -> BuildingPackage.type(item) != null).mapToInt(ItemStack::getCount).sum() == 1,
                "Reclaim must not create duplicate package handles");
        var saved = manifest(helper, id);
        helper.assertTrue(saved.getString("type").equals(expected.id), "The persisted archive identity must agree with the actual-grade item");
        if (expected.isStarOrrery()) helper.assertTrue(saved.getString("type").equals("star_orrery_l" + expected.orreryLevel()),
                "New orrery archives must name their exact grade, including L0 and L4-L9");
        assertState(helper, id, "SEALED");
        return id;
    }

    // Independent 3x3x3 oracle: four horizontal face centers are ports; all other non-core cells are casings.
    // Do not derive expected materials from the production template/structure that these tests validate.
    private static Block orreryCentralBlock(BlockPos offset) {
        if (Math.abs(offset.getX()) > 1 || Math.abs(offset.getY()) > 1 || Math.abs(offset.getZ()) > 1) return null;
        if (offset.equals(BlockPos.ZERO)) return ModContent.STAR_COMPUTATION_ENGINE_CORE.get();
        return offset.getY() == 0 && Math.abs(offset.getX()) + Math.abs(offset.getZ()) == 1
                ? ModContent.STAR_COMPUTATION_OBSERVATION_PORT.get() : ModContent.STAR_COMPUTATION_ENGINE_CASING.get();
    }
    private static void assertOrreryTemplate(GameTestHelper helper, BuildingType type) {
        int expected = switch (type) {
            case STAR_ORRERY_L1 -> 979; case STAR_ORRERY_L2 -> 1011; case STAR_ORRERY_L3 -> 1051;
            default -> throw new IllegalArgumentException("Not an orderable orrery: " + type);
        };
        helper.assertTrue(type.template().size() == expected, "Each orrery template must add all 26 central members to the former core-only blueprint: " + type);
        helper.assertTrue(type.template().stream().map(BuildingArchiveStore.Entry::offset).distinct().count() == expected, "Every orrery cell must be unique");
        var indexed = type.template().stream().collect(java.util.stream.Collectors.toMap(BuildingArchiveStore.Entry::offset, BuildingArchiveStore.Entry::state));
        for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))) {
            var state = indexed.get(offset);
            helper.assertTrue(state != null && state.is(orreryCentralBlock(offset)), "The north-authored template must contain the exact central material at " + offset);
            helper.assertTrue(state.getValue(HorizontalDirectionalBlock.FACING) == Direction.NORTH && !state.getValue(StarComputationEngineCoreBlock.FORMED),
                    "Fresh central cells must start unformed and facing north at " + offset);
            if (!offset.equals(BlockPos.ZERO)) helper.assertTrue(!state.getValue(StarComputationEngineCasingBlock.INVISIBLE), "Fresh central members must start visible");
        }
    }
    private static void assertOrreryCentralBody(GameTestHelper helper, BlockPos origin, Direction facing, boolean formed) {
        var level = helper.getLevel(); var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
        helper.assertTrue(core != null && core.isStarOrrery(), "The central body must retain the orrery core");
        var members = formed ? core.foldingMembers() : java.util.Set.<BlockPos>of();
        for (var offset : BlockPos.betweenClosed(new BlockPos(-1, -1, -1), new BlockPos(1, 1, 1))) {
            var pos = origin.offset(offset.rotate(BuildingType.rotation(facing))); var state = level.getBlockState(pos);
            helper.assertTrue(state.is(orreryCentralBlock(offset)), "Central material must be exact (22 casings, four ports, one core) at " + pos);
            helper.assertTrue(state.getValue(StarComputationEngineCoreBlock.FORMED) == formed, "Every central cell must automatically update its formed flag at " + pos);
            helper.assertTrue(state.getValue(HorizontalDirectionalBlock.FACING) == facing, "Every central cell's facing must survive rotation at " + pos);
            if (!offset.equals(BlockPos.ZERO)) {
                helper.assertTrue(state.getValue(StarComputationEngineCasingBlock.INVISIBLE) == formed, "Central members must be hidden only while formed at " + pos);
                if (formed) helper.assertTrue(members.contains(pos), "All 26 legal central members must be in the folded footprint at " + pos);
            }
        }
    }
    private static void assertOrreryCentralCleared(GameTestHelper helper, BlockPos origin) {
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1)))
            helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Sealing must remove the core and all 26 central members, leaving no material behind at " + pos);
    }
    private static void assertUnformedOrrery(GameTestHelper helper, ServerPlayer player, BuildingType type, BlockPos origin, Direction facing) {
        var level = helper.getLevel(); var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
        helper.assertTrue(core != null && !core.isFormed() && !level.getBlockState(origin).getValue(StarComputationEngineCoreBlock.FORMED),
                "Missing or wrong central materials must invalidate formation automatically");
        helper.assertTrue(core.getBenefits().level() == 0 && core.getLitRings() == 0 && core.getEffectiveEntropy() == 0
                && core.getAnchorCount() == 0 && core.getInfoConsumption() == 0, "An incomplete central body must grant no benefits or entropy");
        // Validate every automatic state update before any interaction can trigger a production recheck.
        var members = new ArrayList<BlockPos>();
        for (var pos : BlockPos.betweenClosed(origin.offset(-1, -1, -1), origin.offset(1, 1, 1))) {
            if (pos.equals(origin)) continue;
            var state = level.getBlockState(pos);
            if (state.hasProperty(StarComputationEngineCasingBlock.INVISIBLE))
                helper.assertTrue(!state.getValue(StarComputationEngineCoreBlock.FORMED) && !state.getValue(StarComputationEngineCasingBlock.INVISIBLE),
                        "Remaining central members must automatically become visible and unformed at " + pos);
            members.add(pos.immutable());
        }
        for (var entry : type.template()) if (entry.state().getBlock() instanceof StarPositionAnchorBlock) {
            var pos = origin.offset(entry.offset().rotate(BuildingType.rotation(facing))); var state = level.getBlockState(pos);
            helper.assertTrue(state.is(entry.state().getBlock()) && !state.getValue(StarPositionAnchorBlock.FORMED), "The physical ring must remain present but unformed without a complete center");
            members.add(pos);
        }
        for (var pos : members) assertDoesNotProxy(helper, player, pos);
    }
    private static void assertDoesNotProxy(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        var level = helper.getLevel();
        helper.assertTrue(StarComputationEngineCoreBlockEntity.findCore(level, pos) == null, "Invalid, loose or retired members must not resolve a controller at " + pos);
        var host = level.getCapability(AECapabilities.IN_WORLD_GRID_NODE_HOST, pos, null);
        for (var side : Direction.values())
            helper.assertTrue(host == null || host.getGridNode(side) == null, "Invalid members must expose no AE node on any side at " + pos);
        clickBlock(helper, player, pos);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "Invalid members must not open a controller menu at " + pos);
    }

    private static void roundTrip(GameTestHelper helper, BuildingType type) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        var construction = type.isStarOrrery() ? orreryTestLayout(type.orreryLevel()) : type.template();
        var sourceBase = helper.absolutePos(new BlockPos(48, type.isStarOrrery() ? 2 : 4, 64));
        var targetBase = helper.absolutePos(new BlockPos(144, type.isStarOrrery() ? 2 : 4, 64));
        var source = BuildingTransfer.controllerAtBase(construction, sourceBase);
        var target = BuildingTransfer.controllerAtBase(construction, targetBase);
        var originalFacing = type.isStarOrrery() ? Direction.EAST : Direction.NORTH;
        createSource(level, player, type, source, originalFacing);
        if (type.isStarOrrery()) seedOrreryContents(level, player, source);
        var archive = new UUID[1];
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            helper.assertTrue(formed(level, source), "Source " + type + " must form before archiving");
            if (type.isStarOrrery()) assertOrreryCentralBody(helper, source, originalFacing, true);
            helper.assertTrue(BuildingTransfer.seal(player, level.getBlockEntity(source)), "Native source sealing must succeed");
            if (type.isStarOrrery()) assertOrreryCentralCleared(helper, source);
            archive[0] = assertSealedPackage(helper, player, type);
            helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, originalFacing), "Archived deployment must preserve the original facing");
        }).thenWaitUntil(() -> assertRestored(helper, player, type, target, originalFacing)).thenExecute(() -> {
            assertState(helper, archive[0], "DEPLOYED");
            assertOpensMenu(helper, player, type, target);
            if (type == BuildingType.FABRICATION_WELL) assertWellContents(helper, target, originalFacing);
            if (type.isStarOrrery()) assertOrreryContents(helper, player, target);
            helper.assertTrue(level.getBlockState(source).isAir(), "Source controller must stay removed");
            if (type.isStarOrrery()) assertOrreryCentralCleared(helper, source);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Only a completed deployment consumes the package");
            // These are the real GUI reclaim entry points, not a second call directly to the archive store.
            var restored = level.getBlockEntity(target);
            if (restored instanceof MatterFabricationBlockEntity well) well.startDismantle(player);
            else if (restored instanceof MolecularCenterBlockEntity array) array.startDismantle(player);
            else if (restored instanceof OmniComputationCoreBlockEntity omni) omni.startDismantle(player);
            else helper.assertTrue(BuildingTransfer.seal(player, restored), "Engine must be reclaimable again");
            if (type.isStarOrrery()) assertOrreryCentralCleared(helper, target);
            var reclaimed = assertSealedPackage(helper, player, type);
            helper.assertTrue(!archive[0].equals(reclaimed), "A second reclaim must issue a new archive while the first stays deployed");
            assertState(helper, archive[0], "DEPLOYED");
            helper.assertTrue(BuildingTransfer.deploy(player, 0, sourceBase, Direction.WEST), "Reclaimed package must support a second, rotated deployment");
        }).thenWaitUntil(() -> assertRestored(helper, player, type, source, Direction.WEST)).thenExecute(() -> {
            assertOpensMenu(helper, player, type, source);
            if (type == BuildingType.FABRICATION_WELL) assertWellContents(helper, source, Direction.WEST);
            if (type.isStarOrrery()) assertOrreryContents(helper, player, source);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Second successful deployment must consume exactly its own receipt");
            helper.assertTrue(level.getBlockState(target).isAir(), "Second reclaim must leave no duplicate controller");
            if (type.isStarOrrery()) assertOrreryCentralCleared(helper, target);
        }).thenSucceed();
    }

    private static void createSource(ServerLevel level, ServerPlayer player, BuildingType type, BlockPos origin) {
        createSource(level, player, type, origin, Direction.NORTH);
    }
    private static void createSource(ServerLevel level, ServerPlayer player, BuildingType type, BlockPos origin, Direction facing) {
        player.setPos(origin.getX(), origin.getY() + 2, origin.getZ());
        var rotation = BuildingType.rotation(facing);
        var construction = type.isStarOrrery() ? orreryTestLayout(type.orreryLevel()) : type.template();
        for (var entry : construction)
            level.setBlock(origin.offset(entry.offset().rotate(rotation)), BuildingType.rotateState(entry.state(), rotation), 2 | 16);
        ((IGridConnectedBlockEntity) level.getBlockEntity(origin)).getMainNode().setOwningPlayer(player);
        if (type == BuildingType.FABRICATION_WELL) {
            var pos = MatterFabricationStructure.worldPos(origin, facing, MatterFabricationStructure.patternAssemblyBays().getFirst());
            level.setBlock(pos, ModContent.MATTER_FABRICATION_PATTERN_ASSEMBLY.get().defaultBlockState(), 2 | 16);
            var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(pos);
            assembly.getMainNode().setOwningPlayer(player);
            assembly.getLogic().getPatternInv().setItemDirect(3, encodedPattern());
            assembly.getCatalystInventory().setItemDirect(7, catalyst());
            assembly.getLogic().setPriority(37);
        }
    }
    private static ItemStack encodedPattern() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 9)),
                List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 2)));
    }
    private static ItemStack catalyst() {
        var stack = new ItemStack(Items.DIAMOND_PICKAXE); stack.setDamageValue(23);
        var component = new CompoundTag(); component.putString("foreign_native_component", "retained-through-two-deployments");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(component)); return stack;
    }
    private static boolean formed(ServerLevel level, BlockPos origin) {
        var entity = level.getBlockEntity(origin);
        if (entity instanceof MatterFabricationBlockEntity well) return well.isStructureFormed();
        if (entity instanceof MolecularCenterBlockEntity array) return array.isFormed();
        if (entity instanceof OmniComputationCoreBlockEntity omni) return omni.isStructureFormed();
        if (entity instanceof SingularityBlockEntity singularity) return singularity.formed();
        return entity instanceof StarComputationEngineCoreBlockEntity engine && engine.isFormed();
    }
    private static void assertRestored(GameTestHelper helper, ServerPlayer player, BuildingType type, BlockPos origin, Direction facing) {
        var level = helper.getLevel();
        helper.assertTrue(!BuildingTransfer.locked(level, origin), "Completed " + type + " must unlock its controller");
        helper.assertTrue(formed(level, origin), "Restored " + type + " must form automatically, without manual refresh");
        if (type.isStarOrrery()) {
            var core = (StarComputationEngineCoreBlockEntity) level.getBlockEntity(origin);
            helper.assertTrue(core.getLitRings() == type.orreryLevel() && core.getBenefits().level() == type.orreryLevel(),
                    "The complete ring prefix and benefits must match " + type);
            assertOrreryCentralBody(helper, origin, facing, true);
            helper.assertTrue(core.foldingMembers().size() == orreryTestLayout(type.orreryLevel()).size(), "All real central, foundation and ring members must survive relocation");
            helper.assertTrue(BuildingType.forController(core) == BuildingType.starOrrery(type.orreryLevel()), "Controller classification must report the exact runtime ring count");
            for (int ring = 0; ring < type.orreryLevel(); ring++) for (int slot = 0; slot < StarOrreryLayout.slotCount(ring); slot++) {
                var state = level.getBlockState(StarOrreryLayout.slot(origin, facing, ring, slot));
                helper.assertTrue(state.is(orreryAnchor(ring)) && state.getValue(StarPositionAnchorBlock.FORMED)
                        && state.getValue(StarPositionAnchorBlock.STAR_ORRERY) && state.getValue(StarPositionAnchorBlock.ANCHOR_POS) == ring
                        && state.getValue(HorizontalDirectionalBlock.FACING) == facing, "Every native anchor tier, ring index and facing must survive relocation");
            }
        }
        helper.assertTrue(BuildingType.facing(level.getBlockState(origin)) == facing, "Controller facing must follow calibration");
        var entity = (IGridConnectedBlockEntity) level.getBlockEntity(origin);
        helper.assertTrue(entity.getMainNode().getNode() != null, "Restored controller must initialize its native ME node");
        helper.assertTrue(entity.getMainNode().getNode().getOwningPlayerId() == IPlayerRegistry.getPlayerId(player), "Native ME owner must survive relocation");
    }
    private static void assertWellContents(GameTestHelper helper, BlockPos origin, Direction facing) {
        var level = helper.getLevel();
        var pos = MatterFabricationStructure.worldPos(origin, facing, MatterFabricationStructure.patternAssemblyBays().getFirst());
        var assembly = (MatterFabricationPatternAssemblyBlockEntity) level.getBlockEntity(pos);
        helper.assertTrue(assembly != null, "An installed pattern assembly must not become a default casing");
        helper.assertTrue(ItemStack.matches(encodedPattern(), assembly.getLogic().getPatternInv().getStackInSlot(3)), "Real AE encoded pattern and its components must survive");
        helper.assertTrue(ItemStack.matches(catalyst(), assembly.getCatalystInventory().getStackInSlot(7)), "Catalyst slot, damage and foreign components must survive");
        helper.assertTrue(assembly.getLogic().getPriority() == 37, "Native provider settings must survive");
        helper.assertTrue(assembly.saveWithFullMetadata(level.registryAccess()).getLong("fabrication_controller") == origin.asLong(), "Assembly must bind to the relocated controller");
        helper.assertTrue(assembly.getMainNode().getNode() != null, "Restored assembly must initialize its native node");
    }
    private static void clickBlock(GameTestHelper helper, ServerPlayer player, BlockPos pos) {
        var level = helper.getLevel();
        player.closeContainer(); player.setPos(pos.getX(), pos.getY(), pos.getZ() - 2);
        var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        var event = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, pos, hit);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        helper.assertTrue(!event.isCanceled(), "Unreserved block interaction must not be cancelled by the migration lock");
        level.getBlockState(pos).useWithoutItem(level, player, hit);
    }
    private static void assertOpensMenu(GameTestHelper helper, ServerPlayer player, BuildingType type, BlockPos pos) {
        assertOpensMenu(helper, player, type, pos, pos);
    }
    private static void assertOpensMenu(GameTestHelper helper, ServerPlayer player, BuildingType type, BlockPos pos, BlockPos controller) {
        clickBlock(helper, player, pos);
        boolean opened = switch (type) {
            case FABRICATION_WELL -> player.containerMenu instanceof com.atir.molecularmanipulator.menu.MatterFabricationMenu;
            case SEQUENCE_ARRAY -> player.containerMenu instanceof com.atir.molecularmanipulator.menu.MolecularCenterMenu;
            case OMNI_COMPUTATION -> player.containerMenu instanceof com.atir.molecularmanipulator.menu.OmniComputationMenu;
            case EVENT_HORIZON_SINGULARITY -> player.containerMenu instanceof com.atir.molecularmanipulator.menu.SingularityMenu;
            case STAR_COMPUTATION_ENGINE, STAR_ORRERY_L0, STAR_ORRERY_L1, STAR_ORRERY_L2, STAR_ORRERY_L3,
                    STAR_ORRERY_L4, STAR_ORRERY_L5, STAR_ORRERY_L6, STAR_ORRERY_L7, STAR_ORRERY_L8, STAR_ORRERY_L9
                    -> player.containerMenu instanceof com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
        };
        helper.assertTrue(opened, "Restored " + type + " must open its real server menu");
        helper.assertTrue(((appeng.menu.AEBaseMenu) player.containerMenu).getBlockEntity() == helper.getLevel().getBlockEntity(controller),
                "The real menu must be bound to the actual controller, even when opened through a member");
        player.closeContainer();
    }

    @GameTest(template = "transfer_site")
    public static void nativeAirIsEmptyButBlueprintAirStillRequiresAnUnobstructedCell(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        var origin = helper.absolutePos(new BlockPos(24, 4, 24));
        var layout = List.of(new BuildingArchiveStore.Entry(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null),
                new BuildingArchiveStore.Entry(new BlockPos(1, 0, 0), Blocks.AIR.defaultBlockState(), null));
        for (var air : List.of(Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR)) {
            level.setBlock(origin, air.defaultBlockState(), 2 | 16);
            level.setBlock(origin.east(), air.defaultBlockState(), 2 | 16);
            var site = BuildingTransfer.survey(player, layout, origin, Direction.NORTH);
            helper.assertTrue(site.conflicts().isEmpty() && site.obstructions() == 0, "Native " + air + " must not obstruct either a solid or reserved-air cell");
        }
        level.setBlock(origin.east(), Blocks.STONE.defaultBlockState(), 2 | 16);
        var site = BuildingTransfer.survey(player, layout, origin, Direction.NORTH);
        helper.assertTrue(site.obstructions() == 1 && site.outsideHeight() == 0 && site.restricted() == 0
                && site.conflicts().equals(List.of(origin.east())), "Solid blocks in required interior AIR must be reported, not silently erased or ignored");
        helper.assertTrue(site.minY() == level.getMinBuildHeight() && site.maxY() == level.getMaxBuildHeight(), "Survey details must use this dimension's actual bounds");
        level.setBlock(origin.east(), Blocks.AIR.defaultBlockState(), 2 | 16);
        level.setBlock(origin, Blocks.WATER.defaultBlockState(), 2 | 16);
        helper.assertTrue(BuildingTransfer.survey(player, layout, origin, Direction.NORTH).conflicts().isEmpty(), "Native replaceable blocks remain valid deployment space");
        helper.succeed();
    }

    @GameTest(template = "transfer_site")
    public static void dimensionBuildLimitsRejectVoidAirBeforeReservingAnyPackage(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper);
        var origin = helper.absolutePos(new BlockPos(24, 4, 24));
        var layout = List.of(new BuildingArchiveStore.Entry(BlockPos.ZERO, Blocks.STONE.defaultBlockState(), null));
        int min = level.getMinBuildHeight(), max = level.getMaxBuildHeight();
        for (int y : new int[]{min, max - 1}) {
            var pos = new BlockPos(origin.getX(), y, origin.getZ()); var previous = level.getBlockState(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            var site = BuildingTransfer.survey(player, layout, pos, Direction.NORTH);
            level.setBlock(pos, previous, 2 | 16);
            helper.assertTrue(site.conflicts().isEmpty(), "Dimension boundaries are inclusive at Y=" + y);
        }
        for (int y : new int[]{min - 1, max}) {
            var pos = new BlockPos(origin.getX(), y, origin.getZ());
            helper.assertTrue(level.getBlockState(pos).isAir(), "Out-of-range native reads return air, which must not pass the height check");
            var site = BuildingTransfer.survey(player, layout, pos, Direction.NORTH);
            helper.assertTrue(site.outsideHeight() == 1 && site.obstructions() == 0 && site.conflicts().equals(List.of(pos)), "Out-of-height cells need their own conflict reason at Y=" + y);
        }
        var needsAirAbove = List.of(layout.getFirst(), new BuildingArchiveStore.Entry(new BlockPos(0, 1, 0), Blocks.AIR.defaultBlockState(), null));
        helper.assertTrue(BuildingTransfer.survey(player, needsAirAbove, new BlockPos(origin.getX(), max - 1, origin.getZ()), Direction.NORTH).outsideHeight() == 1,
                "Required blueprint AIR beyond the build limit must not be treated as an ignored wildcard");
        player.getInventory().setItem(0, BuildingPackage.fresh(BuildingType.STAR_ORRERY_L1));
        for (int baseY : new int[]{min - 1, max - 2}) {
            var base = new BlockPos(origin.getX(), baseY, origin.getZ());
            helper.assertTrue(!BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "A whole building crossing either dimension limit must be rejected");
            helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) == null, "Invalid height must not reserve the package");
            helper.assertTrue(!BuildingTransfer.locked(level, BuildingTransfer.controllerAtBase(BuildingType.STAR_ORRERY_L1.template(), base)), "Invalid height must not lock the site");
        }
        helper.succeed();
    }

    @GameTest(template = "transfer_site")
    public static void panelStatusAndSurveyPayloadsRoundTripWithRealRegistries(GameTestHelper helper) {
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            var id = UUID.randomUUID(); var request = UUID.randomUUID(); var pos = helper.absolutePos(new BlockPos(24, 4, 24));
            for (var operation : java.util.Arrays.asList(null, id)) {
                var control = new BuildingPayloads.TransferControl(request, operation, 3, pos, operation != null);
                BuildingPayloads.TransferControl.CODEC.encode(buffer, control);
                helper.assertTrue(control.equals(BuildingPayloads.TransferControl.CODEC.decode(buffer)), "Panel control must preserve optional operation, receipt slot and action");
                var status = new BuildingPayloads.TransferStatus(request, operation, operation == null ? "" : "ROLLING_BACK");
                BuildingPayloads.TransferStatus.CODEC.encode(buffer, status);
                helper.assertTrue(status.equals(BuildingPayloads.TransferStatus.CODEC.decode(buffer)), "Status must preserve the request and operation correlation");
                buffer.clear();
            }
            var site = new BuildingTransfer.SiteSurvey(List.of(pos), 0, 1, 0, helper.getLevel().getMinBuildHeight(), helper.getLevel().getMaxBuildHeight());
            var preview = new BuildingPayloads.Preview(request, 0, 1, 1,
                    List.of(new BuildingPayloads.Placement(pos, net.minecraft.world.level.block.Block.getId(Blocks.AIR.defaultBlockState()), true)), "", site.summary(), site.detail());
            BuildingPayloads.Preview.CODEC.encode(buffer, preview);
            helper.assertTrue(preview.equals(BuildingPayloads.Preview.CODEC.decode(buffer)), "Preview must preserve required-air conflicts and localized height/obstruction details without inventory NBT");
            helper.assertTrue(!buffer.isReadable(), "The codecs must consume their complete payloads");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "transfer_site", timeoutTicks = 1000)
    public static void obstructionBeforeReservationDoesNotStrandPackage(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.FABRICATION_WELL;
        var base = helper.absolutePos(new BlockPos(24, 4, 24));
        var origin = BuildingTransfer.controllerAtBase(type.template(), base);
        player.getInventory().setItem(0, BuildingPackage.fresh(type));
        var obstruction = origin.offset(type.template().getFirst().offset());
        level.setBlock(obstruction, Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(!BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "An obstructed site must be rejected before reservation");
        helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) == null, "Rejected deployment must leave the package usable");
        helper.assertTrue(!BuildingTransfer.locked(level, origin), "Rejected deployment must not lock the controller site");
        helper.assertTrue(level.getBlockState(obstruction).is(Blocks.STONE), "Preflight must not erase the obstruction");
        level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "Clearing preflight obstruction must allow the same package to deploy");
        var id = BuildingPackage.reservation(player.getInventory().getItem(0));
        helper.startSequence().thenWaitUntil(() -> assertRestored(helper, player, type, origin, Direction.NORTH))
                .thenExecute(() -> assertState(helper, id, "DEPLOYED")).thenSucceed();
    }

    @GameTest(template = "transfer_site", timeoutTicks = 1000)
    public static void midBuildObstructionCanBeClearedAndResumesAutomatically(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.FABRICATION_WELL;
        var base = helper.absolutePos(new BlockPos(24, 4, 24));
        var origin = BuildingTransfer.controllerAtBase(type.template(), base);
        player.getInventory().setItem(0, BuildingPackage.fresh(type));
        helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "Fresh deployment must start");
        var id = BuildingPackage.reservation(player.getInventory().getItem(0));
        var obstruction = origin.offset(type.template().get(type.template().size() / 2).offset());
        helper.runAtTickTime(50, () -> forceExternalObstruction(level, obstruction));
        helper.runAtTickTime(180, () -> {
            helper.assertTrue(manifest(helper, id).getString("phase").equals("BLOCKED"), "Encountered obstruction must publish a recoverable blocked phase");
            helper.assertTrue(BuildingTransfer.mayClearObstruction(level, obstruction), "The actual foreign obstruction must be clearable");
            var installed = origin.offset(type.template().getFirst().offset());
            helper.assertTrue(!BuildingTransfer.mayClearObstruction(level, installed), "Installed parts must remain protected");
            helper.assertTrue(!level.setBlock(installed, Blocks.AIR.defaultBlockState(), 3), "The low-level site lock must protect installed parts");
            var stranger = testPlayer(helper);
            helper.assertTrue(!stranger.gameMode.destroyBlock(obstruction), "Other players must not bypass the transfer owner's site lock");
            helper.assertTrue(player.gameMode.destroyBlock(obstruction), "Owner must be able to break the blocking foreign block through the lock");
        });
        helper.runAtTickTime(700, () -> {
            assertState(helper, id, "DEPLOYED");
            helper.assertTrue(formed(level, origin) && !BuildingTransfer.locked(level, origin), "Cleared obstruction must resume, commit and form without a second deployment");
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Resumed deployment must consume its original receipt");
            helper.succeed();
        });
    }

    @GameTest(template = "transfer_site", timeoutTicks = 1500)
    public static void sealedRollbackReplaysAfterRecoveryAndPreservesNativeContents(GameTestHelper helper) {
        var packets = new ArrayList<net.minecraft.network.protocol.Packet<?>>();
        var level = helper.getLevel(); var player = testPlayer(helper, packets::add); var type = BuildingType.FABRICATION_WELL;
        var sourceBase = helper.absolutePos(new BlockPos(24, 4, 24));
        var targetBase = helper.absolutePos(new BlockPos(80, 4, 24));
        var source = BuildingTransfer.controllerAtBase(type.template(), sourceBase);
        var target = BuildingTransfer.controllerAtBase(type.template(), targetBase);
        createSource(level, player, type, source);
        var obstruction = target.offset(type.template().get(type.template().size() / 2).offset());
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(BuildingTransfer.seal(player, level.getBlockEntity(source)), "Source must seal");
            var id = BuildingPackage.archive(player.getInventory().getItem(0));
            helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, Direction.NORTH), "Sealed deployment must start");
            helper.runAtTickTime(70, () -> forceExternalObstruction(level, obstruction));
            helper.runAtTickTime(200, () -> {
                helper.assertTrue(manifest(helper, id).getString("phase").equals("BLOCKED"), "Interrupted deployment must expose its obstruction");
                player.getInventory().selected = 0; player.setShiftKeyDown(true);
                player.getMainHandItem().getItem().use(level, player, InteractionHand.MAIN_HAND);
                player.setShiftKeyDown(false);
                assertState(helper, id, "DEPLOYING"); // The removed package gesture must no longer withdraw construction.
                var rejected = UUID.randomUUID();
                BuildingPayloads.transferControl(player, new BuildingPayloads.TransferControl(rejected, id, 0, target, true));
                assertState(helper, id, "DEPLOYING");
                helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof BuildingPayloads.TransferStatus status && status.request().equals(rejected)
                        && status.state().equals("BLOCKED")), "Panel status queries must receive an acknowledgement even when a held calibrator is required to interrupt");
                player.getInventory().setItem(1, new ItemStack(BuildingContent.CALIBRATOR.get())); player.getInventory().selected = 1;
                var stranger = testPlayer(helper);
                stranger.getInventory().setItem(0, new ItemStack(BuildingContent.CALIBRATOR.get()));
                BuildingPayloads.transferControl(stranger, new BuildingPayloads.TransferControl(UUID.randomUUID(), id, -1, target, true));
                assertState(helper, id, "DEPLOYING");
                var query = UUID.randomUUID();
                BuildingPayloads.transferControl(player, new BuildingPayloads.TransferControl(query, null, 0, target, false));
                helper.assertTrue(packets.stream().anyMatch(packet -> packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom
                        && custom.payload() instanceof BuildingPayloads.TransferStatus status && status.request().equals(query)
                        && id.equals(status.operation()) && status.state().equals("BLOCKED")), "Reopened panel must recover its owned blocked operation from the package receipt");
                BuildingPayloads.transferControl(player, new BuildingPayloads.TransferControl(UUID.randomUUID(), id, 0, target, true));
                assertState(helper, id, "ROLLING_BACK");
                helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) != null, "Receipt must stay locked while destination cleanup is incomplete");
            });
            helper.runAtTickTime(210, () -> {
                // Simulate process-local state loss, using only production startup recovery and disk journals.
                assertState(helper, id, "ROLLING_BACK");
                BuildingTransfer.stop(new ServerStoppedEvent(level.getServer()));
                BuildingTransfer.start(new ServerStartedEvent(level.getServer()));
                helper.assertTrue(BuildingTransfer.locked(level, target), "Startup recovery must reconstruct rollback locks");
            });
            helper.runAtTickTime(400, () -> {
                assertState(helper, id, "SEALED");
                var stack = player.getInventory().getItem(0);
                helper.assertTrue(id.equals(BuildingPackage.archive(stack)) && BuildingPackage.reservation(stack) == null, "Rollback must return the same usable archive handle");
                helper.assertTrue(level.getBlockState(obstruction).is(Blocks.OBSIDIAN), "Rollback must preserve foreign obstructions");
                helper.assertTrue(!BuildingTransfer.locked(level, target), "Durably rolled-back site must unlock");
                for (var entry : type.template()) if (!entry.state().isAir()) {
                    var pos = target.offset(entry.offset());
                    helper.assertTrue(!level.getBlockState(pos).is(entry.state().getBlock()), "Rollback must leave no archived native inventory or building part at " + pos);
                }
                level.setBlock(obstruction, Blocks.AIR.defaultBlockState(), 3);
                helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, Direction.NORTH), "The same sealed archive must redeploy after rollback");
            });
            helper.runAtTickTime(1100, () -> {
                assertState(helper, id, "DEPLOYED");
                assertRestored(helper, player, type, target, Direction.NORTH);
                assertWellContents(helper, target, Direction.NORTH);
                helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Only the successful retry may consume the package");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "transfer_site", timeoutTicks = 500)
    public static void freshRollbackBeforeAndAfterPlacementDoesNotMintArchivePackages(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.STAR_ORRERY_L1;
        var base = helper.absolutePos(new BlockPos(24, 4, 24));
        var origin = BuildingTransfer.controllerAtBase(type.template(), base);
        player.getInventory().setItem(0, BuildingPackage.fresh(type));
        player.getInventory().setItem(1, new ItemStack(BuildingContent.CALIBRATOR.get())); player.getInventory().selected = 1;
        helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "Fresh template must start");
        var first = BuildingPackage.reservation(player.getInventory().getItem(0));
        helper.runAtTickTime(10, () -> {
            BuildingPayloads.transferControl(player, new BuildingPayloads.TransferControl(UUID.randomUUID(), null, 0, origin, true));
            assertState(helper, first, "ROLLING_BACK");
        });
        helper.runAtTickTime(30, () -> {
            assertState(helper, first, "CANCELLED");
            helper.assertTrue(BuildingPackage.archive(player.getInventory().getItem(0)) == null, "Fresh package must remain a fresh template");
            helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) == null, "Cancelled fresh receipt must release");
            helper.assertTrue(BuildingTransfer.deploy(player, 0, base, Direction.NORTH), "The original fresh template must be reusable");
        });
        helper.runAtTickTime(90, () -> {
            var part = origin.offset(type.template().getFirst().offset());
            helper.assertTrue(!level.getBlockState(part).isAir(), "Second cancellation must exercise a physically started deployment");
            player.setPos(part.getX(), part.getY(), part.getZ());
            var id = BuildingPackage.reservation(player.getInventory().getItem(0));
            player.setShiftKeyDown(true);
            var hit = new net.minecraft.world.phys.BlockHitResult(net.minecraft.world.phys.Vec3.atCenterOf(part), Direction.UP, part, false);
            var click = new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, part, hit);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(click);
            helper.assertTrue(!click.isCanceled() && click.getUseItem() == net.neoforged.neoforge.common.util.TriState.TRUE, "Locked construction must allow the calibrator to reopen its panel");
            player.getMainHandItem().getItem().onItemUseFirst(player.getMainHandItem(), new net.minecraft.world.item.context.UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            player.setShiftKeyDown(false);
            assertState(helper, id, "DEPLOYING"); // Sneaking only opens the panel now.
            BuildingPayloads.transferControl(player, new BuildingPayloads.TransferControl(UUID.randomUUID(), null, -1, part, true));
            assertState(helper, id, "ROLLING_BACK");
        });
        helper.runAtTickTime(130, () -> {
            BuildingTransfer.collect(player);
            helper.assertTrue(player.getInventory().items.stream().filter(stack -> BuildingPackage.type(stack) != null).count() == 1, "Collect must not mint a second archive package for a cancelled fresh template");
            helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) == null, "Second rollback must release its receipt");
            helper.assertTrue(!BuildingTransfer.locked(level, origin), "Cancelled fresh site must unlock");
            for (var entry : type.template()) helper.assertTrue(level.getBlockState(origin.offset(entry.offset())).isAir(), "Cancelled fresh site must be empty");
            helper.succeed();
        });
    }

    @GameTest(template = "transfer_site", batch = "contentsRollback", timeoutTicks = 1500)
    public static void rollbackAfterNativeInventoryLoadRetainsOneCompleteArchive(GameTestHelper helper) {
        var level = helper.getLevel(); var player = testPlayer(helper); var type = BuildingType.FABRICATION_WELL;
        var sourceBase = helper.absolutePos(new BlockPos(24, 4, 24));
        var targetBase = helper.absolutePos(new BlockPos(80, 4, 24));
        var source = BuildingTransfer.controllerAtBase(type.template(), sourceBase);
        var target = BuildingTransfer.controllerAtBase(type.template(), targetBase);
        var assemblyPos = MatterFabricationStructure.worldPos(target, Direction.NORTH, MatterFabricationStructure.patternAssemblyBays().getFirst());
        var archive = new UUID[1];
        createSource(level, player, type, source);
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            helper.assertTrue(BuildingTransfer.seal(player, level.getBlockEntity(source)), "Source with native inventory must seal");
            archive[0] = BuildingPackage.archive(player.getInventory().getItem(0));
            helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, Direction.NORTH), "Deployment with archived contents must start");
        }).thenWaitUntil(() -> {
            var entity = level.getBlockEntity(assemblyPos);
            helper.assertTrue(entity instanceof MatterFabricationPatternAssemblyBlockEntity, "Assembly must be physically present");
            var assembly = (MatterFabricationPatternAssemblyBlockEntity) entity;
            helper.assertTrue(ItemStack.matches(catalyst(), assembly.getCatalystInventory().getStackInSlot(7)), "Wait until archived inventory has actually loaded");
            helper.assertTrue(BuildingTransfer.locked(level, target), "Exercise cancellation before native contents commit");
        }).thenExecute(() -> {
            helper.assertTrue(manifest(helper, archive[0]).getString("phase").equals("CONTENTS"), "Cancellation must exercise the native content phase");
            helper.assertTrue(BuildingTransfer.cancelDeployment(player, archive[0]), "Partly restored native contents must remain withdrawable");
        }).thenWaitUntil(() -> helper.assertTrue(!BuildingTransfer.locked(level, target), "Rollback must save and unlock")).thenExecute(() -> {
            assertState(helper, archive[0], "SEALED");
            helper.assertTrue(level.getBlockEntity(assemblyPos) == null, "Rollback must not leave a second restored native inventory");
            helper.assertTrue(BuildingPackage.reservation(player.getInventory().getItem(0)) == null, "Only completed rollback releases its receipt");
            helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, Direction.NORTH), "The complete archive must deploy after content-phase rollback");
        }).thenWaitUntil(() -> assertRestored(helper, player, type, target, Direction.NORTH)).thenExecute(() -> {
            assertState(helper, archive[0], "DEPLOYED");
            assertWellContents(helper, target, Direction.NORTH);
            assertOpensMenu(helper, player, type, target);
            helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "The archive must have exactly one committed deployment");
        }).thenSucceed();
    }

    /** Simulate a chunk generator/mod writing beneath normal LevelChunk mutation locks, not an ordinary player edit. */
    private static void forceExternalObstruction(ServerLevel level, BlockPos pos) {
        var chunk = level.getChunkAt(pos);
        chunk.getSection(chunk.getSectionIndex(pos.getY())).setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, Blocks.OBSIDIAN.defaultBlockState());
        chunk.setUnsaved(true);
    }
    private static CompoundTag manifest(GameTestHelper helper, UUID id) {
        try { return BuildingArchiveStore.forServer(helper.getLevel().getServer()).readManifest(id); }
        catch (IOException error) { throw new RuntimeException(error); }
    }
    private static void assertState(GameTestHelper helper, UUID id, String state) {
        var manifest = manifest(helper, id);
        helper.assertTrue(state.equals(manifest.getString("state")), "Expected " + state + ", got " + manifest);
    }
}
