package com.atir.molecularmanipulator.building;

import appeng.api.AECapabilities;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.features.IPlayerRegistry;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
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
        var sourceBase = helper.absolutePos(new BlockPos(48, 4, 64));
        var targetBase = helper.absolutePos(new BlockPos(144, 4, 64));
        var source = BuildingTransfer.controllerAtBase(type.template(), sourceBase);
        var target = BuildingTransfer.controllerAtBase(type.template(), targetBase);
        var originalFacing = type.isStarOrrery() ? Direction.EAST : Direction.NORTH;
        createSource(level, player, type, source, originalFacing);
        var archive = new UUID[1];
        helper.startSequence().thenIdle(20).thenExecute(() -> {
            helper.assertTrue(formed(level, source), "Source " + type + " must form before archiving");
            if (type.isStarOrrery()) assertOrreryCentralBody(helper, source, originalFacing, true);
            helper.assertTrue(BuildingTransfer.seal(player, level.getBlockEntity(source)), "Native source sealing must succeed");
            if (type.isStarOrrery()) assertOrreryCentralCleared(helper, source);
            archive[0] = BuildingPackage.archive(player.getInventory().getItem(0));
            helper.assertTrue(archive[0] != null, "Reclaim must deliver its archive handle");
            helper.assertTrue(BuildingTransfer.deploy(player, 0, targetBase, originalFacing), "Archived deployment must preserve the original facing");
        }).thenWaitUntil(() -> assertRestored(helper, player, type, target, originalFacing)).thenExecute(() -> {
            assertState(helper, archive[0], "DEPLOYED");
            assertOpensMenu(helper, player, type, target);
            if (type == BuildingType.FABRICATION_WELL) assertWellContents(helper, target, originalFacing);
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
            helper.assertTrue(BuildingPackage.archive(player.getInventory().getItem(0)) != null, "Reclaimed restored machine must return a package");
            helper.assertTrue(BuildingTransfer.deploy(player, 0, sourceBase, Direction.WEST), "Reclaimed package must support a second, rotated deployment");
        }).thenWaitUntil(() -> assertRestored(helper, player, type, source, Direction.WEST)).thenExecute(() -> {
            assertOpensMenu(helper, player, type, source);
            if (type == BuildingType.FABRICATION_WELL) assertWellContents(helper, source, Direction.WEST);
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
        for (var entry : type.template())
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
            helper.assertTrue(core.foldingMembers().size() == type.template().size(), "All real central, foundation and ring members must survive relocation");
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
            case STAR_COMPUTATION_ENGINE, STAR_ORRERY_L1, STAR_ORRERY_L2, STAR_ORRERY_L3 -> player.containerMenu instanceof com.atir.molecularmanipulator.menu.StarComputationEngineMenu;
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
