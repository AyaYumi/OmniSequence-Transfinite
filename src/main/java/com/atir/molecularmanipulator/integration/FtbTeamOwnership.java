package com.atir.molecularmanipulator.integration;

import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Resolves the team at placement without requiring FTB Teams in every modpack. */
public final class FtbTeamOwnership {
    private FtbTeamOwnership() {
    }

    @Nullable
    public static UUID forPlayer(ServerPlayer player) {
        if (!ModList.get().isLoaded("ftbteams")) {
            return player.getUUID();
        }
        try {
            return FtbApi.forPlayer(player);
        } catch (RuntimeException | LinkageError exception) {
            RateLimitedLog.warn("Unable to bind placed singularity to FTB team for {}",
                    player.getUUID(), exception);
            return null;
        }
    }

    /** Also resolves offline placers when migrating older controllers. */
    @Nullable
    public static UUID forPlayerId(UUID playerId) {
        if (playerId == null) return null;
        if (!ModList.get().isLoaded("ftbteams")) return playerId;
        try {
            return FtbApi.forPlayerId(playerId);
        } catch (RuntimeException | LinkageError exception) {
            RateLimitedLog.warn("Unable to resolve FTB team for {}", playerId, exception);
            return null;
        }
    }

    /** Loaded only when FTB Teams is present. Uses its public server API. */
    private static final class FtbApi {
        @Nullable
        private static UUID forPlayerId(UUID playerId) {
            var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
            if (api == null || !api.isManagerLoaded()) return null;
            return api.getManager().getTeamForPlayerID(playerId)
                    .map(dev.ftb.mods.ftbteams.api.Team::getTeamId).orElse(playerId);
        }

        @Nullable
        private static UUID forPlayer(ServerPlayer player) {
            var api = dev.ftb.mods.ftbteams.api.FTBTeamsAPI.api();
            if (api == null || !api.isManagerLoaded()) return null;
            return api.getManager().getTeamForPlayer(player)
                    .map(dev.ftb.mods.ftbteams.api.Team::getTeamId)
                    .orElse(player.getUUID());
        }
    }
}
