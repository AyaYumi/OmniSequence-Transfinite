package com.atir.molecularmanipulator.verification;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/** Uses the real optional FTB implementation while leaving baseline tests independent of it. */
public final class FtbTeamFixture {
    private FtbTeamFixture() {}
    private static Object call(Object target, String name, Object... args) throws Exception {
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            boolean matches = true;
            for (int i = 0; i < args.length; i++)
                if (args[i] != null && !method.getParameterTypes()[i].isInstance(args[i])) matches = false;
            if (matches) return method.invoke(target, args);
        }
        throw new NoSuchMethodException(target.getClass().getName() + "." + name);
    }
    private static Object manager() throws Exception {
        Object api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
        return call(api, "getManager");
    }
    public static UUID sharedParty(ServerPlayer owner, ServerPlayer member) throws Exception {
        Object manager = manager();
        call(manager, "playerLoggedIn", null, owner.getUUID(), owner.getGameProfile().getName());
        call(manager, "playerLoggedIn", null, member.getUUID(), member.getGameProfile().getName());
        Object party = call(manager, "createParty", owner.getUUID(), null, "OmniSyncFixture_" + owner.getUUID().toString().substring(0, 8), null, null);
        call(party, "invite", owner, java.util.List.of(member.getGameProfile()));
        call(party, "join", member);
        return (UUID) call(party, "getTeamId");
    }
    public static void leaveParty(ServerPlayer player) throws Exception {
        Object party = ((Optional<?>) call(manager(), "getTeamForPlayer", player)).orElseThrow();
        call(party, "leave", player.getUUID());
    }
}
