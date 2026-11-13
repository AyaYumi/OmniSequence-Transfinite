package com.atir.molecularmanipulator.integration.ae2;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridConnection;
import appeng.api.networking.IGridNode;
import com.atir.molecularmanipulator.blockentity.MolecularCenterBlockEntity.QuantumLinkState;
import java.util.function.DoubleSupplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

/** One persistent binding and one transient native ME connection; no parallel item-transfer path. */
public final class SequenceWirelessLink {
    public static final double LINK_POWER = 512.0;
    private static final String BINDING_TAG = "sequence_wireless_binding";
    private final SequenceWirelessHost machine;
    private final DoubleSupplier baseIdlePower;
    private SequenceWirelessBinding binding;
    private IGridConnection connection;
    private IGridNode remoteNode;
    private boolean powerApplied;
    private QuantumLinkState state = QuantumLinkState.EMPTY;

    public SequenceWirelessLink(SequenceWirelessHost machine, DoubleSupplier baseIdlePower) {
        this.machine = machine;
        this.baseIdlePower = baseIdlePower;
    }

    public boolean isBound() { return binding != null; }
    public SequenceWirelessBinding binding() { return binding; }
    public QuantumLinkState state() { return state; }
    public boolean ownsConnection(IGridConnection candidate) { return connection == candidate; }

    public void bind(SequenceWirelessBinding target) {
        disconnect();
        binding = target;
        machine.saveChanges();
    }

    public void clear() {
        disconnect();
        binding = null;
        machine.saveChanges();
    }

    public void save(CompoundTag tag) {
        if (binding == null) tag.remove(BINDING_TAG);
        else tag.put(BINDING_TAG, binding.save());
    }

    public void load(CompoundTag tag) {
        disconnect();
        binding = SequenceWirelessBinding.load(tag.getCompound(BINDING_TAG));
    }

    public QuantumLinkState update(boolean quantumItemPresent, QuantumLinkState connectedState) {
        if (binding == null) return stop(QuantumLinkState.EMPTY);
        if (quantumItemPresent) return stop(QuantumLinkState.QUANTUM_CONFLICT);
        if (!(machine.getBlockEntity().getLevel() instanceof ServerLevel level)) return stop(QuantumLinkState.SEARCHING);
        var localNode = machine.getMainNode().getNode();
        if (localNode == null) return stop(QuantumLinkState.SEARCHING);
        if (!localNode.getInWorldConnections().isEmpty()) return stop(QuantumLinkState.WIRED_CONFLICT);
        var hub = binding.resolve(level.getServer());
        if (hub == null) return stop(QuantumLinkState.REMOTE_MISSING);
        if (!SequenceWirelessAccess.sameTeam(machine, hub)) return stop(QuantumLinkState.ACCESS_DENIED);
        var targetNode = hub.getMainNode().getNode();
        if (targetNode == null || !hub.hasWiredConnection()) return stop(QuantumLinkState.REMOTE_OFFLINE);
        if (localNode == targetNode) return stop(QuantumLinkState.CONNECTION_ERROR);
        if (connection != null && (remoteNode != targetNode
                || !localNode.getConnections().contains(connection)
                || !targetNode.getConnections().contains(connection))) disconnect();
        // Create the topology before checking power/channels so an isolated receiver can start and recover.
        if (connection == null) {
            try {
                connection = GridHelper.createConnection(localNode, targetNode);
                remoteNode = targetNode;
            } catch (IllegalStateException exception) {
                return stop(QuantumLinkState.CONNECTION_ERROR);
            }
        }
        double power = baseIdlePower.getAsDouble() + LINK_POWER;
        if (localNode.getIdlePowerUsage() != power) machine.getMainNode().setIdlePowerUsage(power);
        powerApplied = true;
        return state = localNode.isOnline() ? connectedState : QuantumLinkState.REMOTE_OFFLINE;
    }

    private QuantumLinkState stop(QuantumLinkState nextState) {
        disconnect();
        return state = nextState;
    }

    public void disconnect() {
        var localNode = machine.getMainNode().getNode();
        var oldConnection = connection;
        connection = null;
        remoteNode = null;
        if (oldConnection != null && localNode != null && localNode.getConnections().contains(oldConnection)) oldConnection.destroy();
        if (powerApplied && localNode != null) machine.getMainNode().setIdlePowerUsage(baseIdlePower.getAsDouble());
        powerApplied = false;
    }
}
