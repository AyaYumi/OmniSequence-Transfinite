package com.atir.molecularmanipulator.blockentity;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessBinding;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessHost;
import com.atir.molecularmanipulator.integration.ae2.SequenceWirelessHubs;
import com.atir.molecularmanipulator.registry.WirelessContent;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;

public final class SequenceWirelessHubBlockEntity extends AENetworkedBlockEntity {
    public static final double IDLE_POWER = 64.0;
    private UUID hubId = UUID.randomUUID();
    private UUID boundTeam;

    public SequenceWirelessHubBlockEntity(BlockPos pos, BlockState state) {
        super(WirelessContent.HUB_BE.get(), pos, state);
        getMainNode().setFlags(GridFlags.DENSE_CAPACITY).setIdlePowerUsage(IDLE_POWER);
    }

    @Override
    protected Item getItemFromBlockEntity() {
        return getBlockState().getBlock().asItem();
    }

    public UUID getHubId() {
        return hubId;
    }

    public SequenceWirelessBinding binding() {
        return new SequenceWirelessBinding(getGlobalPos(), hubId);
    }

    public boolean hasWiredConnection() {
        var node = getMainNode().getNode();
        return node != null && !node.getInWorldConnections().isEmpty();
    }

    public boolean isOnline() {
        var node = getMainNode().getNode();
        return hasWiredConnection() && node.isOnline();
    }

    public int connectedMachines() {
        var node = getMainNode().getNode();
        if (node == null) return 0;
        int count = 0;
        for (var connection : node.getConnections()) {
            if (connection.getOtherSide(node).getOwner() instanceof SequenceWirelessHost host
                    && host.getWirelessLink().ownsConnection(connection)) count++;
        }
        return count;
    }

    public UUID getBoundTeam() {
        if (boundTeam == null && level instanceof ServerLevel) {
            var node = getMainNode().getNode();
            var team = node == null ? null : FtbTeamOwnership.forPlayerId(node.getOwningPlayerProfileId());
            if (team != null) {
                boundTeam = team;
                saveChanges();
            }
        }
        return boundTeam;
    }

    public boolean canManage(ServerPlayer player) {
        if (isRemoved() || !(level instanceof ServerLevel serverLevel) || player.getServer() != serverLevel.getServer()) return false;
        if (player.hasPermissions(2)) return true;
        var team = getBoundTeam();
        return team != null && team.equals(FtbTeamOwnership.forPlayer(player));
    }

    @Override
    public void onReady() {
        super.onReady();
        SequenceWirelessHubs.add(this);
    }

    @Override
    public void onChunkUnloaded() {
        SequenceWirelessHubs.remove(this);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        SequenceWirelessHubs.remove(this);
        super.setRemoved();
    }

    @Override
    public void setOwner(Player player) {
        getMainNode().setOwningPlayer(player);
        if (player instanceof ServerPlayer serverPlayer) {
            boundTeam = FtbTeamOwnership.forPlayer(serverPlayer);
            saveChanges();
        }
    }

    @Override
    public AECableType getCableConnectionType(Direction direction) {
        return AECableType.DENSE_SMART;
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        markForUpdate();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("sequence_hub_id", hubId);
        if (boundTeam != null) tag.putUUID("sequence_hub_team", boundTeam);
        else tag.remove("sequence_hub_team");
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        hubId = tag.hasUUID("sequence_hub_id") ? tag.getUUID("sequence_hub_id") : UUID.randomUUID();
        boundTeam = tag.hasUUID("sequence_hub_team") ? tag.getUUID("sequence_hub_team") : null;
    }
}
