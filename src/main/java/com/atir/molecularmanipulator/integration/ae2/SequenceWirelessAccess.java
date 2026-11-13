package com.atir.molecularmanipulator.integration.ae2;

import com.atir.molecularmanipulator.blockentity.MatterFabricationPatternAssemblyBlockEntity;
import com.atir.molecularmanipulator.blockentity.MatterFabricationPortBlockEntity;
import com.atir.molecularmanipulator.blockentity.OmniComputationCoreBlockEntity;
import com.atir.molecularmanipulator.blockentity.SequenceWirelessHubBlockEntity;
import com.atir.molecularmanipulator.building.BuildingTransfer;
import com.atir.molecularmanipulator.integration.FtbTeamOwnership;
import com.atir.molecularmanipulator.recipelock.RecipeMachineOwnership;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/** The UI and memory card share the same server-owned controller and placement-team checks. */
public final class SequenceWirelessAccess {
    private SequenceWirelessAccess() {}

    @Nullable
    public static SequenceWirelessHost host(@Nullable BlockEntity entity) {
        if (entity instanceof MatterFabricationPortBlockEntity port) entity = port.getController();
        if (entity instanceof MatterFabricationPatternAssemblyBlockEntity assembly) entity = assembly.getController();
        if (entity instanceof OmniComputationCoreBlockEntity core && core.isSingleBlock()) return null;
        return entity instanceof SequenceWirelessHost host ? host : null;
    }

    @Nullable
    public static UUID team(SequenceWirelessHost host) {
        var team = RecipeMachineOwnership.team(host.getBlockEntity());
        if (team != null) return team;
        // Older saves may only have AE2's original placer. Never claim on menu open.
        var node = host.getMainNode().getNode();
        return node == null ? null : FtbTeamOwnership.forPlayerId(node.getOwningPlayerProfileId());
    }

    public static boolean canConfigure(ServerPlayer player, SequenceWirelessHost host) {
        var entity = host.getBlockEntity();
        if (!(entity.getLevel() instanceof ServerLevel level) || player.level() != level
                || entity.isRemoved() || !level.hasChunkAt(entity.getBlockPos())
                || level.getBlockEntity(entity.getBlockPos()) != entity
                || !player.mayBuild() || player.isSpectator()
                || !level.mayInteract(player, entity.getBlockPos())
                || BuildingTransfer.locked(level, entity.getBlockPos())) return false;
        if (player.hasPermissions(2)) return true;
        var team = team(host);
        return team != null && team.equals(FtbTeamOwnership.forPlayer(player));
    }

    public static boolean sameTeam(SequenceWirelessHost host, SequenceWirelessHubBlockEntity hub) {
        var team = team(host);
        return team != null && team.equals(hub.getBoundTeam());
    }
}
