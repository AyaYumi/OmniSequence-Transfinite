package com.atir.molecularmanipulator.integration;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.ModList;

/**
 * Optional, read-only FTB Quests bridge. Call on the server thread with the engine's boundTeam, never
 * the current viewer's UUID. No player lookup, team-data creation, detection or completion is run.
 *
 * <p>Verified against FTB Quests v2001.4.9 (Minecraft 1.20.1, Forge):
 * <a href="https://github.com/FTBTeam/FTB-Quests/tree/v2001.4.9/common/src/main/java/dev/ftb/mods/ftbquests/quest">source</a>.
 * ServerQuestFile exposes INSTANCE, server and isLoading(); BaseQuestFile exposes getBase(long) and
 * getNullableTeamData(UUID); TeamData exposes isCompleted(QuestObject) and getProgress(Task).
 * Task.getMaxProgress() returns long. QuestObjectBase.getRawTitle() is server-safe;
 * its getTitle() method is client-only in this Forge release.
 */
public final class FtbQuestsProgress {
    private FtbQuestsProgress() {}

    /** Unavailable always implies not completed; reason is empty only for a completed requirement. */
    public record Result(boolean available, boolean completed, String title, String reason) {}

    /**
     * Check one Quest or Task ID, supplied as hexadecimal text (optionally prefixed with 0x).
     * FTB IDs are positive signed longs: even IDs above JavaScript's safe integer range remain exact.
     * Only reflection metadata is cached; server, team data and progress are read afresh on each call.
     */
    public static Result check(MinecraftServer server, UUID teamId, String hexId) {
        try {
            var mods = ModList.get();
            return check(mods != null && mods.isLoaded("ftbquests"), server, teamId, hexId,
                    () -> ApiHolder.API);
        } catch (RuntimeException | LinkageError failure) {
            return unavailable(apiError(failure));
        }
    }

    // The same lazy, fail-closed path is exercised by fake APIs without booting a Minecraft server.
    static Result check(boolean installed, Object server, UUID teamId, String hexId, Supplier<Api> apiSupplier) {
        if (!installed) return unavailable("FTB Quests is not installed");
        if (server == null) return unavailable("FTB Quests server is not ready");
        if (teamId == null) return unavailable("FTB Quests bound team is not ready");

        final long id;
        try {
            id = parseHexId(hexId);
        } catch (IllegalArgumentException invalid) {
            return unavailable("Invalid FTB Quests ID: expected hexadecimal text in 1..7FFFFFFFFFFFFFFF");
        }

        try {
            var api = apiSupplier.get();
            return api.accessors == null ? unavailable(api.reason) : api.accessors.query(server, teamId, id);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            return unavailable(apiError(failure));
        }
    }

    /** Matches FTB's positive-long ID domain, without any decimal/double conversion or truncation. */
    static long parseHexId(String hexId) {
        if (hexId == null) throw new IllegalArgumentException("Missing ID");
        String digits = hexId.trim();
        if (digits.startsWith("0x") || digits.startsWith("0X")) digits = digits.substring(2);
        if (digits.isEmpty() || digits.length() > 16) throw new IllegalArgumentException("Invalid ID length");
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (!(c >= '0' && c <= '9' || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F')) {
                throw new IllegalArgumentException("Non-hexadecimal ID");
            }
        }
        long id = Long.parseLong(digits, 16);
        if (id <= 0) throw new IllegalArgumentException("Non-positive ID");
        return id;
    }

    private static Result unavailable(String reason) {
        return new Result(false, false, "", reason);
    }

    private static String apiError(Throwable failure) {
        while (failure instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            failure = invocation.getCause();
        }
        if (failure instanceof VirtualMachineError fatal) throw fatal;
        if (failure instanceof ThreadDeath fatal) throw fatal;
        return "FTB Quests API incompatible, failed, or dependency missing (" + failure.getClass().getSimpleName()
                + (failure.getMessage() == null ? "" : ": " + failure.getMessage()) + ")";
    }

    private static final class ApiHolder {
        private static final Api API = Api.discover(FtbQuestsProgress.class.getClassLoader());
    }

    // Both successful and unsuccessful discovery are cached by ApiHolder, not retried every tick.
    record Api(Accessors accessors, String reason) {
        static Api discover(ClassLoader loader) {
            try {
                String pkg = "dev.ftb.mods.ftbquests.quest.";
                return bind(Class.forName(pkg + "ServerQuestFile", false, loader),
                        Class.forName(pkg + "QuestObjectBase", false, loader),
                        Class.forName(pkg + "QuestObject", false, loader),
                        Class.forName(pkg + "Quest", false, loader),
                        Class.forName(pkg + "task.Task", false, loader),
                        Class.forName(pkg + "TeamData", false, loader), MinecraftServer.class);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                return new Api(null, apiError(failure));
            }
        }

        static Api bind(Class<?> file, Class<?> base, Class<?> object, Class<?> quest, Class<?> task,
                        Class<?> data, Class<?> server) {
            try {
                if (!base.isAssignableFrom(object) || !object.isAssignableFrom(quest)
                        || !object.isAssignableFrom(task)) {
                    throw new NoSuchMethodException("Incompatible Quest/Task hierarchy");
                }
                return new Api(new Accessors(quest, task,
                        field(file, "INSTANCE", file, true), field(file, "server", server, false),
                        method(file, "isLoading", boolean.class), method(file, "getBase", base, long.class),
                        method(file, "getNullableTeamData", data, UUID.class),
                        method(data, "getTeamId", UUID.class), method(base, "getRawTitle", String.class),
                        method(data, "isCompleted", boolean.class, object),
                        method(task, "getMaxProgress", long.class), method(data, "getProgress", long.class, task)), "");
            } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
                return new Api(null, apiError(failure));
            }
        }

        private static Field field(Class<?> owner, String name, Class<?> type, boolean isStatic)
                throws NoSuchFieldException {
            Field field = owner.getField(name);
            if (field.getType() != type || Modifier.isStatic(field.getModifiers()) != isStatic) {
                throw new NoSuchFieldException(owner.getName() + "." + name + " has an incompatible type/modifier");
            }
            return field;
        }

        private static Method method(Class<?> owner, String name, Class<?> returns, Class<?>... parameters)
                throws NoSuchMethodException {
            Method method = owner.getMethod(name, parameters);
            if (method.getReturnType() != returns || Modifier.isStatic(method.getModifiers())) {
                throw new NoSuchMethodException(owner.getName() + "." + name + " has an incompatible return type/modifier");
            }
            return method;
        }
    }

    private record Accessors(Class<?> quest, Class<?> task, Field instance, Field owningServer,
                             Method isLoading, Method getBase, Method getNullableTeamData, Method getTeamId,
                             Method getRawTitle, Method isCompleted, Method getMaxProgress, Method getProgress) {
        Result query(Object server, UUID teamId, long id) throws ReflectiveOperationException {
            Object file = instance.get(null);
            if (file == null || owningServer.get(file) != server || (boolean) isLoading.invoke(file)) {
                return unavailable("FTB Quests server data is not ready for this server");
            }

            Object target = getBase.invoke(file, id);
            if (target == null) return unavailable("FTB Quests quest/task not found: " + Long.toHexString(id));
            boolean isTask = task.isInstance(target);
            if (!isTask && !quest.isInstance(target)) {
                return unavailable("FTB Quests ID is not a Quest or Task: " + Long.toHexString(id));
            }
            String title = (String) getRawTitle.invoke(target);
            if (title.isBlank()) title = Long.toHexString(id);

            // This is a plain map lookup, unlike getTeamData(Player) / getOrCreateTeamData(...).
            Object team = getNullableTeamData.invoke(file, teamId);
            if (team == null) {
                return new Result(false, false, title, "FTB Quests bound team data is not ready: " + teamId);
            }
            if (!teamId.equals(getTeamId.invoke(team))) {
                return new Result(false, false, title, "FTB Quests API returned data for a different team");
            }

            final boolean completed;
            if (isTask) {
                long max = (long) getMaxProgress.invoke(target);
                long progress = (long) getProgress.invoke(team, target);
                if (max <= 0 || progress < 0) {
                    return new Result(false, false, title, "FTB Quests task progress/max is invalid");
                }
                // Optional/flexible tasks can disagree with their parent or completion timestamp.
                completed = progress >= max;
            } else {
                completed = (boolean) isCompleted.invoke(team, target);
            }
            return new Result(true, completed, title, completed ? "" : "FTB Quests quest/task is not completed");
        }
    }
}
