package com.atir.molecularmanipulator.blockentity;

import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGridConnection;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.QuantumCluster;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import com.atir.molecularmanipulator.registry.SingularityContent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityQuantumLinkGameTests {
    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_quantum", timeoutTicks = 500)
    public static void retunedAe2RegistrationDoesNotFalselyOccupyFrequency(GameTestHelper helper) {
        var fixture = new Fixture(helper.getLevel());
        var origin = new BlockPos(14008, 120, 14008);
        var hub = fixture.hub(origin, false);
        var target = fixture.bridge(origin.east(128));
        var retuned = fixture.bridge(origin.east(144));
        var busyA = fixture.bridge(origin.east(160));
        var busyB = fixture.bridge(origin.east(176));
        long frequency = 91_357_901L, busyFrequency = 91_357_902L;
        var retainedLink = new IGridConnection[1];

        helper.startSequence().thenWaitUntil(() -> {
            assertReady(helper, target, retuned, busyA, busyB);
            helper.assertTrue(hub.getMainNode().isReady(), "Multiblock controller node must initialize");
        }).thenExecute(() -> {
            setFrequency(target, frequency);
            setFrequency(retuned, frequency);
            setFrequency(busyA, busyFrequency);
            setFrequency(busyB, busyFrequency);
            helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, -frequency) == target.getCluster()
                    && Locatables.quantumNetworkBridges().get(fixture.level, frequency) == retuned.getCluster(),
                    "Real AE2 clusters must register both signs of the target frequency");
            hub.getQuantumInventory().setItemDirect(0, singularity(frequency));
            helper.assertTrue(hub.getQuantumLinkState() == QuantumLinkState.FREQUENCY_OCCUPIED
                    && hub.getMainNode().getNode().getConnections().isEmpty(),
                    "Two genuine AE2 bridges must remain an occupied pair");

            // No synthetic Locatables entries: this exercises AE2 19.2's real retune path.
            // Both busy-frequency signs are taken, so the retuned cluster re-registers +frequency.
            setFrequency(retuned, busyFrequency);
            helper.assertTrue(retuned.getQEFrequency() == busyFrequency
                    && Locatables.quantumNetworkBridges().get(fixture.level, frequency) == retuned.getCluster(),
                    "AE2 must reproduce a stale old-frequency registration after a rejected retune");
        }).thenWaitUntil(() -> assertConnected(helper, hub, target, QuantumLinkState.CONNECTED_BUILD_ONLY))
                .thenExecute(() -> {
                    helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, frequency) == retuned.getCluster(),
                            "Hub must ignore the stale entry without changing AE2's shared registry");
                    retainedLink[0] = hub.getMainNode().getNode().getConnections().getFirst();
                }).thenIdle(60).thenExecute(() -> {
                    assertConnected(helper, hub, target, QuantumLinkState.CONNECTED_BUILD_ONLY);
                    helper.assertTrue(hub.getMainNode().getNode().getConnections().getFirst() == retainedLink[0],
                            "Polling must retain the existing connection rather than repeatedly rebuilding it");
                    fixture.close();
                    System.out.println("SINGULARITY_QUANTUM_RETUNE_PASS realAe2Retune=true trueOccupancy=true staleIgnored=true buildOnly=true stableConnection=true");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_quantum", timeoutTicks = 700)
    public static void formedHubReconnectsAfterRegistrationNodeAndSaveLifecycles(GameTestHelper helper) {
        var fixture = new Fixture(helper.getLevel());
        var origin = new BlockPos(14408, 120, 14408);
        var hub = new SingularityBlockEntity[]{fixture.hub(origin, true)};
        var temporary = fixture.bridge(origin.east(128));
        var remotePos = origin.east(144);
        var remote = new QuantumBridgeBlockEntity[]{fixture.bridge(remotePos)};
        var oldCluster = new QuantumCluster[1];
        var wiredPrevious = new BlockState[1];
        long frequency = 91_357_903L;
        hub[0].getQuantumInventory().setItemDirect(0, singularity(frequency));

        helper.startSequence().thenWaitUntil(() -> {
            assertReady(helper, temporary, remote[0]);
            helper.assertTrue(hub[0].formed() && hub[0].getQuantumLinkState() == QuantumLinkState.REMOTE_MISSING,
                    "A complete multiblock must wait safely when its partner has not registered yet");
        }).thenExecute(() -> {
            setFrequency(temporary, frequency);
            setFrequency(remote[0], frequency);
            setFrequency(temporary, 0);
            helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, frequency) == remote[0].getCluster()
                    && Locatables.quantumNetworkBridges().get(fixture.level, -frequency) == null,
                    "Removing the first ring must leave a live positive-sign endpoint");
        }).thenWaitUntil(() -> assertConnected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED))
                .thenExecute(() -> {
                    wiredPrevious[0] = fixture.level.getBlockState(origin.north());
                    fixture.place(origin.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                }).thenWaitUntil(() -> helper.assertTrue(hub[0].getQuantumLinkState() == QuantumLinkState.WIRED_CONFLICT,
                        "Endpoint filtering must not bypass the existing wired-network conflict rule"))
                .thenExecute(() -> fixture.place(origin.north(), wiredPrevious[0]))
                .thenWaitUntil(() -> assertConnected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED))
                .thenExecute(() -> {
                    oldCluster[0] = remote[0].getCluster();
                    fixture.remove(remotePos);
                    helper.assertTrue(oldCluster[0].isDestroyed(), "Removing the real bridge must destroy its old cluster");
                    // Simulate a stale index snapshot retained by another integration during teardown.
                    Locatables.quantumNetworkBridges().register(fixture.level, frequency, oldCluster[0]);
                }).thenWaitUntil(() -> helper.assertTrue(hub[0].getQuantumLinkState() == QuantumLinkState.REMOTE_MISSING
                        && hub[0].getMainNode().getNode().getConnections().isEmpty(),
                        "A destroyed registered cluster must be treated as missing without dereferencing its cleared center"))
                .thenExecute(() -> {
                    remote[0] = (QuantumBridgeBlockEntity) fixture.place(remotePos, AEBlocks.QUANTUM_LINK.block().defaultBlockState());
                    setFrequency(remote[0], frequency);
                }).thenWaitUntil(() -> {
                    assertReady(helper, remote[0]);
                    assertConnected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED);
                    helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, -frequency) == remote[0].getCluster(),
                            "Rebuilt bridge must now register the negative sign");
                }).thenExecute(() -> {
                    var saved = hub[0].saveWithFullMetadata(fixture.level.registryAccess());
                    var state = hub[0].getBlockState();
                    fixture.level.removeBlockEntity(origin);
                    hub[0] = (SingularityBlockEntity) BlockEntity.loadStatic(origin, state, saved, fixture.level.registryAccess());
                    helper.assertTrue(hub[0] != null && hub[0].getQuantumFrequency() == frequency,
                            "World reload must retain the exact singularity component");
                    fixture.level.setBlockEntity(hub[0]);
                }).thenWaitUntil(() -> assertConnected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED))
                .thenExecute(() -> {
                    Locatables.quantumNetworkBridges().unregister(fixture.level, frequency);
                    fixture.close();
                    System.out.println("SINGULARITY_QUANTUM_LIFECYCLE_PASS formed=true delayedRegistration=true bothSigns=true wiredConflict=true destroyedEntry=true bridgeRebuild=true hubReload=true");
                }).thenSucceed();
    }

    @GameTest(template = "multiblock_dismantle_empty", batch = "singularity_quantum", timeoutTicks = 400)
    public static void controllerClaimsStillExcludeDuplicatesAndReleaseOnRemoval(GameTestHelper helper) {
        var fixture = new Fixture(helper.getLevel());
        var origin = new BlockPos(14808, 120, 14808);
        var first = fixture.hub(origin, false);
        var second = fixture.hub(origin.south(8), false);
        var remote = fixture.bridge(origin.east(128));
        long frequency = 91_357_904L;
        helper.startSequence().thenWaitUntil(() -> {
            assertReady(helper, remote);
            helper.assertTrue(first.getMainNode().isReady() && second.getMainNode().isReady(),
                    "Both controller nodes must initialize");
        }).thenExecute(() -> {
            setFrequency(remote, frequency);
            first.getQuantumInventory().setItemDirect(0, singularity(frequency));
            second.getQuantumInventory().setItemDirect(0, singularity(frequency));
            helper.assertTrue(second.getQuantumLinkState() == QuantumLinkState.FREQUENCY_OCCUPIED,
                    "Live controller claims must still reject duplicate hubs");
        }).thenWaitUntil(() -> assertConnected(helper, first, remote, QuantumLinkState.CONNECTED_BUILD_ONLY))
                .thenExecute(() -> first.getQuantumInventory().setItemDirect(0, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    helper.assertTrue(first.getQuantumLinkState() == QuantumLinkState.EMPTY,
                            "Removing a singularity must empty the link state");
                    assertConnected(helper, second, remote, QuantumLinkState.CONNECTED_BUILD_ONLY);
                }).thenExecute(() -> {
                    first.getQuantumInventory().setItemDirect(0, singularity(frequency));
                    helper.assertTrue(first.getQuantumLinkState() == QuantumLinkState.FREQUENCY_OCCUPIED,
                            "The second hub must now own the frequency");
                    // Break with the singularity still installed, exercising setRemoved's claim release.
                    fixture.level.setBlock(second.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
                }).thenWaitUntil(() -> assertConnected(helper, first, remote, QuantumLinkState.CONNECTED_BUILD_ONLY))
                .thenExecute(() -> {
                    fixture.close();
                    System.out.println("SINGULARITY_QUANTUM_CLAIMS_PASS duplicateRejected=true inventoryRelease=true removalRelease=true automaticRetry=true");
                }).thenSucceed();
    }

    private static void assertReady(GameTestHelper helper, QuantumBridgeBlockEntity... bridges) {
        for (var bridge : bridges) helper.assertTrue(bridge.isFormed() && bridge.getCluster() != null
                && !bridge.getCluster().isDestroyed() && bridge.getMainNode().isOnline(),
                "Real AE2 bridge must form, initialize its node and receive power at " + bridge.getBlockPos());
    }

    private static void assertConnected(GameTestHelper helper, SingularityBlockEntity hub,
            QuantumBridgeBlockEntity remote, QuantumLinkState expected) {
        helper.assertTrue(hub.networkOnline() && hub.getQuantumLinkState() == expected,
                "Hub must connect to its live partner: expected=" + expected + ", actual=" + hub.getQuantumLinkState());
        var node = hub.getMainNode().getNode();
        helper.assertTrue(node.getInWorldConnections().isEmpty() && node.getConnections().size() == 1
                && node.getConnections().getFirst().getOtherSide(node) == remote.getMainNode().getNode(),
                "Exactly one wireless connection must target the current real bridge node");
        helper.assertTrue(node.getIdlePowerUsage() == 516.0, "Quantum link must retain its existing 4 + 512 AE/t idle draw");
    }

    private static ItemStack singularity(long frequency) {
        var stack = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        stack.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
        return stack;
    }

    private static void setFrequency(QuantumBridgeBlockEntity bridge, long frequency) {
        bridge.getInternalInventory().setItemDirect(0, frequency == 0 ? ItemStack.EMPTY : singularity(frequency));
    }

    private static final class Fixture {
        private final ServerLevel level;
        private final Set<BlockPos> blocks = new LinkedHashSet<>();
        private final Set<ChunkPos> chunks = new LinkedHashSet<>();

        private Fixture(ServerLevel level) { this.level = level; }

        private BlockEntity place(BlockPos pos, BlockState state) {
            var chunk = new ChunkPos(pos);
            if (chunks.add(chunk)) {
                level.setChunkForced(chunk.x, chunk.z, true);
                level.getChunk(chunk.x, chunk.z);
            }
            blocks.add(pos.immutable());
            level.setBlock(pos, state, 3);
            return level.getBlockEntity(pos);
        }

        private SingularityBlockEntity hub(BlockPos origin, boolean complete) {
            if (complete) {
                for (var part : SingularityStructure.parts()) place(
                        SingularityStructure.worldPos(origin, Direction.NORTH, part), SingularityStructure.state(part, Direction.NORTH));
                for (var air : SingularityStructure.requiredAir()) place(
                        SingularityStructure.worldPos(origin, Direction.NORTH, air), Blocks.AIR.defaultBlockState());
            } else place(origin, SingularityContent.CONTROLLER.get().defaultBlockState());
            return (SingularityBlockEntity) level.getBlockEntity(origin);
        }

        private QuantumBridgeBlockEntity bridge(BlockPos center) {
            // Vertical 3x3 bridge, with power on a non-corner ring's outward face.
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) place(center.offset(x, y, 0),
                    (x == 0 && y == 0 ? AEBlocks.QUANTUM_LINK : AEBlocks.QUANTUM_RING).block().defaultBlockState());
            place(center.east(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            return (QuantumBridgeBlockEntity) level.getBlockEntity(center);
        }

        private void remove(BlockPos pos) {
            var entity = level.getBlockEntity(pos);
            if (entity instanceof SingularityBlockEntity hub) hub.getQuantumInventory().setItemDirect(0, ItemStack.EMPTY);
            if (entity instanceof QuantumBridgeBlockEntity bridge) setFrequency(bridge, 0);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }

        private void close() {
            // Clear inputs first so cluster teardown cannot re-register an old key during cleanup.
            for (var pos : blocks) {
                var entity = level.getBlockEntity(pos);
                if (entity instanceof SingularityBlockEntity hub) hub.getQuantumInventory().setItemDirect(0, ItemStack.EMPTY);
                if (entity instanceof QuantumBridgeBlockEntity bridge) setFrequency(bridge, 0);
            }
            var reversed = new ArrayList<>(blocks);
            java.util.Collections.reverse(reversed);
            for (var pos : reversed) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            for (var chunk : chunks) level.setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
