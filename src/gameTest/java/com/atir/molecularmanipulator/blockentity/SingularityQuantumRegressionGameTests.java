package com.atir.molecularmanipulator.blockentity;

import appeng.api.features.Locatables;
import appeng.api.ids.AEComponents;
import appeng.api.networking.IGridConnection;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.QuantumCluster;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Live AE2 bridge registrations, not mocked Locatables endpoints or forced grid flags. */
@GameTestHolder("molecularmanipulator")
@PrefixGameTestTemplate(false)
public final class SingularityQuantumRegressionGameTests {
    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 500)
    public static void rejectedRealAe2RetuneDoesNotFalselyOccupyTheOldFrequency(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var hub = fixture.hub(fixture.origin, false);
        var target = fixture.bridge(fixture.origin.east(128));
        var retuned = fixture.bridge(fixture.origin.east(144));
        var busyA = fixture.bridge(fixture.origin.east(160));
        var busyB = fixture.bridge(fixture.origin.east(176));
        long frequency = 91_361_001L, busyFrequency = 91_361_002L;
        cleanupFrequencies(fixture, frequency, busyFrequency);
        var retained = new IGridConnection[1];
        helper.startSequence().thenWaitUntil(() -> {
            ready(helper, target, retuned, busyA, busyB);
            helper.assertTrue(hub.getMainNode().isReady(), "Real Hub node initializes");
        }).thenExecute(() -> {
            frequency(target, frequency);
            frequency(retuned, frequency);
            frequency(busyA, busyFrequency);
            frequency(busyB, busyFrequency);
            helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, -frequency) == target.getCluster()
                            && Locatables.quantumNetworkBridges().get(fixture.level, frequency) == retuned.getCluster(),
                    "Two real AE2 bridges register the two signs of one frequency");
            hub.getQuantumInventory().setItemDirect(0, singularity(frequency));
            helper.assertTrue(hub.getQuantumLinkState() == QuantumLinkState.FREQUENCY_OCCUPIED
                            && hub.getMainNode().getNode().getConnections().isEmpty(),
                    "A genuinely occupied pair must still be rejected");
            // AE2 19.2 keeps an old-sign registration after a retune to an already occupied pair.
            frequency(retuned, busyFrequency);
            helper.assertTrue(retuned.getQEFrequency() == busyFrequency
                            && Locatables.quantumNetworkBridges().get(fixture.level, frequency) == retuned.getCluster(),
                    "Reproduce the stale index entry through the real AE2 retune path");
        }).thenWaitUntil(() -> connected(helper, hub, target, QuantumLinkState.CONNECTED_BUILD_ONLY))
                .thenExecute(() -> {
                    helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, frequency) == retuned.getCluster(),
                            "Ignore stale endpoint liveness without mutating AE2's shared registry");
                    retained[0] = hub.getMainNode().getNode().getConnections().getFirst();
                }).thenIdle(60).thenExecute(() -> {
                    connected(helper, hub, target, QuantumLinkState.CONNECTED_BUILD_ONLY);
                    helper.assertTrue(hub.getMainNode().getNode().getConnections().getFirst() == retained[0],
                            "Normal polling retains the same wireless connection instead of rebuilding it");
                }).thenSucceed();
    }

    @GameTest(template = ServerRegressionFixture.TEMPLATE, timeoutTicks = 900)
    public static void liveBridgeAndControllerLifecycleReconnectsAndReleasesFrequencyClaims(GameTestHelper helper) {
        var fixture = new ServerRegressionFixture(helper);
        var hub = new SingularityBlockEntity[]{fixture.hub(fixture.origin, true)};
        var spare = fixture.hub(fixture.origin.south(128), false);
        var temporary = fixture.bridge(fixture.origin.east(128));
        var remotePos = fixture.origin.east(144);
        var remote = new QuantumBridgeBlockEntity[]{fixture.bridge(remotePos)};
        long frequency = 91_361_003L;
        cleanupFrequencies(fixture, frequency);
        var oldCluster = new QuantumCluster[1];
        var wiredState = new BlockState[1];
        hub[0].getQuantumInventory().setItemDirect(0, singularity(frequency));
        helper.startSequence().thenWaitUntil(() -> {
            ready(helper, temporary, remote[0]);
            helper.assertTrue(hub[0].formed() && spare.getMainNode().isReady()
                            && hub[0].getQuantumLinkState() == QuantumLinkState.REMOTE_MISSING,
                    "A genuinely formed Hub safely waits for delayed partner registration");
        }).thenExecute(() -> {
            frequency(temporary, frequency);
            frequency(remote[0], frequency);
            frequency(temporary, 0);
            helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, frequency) == remote[0].getCluster()
                            && Locatables.quantumNetworkBridges().get(fixture.level, -frequency) == null,
                    "The first bridge's removal leaves a live positive-sign endpoint");
            spare.getQuantumInventory().setItemDirect(0, singularity(frequency));
            helper.assertTrue(spare.getQuantumLinkState() == QuantumLinkState.FREQUENCY_OCCUPIED,
                    "A second live controller cannot claim the same frequency");
        }).thenWaitUntil(() -> connected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED))
                .thenExecute(() -> {
                    wiredState[0] = fixture.level.getBlockState(fixture.origin.north());
                    fixture.place(fixture.origin.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                }).thenWaitUntil(() -> helper.assertTrue(hub[0].getQuantumLinkState() == QuantumLinkState.WIRED_CONFLICT,
                        "Endpoint filtering must not bypass wired-network conflicts"))
                .thenExecute(() -> fixture.place(fixture.origin.north(), wiredState[0]))
                .thenWaitUntil(() -> connected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED))
                .thenExecute(() -> {
                    oldCluster[0] = remote[0].getCluster();
                    frequency(remote[0], 0);
                    fixture.place(remotePos, Blocks.AIR.defaultBlockState());
                    helper.assertTrue(oldCluster[0].isDestroyed(), "Removing the actual link destroys its cluster");
                    // A teardown observer may retain a stale index snapshot after the center has been cleared.
                    Locatables.quantumNetworkBridges().register(fixture.level, frequency, oldCluster[0]);
                }).thenWaitUntil(() -> helper.assertTrue(hub[0].getQuantumLinkState() == QuantumLinkState.REMOTE_MISSING
                                && hub[0].getMainNode().getNode().getConnections().isEmpty(),
                        "A destroyed registered cluster is missing, never a dereference of its cleared center"))
                .thenExecute(() -> {
                    remote[0] = fixture.place(remotePos, AEBlocks.QUANTUM_LINK.block().defaultBlockState(), QuantumBridgeBlockEntity.class);
                    frequency(remote[0], frequency);
                }).thenWaitUntil(() -> {
                    ready(helper, remote[0]);
                    connected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED);
                    helper.assertTrue(Locatables.quantumNetworkBridges().get(fixture.level, -frequency) == remote[0].getCluster(),
                            "Rebuilt remote registers and connects using the negative sign");
                }).thenExecute(() -> {
                    var saved = hub[0].saveWithFullMetadata(fixture.level.registryAccess());
                    var state = hub[0].getBlockState();
                    fixture.level.removeBlockEntity(fixture.origin);
                    hub[0] = (SingularityBlockEntity) BlockEntity.loadStatic(fixture.origin, state, saved, fixture.level.registryAccess());
                    helper.assertTrue(hub[0] != null && hub[0].getQuantumFrequency() == frequency,
                            "Native BE NBT reload retains the exact singularity component");
                    fixture.level.setBlockEntity(hub[0]);
                }).thenWaitUntil(() -> connected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED))
                .thenExecute(() -> hub[0].getQuantumInventory().setItemDirect(0, ItemStack.EMPTY))
                .thenWaitUntil(() -> {
                    helper.assertTrue(hub[0].getQuantumLinkState() == QuantumLinkState.EMPTY, "Inventory removal releases the first claim");
                    connected(helper, spare, remote[0], QuantumLinkState.CONNECTED_BUILD_ONLY);
                }).thenExecute(() -> {
                    hub[0].getQuantumInventory().setItemDirect(0, singularity(frequency));
                    helper.assertTrue(hub[0].getQuantumLinkState() == QuantumLinkState.FREQUENCY_OCCUPIED,
                            "The replacement controller now exclusively owns the frequency");
                    // Keep the singularity installed while setRemoved tears down the claiming node.
                    fixture.level.removeBlockEntity(spare.getBlockPos());
                    fixture.place(spare.getBlockPos(), Blocks.AIR.defaultBlockState());
                }).thenWaitUntil(() -> connected(helper, hub[0], remote[0], QuantumLinkState.CONNECTED)).thenSucceed();
    }

    private static void ready(GameTestHelper helper, QuantumBridgeBlockEntity... bridges) {
        for (var bridge : bridges) helper.assertTrue(bridge.isFormed() && bridge.getCluster() != null
                        && !bridge.getCluster().isDestroyed() && bridge.getMainNode().isOnline(),
                "Actual AE2 bridge forms and receives power at " + bridge.getBlockPos());
    }

    private static void connected(GameTestHelper helper, SingularityBlockEntity hub,
            QuantumBridgeBlockEntity remote, QuantumLinkState expected) {
        helper.assertTrue(hub.networkOnline() && hub.getQuantumLinkState() == expected,
                "Live partner state: expected=" + expected + ", actual=" + hub.getQuantumLinkState());
        var node = hub.getMainNode().getNode();
        helper.assertTrue(node.getInWorldConnections().isEmpty() && node.getConnections().size() == 1
                        && node.getConnections().getFirst().getOtherSide(node) == remote.getMainNode().getNode(),
                "Exactly one wireless connection targets the current actual remote node");
        helper.assertTrue(node.getIdlePowerUsage() == 516.0, "The link retains its 4 + 512 AE/t idle draw");
    }

    private static ItemStack singularity(long frequency) {
        var stack = AEItems.QUANTUM_ENTANGLED_SINGULARITY.stack();
        stack.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
        return stack;
    }

    private static void frequency(QuantumBridgeBlockEntity bridge, long frequency) {
        bridge.getInternalInventory().setItemDirect(0, frequency == 0 ? ItemStack.EMPTY : singularity(frequency));
    }

    private static void cleanupFrequencies(ServerRegressionFixture fixture, long... frequencies) {
        fixture.onCleanup(() -> {
            for (long frequency : frequencies) {
                Locatables.quantumNetworkBridges().unregister(fixture.level, frequency);
                Locatables.quantumNetworkBridges().unregister(fixture.level, -frequency);
            }
        });
    }
}
