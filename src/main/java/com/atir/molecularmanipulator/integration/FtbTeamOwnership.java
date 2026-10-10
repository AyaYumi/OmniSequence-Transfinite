package com.atir.molecularmanipulator.integration;

import com.atir.molecularmanipulator.diagnostics.RateLimitedLog;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
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
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            RateLimitedLog.warn("Unable to resolve FTB team for {}", playerId, exception);
            return null;
        }
    }

    /** Uses the optional public API without resolving FTB classes during mod loading. */
    private static final class FtbApi {
        private static UUID forPlayerId(UUID playerId) throws ReflectiveOperationException {
            var apiClass = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI");
            var api = apiClass.getMethod("api").invoke(null);
            var contract = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI$API");
            if (api == null || !(boolean) contract.getMethod("isManagerLoaded").invoke(api)) return null;
            var manager = contract.getMethod("getManager").invoke(api);
            var managerClass = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            var team = (java.util.Optional<?>) managerClass.getMethod("getTeamForPlayerID", UUID.class).invoke(manager, playerId);
            if (team.isEmpty()) return playerId;
            return (UUID) Class.forName("dev.ftb.mods.ftbteams.api.Team").getMethod("getTeamId").invoke(team.get());
        }
        private static UUID forPlayer(ServerPlayer player) throws ReflectiveOperationException { return forPlayerId(player.getUUID()); }
    }
}
