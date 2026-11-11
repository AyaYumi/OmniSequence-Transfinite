package com.atir.molecularmanipulator.integration;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Exercises the production reflection/query path with the verified API shapes, without FTB or a server. */
class FtbQuestsProgressTest {
    private static final UUID BOUND_TEAM = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID VIEWER_TEAM = UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final long QUEST_ID = 0xABCD;
    private static final long TASK_ID = 0xBCDE;
    private static final FtbQuestsProgress.Api API = bind(FakeFile.class, FakeTask.class, Object.class);

    private final Object server = new Object();
    private final FakeQuest quest = new FakeQuest("Build the orrery");
    private final FakeTask task = new FakeTask("Supply lenses", 10);
    private final FakeTeamData bound = new FakeTeamData(BOUND_TEAM);
    private final FakeTeamData viewer = new FakeTeamData(VIEWER_TEAM);
    private FakeFile file;

    @BeforeEach void setUp() {
        file = new FakeFile(server);
        file.objects.put(QUEST_ID, quest);
        file.objects.put(TASK_ID, task);
        file.teams.put(BOUND_TEAM, bound);
        file.teams.put(VIEWER_TEAM, viewer);
        FakeFile.INSTANCE = file;
    }

    @AfterEach void noCreationAndNoLeakedSingleton() {
        assertEquals(0, file.creations, "Progress checks must never create team data");
        FakeFile.INSTANCE = null;
    }

    @Test void completedQuestUsesTheBoundTeamRecordAndTitle() {
        bound.completed.add(quest);
        var result = check("0xABCD");
        assertEquals(new FtbQuestsProgress.Result(true, true, "Build the orrery", ""), result);
        assertEquals(BOUND_TEAM, file.lastTeam);
        assertEquals(1, bound.completionReads);
        assertEquals(0, viewer.completionReads);
        assertEquals(0, bound.progressReads);
    }

    @Test void viewerCompletionCannotCompleteTheBoundTeamsQuest() {
        viewer.completed.add(quest);
        var result = check("abcd");
        assertTrue(result.available());
        assertFalse(result.completed());
        assertEquals("Build the orrery", result.title());
        assertFalse(result.reason().isBlank());
        assertEquals(BOUND_TEAM, file.lastTeam);
        assertEquals(0, viewer.completionReads);
    }

    @Test void taskRemainsIncompleteEvenIfItsParentQuestIsCompleted() {
        bound.completed.add(quest);
        bound.progress.put(task, 9L);
        var result = check("bcde");
        assertTrue(result.available());
        assertFalse(result.completed());
        assertEquals("Supply lenses", result.title());
        assertEquals(1, bound.progressReads);
        assertEquals(0, bound.completionReads, "Task checks must not read a completion timestamp or parent");
    }

    @Test void taskAtItsOwnMaximumCompletesWithoutParentOrTimestampCompletion() {
        bound.progress.put(task, 10L);
        assertTrue(check("0XbCdE").completed());
        assertEquals(0, bound.completionReads);
        assertTrue(bound.completed.isEmpty(), "The query must not mark either object completed");
        assertEquals(Map.of(task, 10L), bound.progress);
    }

    @Test void differentChildTasksUseTheirOwnProgressAndMaximum() {
        var other = new FakeTask("Supply motors", 20);
        file.objects.put(0xCDEFL, other);
        bound.progress.put(task, 10L);
        bound.progress.put(other, 10L);
        assertTrue(check("BCDE").completed());
        assertFalse(check("CDEF").completed());
        assertEquals(0, bound.completionReads);
    }

    @Test void absentTaskProgressIsAvailableButNotCompleted() {
        var result = check("BCDE");
        assertTrue(result.available());
        assertFalse(result.completed());
        assertTrue(bound.progress.isEmpty());
    }

    @Test void taskProgressUsesExactLongComparisonNotDoubleOrInt() {
        task.max = 0x20000000000001L;
        bound.progress.put(task, task.max - 1);
        assertFalse(check("BCDE").completed());
        bound.progress.put(task, task.max);
        assertTrue(check("BCDE").completed());
        bound.progress.put(task, Long.MAX_VALUE);
        assertTrue(check("BCDE").completed());
    }

    @Test void highHexadecimalIdsReachTheLookupWithoutJavascriptPrecisionLoss() {
        for (long id : new long[]{0x20000000000001L, 0x7FEDCBA987654321L, Long.MAX_VALUE}) {
            file.objects.put(id, quest);
            bound.completed.add(quest);
            assertTrue(check("0x" + Long.toHexString(id)).completed());
            assertEquals(id, file.lastId);
        }
        assertEquals(16, FtbQuestsProgress.parseHexId("10"), "Unprefixed text is still hexadecimal");
        assertEquals(QUEST_ID, FtbQuestsProgress.parseHexId("  0X000000000000aBcD  "));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "0x", "0X", "0", "0000000000000000", "-1", "+A", "#ABCD", "0x-1",
            "AB CD", "0x1G", "0x0xABCD", "8000000000000000", "FFFFFFFFFFFFFFFF", "10000000000000000", "１２"})
    void invalidIdsFailClosedBeforeDiscoveringTheOptionalApi(String id) {
        assertThrows(IllegalArgumentException.class, () -> FtbQuestsProgress.parseHexId(id));
        var result = FtbQuestsProgress.check(true, server, BOUND_TEAM, id, () -> {
            throw new AssertionError("Invalid IDs must not trigger API discovery");
        });
        assertUnavailable(result, "Invalid FTB Quests ID");
    }

    @Test void missingQuestDoesNotBecomeAnIncompleteOrCompletedRequirement() {
        assertUnavailable(check("1234"), "not found");
        assertEquals(0, bound.completionReads);
        assertNull(file.lastTeam);
    }

    @Test void chaptersAndRewardsAreNotAcceptedAsQuestsOrTasks() {
        file.objects.put(0x1234L, new FakeObject("A chapter, not a quest"));
        assertUnavailable(check("1234"), "not a Quest or Task");
        assertEquals(0, bound.completionReads);
    }

    @Test void missingBoundTeamDoesNotFallBackToViewerOrCreateProgress() {
        file.teams.remove(BOUND_TEAM);
        viewer.completed.add(quest);
        var result = check("ABCD");
        assertUnavailable(result, "bound team data is not ready");
        assertEquals("Build the orrery", result.title());
        assertUnavailable(check("ABCD"), "bound team data is not ready");
        assertEquals(Map.of(VIEWER_TEAM, viewer), file.teams);
        assertEquals(0, viewer.completionReads);
        file.teams.put(BOUND_TEAM, bound);
        bound.completed.add(quest);
        assertTrue(check("ABCD").completed(), "Not-ready results must not be cached");
    }

    @Test void dataForAnUnexpectedTeamIsRejected() {
        file.teams.put(BOUND_TEAM, viewer);
        viewer.completed.add(quest);
        assertUnavailable(check("ABCD"), "different team");
        assertEquals(0, viewer.completionReads);
    }

    @Test void absentModIsCheckedBeforeArgumentsOrOptionalClassDiscovery() {
        var result = FtbQuestsProgress.check(false, null, null, "not hex", () -> {
            throw new AssertionError("An absent mod must never load optional classes");
        });
        assertUnavailable(result, "not installed");
    }

    @Test void publicEntryPointLoadsWithoutFtbQuestsOrAMinecraftServer() {
        assertUnavailable(FtbQuestsProgress.check(null, BOUND_TEAM, "ABCD"), "not installed");
    }

    @Test void missingServerOrBindingDoesNotDiscoverAnApi() {
        assertUnavailable(FtbQuestsProgress.check(true, null, BOUND_TEAM, "ABCD", () -> {
            throw new AssertionError("No server");
        }), "server is not ready");
        assertUnavailable(FtbQuestsProgress.check(true, server, null, "ABCD", () -> {
            throw new AssertionError("No bound team");
        }), "bound team is not ready");
    }

    @Test void singletonReadinessIsLiveRatherThanCachedWithTheAccessors() {
        FakeFile.INSTANCE = null;
        assertUnavailable(check("ABCD"), "server data is not ready");
        FakeFile.INSTANCE = file;
        bound.completed.add(quest);
        assertTrue(check("ABCD").completed());
    }

    @Test void queryRefusesAFileBelongingToAnotherServer() {
        bound.completed.add(quest);
        assertUnavailable(FtbQuestsProgress.check(true, new Object(), BOUND_TEAM, "ABCD", () -> API),
                "server data is not ready");
        assertTrue(check("ABCD").completed());
    }

    @Test void loadingFileIsUnavailableButCanBeQueriedAfterLoading() {
        file.loading = true;
        assertUnavailable(check("ABCD"), "server data is not ready");
        file.loading = false;
        assertTrue(check("ABCD").available());
    }

    @Test void cachedAccessorsDoNotCacheObjectsTitlesOrCompletion() {
        assertFalse(check("ABCD").completed());
        bound.completed.add(quest);
        assertTrue(check("ABCD").completed());
        var replacement = new FakeQuest("Reloaded quest");
        file.objects.put(QUEST_ID, replacement);
        var result = check("ABCD");
        assertTrue(result.available());
        assertFalse(result.completed());
        assertEquals("Reloaded quest", result.title());
    }

    @Test void missingOptionalClassesProduceAnUnavailableReason() {
        var absent = FtbQuestsProgress.Api.discover(new ClassLoader(null) {});
        assertUnavailable(checkWith(absent), "ClassNotFoundException");
    }

    @Test void missingTransitiveDependencyDuringDiscoveryDoesNotEscape() {
        var missingDependency = new ClassLoader(null) {
            @Override public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.startsWith("dev.ftb.mods.ftbquests.")) throw new NoClassDefFoundError("architectury API");
                return super.loadClass(name);
            }
        };
        assertUnavailable(checkWith(FtbQuestsProgress.Api.discover(missingDependency)), "NoClassDefFoundError");
    }

    @Test void missingMembersOrChangedFieldAndMethodTypesAreIncompatible() {
        assertUnavailable(checkWith(bind(Object.class, FakeTask.class, Object.class)), "NoSuchFieldException");
        assertUnavailable(checkWith(bind(FakeFile.class, FakeTask.class, String.class)), "server");
        assertUnavailable(checkWith(bind(FakeFile.class, WrongReturnTask.class, Object.class)), "getMaxProgress");
        assertUnavailable(checkWith(bind(FakeFile.class, StaticMaxTask.class, Object.class)), "getMaxProgress");
    }

    @Test void invocationErrorsAreUnwrappedAndNeverCountAsCompletion() {
        bound.completed.add(quest);
        bound.failure = new IllegalStateException("team data unavailable");
        var result = check("ABCD");
        assertUnavailable(result, "IllegalStateException");
        assertTrue(result.reason().contains("team data unavailable"));
        assertFalse(result.reason().contains("InvocationTargetException"));
        bound.failure = null;
        assertTrue(check("ABCD").completed(), "Transient API errors must not poison cached accessors");
    }

    @Test void missingDependencyDuringTaskProgressReadFailsClosed() {
        bound.progress.put(task, 10L);
        bound.failure = new NoClassDefFoundError("optional task dependency");
        assertUnavailable(check("BCDE"), "NoClassDefFoundError");
    }

    @Test void malformedTitleAndInvalidTaskRangesAreUnavailable() {
        quest.title = null;
        assertUnavailable(check("ABCD"), "NullPointerException");
        task.max = 0;
        assertUnavailable(check("BCDE"), "progress/max is invalid");
        task.max = -1;
        assertUnavailable(check("BCDE"), "progress/max is invalid");
        task.max = 10;
        bound.progress.put(task, -1L);
        assertUnavailable(check("BCDE"), "progress/max is invalid");
    }

    @Test void virtualMachineFailuresAreNotSwallowedByReflection() {
        var fatal = new InternalError("simulated VM failure");
        bound.failure = fatal;
        assertSame(fatal, assertThrows(InternalError.class, () -> check("ABCD")));
    }

    private FtbQuestsProgress.Result check(String id) {
        return FtbQuestsProgress.check(true, server, BOUND_TEAM, id, () -> API);
    }

    private FtbQuestsProgress.Result checkWith(FtbQuestsProgress.Api api) {
        return FtbQuestsProgress.check(true, server, BOUND_TEAM, "ABCD", () -> api);
    }

    private static FtbQuestsProgress.Api bind(Class<?> file, Class<?> task, Class<?> server) {
        return FtbQuestsProgress.Api.bind(file, FakeBase.class, FakeObject.class, FakeQuest.class, task,
                FakeTeamData.class, server);
    }

    private static void assertUnavailable(FtbQuestsProgress.Result result, String reasonFragment) {
        assertFalse(result.available(), result.toString());
        assertFalse(result.completed(), "Unavailable must never mean completed");
        assertTrue(result.reason().contains(reasonFragment), result.reason());
    }

    public static class FakeBase {
        Component title;
        FakeBase(String title) { this.title = Component.literal(title); }
        public Component getTitle() { return title; }
    }

    public static class FakeObject extends FakeBase {
        FakeObject(String title) { super(title); }
    }

    public static class FakeQuest extends FakeObject {
        FakeQuest(String title) { super(title); }
    }

    public static class FakeTask extends FakeObject {
        long max;
        FakeTask(String title, long max) { super(title); this.max = max; }
        public long getMaxProgress() { return max; }
        public FakeQuest getQuest() { throw new AssertionError("Do not substitute the parent quest for this task"); }
    }

    public static class WrongReturnTask extends FakeObject {
        WrongReturnTask() { super("Incompatible task"); }
        public int getMaxProgress() { return 10; }
    }

    public static class StaticMaxTask extends FakeObject {
        StaticMaxTask() { super("Incompatible static task API"); }
        public static long getMaxProgress() { return 10; }
    }

    public static class FakeFile extends FakeBase {
        public static FakeFile INSTANCE;
        public final Object server;
        final Map<Long, FakeBase> objects = new HashMap<>();
        final Map<UUID, FakeTeamData> teams = new HashMap<>();
        boolean loading;
        long lastId;
        UUID lastTeam;
        int creations;

        FakeFile(Object server) { super("Quest file"); this.server = server; }
        public boolean isLoading() { return loading; }
        public FakeBase getBase(long id) { lastId = id; return objects.get(id); }
        public FakeTeamData getNullableTeamData(UUID id) { lastTeam = id; return teams.get(id); }
        public FakeTeamData getOrCreateTeamData(UUID id) {
            creations++;
            throw new AssertionError("Read-only progress checks must not create team data");
        }
    }

    public static class FakeTeamData {
        final UUID id;
        final Set<FakeObject> completed = new HashSet<>();
        final Map<FakeTask, Long> progress = new HashMap<>();
        int completionReads;
        int progressReads;
        Throwable failure;

        FakeTeamData(UUID id) { this.id = id; }
        public UUID getTeamId() { return id; }
        public boolean isCompleted(FakeObject object) {
            completionReads++;
            failIfRequested();
            if (object instanceof FakeTask) throw new AssertionError("Use the task's own progress, not its timestamp");
            return completed.contains(object);
        }
        public long getProgress(FakeTask task) {
            progressReads++;
            failIfRequested();
            return progress.getOrDefault(task, 0L);
        }
        private void failIfRequested() {
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
        }
    }
}
